package com.hifleet.behaviorplatform;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.regionbehavior.RegionBehaviorRunResult;
import com.hifleet.regionbehavior.RegionBehaviorRunService;
import com.hifleet.regionbehavior.RegionMatchedEvent;
import org.springframework.stereotype.Service;

@Service
public final class RegionBehaviorScopeExecutor implements BehaviorScopeExecutor {
    private final RegionDefinitionRegistryComponent definitions;
    private final RegionBehaviorRunService regionRuns;
    public RegionBehaviorScopeExecutor(RegionDefinitionRegistryComponent definitions,
                                       RegionBehaviorRunService regionRuns) {
        this.definitions = definitions; this.regionRuns = regionRuns;
    }
    public boolean supports(BehaviorTargetSourceType type) { return type == BehaviorTargetSourceType.REGION; }
    public JSONObject preflight(BehaviorScopeContext context) {
        RegionDefinition definition = definitions.resolve(context.getEnvelope());
        JSONObject result = regionRuns.preflight(legacyParams(context, definition).toJSONString());
        if (!"PASS".equals(result.getString("status")))
            throw new IllegalStateException("region preflight failed: " + result.getString("reason"));
        result.put("sourceResolvedCount", result.getIntValue("querySlices"));
        result.put("regionId", definition.getRegionId()); result.put("regionVersion", definition.getVersion());
        result.put("regionFingerprint", definition.getFingerprint()); result.put("regionAreaSqKm", definition.getAreaSqKm());
        return result;
    }
    public BehaviorScopeExecutionResult execute(BehaviorScopeContext context) {
        RegionDefinition definition = definitions.resolve(context.getEnvelope());
        BehaviorScopeExecutionResult result = new BehaviorScopeExecutionResult();
        String key = definition.getFingerprint();
        if (context.getEnvelope().isResume() && context.getCheckpoints().skipSuccess(key)) {
            result.skipped(); return result;
        }
        int attempt = context.getCheckpoints().nextAttempt(key);
        if (attempt > 1) result.retried();
        RegionBehaviorRunResult region = regionRuns.run(legacyParams(context, definition).toJSONString());
        JSONObject summary = region.summaryJson();
        result.getMetrics().putAll(summary);
        result.getMetrics().put("regionVersion", definition.getVersion());
        result.getMetrics().put("regionFingerprint", definition.getFingerprint());
        result.getMetrics().put("regionAreaSqKm", definition.getAreaSqKm());
        result.processed();
        for (RegionMatchedEvent event : region.getRegionEvents()) add(event, result, context);
        for (RegionMatchedEvent event : region.getBoundaryEvents()) add(event, result, context);
        if (!region.isQueryComplete() || !region.isSuccess()) {
            context.getCheckpoints().record(key, "PARTIAL", attempt);
            BehaviorFailureRecord failure = new BehaviorFailureRecord(null, null,
                    BehaviorTargetSourceType.REGION, "REGION_DISCOVERY", "REGION_PARTIAL_FAIL_CLOSED",
                    "region query or analysis is incomplete", 0, summary.getIntValue("truncatedSlices"),
                    context.getEnvelope().getStartTime() + "/" + context.getEnvelope().getEndTime(), attempt);
            failure.setCompleteness("PARTIAL"); failure.setRegionFingerprint(definition.getFingerprint());
            result.getFailures().add(failure);
        } else {
            result.successful(); context.getCheckpoints().record(key, "SUCCESS", attempt);
        }
        return result;
    }
    private void add(RegionMatchedEvent event, BehaviorScopeExecutionResult result, BehaviorScopeContext context) {
        String scene = event.getSceneShadowDecision() == null ? "UNKNOWN" : event.getSceneShadowDecision();
        String disposition = scene.contains("INLAND") ? "REJECTED"
                : scene.contains("OPEN_WATER") ? "ACCEPTED" : "REVIEW";
        result.getAuditResults().add(new BehaviorAuditResult(event.getEventKey(), event.getMmsi(),
                event.getTargetMmsi(), event.getStartTime(), event.getEndTime(), true,
                scene, disposition, null));
        result.addCandidates(1);
        if ("REVIEW".equals(disposition)) result.addReviews(1); else if ("ACCEPTED".equals(disposition)) result.addRetained(1);
    }
    private JSONObject legacyParams(BehaviorScopeContext context, RegionDefinition definition) {
        BehaviorJobEnvelope envelope = context.getEnvelope();
        JSONObject value = new JSONObject(true);
        value.put("mode", "region_behavior_v1"); value.put("regionId", definition.getRegionId());
        value.put("regionName", definition.getRegionName()); value.put("polygonWkt", definition.getPolygonWkt());
        value.put("startTime", envelope.getStartTime()); value.put("endTime", envelope.getEndTime());
        JSONArray types = new JSONArray(); types.add(context.getProfile().getBehaviorType().name());
        value.put("behaviorTypes", types); value.put("shipTypePolicy", "EXISTING_MERCHANT_POLICY");
        value.put("discoveryTimeSliceMinutes", definition.getDiscoveryTimeSliceMinutes());
        value.put("regionBufferNm", definition.getRegionBufferNm());
        value.put("maxCandidateVessels", Math.min(envelope.getShipLimit(), definition.getMaxCandidateVessels()));
        value.put("batchNo", envelope.getBatchNo()); value.put("dryRun", true);
        value.put("storeCandidates", false); value.put("parallelism", 1);
        return value;
    }
}
