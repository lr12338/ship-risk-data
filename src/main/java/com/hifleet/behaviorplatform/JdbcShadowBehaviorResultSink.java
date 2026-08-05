package com.hifleet.behaviorplatform;

import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class JdbcShadowBehaviorResultSink implements ShadowBehaviorResultSink {
    private final JdbcTemplate jdbc;
    public JdbcShadowBehaviorResultSink(@Qualifier("newDataanalysisJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
    public boolean upsert(BehaviorRunManifest manifest, BehaviorAuditResult value) {
        jdbc.update("INSERT INTO dataanalysis.ship_behavior_shadow_result "
                        + "(batch_no,profile_id,profile_hash,rule_set_hash,comparison_key,mmsi,target_mmsi,"
                        + "start_time,end_time,control_accepted,scene_decision,filtered_disposition,evidence_json) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE scene_decision=VALUES(scene_decision),"
                        + "filtered_disposition=VALUES(filtered_disposition),evidence_json=VALUES(evidence_json),"
                        + "updated_at=CURRENT_TIMESTAMP",
                manifest.getBatchNo(), manifest.getProfileId(), manifest.getProfileHash(),
                manifest.getRuleSetHash(), value.getComparisonKey(), value.getMmsi(),
                value.getTargetMmsi(), value.getStartTime(),
                value.getEndTime(), value.isControlAccepted(), value.getSceneDecision(),
                value.getFilteredDisposition(), JSON.toJSONString(value.getRiskEvidence()));
        return true;
    }
}
