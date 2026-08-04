package com.hifleet.maritime;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MaritimeSceneShadowAuditService {
    private final MaritimeSceneService sceneService;

    @Autowired(required = false)
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate jdbcTemplate;
    @Value("${ship.behavior.maritime-scene.audit.store-enabled:false}")
    private boolean storeEnabled;

    private MaritimeSceneAuditRepository auditRepository =
            new MaritimeSceneAuditRepository(null, false);

    @Autowired
    public MaritimeSceneShadowAuditService(MaritimeSceneService sceneService) {
        this.sceneService = sceneService;
    }

    @PostConstruct
    public void initializeRepository() {
        auditRepository = new MaritimeSceneAuditRepository(jdbcTemplate, storeEnabled);
    }

    public JSONObject preflight(MaritimeSceneShadowAuditParams params) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("status", "PASS");
        result.put("listBatchNo", params.getListBatchNo());
        result.put("shipLimit", params.getShipLimit());
        result.put("dryRun", params.isDryRun());
        result.put("formalEventsChanged", false);
        result.put("formalEventMutation", false);
        result.put("databaseWrites", 0);
        result.put("enforceEnabled", false);
        result.put("scenePolicyVersion", params.getScenePolicyVersion());
        result.put("shadowAuditRunnerImplemented", false);
        if (!params.isDryRun() && !storeEnabled) {
            result.put("status", "SHADOW_BLOCKED");
            result.put("reason", "write run requires audit.store-enabled=true");
            return JSON.parseObject(JSON.toJSONString(result));
        }
        Map<String, Object> scenePreflight = sceneService.preflight();
        result.put("scene", scenePreflight);
        if (!Boolean.TRUE.equals(scenePreflight.get("available"))) {
            result.put("status", "SHADOW_DISABLED");
            result.put("reason", "MaritimeSceneService is not available; formal behavior remains unchanged");
            return JSON.parseObject(JSON.toJSONString(result));
        }
        String actualVersion = String.valueOf(scenePreflight.get("releaseVersion"));
        String actualFingerprint = String.valueOf(scenePreflight.get("snapshotFingerprint"));
        String actualPolicy = String.valueOf(scenePreflight.get("policyVersion"));
        if (!params.getSceneSnapshotVersion().equals(actualVersion)
                || !params.getExpectedSceneFingerprint().equalsIgnoreCase(actualFingerprint)
                || !params.getScenePolicyVersion().equals(actualPolicy)) {
            result.put("status", "SHADOW_BLOCKED");
            result.put("reason", "snapshot version, fingerprint, or policy version mismatch");
        }
        return JSON.parseObject(JSON.toJSONString(result));
    }

    public JSONObject run(String rawParams) {
        MaritimeSceneShadowAuditParams params = MaritimeSceneShadowAuditParams.parse(rawParams);
        JSONObject preflight = preflight(params);
        JSONObject result = new JSONObject(true);
        result.put("status", preflight.getString("status"));
        result.put("preflight", preflight);
        result.put("batchNo", params.getBatchNo());
        result.put("behaviorTypes", params.getBehaviorTypes());
        result.put("shipLimit", params.getShipLimit());
        result.put("dryRun", params.isDryRun());
        result.put("formalEventsChanged", false);
        result.put("existingAisReplayAndTimeoutSemanticsUnchanged", true);
        result.put("auditStorageEnabled", storeEnabled);
        if ("PASS".equals(preflight.getString("status"))) {
            result.put("status", "SHADOW_RUNNER_NOT_IMPLEMENTED");
            result.put("executionNote", "Preflight passed, but no ship/AIS episode runner is implemented");
        }
        return result;
    }

    public JSONObject auditEpisode(
            String batchNo, String behaviorType, String mmsi, String targetMmsi,
            String startTime, String endTime, List<MaritimeSceneDecision> decisions,
            List<Long> epochMillis) {
        SceneEpisodeAggregator.Summary summary = sceneService.aggregate(decisions, epochMillis);
        String decision = sceneService.shadowDecision(summary);
        JSONObject evidence = new JSONObject(true);
        evidence.put("pointCount", summary.getPointCount());
        evidence.put("dominantScene", summary.getDominantScene());
        evidence.put("dominantSceneConfidence", summary.getDominantSceneConfidence());
        evidence.put("inlandPointRatio", summary.getInlandPointRatio());
        evidence.put("highConfidenceInlandPointRatio", summary.getHighConfidenceInlandPointRatio());
        evidence.put("highConfidenceOpenWaterPointRatio", summary.getHighConfidenceOpenWaterPointRatio());
        evidence.put("lakePointRatio", summary.getLakePointRatio());
        evidence.put("canalPointRatio", summary.getCanalPointRatio());
        evidence.put("internalWaterPointRatio", summary.getInternalWaterPointRatio());
        evidence.put("territorialSeaPointRatio", summary.getTerritorialSeaPointRatio());
        evidence.put("offshoreEezPointRatio", summary.getOffshoreEezPointRatio());
        evidence.put("highSeasPointRatio", summary.getHighSeasPointRatio());
        evidence.put("unknownPointRatio", summary.getUnknownPointRatio());
        evidence.put("conflictPointRatio", summary.getConflictPointRatio());
        evidence.put("sceneTransitionCount", summary.getSceneTransitionCount());
        evidence.put("coastDistanceMinNm", summary.getCoastDistanceMinNm());
        evidence.put("coastDistanceP50Nm", summary.getCoastDistanceP50Nm());
        evidence.put("coastDistanceMaxNm", summary.getCoastDistanceMaxNm());
        String fingerprint = decisions == null || decisions.isEmpty()
                ? "" : decisions.get(0).getSnapshotFingerprint();
        String version = decisions == null || decisions.isEmpty()
                ? "" : decisions.get(0).getSnapshotVersion();
        String key = auditRepository.auditKey(
                batchNo, behaviorType, mmsi, targetMmsi, startTime, endTime, fingerprint);
        auditRepository.store(
                batchNo, behaviorType, mmsi, targetMmsi, startTime, endTime, version,
                fingerprint, summary, decision, evidence.toJSONString());
        JSONObject result = new JSONObject(true);
        result.put("auditKey", key);
        result.put("shadowDecision", decision);
        result.put("evidence", evidence);
        result.put("formalEventsChanged", false);
        return result;
    }

    public MaritimeSceneAuditRepository getAuditRepository() { return auditRepository; }
}
