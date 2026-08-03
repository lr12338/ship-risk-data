package com.hifleet.maritime;

import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MaritimeSceneAuditRepository {
    private static final String UPSERT =
            "INSERT INTO dataanalysis.ship_behavior_maritime_scene_audit "
                    + "(audit_key,batch_no,behavior_type,mmsi,target_mmsi,start_time,end_time,"
                    + "snapshot_version,snapshot_fingerprint,scene_policy_version,dominant_scene,scene_confidence,"
                    + "shadow_decision,evidence_json) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                    + "ON CONFLICT (audit_key) DO UPDATE SET updated_at=CURRENT_TIMESTAMP,"
                    + "shadow_decision=EXCLUDED.shadow_decision,evidence_json=EXCLUDED.evidence_json";

    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;

    public MaritimeSceneAuditRepository(JdbcTemplate jdbcTemplate, boolean enabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
    }

    public String auditKey(
            String batchNo, String behaviorType, String mmsi, String targetMmsi,
            String startTime, String endTime, String snapshotFingerprint) {
        return sha256(join(batchNo, behaviorType, mmsi, targetMmsi, startTime, endTime,
                snapshotFingerprint));
    }

    public boolean store(
            String batchNo, String behaviorType, String mmsi, String targetMmsi,
            String startTime, String endTime, String snapshotVersion, String snapshotFingerprint,
            String dominantScene, String sceneConfidence, String shadowDecision, String evidenceJson) {
        if (!enabled || jdbcTemplate == null) return false;
        String key = auditKey(batchNo, behaviorType, mmsi, targetMmsi, startTime, endTime,
                snapshotFingerprint);
        jdbcTemplate.update(UPSERT, key, batchNo, behaviorType, mmsi, targetMmsi,
                startTime, endTime, snapshotVersion, snapshotFingerprint,
                MaritimeScenePolicy.VERSION, dominantScene, sceneConfidence,
                shadowDecision, evidenceJson);
        return true;
    }

    private static String join(String... values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) result.append(value == null ? "" : value).append('\u001f');
        return result.toString();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
