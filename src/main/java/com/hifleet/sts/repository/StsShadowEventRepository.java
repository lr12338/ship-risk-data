package com.hifleet.sts.repository;

import com.hifleet.sts.domain.StsShadowEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Repository for STS shadow event table — EXPERIMENTAL ONLY.
 *
 * Writes ONLY to dataanalysis.ship_sts_shadow_event.
 * Does NOT touch ship_behavior_event or ship_behavior_event_detail.
 *
 * Uses INSERT IGNORE for idempotency on (batch_no, event_key).
 */
@Repository
public class StsShadowEventRepository {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String INSERT_SQL = "INSERT IGNORE INTO dataanalysis.ship_sts_shadow_event (" +
            "batch_no, event_key, algorithm_version, " +
            "seed_batch_no, region_set_code, " +
            "region_id, region_version, " +
            "own_imo, own_mmsi, target_imo, target_mmsi, identity_mode, " +
            "start_time, end_time, duration_minutes, " +
            "center_lon, center_lat, " +
            "behavior_type, baseline_rule, " +
            "min_distance_nm, avg_distance_nm, " +
            "own_avg_speed_kn, target_avg_speed_kn, " +
            "behavior_score, target_role, review_status, score_detail_json" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("newDataanalysisDataSource")
    private DataSource dataSource;

    /**
     * Insert a single shadow event if absent.
     * @return 1 if inserted, 0 if duplicate
     */
    public int insertIfAbsent(StsShadowEvent event) {
        return jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(INSERT_SQL);
            int idx = 1;
            ps.setString(idx++, event.getBatchNo());
            ps.setString(idx++, event.getEventKey());
            ps.setString(idx++, event.getAlgorithmVersion());
            ps.setString(idx++, event.getSeedBatchNo());
            ps.setString(idx++, event.getRegionSetCode());
            ps.setString(idx++, event.getRegionId());
            ps.setString(idx++, event.getRegionVersion());
            ps.setString(idx++, event.getOwnImo());
            ps.setString(idx++, event.getOwnMmsi());
            ps.setString(idx++, event.getTargetImo());
            ps.setString(idx++, event.getTargetMmsi());
            ps.setString(idx++, event.getIdentityMode());
            setTimestampOrNull(ps, idx++, event.getStartTime());
            setTimestampOrNull(ps, idx++, event.getEndTime());
            setIntOrNull(ps, idx++, event.getDurationMinutes());
            setDoubleOrNull(ps, idx++, event.getCenterLon());
            setDoubleOrNull(ps, idx++, event.getCenterLat());
            ps.setString(idx++, event.getBehaviorType());
            ps.setString(idx++, event.getBaselineRule());
            setDoubleOrNull(ps, idx++, event.getMinDistanceNm());
            setDoubleOrNull(ps, idx++, event.getAvgDistanceNm());
            setDoubleOrNull(ps, idx++, event.getOwnAvgSpeedKn());
            setDoubleOrNull(ps, idx++, event.getTargetAvgSpeedKn());
            setDoubleOrNull(ps, idx++, event.getBehaviorScore());
            ps.setString(idx++, event.getTargetRole());
            ps.setString(idx++, event.getReviewStatus());
            ps.setString(idx++, event.getScoreDetailJson());
            return ps;
        });
    }

    public int insertIfNoOverlappingPhysicalEvent(final StsShadowEvent event) {
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        Integer inserted = tx.execute(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT batch_no FROM dataanalysis.ship_sts_backfill_batch "
                            + "WHERE batch_no=? FOR UPDATE",
                    new Object[]{event.getBatchNo()}, String.class);
            if (existsOverlappingPhysicalEvent(event.getBatchNo(), event.getRegionId(),
                    event.getOwnImo(), event.getTargetImo(), event.getTargetMmsi(),
                    event.getStartTime(), event.getEndTime())) {
                return 0;
            }
            return insertIfAbsent(event);
        });
        return inserted == null ? 0 : inserted;
    }

    public int countByBatchAndWindow(String batchNo, LocalDateTime from, LocalDateTime to) {
        String sql = "SELECT COUNT(*) FROM dataanalysis.ship_sts_shadow_event "
                + "WHERE batch_no=? AND start_time>=? AND start_time<?";
        Integer count = jdbcTemplate.queryForObject(sql,
                new Object[]{batchNo, Timestamp.valueOf(from), Timestamp.valueOf(to)}, Integer.class);
        return count == null ? 0 : count;
    }

    public boolean existsOverlappingPhysicalEvent(String batchNo, String regionId, String ownImo,
                                                  String targetImo, String targetMmsi,
                                                  String startTime, String endTime) {
        String targetClause = targetImo == null
                ? "target_imo IS NULL AND target_mmsi=?"
                : "target_imo=?";
        String sql = "SELECT COUNT(*) FROM dataanalysis.ship_sts_shadow_event "
                + "WHERE batch_no=? AND region_id=? AND own_imo=? AND " + targetClause
                + " AND start_time<=? AND end_time>=?";
        Object target = targetImo == null ? targetMmsi : targetImo;
        Integer count = jdbcTemplate.queryForObject(sql,
                new Object[]{batchNo, regionId, ownImo, target,
                        toTimestamp(endTime), toTimestamp(startTime)}, Integer.class);
        return count != null && count > 0;
    }

    private void setTimestampOrNull(PreparedStatement ps, int index, String value) throws java.sql.SQLException {
        if (value == null || value.isEmpty()) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, Timestamp.valueOf(LocalDateTime.parse(value, TIME_FMT)));
        }
    }

    private Timestamp toTimestamp(String value) {
        return Timestamp.valueOf(LocalDateTime.parse(value, TIME_FMT));
    }

    private void setIntOrNull(PreparedStatement ps, int index, Integer value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private void setDoubleOrNull(PreparedStatement ps, int index, Double value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.DOUBLE);
        } else {
            ps.setDouble(index, value);
        }
    }
}
