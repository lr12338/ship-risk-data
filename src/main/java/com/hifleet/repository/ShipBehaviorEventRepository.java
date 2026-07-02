package com.hifleet.repository;

import com.hifleet.domain.ShipBehaviorEvent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ShipBehaviorEventRepository {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String INSERT_SQL = "INSERT INTO dataanalysis.ship_behavior_event " +
            "(behavior_type, mmsi, target_mmsi, start_time, end_time, duration_minutes, " +
            "start_lon, start_lat, end_lon, end_lat, min_distance_nm, max_distance_nm, " +
            "avg_speed, point_count, rule_version, batch_no, event_sub_type, severity_level, " +
            "event_score, confidence_level, exclusion_flag, exclusion_reason, ais_gap_count, " +
            "max_ais_gap_minutes, track_coverage_ratio, turn_count_per_hour, cog_change_per_hour, " +
            "tortuosity, center_lon, center_lat, radius_nm, score_detail_json) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Value("${ship.behavior.store.enabled:true}")
    private boolean storeEnabled;

    @Value("${ship.behavior.store.batch-size:200}")
    private int batchSize;

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate newDataanalysisJdbcTemplate;

    @Autowired
    private ShipBehaviorTableInitializer shipBehaviorTableInitializer;

    public void saveEvents(List<ShipBehaviorEvent> events) {
        if (!storeEnabled || events == null || events.isEmpty()) {
            return;
        }
        shipBehaviorTableInitializer.ensureTableReady();
        List<ShipBehaviorEvent> validEvents = filterValid(events);
        int effectiveBatchSize = Math.max(1, batchSize);
        for (int offset = 0; offset < validEvents.size(); offset += effectiveBatchSize) {
            int end = Math.min(offset + effectiveBatchSize, validEvents.size());
            saveBatch(validEvents.subList(offset, end));
        }
    }

    public List<ShipBehaviorEvent> findAlongsideEvents(String batchNo, String mmsi, String startTime, String endTime) {
        shipBehaviorTableInitializer.ensureTableReady();
        String sql = "SELECT behavior_type, mmsi, target_mmsi, start_time, end_time, duration_minutes, "
                + "min_distance_nm, max_distance_nm, avg_speed, point_count, rule_version, batch_no, "
                + "event_sub_type, severity_level, event_score, confidence_level, exclusion_flag, "
                + "exclusion_reason, ais_gap_count, max_ais_gap_minutes "
                + "FROM dataanalysis.ship_behavior_event "
                + "WHERE behavior_type = ? AND batch_no = ? AND mmsi = ? "
                + "AND start_time >= ? AND end_time <= ? ORDER BY start_time";
        return newDataanalysisJdbcTemplate.query(sql,
                new Object[]{ShipBehaviorEvent.TYPE_ALONGSIDE, batchNo, mmsi,
                        parseTime(startTime), parseTime(endTime)},
                (rs, rowNum) -> mapEvent(rs));
    }

    private List<ShipBehaviorEvent> filterValid(List<ShipBehaviorEvent> events) {
        List<ShipBehaviorEvent> result = new ArrayList<ShipBehaviorEvent>();
        for (ShipBehaviorEvent event : events) {
            if (event == null
                    || StringUtils.isBlank(event.getBehaviorType())
                    || StringUtils.isBlank(event.getMmsi())
                    || StringUtils.isBlank(event.getStartTime())
                    || StringUtils.isBlank(event.getEndTime())) {
                continue;
            }
            result.add(event);
        }
        return result;
    }

    private void saveBatch(final List<ShipBehaviorEvent> batch) {
        newDataanalysisJdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement stmt, int i) throws SQLException {
                ShipBehaviorEvent event = batch.get(i);
                stmt.setString(1, event.getBehaviorType());
                stmt.setString(2, event.getMmsi());
                stmt.setString(3, event.getTargetMmsi());
                stmt.setTimestamp(4, parseTime(event.getStartTime()));
                stmt.setTimestamp(5, parseTime(event.getEndTime()));
                stmt.setObject(6, event.getDurationMinutes());
                stmt.setObject(7, event.getStartLon());
                stmt.setObject(8, event.getStartLat());
                stmt.setObject(9, event.getEndLon());
                stmt.setObject(10, event.getEndLat());
                stmt.setObject(11, event.getMinDistanceNm());
                stmt.setObject(12, event.getMaxDistanceNm());
                stmt.setObject(13, event.getAvgSpeed());
                stmt.setObject(14, event.getPointCount());
                stmt.setString(15, event.getRuleVersion());
                stmt.setString(16, event.getBatchNo());
                stmt.setString(17, event.getEventSubType());
                stmt.setString(18, event.getSeverityLevel());
                stmt.setObject(19, event.getEventScore());
                stmt.setString(20, event.getConfidenceLevel());
                stmt.setString(21, event.getExclusionFlag());
                stmt.setString(22, event.getExclusionReason());
                stmt.setObject(23, event.getAisGapCount());
                stmt.setObject(24, event.getMaxAisGapMinutes());
                stmt.setObject(25, event.getTrackCoverageRatio());
                stmt.setObject(26, event.getTurnCountPerHour());
                stmt.setObject(27, event.getCogChangePerHour());
                stmt.setObject(28, event.getTortuosity());
                stmt.setObject(29, event.getCenterLon());
                stmt.setObject(30, event.getCenterLat());
                stmt.setObject(31, event.getRadiusNm());
                stmt.setString(32, event.getScoreDetailJson());
            }

            @Override
            public int getBatchSize() {
                return batch.size();
            }
        });
    }

    private Timestamp parseTime(String time) {
        return Timestamp.valueOf(LocalDateTime.parse(normalizeTime(time), TIME_FORMATTER));
    }

    private ShipBehaviorEvent mapEvent(ResultSet rs) throws SQLException {
        ShipBehaviorEvent event = new ShipBehaviorEvent();
        event.setBehaviorType(rs.getString("behavior_type"));
        event.setMmsi(rs.getString("mmsi"));
        event.setTargetMmsi(rs.getString("target_mmsi"));
        event.setStartTime(formatTimestamp(rs.getTimestamp("start_time")));
        event.setEndTime(formatTimestamp(rs.getTimestamp("end_time")));
        event.setDurationMinutes(rs.getObject("duration_minutes") == null ? null : rs.getLong("duration_minutes"));
        event.setMinDistanceNm(rs.getObject("min_distance_nm") == null ? null : rs.getDouble("min_distance_nm"));
        event.setMaxDistanceNm(rs.getObject("max_distance_nm") == null ? null : rs.getDouble("max_distance_nm"));
        event.setAvgSpeed(rs.getObject("avg_speed") == null ? null : rs.getDouble("avg_speed"));
        event.setPointCount(rs.getObject("point_count") == null ? null : rs.getInt("point_count"));
        event.setRuleVersion(rs.getString("rule_version"));
        event.setBatchNo(rs.getString("batch_no"));
        event.setEventSubType(rs.getString("event_sub_type"));
        event.setSeverityLevel(rs.getString("severity_level"));
        event.setEventScore(rs.getObject("event_score") == null ? null : rs.getDouble("event_score"));
        event.setConfidenceLevel(rs.getString("confidence_level"));
        event.setExclusionFlag(rs.getString("exclusion_flag"));
        event.setExclusionReason(rs.getString("exclusion_reason"));
        event.setAisGapCount(rs.getObject("ais_gap_count") == null ? null : rs.getInt("ais_gap_count"));
        event.setMaxAisGapMinutes(rs.getObject("max_ais_gap_minutes") == null ? null : rs.getLong("max_ais_gap_minutes"));
        return event;
    }

    private String formatTimestamp(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().format(TIME_FORMATTER);
    }

    private String normalizeTime(String time) {
        String trimmed = time.trim();
        return trimmed.length() == 16 ? trimmed + ":00" : trimmed;
    }
}
