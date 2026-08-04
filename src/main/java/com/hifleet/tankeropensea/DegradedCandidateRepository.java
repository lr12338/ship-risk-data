package com.hifleet.tankeropensea;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.trajectory.TrajectoryPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Repository
public class DegradedCandidateRepository {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String INSERT_SQL =
            "INSERT INTO dataanalysis.ship_behavior_event_degraded_candidate "
                    + "(candidate_key,batch_no,behavior_type,mmsi,target_mmsi,"
                    + "start_time,end_time,rule_version,degradation_reason,"
                    + "exclusion_fingerprint,exclusion_health,source_health_json,"
                    + "candidate_payload_json) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE degradation_reason=VALUES(degradation_reason),"
                    + "exclusion_fingerprint=VALUES(exclusion_fingerprint),"
                    + "exclusion_health=VALUES(exclusion_health),"
                    + "source_health_json=VALUES(source_health_json),"
                    + "candidate_payload_json=VALUES(candidate_payload_json),"
                    + "updated_at=CURRENT_TIMESTAMP";

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("newDataanalysisDataSource")
    private DataSource dataSource;

    public void requireReady() {
        try {
            jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM dataanalysis.ship_behavior_event_degraded_candidate",
                    Integer.class);
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "degraded candidate table is unavailable", ex);
        }
    }

    public void replaceWindow(final String behaviorType, final String batchNo,
                              final String mmsi, final String startTime,
                              final String endTime,
                              final List<DegradedCandidate> candidates,
                              final PortAnchorBerthExclusionService.Snapshot snapshot) {
        Runnable action = () -> {
            jdbcTemplate.update(
                    "DELETE FROM dataanalysis.ship_behavior_event_degraded_candidate "
                            + "WHERE behavior_type=? AND batch_no=? AND mmsi=? "
                            + "AND start_time>=? AND start_time<? "
                            + "AND review_status='PENDING'",
                    behaviorType, batchNo, mmsi,
                    timestamp(startTime), timestamp(endTime));
            for (DegradedCandidate candidate : candidates == null
                    ? Collections.<DegradedCandidate>emptyList() : candidates) {
                insert(candidate, snapshot);
            }
        };
        if (dataSource == null) {
            action.run();
        } else {
            new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                    .execute(status -> {
                        action.run();
                        return null;
                    });
        }
    }

    private void insert(DegradedCandidate candidate,
                        PortAnchorBerthExclusionService.Snapshot snapshot) {
        if (candidate == null || candidate.getTrace() == null
                || candidate.getTrace().getEvent() == null) return;
        ShipBehaviorTrace trace = candidate.getTrace();
        ShipBehaviorEvent event = trace.getEvent();
        event.setConfidenceLevel("LOW");
        event.setExclusionFlag("DOWNGRADED_NORMAL_LIKELY");
        event.setExclusionReason(candidate.getReason());
        jdbcTemplate.update(INSERT_SQL,
                candidateKey(event),
                event.getBatchNo(),
                event.getBehaviorType(),
                event.getMmsi(),
                event.getTargetMmsi(),
                timestamp(event.getStartTime()),
                timestamp(event.getEndTime()),
                event.getRuleVersion(),
                candidate.getReason(),
                snapshot.getFingerprint(),
                snapshot.getHealth().name(),
                snapshot.getHealthReportJson(),
                payload(trace));
    }

    private String payload(ShipBehaviorTrace trace) {
        JSONObject result = new JSONObject(true);
        result.put("event", JSON.toJSON(trace.getEvent()));
        result.put("ownTrack", track(trace.getOwnPoints()));
        result.put("targetTrack", track(trace.getTargetPoints()));
        return result.toJSONString();
    }

    private JSONArray track(List<TrajectoryPoint> points) {
        JSONArray result = new JSONArray();
        if (points == null) return result;
        for (TrajectoryPoint point : points) {
            JSONObject value = new JSONObject(true);
            value.put("mmsi", point.getMmsi());
            value.put("time", point.getTime() == null
                    ? null : point.getTime().format(FORMATTER));
            value.put("lon", point.getLon());
            value.put("lat", point.getLat());
            value.put("speed", point.getSpeed());
            value.put("course", point.getCourse());
            value.put("navStatus", point.getNavStatus());
            result.add(value);
        }
        return result;
    }

    private String candidateKey(ShipBehaviorEvent event) {
        String canonical = safe(event.getBatchNo()) + "|"
                + safe(event.getBehaviorType()) + "|" + safe(event.getRuleVersion())
                + "|" + safe(event.getMmsi()) + "|" + safe(event.getTargetMmsi())
                + "|" + safe(event.getStartTime()) + "|" + safe(event.getEndTime());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : bytes) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "cannot calculate degraded candidate key", ex);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private Timestamp timestamp(String value) {
        return Timestamp.valueOf(LocalDateTime.parse(value, FORMATTER));
    }
}
