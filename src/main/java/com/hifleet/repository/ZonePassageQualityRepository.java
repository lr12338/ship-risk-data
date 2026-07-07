package com.hifleet.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

/**
 * 区域通行数据质量评估结果持久化。
 */
@Repository
public class ZonePassageQualityRepository {

    private static final String VALID_CATEGORIES = "'STORM_WAVE','STORM_WIND','STRAIT','SEA_AREA','BRIDGE',"
            + "'WIND_FARM','HIGH_RISK','ICE','MARINE_PROTECTED','AQUACULTURE','EMISSION_CONTROL','MARPOL','JWC'";

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate jdbc;

    public boolean existsReport(String batchMonth, String startTime, String endTime) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataanalysis.ship_zone_passage_quality_report "
                        + "WHERE batch_month = ? AND start_time = ? AND end_time = ?",
                new Object[]{batchMonth, startTime, endTime}, Integer.class);
        return count != null && count > 0;
    }

    public List<BatchKey> findComputedBatches(String fromTime, String toTime) {
        String sql = "SELECT batch_month, start_time, end_time, "
                + "SUM(CASE WHEN status = 'DONE' THEN 1 ELSE 0 END) AS done_cnt, "
                + "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed_cnt "
                + "FROM dataanalysis.ship_zone_passage_status "
                + "WHERE start_time >= ? AND start_time < ? "
                + "GROUP BY batch_month, start_time, end_time "
                + "HAVING done_cnt > 0 ORDER BY start_time";
        return jdbc.query(sql, new Object[]{fromTime, toTime}, (rs, i) -> new BatchKey(
                rs.getString("batch_month"),
                formatTs(rs.getTimestamp("start_time")),
                formatTs(rs.getTimestamp("end_time")),
                rs.getInt("done_cnt"),
                rs.getInt("failed_cnt")));
    }

    public List<BatchKey> findFullyCompletedBatches(String fromTime, String toTime, int expectedShips) {
        String sql = "SELECT batch_month, start_time, end_time, "
                + "SUM(CASE WHEN status = 'DONE' THEN 1 ELSE 0 END) AS done_cnt, "
                + "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed_cnt "
                + "FROM dataanalysis.ship_zone_passage_status "
                + "WHERE start_time >= ? AND start_time < ? "
                + "GROUP BY batch_month, start_time, end_time "
                + "HAVING done_cnt >= ? AND failed_cnt = 0 ORDER BY start_time";
        return jdbc.query(sql, new Object[]{fromTime, toTime, expectedShips}, (rs, i) -> new BatchKey(
                rs.getString("batch_month"),
                formatTs(rs.getTimestamp("start_time")),
                formatTs(rs.getTimestamp("end_time")),
                rs.getInt("done_cnt"),
                rs.getInt("failed_cnt")));
    }

    public int countShipsByStatus(String batchMonth, String startTime, String endTime, String status) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataanalysis.ship_zone_passage_status "
                        + "WHERE batch_month = ? AND start_time = ? AND end_time = ? AND status = ?",
                new Object[]{batchMonth, startTime, endTime, status}, Integer.class);
        return c == null ? 0 : c;
    }

    public int sumEventCountInStatus(String batchMonth, String startTime, String endTime) {
        Integer s = jdbc.queryForObject(
                "SELECT COALESCE(SUM(event_count), 0) FROM dataanalysis.ship_zone_passage_status "
                        + "WHERE batch_month = ? AND start_time = ? AND end_time = ? AND status = 'DONE'",
                new Object[]{batchMonth, startTime, endTime}, Integer.class);
        return s == null ? 0 : s;
    }

    public int countActualEvents(String startTime, String endTime) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataanalysis.ship_zone_crossing_event "
                        + "WHERE event_time >= ? AND event_time < ?",
                new Object[]{startTime, endTime}, Integer.class);
        return c == null ? 0 : c;
    }

    public List<EventCountMismatch> findEventCountMismatches(String batchMonth, String startTime, String endTime) {
        String sql = "SELECT s.mmsi, s.event_count AS status_cnt, COUNT(e.id) AS actual_cnt "
                + "FROM dataanalysis.ship_zone_passage_status s "
                + "LEFT JOIN dataanalysis.ship_zone_crossing_event e "
                + "  ON e.mmsi = s.mmsi AND e.event_time >= s.start_time AND e.event_time < s.end_time "
                + "WHERE s.batch_month = ? AND s.start_time = ? AND s.end_time = ? AND s.status = 'DONE' "
                + "GROUP BY s.mmsi, s.event_count HAVING status_cnt <> actual_cnt LIMIT 500";
        return jdbc.query(sql, new Object[]{batchMonth, startTime, endTime},
                (rs, i) -> new EventCountMismatch(rs.getString("mmsi"), rs.getInt("status_cnt"), rs.getInt("actual_cnt")));
    }

    public List<UnbalancedPair> findUnbalancedPairs(String startTime, String endTime) {
        String sql = "SELECT mmsi, zone_id, zone_name, event_category, "
                + "SUM(CASE WHEN event_type = 'ENTER' THEN 1 ELSE 0 END) AS enter_cnt, "
                + "SUM(CASE WHEN event_type = 'EXIT' THEN 1 ELSE 0 END) AS exit_cnt "
                + "FROM dataanalysis.ship_zone_crossing_event "
                + "WHERE event_time >= ? AND event_time < ? "
                + "GROUP BY mmsi, zone_id, zone_name, event_category "
                + "HAVING enter_cnt <> exit_cnt LIMIT 500";
        return jdbc.query(sql, new Object[]{startTime, endTime},
                (rs, i) -> new UnbalancedPair(rs.getString("mmsi"), rs.getInt("zone_id"),
                        rs.getString("zone_name"), rs.getString("event_category"),
                        rs.getInt("enter_cnt"), rs.getInt("exit_cnt")));
    }

    public int countUnbalancedPairGroups(String startTime, String endTime) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ("
                        + "SELECT mmsi, zone_id, event_category FROM dataanalysis.ship_zone_crossing_event "
                        + "WHERE event_time >= ? AND event_time < ? "
                        + "GROUP BY mmsi, zone_id, event_category "
                        + "HAVING SUM(CASE WHEN event_type='ENTER' THEN 1 ELSE 0 END) "
                        + "     <> SUM(CASE WHEN event_type='EXIT' THEN 1 ELSE 0 END)"
                        + ") t",
                new Object[]{startTime, endTime}, Integer.class);
        return c == null ? 0 : c;
    }

    public int countNullFieldEvents(String startTime, String endTime) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataanalysis.ship_zone_crossing_event "
                        + "WHERE event_time >= ? AND event_time < ? "
                        + "AND (lon IS NULL OR lat IS NULL OR event_category IS NULL OR event_type IS NULL OR mmsi IS NULL)",
                new Object[]{startTime, endTime}, Integer.class);
        return c == null ? 0 : c;
    }

    public int countOutOfRangeEvents(String batchMonth, String startTime, String endTime) {
        String sql = "SELECT COUNT(*) FROM dataanalysis.ship_zone_crossing_event e "
                + "INNER JOIN dataanalysis.ship_zone_passage_status s ON e.mmsi = s.mmsi "
                + "WHERE s.batch_month = ? AND s.start_time = ? AND s.end_time = ? AND s.status = 'DONE' "
                + "AND (e.event_time < s.start_time OR e.event_time >= s.end_time)";
        Integer c = jdbc.queryForObject(sql,
                new Object[]{batchMonth, startTime, endTime}, Integer.class);
        return c == null ? 0 : c;
    }

    public int countInvalidCategoryEvents(String startTime, String endTime) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataanalysis.ship_zone_crossing_event "
                        + "WHERE event_time >= ? AND event_time < ? "
                        + "AND event_category NOT IN (" + VALID_CATEGORIES + ")",
                new Object[]{startTime, endTime}, Integer.class);
        return c == null ? 0 : c;
    }

    public List<DuplicateEvent> findDuplicateEvents(String startTime, String endTime) {
        String sql = "SELECT mmsi, zone_id, event_category, event_type, event_time, COUNT(*) AS dup_cnt "
                + "FROM dataanalysis.ship_zone_crossing_event "
                + "WHERE event_time >= ? AND event_time < ? "
                + "GROUP BY mmsi, zone_id, event_category, event_type, event_time "
                + "HAVING dup_cnt > 1 LIMIT 200";
        return jdbc.query(sql, new Object[]{startTime, endTime},
                (rs, i) -> new DuplicateEvent(rs.getString("mmsi"), rs.getInt("zone_id"),
                        rs.getString("event_category"), rs.getString("event_type"),
                        formatTs(rs.getTimestamp("event_time")), rs.getInt("dup_cnt")));
    }

    public int countDuplicateEventGroups(String startTime, String endTime) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ("
                        + "SELECT mmsi, zone_id, event_category, event_type, event_time "
                        + "FROM dataanalysis.ship_zone_crossing_event "
                        + "WHERE event_time >= ? AND event_time < ? "
                        + "GROUP BY mmsi, zone_id, event_category, event_type, event_time "
                        + "HAVING COUNT(*) > 1"
                        + ") t",
                new Object[]{startTime, endTime}, Integer.class);
        return c == null ? 0 : c;
    }

    public List<StoredEvent> findEventsForOrderingCheck(String startTime, String endTime) {
        String sql = "SELECT mmsi, zone_id, zone_name, event_category, event_type, event_time "
                + "FROM dataanalysis.ship_zone_crossing_event "
                + "WHERE event_time >= ? AND event_time < ? "
                + "ORDER BY mmsi, zone_id, event_category, event_time LIMIT 50000";
        return jdbc.query(sql, new Object[]{startTime, endTime},
                (rs, i) -> new StoredEvent(rs.getString("mmsi"), rs.getInt("zone_id"),
                        rs.getString("zone_name"), rs.getString("event_category"),
                        rs.getString("event_type"), formatTs(rs.getTimestamp("event_time")),
                        null, null));
    }

    public List<StoredEvent> findEventsForSpatialSample(String startTime, String endTime, int limit) {
        String sql = "SELECT mmsi, zone_id, zone_name, event_category, event_type, event_time, lon, lat "
                + "FROM dataanalysis.ship_zone_crossing_event "
                + "WHERE event_time >= ? AND event_time < ? "
                + "AND event_category NOT IN ('STORM_WAVE','STORM_WIND') "
                + "AND lon IS NOT NULL AND lat IS NOT NULL "
                + "ORDER BY RAND() LIMIT ?";
        return jdbc.query(sql, new Object[]{startTime, endTime, limit},
                (rs, i) -> new StoredEvent(rs.getString("mmsi"), rs.getInt("zone_id"),
                        rs.getString("zone_name"), rs.getString("event_category"),
                        rs.getString("event_type"), formatTs(rs.getTimestamp("event_time")),
                        rs.getDouble("lon"), rs.getDouble("lat")));
    }

    public List<StoredEvent> findStoredEventsByMmsi(String mmsi, String startTime, String endTime) {
        String sql = "SELECT mmsi, zone_id, zone_name, event_category, event_type, event_time, lon, lat "
                + "FROM dataanalysis.ship_zone_crossing_event "
                + "WHERE mmsi = ? AND event_time >= ? AND event_time < ? "
                + "ORDER BY event_time";
        return jdbc.query(sql, new Object[]{mmsi, startTime, endTime},
                (rs, i) -> new StoredEvent(rs.getString("mmsi"), rs.getInt("zone_id"),
                        rs.getString("zone_name"), rs.getString("event_category"),
                        rs.getString("event_type"), formatTs(rs.getTimestamp("event_time")),
                        rs.getDouble("lon"), rs.getDouble("lat")));
    }

    public List<String> findDoneMmsiForBatch(String batchMonth, String startTime, String endTime) {
        String sql = "SELECT mmsi FROM dataanalysis.ship_zone_passage_status "
                + "WHERE batch_month = ? AND start_time = ? AND end_time = ? AND status = 'DONE'";
        List<String> list = jdbc.query(sql, new Object[]{batchMonth, startTime, endTime},
                (rs, i) -> rs.getString("mmsi"));
        return list == null ? Collections.<String>emptyList() : list;
    }

    /**
     * 批次内 DONE 船舶及其船型、吨位（关联 ais41.recentships）。
     */
    public List<ShipProfile> findDoneShipProfiles(String batchMonth, String startTime, String endTime) {
        String sql = "SELECT s.mmsi, COALESCE(r.type, '未知') AS ship_type, "
                + "COALESCE(r.dwt, 0) AS dwt, COALESCE(r.length, '0') AS ship_length "
                + "FROM dataanalysis.ship_zone_passage_status s "
                + "LEFT JOIN ais41.recentships r ON CAST(r.mmsi AS CHAR) = s.mmsi "
                + "WHERE s.batch_month = ? AND s.start_time = ? AND s.end_time = ? AND s.status = 'DONE'";
        return jdbc.query(sql, new Object[]{batchMonth, startTime, endTime},
                (rs, i) -> new ShipProfile(
                        rs.getString("mmsi"),
                        rs.getString("ship_type"),
                        rs.getInt("dwt"),
                        parseShipLength(rs.getString("ship_length"))));
    }

    private static double parseShipLength(String length) {
        if (length == null || length.trim().isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(length.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public List<StoredEvent> findRandomEventsByMmsi(String mmsi, String startTime, String endTime, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        String sql = "SELECT mmsi, zone_id, zone_name, event_category, event_type, event_time, lon, lat "
                + "FROM dataanalysis.ship_zone_crossing_event "
                + "WHERE mmsi = ? AND event_time >= ? AND event_time < ? "
                + "AND event_category NOT IN ('STORM_WAVE','STORM_WIND') "
                + "AND lon IS NOT NULL AND lat IS NOT NULL "
                + "ORDER BY RAND() LIMIT ?";
        return jdbc.query(sql, new Object[]{mmsi, startTime, endTime, limit},
                (rs, i) -> new StoredEvent(rs.getString("mmsi"), rs.getInt("zone_id"),
                        rs.getString("zone_name"), rs.getString("event_category"),
                        rs.getString("event_type"), formatTs(rs.getTimestamp("event_time")),
                        rs.getDouble("lon"), rs.getDouble("lat")));
    }

    /** @deprecated 使用分层抽样 {@link #findDoneShipProfiles} + QualityStratifiedSampler */
    public List<String> findDoneMmsiSample(String batchMonth, String startTime, String endTime, int limit) {
        String sql = "SELECT mmsi FROM dataanalysis.ship_zone_passage_status "
                + "WHERE batch_month = ? AND start_time = ? AND end_time = ? AND status = 'DONE' "
                + "ORDER BY RAND() LIMIT ?";
        List<String> list = jdbc.query(sql, new Object[]{batchMonth, startTime, endTime, limit},
                (rs, i) -> rs.getString("mmsi"));
        return list == null ? Collections.<String>emptyList() : list;
    }

    public List<String> findFailedMmsi(String batchMonth, String startTime, String endTime) {
        String sql = "SELECT mmsi FROM dataanalysis.ship_zone_passage_status "
                + "WHERE batch_month = ? AND start_time = ? AND end_time = ? AND status = 'FAILED' LIMIT 200";
        List<String> list = jdbc.query(sql, new Object[]{batchMonth, startTime, endTime},
                (rs, i) -> rs.getString("mmsi"));
        return list == null ? Collections.<String>emptyList() : list;
    }

    public List<CategoryStatRow> findCategoryStats(String startTime, String endTime) {
        String sql = "SELECT event_category, "
                + "COUNT(*) AS event_cnt, COUNT(DISTINCT mmsi) AS ship_cnt, "
                + "SUM(CASE WHEN event_type='ENTER' THEN 1 ELSE 0 END) AS enter_cnt, "
                + "SUM(CASE WHEN event_type='EXIT' THEN 1 ELSE 0 END) AS exit_cnt "
                + "FROM dataanalysis.ship_zone_crossing_event "
                + "WHERE event_time >= ? AND event_time < ? "
                + "GROUP BY event_category ORDER BY event_cnt DESC";
        return jdbc.query(sql, new Object[]{startTime, endTime},
                (rs, i) -> new CategoryStatRow(rs.getString("event_category"),
                        rs.getInt("event_cnt"), rs.getInt("ship_cnt"),
                        rs.getInt("enter_cnt"), rs.getInt("exit_cnt")));
    }

    public int countUnbalancedByCategory(String startTime, String endTime, String category) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ("
                        + "SELECT mmsi, zone_id FROM dataanalysis.ship_zone_crossing_event "
                        + "WHERE event_time >= ? AND event_time < ? AND event_category = ? "
                        + "GROUP BY mmsi, zone_id "
                        + "HAVING SUM(CASE WHEN event_type='ENTER' THEN 1 ELSE 0 END) "
                        + "     <> SUM(CASE WHEN event_type='EXIT' THEN 1 ELSE 0 END)"
                        + ") t",
                new Object[]{startTime, endTime, category}, Integer.class);
        return c == null ? 0 : c;
    }

    public List<ZoneStatRow> findZoneStats(String startTime, String endTime) {
        String sql = "SELECT zone_id, zone_name, event_category, "
                + "COUNT(*) AS event_cnt, COUNT(DISTINCT mmsi) AS ship_cnt, "
                + "SUM(CASE WHEN event_type='ENTER' THEN 1 ELSE 0 END) AS enter_cnt, "
                + "SUM(CASE WHEN event_type='EXIT' THEN 1 ELSE 0 END) AS exit_cnt "
                + "FROM dataanalysis.ship_zone_crossing_event "
                + "WHERE event_time >= ? AND event_time < ? "
                + "GROUP BY zone_id, zone_name, event_category ORDER BY event_cnt DESC LIMIT 300";
        return jdbc.query(sql, new Object[]{startTime, endTime},
                (rs, i) -> new ZoneStatRow(rs.getInt("zone_id"), rs.getString("zone_name"),
                        rs.getString("event_category"), rs.getInt("event_cnt"), rs.getInt("ship_cnt"),
                        rs.getInt("enter_cnt"), rs.getInt("exit_cnt")));
    }

    public int countUnbalancedByZone(String startTime, String endTime, int zoneId, String category) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ("
                        + "SELECT mmsi FROM dataanalysis.ship_zone_crossing_event "
                        + "WHERE event_time >= ? AND event_time < ? AND zone_id = ? AND event_category = ? "
                        + "GROUP BY mmsi "
                        + "HAVING SUM(CASE WHEN event_type='ENTER' THEN 1 ELSE 0 END) "
                        + "     <> SUM(CASE WHEN event_type='EXIT' THEN 1 ELSE 0 END)"
                        + ") t",
                new Object[]{startTime, endTime, zoneId, category}, Integer.class);
        return c == null ? 0 : c;
    }

    public long insertReport(QualityReport report) {
        String sql = "INSERT INTO dataanalysis.ship_zone_passage_quality_report "
                + "(batch_month, start_time, end_time, quality_status, quality_score, "
                + "total_ships_expected, total_ships_done, total_ships_failed, completion_rate, "
                + "total_events_in_status, total_events_actual, error_count, warn_count, "
                + "event_count_mismatch_ships, unbalanced_pair_groups, null_field_events, out_of_range_events, "
                + "duplicate_event_groups, enter_after_exit_pairs, invalid_category_events, "
                + "replay_sample_ships, replay_mismatch_ships, spatial_check_events, spatial_mismatch_events, "
                + "check_results_json, issue_summary) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setString(i++, report.getBatchMonth());
            ps.setTimestamp(i++, Timestamp.valueOf(report.getStartTime()));
            ps.setTimestamp(i++, Timestamp.valueOf(report.getEndTime()));
            ps.setString(i++, report.getQualityStatus());
            ps.setInt(i++, report.getQualityScore());
            ps.setInt(i++, report.getTotalShipsExpected());
            ps.setInt(i++, report.getTotalShipsDone());
            ps.setInt(i++, report.getTotalShipsFailed());
            ps.setBigDecimal(i++, report.getCompletionRate());
            ps.setInt(i++, report.getTotalEventsInStatus());
            ps.setInt(i++, report.getTotalEventsActual());
            ps.setInt(i++, report.getErrorCount());
            ps.setInt(i++, report.getWarnCount());
            ps.setInt(i++, report.getEventCountMismatchShips());
            ps.setInt(i++, report.getUnbalancedPairGroups());
            ps.setInt(i++, report.getNullFieldEvents());
            ps.setInt(i++, report.getOutOfRangeEvents());
            ps.setInt(i++, report.getDuplicateEventGroups());
            ps.setInt(i++, report.getEnterAfterExitPairs());
            ps.setInt(i++, report.getInvalidCategoryEvents());
            ps.setInt(i++, report.getReplaySampleShips());
            ps.setInt(i++, report.getReplayMismatchShips());
            ps.setInt(i++, report.getSpatialCheckEvents());
            ps.setInt(i++, report.getSpatialMismatchEvents());
            ps.setString(i++, report.getCheckResultsJson());
            ps.setString(i, report.getIssueSummary());
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? 0L : key.longValue();
    }

    public void insertCategoryStats(final List<CategoryStatRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO dataanalysis.ship_zone_passage_quality_category_stat "
                + "(report_id, event_category, event_count, ship_count, enter_count, exit_count, "
                + "passage_pairs, unbalanced_groups, avg_dwell_minutes) VALUES (?,?,?,?,?,?,?,?,?)";
        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int idx) throws SQLException {
                CategoryStatRecord r = records.get(idx);
                ps.setLong(1, r.getReportId());
                ps.setString(2, r.getEventCategory());
                ps.setInt(3, r.getEventCount());
                ps.setInt(4, r.getShipCount());
                ps.setInt(5, r.getEnterCount());
                ps.setInt(6, r.getExitCount());
                ps.setInt(7, r.getPassagePairs());
                ps.setInt(8, r.getUnbalancedGroups());
                if (r.getAvgDwellMinutes() == null) {
                    ps.setObject(9, null);
                } else {
                    ps.setBigDecimal(9, r.getAvgDwellMinutes());
                }
            }

            @Override
            public int getBatchSize() {
                return records.size();
            }
        });
    }

    public void insertZoneStats(final List<ZoneStatRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO dataanalysis.ship_zone_passage_quality_zone_stat "
                + "(report_id, zone_id, zone_name, event_category, event_count, ship_count, "
                + "enter_count, exit_count, unbalanced_groups) VALUES (?,?,?,?,?,?,?,?,?)";
        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int idx) throws SQLException {
                ZoneStatRecord r = records.get(idx);
                ps.setLong(1, r.getReportId());
                ps.setInt(2, r.getZoneId());
                ps.setString(3, r.getZoneName());
                ps.setString(4, r.getEventCategory());
                ps.setInt(5, r.getEventCount());
                ps.setInt(6, r.getShipCount());
                ps.setInt(7, r.getEnterCount());
                ps.setInt(8, r.getExitCount());
                ps.setInt(9, r.getUnbalancedGroups());
            }

            @Override
            public int getBatchSize() {
                return records.size();
            }
        });
    }

    public void insertChecks(final List<CheckRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO dataanalysis.ship_zone_passage_quality_check "
                + "(report_id, check_code, check_name, severity, issue_count, detail) VALUES (?,?,?,?,?,?)";
        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int idx) throws SQLException {
                CheckRecord r = records.get(idx);
                ps.setLong(1, r.getReportId());
                ps.setString(2, r.getCheckCode());
                ps.setString(3, r.getCheckName());
                ps.setString(4, r.getSeverity());
                ps.setInt(5, r.getIssueCount());
                ps.setString(6, r.getDetail());
            }

            @Override
            public int getBatchSize() {
                return records.size();
            }
        });
    }

    public void insertIssues(final List<QualityIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO dataanalysis.ship_zone_passage_quality_issue "
                + "(report_id, check_code, issue_type, severity, mmsi, zone_id, zone_name, event_category, detail) "
                + "VALUES (?,?,?,?,?,?,?,?,?)";
        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                QualityIssue issue = issues.get(i);
                ps.setLong(1, issue.getReportId());
                ps.setString(2, issue.getCheckCode());
                ps.setString(3, issue.getIssueType());
                ps.setString(4, issue.getSeverity());
                ps.setString(5, issue.getMmsi());
                if (issue.getZoneId() == null) {
                    ps.setObject(6, null);
                } else {
                    ps.setInt(6, issue.getZoneId());
                }
                ps.setString(7, issue.getZoneName());
                ps.setString(8, issue.getEventCategory());
                ps.setString(9, issue.getDetail());
            }

            @Override
            public int getBatchSize() {
                return issues.size();
            }
        });
    }

    public void deleteReport(String batchMonth, String startTime, String endTime) {
        List<Long> ids = jdbc.query(
                "SELECT id FROM dataanalysis.ship_zone_passage_quality_report "
                        + "WHERE batch_month = ? AND start_time = ? AND end_time = ?",
                new Object[]{batchMonth, startTime, endTime}, (rs, i) -> rs.getLong("id"));
        if (ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            jdbc.update("DELETE FROM dataanalysis.ship_zone_passage_quality_issue WHERE report_id = ?", id);
            jdbc.update("DELETE FROM dataanalysis.ship_zone_passage_quality_check WHERE report_id = ?", id);
            jdbc.update("DELETE FROM dataanalysis.ship_zone_passage_quality_category_stat WHERE report_id = ?", id);
            jdbc.update("DELETE FROM dataanalysis.ship_zone_passage_quality_zone_stat WHERE report_id = ?", id);
        }
        jdbc.update("DELETE FROM dataanalysis.ship_zone_passage_quality_report "
                        + "WHERE batch_month = ? AND start_time = ? AND end_time = ?",
                batchMonth, startTime, endTime);
    }

    private static String formatTs(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // ---- data classes ----

    public static final class ShipProfile {
        private final String mmsi;
        private final String shipType;
        private final int dwt;
        private final double lengthMeters;

        public ShipProfile(String mmsi, String shipType, int dwt, double lengthMeters) {
            this.mmsi = mmsi;
            this.shipType = shipType;
            this.dwt = dwt;
            this.lengthMeters = lengthMeters;
        }

        public String getMmsi() { return mmsi; }
        public String getShipType() { return shipType; }
        public int getDwt() { return dwt; }
        public double getLengthMeters() { return lengthMeters; }
    }

    public static final class BatchKey {
        private final String batchMonth;
        private final String startTime;
        private final String endTime;
        private final int doneCount;
        private final int failedCount;

        public BatchKey(String batchMonth, String startTime, String endTime, int doneCount, int failedCount) {
            this.batchMonth = batchMonth;
            this.startTime = startTime;
            this.endTime = endTime;
            this.doneCount = doneCount;
            this.failedCount = failedCount;
        }

        public String getBatchMonth() { return batchMonth; }
        public String getStartTime() { return startTime; }
        public String getEndTime() { return endTime; }
        public int getDoneCount() { return doneCount; }
        public int getFailedCount() { return failedCount; }
    }

    public static final class EventCountMismatch {
        private final String mmsi;
        private final int statusCount;
        private final int actualCount;
        public EventCountMismatch(String mmsi, int statusCount, int actualCount) {
            this.mmsi = mmsi; this.statusCount = statusCount; this.actualCount = actualCount;
        }
        public String getMmsi() { return mmsi; }
        public int getStatusCount() { return statusCount; }
        public int getActualCount() { return actualCount; }
    }

    public static final class UnbalancedPair {
        private final String mmsi;
        private final int zoneId;
        private final String zoneName;
        private final String eventCategory;
        private final int enterCount;
        private final int exitCount;
        public UnbalancedPair(String mmsi, int zoneId, String zoneName, String eventCategory,
                              int enterCount, int exitCount) {
            this.mmsi = mmsi; this.zoneId = zoneId; this.zoneName = zoneName;
            this.eventCategory = eventCategory; this.enterCount = enterCount; this.exitCount = exitCount;
        }
        public String getMmsi() { return mmsi; }
        public int getZoneId() { return zoneId; }
        public String getZoneName() { return zoneName; }
        public String getEventCategory() { return eventCategory; }
        public int getEnterCount() { return enterCount; }
        public int getExitCount() { return exitCount; }
    }

    public static final class DuplicateEvent {
        private final String mmsi;
        private final int zoneId;
        private final String eventCategory;
        private final String eventType;
        private final String eventTime;
        private final int dupCount;
        public DuplicateEvent(String mmsi, int zoneId, String eventCategory, String eventType,
                              String eventTime, int dupCount) {
            this.mmsi = mmsi; this.zoneId = zoneId; this.eventCategory = eventCategory;
            this.eventType = eventType; this.eventTime = eventTime; this.dupCount = dupCount;
        }
        public String getMmsi() { return mmsi; }
        public int getZoneId() { return zoneId; }
        public String getEventCategory() { return eventCategory; }
        public String getEventType() { return eventType; }
        public String getEventTime() { return eventTime; }
        public int getDupCount() { return dupCount; }
    }

    public static final class StoredEvent {
        private final String mmsi;
        private final int zoneId;
        private final String zoneName;
        private final String eventCategory;
        private final String eventType;
        private final String eventTime;
        private final Double lon;
        private final Double lat;
        public StoredEvent(String mmsi, int zoneId, String zoneName, String eventCategory,
                           String eventType, String eventTime, Double lon, Double lat) {
            this.mmsi = mmsi; this.zoneId = zoneId; this.zoneName = zoneName;
            this.eventCategory = eventCategory; this.eventType = eventType;
            this.eventTime = eventTime; this.lon = lon; this.lat = lat;
        }
        public String getMmsi() { return mmsi; }
        public int getZoneId() { return zoneId; }
        public String getZoneName() { return zoneName; }
        public String getEventCategory() { return eventCategory; }
        public String getEventType() { return eventType; }
        public String getEventTime() { return eventTime; }
        public Double getLon() { return lon; }
        public Double getLat() { return lat; }
    }

    public static final class CategoryStatRow {
        private final String eventCategory;
        private final int eventCount;
        private final int shipCount;
        private final int enterCount;
        private final int exitCount;
        public CategoryStatRow(String eventCategory, int eventCount, int shipCount, int enterCount, int exitCount) {
            this.eventCategory = eventCategory; this.eventCount = eventCount;
            this.shipCount = shipCount; this.enterCount = enterCount; this.exitCount = exitCount;
        }
        public String getEventCategory() { return eventCategory; }
        public int getEventCount() { return eventCount; }
        public int getShipCount() { return shipCount; }
        public int getEnterCount() { return enterCount; }
        public int getExitCount() { return exitCount; }
    }

    public static final class ZoneStatRow {
        private final int zoneId;
        private final String zoneName;
        private final String eventCategory;
        private final int eventCount;
        private final int shipCount;
        private final int enterCount;
        private final int exitCount;
        public ZoneStatRow(int zoneId, String zoneName, String eventCategory,
                           int eventCount, int shipCount, int enterCount, int exitCount) {
            this.zoneId = zoneId; this.zoneName = zoneName; this.eventCategory = eventCategory;
            this.eventCount = eventCount; this.shipCount = shipCount;
            this.enterCount = enterCount; this.exitCount = exitCount;
        }
        public int getZoneId() { return zoneId; }
        public String getZoneName() { return zoneName; }
        public String getEventCategory() { return eventCategory; }
        public int getEventCount() { return eventCount; }
        public int getShipCount() { return shipCount; }
        public int getEnterCount() { return enterCount; }
        public int getExitCount() { return exitCount; }
    }

    public static final class QualityReport {
        private String batchMonth, startTime, endTime, qualityStatus, checkResultsJson, issueSummary;
        private int qualityScore, totalShipsExpected, totalShipsDone, totalShipsFailed;
        private int totalEventsInStatus, totalEventsActual, errorCount, warnCount;
        private int eventCountMismatchShips, unbalancedPairGroups, nullFieldEvents, outOfRangeEvents;
        private int duplicateEventGroups, enterAfterExitPairs, invalidCategoryEvents;
        private int replaySampleShips, replayMismatchShips, spatialCheckEvents, spatialMismatchEvents;
        private BigDecimal completionRate = BigDecimal.ZERO;

        public String getBatchMonth() { return batchMonth; }
        public void setBatchMonth(String v) { batchMonth = v; }
        public String getStartTime() { return startTime; }
        public void setStartTime(String v) { startTime = v; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String v) { endTime = v; }
        public String getQualityStatus() { return qualityStatus; }
        public void setQualityStatus(String v) { qualityStatus = v; }
        public int getQualityScore() { return qualityScore; }
        public void setQualityScore(int v) { qualityScore = v; }
        public int getTotalShipsExpected() { return totalShipsExpected; }
        public void setTotalShipsExpected(int v) { totalShipsExpected = v; }
        public int getTotalShipsDone() { return totalShipsDone; }
        public void setTotalShipsDone(int v) { totalShipsDone = v; }
        public int getTotalShipsFailed() { return totalShipsFailed; }
        public void setTotalShipsFailed(int v) { totalShipsFailed = v; }
        public BigDecimal getCompletionRate() { return completionRate; }
        public void setCompletionRate(BigDecimal v) { completionRate = v; }
        public int getTotalEventsInStatus() { return totalEventsInStatus; }
        public void setTotalEventsInStatus(int v) { totalEventsInStatus = v; }
        public int getTotalEventsActual() { return totalEventsActual; }
        public void setTotalEventsActual(int v) { totalEventsActual = v; }
        public int getErrorCount() { return errorCount; }
        public void setErrorCount(int v) { errorCount = v; }
        public int getWarnCount() { return warnCount; }
        public void setWarnCount(int v) { warnCount = v; }
        public int getEventCountMismatchShips() { return eventCountMismatchShips; }
        public void setEventCountMismatchShips(int v) { eventCountMismatchShips = v; }
        public int getUnbalancedPairGroups() { return unbalancedPairGroups; }
        public void setUnbalancedPairGroups(int v) { unbalancedPairGroups = v; }
        public int getNullFieldEvents() { return nullFieldEvents; }
        public void setNullFieldEvents(int v) { nullFieldEvents = v; }
        public int getOutOfRangeEvents() { return outOfRangeEvents; }
        public void setOutOfRangeEvents(int v) { outOfRangeEvents = v; }
        public int getDuplicateEventGroups() { return duplicateEventGroups; }
        public void setDuplicateEventGroups(int v) { duplicateEventGroups = v; }
        public int getEnterAfterExitPairs() { return enterAfterExitPairs; }
        public void setEnterAfterExitPairs(int v) { enterAfterExitPairs = v; }
        public int getInvalidCategoryEvents() { return invalidCategoryEvents; }
        public void setInvalidCategoryEvents(int v) { invalidCategoryEvents = v; }
        public int getReplaySampleShips() { return replaySampleShips; }
        public void setReplaySampleShips(int v) { replaySampleShips = v; }
        public int getReplayMismatchShips() { return replayMismatchShips; }
        public void setReplayMismatchShips(int v) { replayMismatchShips = v; }
        public int getSpatialCheckEvents() { return spatialCheckEvents; }
        public void setSpatialCheckEvents(int v) { spatialCheckEvents = v; }
        public int getSpatialMismatchEvents() { return spatialMismatchEvents; }
        public void setSpatialMismatchEvents(int v) { spatialMismatchEvents = v; }
        public String getCheckResultsJson() { return checkResultsJson; }
        public void setCheckResultsJson(String v) { checkResultsJson = v; }
        public String getIssueSummary() { return issueSummary; }
        public void setIssueSummary(String v) { issueSummary = v; }
    }

    public static final class CategoryStatRecord {
        private final long reportId;
        private final String eventCategory;
        private final int eventCount, shipCount, enterCount, exitCount, passagePairs, unbalancedGroups;
        private final BigDecimal avgDwellMinutes;
        public CategoryStatRecord(long reportId, String eventCategory, int eventCount, int shipCount,
                                  int enterCount, int exitCount, int passagePairs, int unbalancedGroups,
                                  BigDecimal avgDwellMinutes) {
            this.reportId = reportId; this.eventCategory = eventCategory;
            this.eventCount = eventCount; this.shipCount = shipCount;
            this.enterCount = enterCount; this.exitCount = exitCount;
            this.passagePairs = passagePairs; this.unbalancedGroups = unbalancedGroups;
            this.avgDwellMinutes = avgDwellMinutes;
        }
        public long getReportId() { return reportId; }
        public String getEventCategory() { return eventCategory; }
        public int getEventCount() { return eventCount; }
        public int getShipCount() { return shipCount; }
        public int getEnterCount() { return enterCount; }
        public int getExitCount() { return exitCount; }
        public int getPassagePairs() { return passagePairs; }
        public int getUnbalancedGroups() { return unbalancedGroups; }
        public BigDecimal getAvgDwellMinutes() { return avgDwellMinutes; }
    }

    public static final class ZoneStatRecord {
        private final long reportId;
        private final int zoneId, eventCount, shipCount, enterCount, exitCount, unbalancedGroups;
        private final String zoneName, eventCategory;
        public ZoneStatRecord(long reportId, int zoneId, String zoneName, String eventCategory,
                              int eventCount, int shipCount, int enterCount, int exitCount, int unbalancedGroups) {
            this.reportId = reportId; this.zoneId = zoneId; this.zoneName = zoneName;
            this.eventCategory = eventCategory; this.eventCount = eventCount;
            this.shipCount = shipCount; this.enterCount = enterCount;
            this.exitCount = exitCount; this.unbalancedGroups = unbalancedGroups;
        }
        public long getReportId() { return reportId; }
        public int getZoneId() { return zoneId; }
        public String getZoneName() { return zoneName; }
        public String getEventCategory() { return eventCategory; }
        public int getEventCount() { return eventCount; }
        public int getShipCount() { return shipCount; }
        public int getEnterCount() { return enterCount; }
        public int getExitCount() { return exitCount; }
        public int getUnbalancedGroups() { return unbalancedGroups; }
    }

    public static final class CheckRecord {
        private final long reportId;
        private final String checkCode, checkName, severity, detail;
        private final int issueCount;
        public CheckRecord(long reportId, String checkCode, String checkName, String severity, int issueCount, String detail) {
            this.reportId = reportId; this.checkCode = checkCode; this.checkName = checkName;
            this.severity = severity; this.issueCount = issueCount; this.detail = detail;
        }
        public long getReportId() { return reportId; }
        public String getCheckCode() { return checkCode; }
        public String getCheckName() { return checkName; }
        public String getSeverity() { return severity; }
        public int getIssueCount() { return issueCount; }
        public String getDetail() { return detail; }
    }

    public static final class QualityIssue {
        private final long reportId;
        private final String checkCode, issueType, severity, mmsi, zoneName, eventCategory, detail;
        private final Integer zoneId;
        public QualityIssue(long reportId, String checkCode, String issueType, String severity,
                            String mmsi, Integer zoneId, String zoneName, String eventCategory, String detail) {
            this.reportId = reportId; this.checkCode = checkCode; this.issueType = issueType;
            this.severity = severity; this.mmsi = mmsi; this.zoneId = zoneId;
            this.zoneName = zoneName; this.eventCategory = eventCategory; this.detail = detail;
        }
        public long getReportId() { return reportId; }
        public String getCheckCode() { return checkCode; }
        public String getIssueType() { return issueType; }
        public String getSeverity() { return severity; }
        public String getMmsi() { return mmsi; }
        public Integer getZoneId() { return zoneId; }
        public String getZoneName() { return zoneName; }
        public String getEventCategory() { return eventCategory; }
        public String getDetail() { return detail; }
    }
}
