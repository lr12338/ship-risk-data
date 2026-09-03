package com.hifleet.sts.repository;

import com.hifleet.sts.domain.StsShadowEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

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

    private void setTimestampOrNull(PreparedStatement ps, int index, String value) throws java.sql.SQLException {
        if (value == null || value.isEmpty()) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, Timestamp.valueOf(LocalDateTime.parse(value, TIME_FMT)));
        }
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
