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
                    + "shadow_decision,inland_point_ratio,lake_point_ratio,canal_point_ratio,"
                    + "internal_water_point_ratio,territorial_sea_point_ratio,offshore_eez_point_ratio,"
                    + "high_seas_point_ratio,land_point_ratio,unknown_point_ratio,conflict_point_ratio,"
                    + "scene_transition_count,coast_distance_min_nm,coast_distance_p50_nm,"
                    + "coast_distance_max_nm,evidence_json) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE updated_at=CURRENT_TIMESTAMP,"
                    + "dominant_scene=VALUES(dominant_scene),scene_confidence=VALUES(scene_confidence),"
                    + "shadow_decision=VALUES(shadow_decision),inland_point_ratio=VALUES(inland_point_ratio),"
                    + "lake_point_ratio=VALUES(lake_point_ratio),canal_point_ratio=VALUES(canal_point_ratio),"
                    + "internal_water_point_ratio=VALUES(internal_water_point_ratio),"
                    + "territorial_sea_point_ratio=VALUES(territorial_sea_point_ratio),"
                    + "offshore_eez_point_ratio=VALUES(offshore_eez_point_ratio),"
                    + "high_seas_point_ratio=VALUES(high_seas_point_ratio),"
                    + "land_point_ratio=VALUES(land_point_ratio),unknown_point_ratio=VALUES(unknown_point_ratio),"
                    + "conflict_point_ratio=VALUES(conflict_point_ratio),"
                    + "scene_transition_count=VALUES(scene_transition_count),"
                    + "coast_distance_min_nm=VALUES(coast_distance_min_nm),"
                    + "coast_distance_p50_nm=VALUES(coast_distance_p50_nm),"
                    + "coast_distance_max_nm=VALUES(coast_distance_max_nm),"
                    + "evidence_json=VALUES(evidence_json)";

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
            SceneEpisodeAggregator.Summary summary, String shadowDecision, String evidenceJson) {
        if (!enabled || jdbcTemplate == null) return false;
        String key = auditKey(batchNo, behaviorType, mmsi, targetMmsi, startTime, endTime,
                snapshotFingerprint);
        jdbcTemplate.update(UPSERT, key, batchNo, behaviorType, mmsi, targetMmsi,
                startTime, endTime, snapshotVersion, snapshotFingerprint,
                MaritimeScenePolicy.VERSION, summary.getDominantScene().name(),
                summary.getDominantSceneConfidence().name(), shadowDecision,
                summary.getInlandPointRatio(), summary.getLakePointRatio(),
                summary.getCanalPointRatio(), summary.getInternalWaterPointRatio(),
                summary.getTerritorialSeaPointRatio(), summary.getOffshoreEezPointRatio(),
                summary.getHighSeasPointRatio(), summary.getLandPointRatio(),
                summary.getUnknownPointRatio(), summary.getConflictPointRatio(),
                summary.getSceneTransitionCount(), summary.getCoastDistanceMinNm(),
                summary.getCoastDistanceP50Nm(), summary.getCoastDistanceMaxNm(), evidenceJson);
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
