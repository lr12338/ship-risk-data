package com.hifleet.behaviorplatform;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BehaviorRunOrchestrator {
    public BehaviorOrchestrationResult evaluate(BehaviorType behaviorType, SceneMode sceneMode,
                                                 RuleSetDefinition ruleSet,
                                                 List<ShipBehaviorTrace> traces,
                                                 BehaviorTraceSceneEvaluator sceneEvaluator) {
        return evaluate(behaviorType, sceneMode, ruleSet, traces, sceneEvaluator, null, null);
    }

    public BehaviorOrchestrationResult evaluate(BehaviorType behaviorType, SceneMode sceneMode,
                                                 RuleSetDefinition ruleSet,
                                                 List<ShipBehaviorTrace> traces,
                                                 BehaviorTraceSceneEvaluator sceneEvaluator,
                                                 ShipTypePolicyEvaluator shipTypes,
                                                 BehaviorRunProfile profile) {
        List<BehaviorCandidate> candidates = new ArrayList<BehaviorCandidate>();
        final Map<String, SceneEvaluation> scenes = new LinkedHashMap<String, SceneEvaluation>();
        final Map<String, ShipBehaviorTrace> byKey = new LinkedHashMap<String, ShipBehaviorTrace>();
        for (ShipBehaviorTrace trace : traces == null
                ? Collections.<ShipBehaviorTrace>emptyList() : traces) {
            ShipBehaviorEvent event = trace == null ? null : trace.getEvent();
            if (event == null) continue;
            String key = BehaviorComparisonKey.of(behaviorType, event.getMmsi(), event.getTargetMmsi(),
                    event.getStartTime(), event.getEndTime(), ruleSet.getRuleSetHash());
            candidates.add(new BehaviorCandidate(key, true));
            byKey.put(key, trace);
            if (sceneMode != SceneMode.OFF) scenes.put(key, sceneEvaluator.evaluate(trace));
        }
        BehaviorComparison comparison = new BehaviorSceneEvaluator(
                key -> scenes.get(key)).evaluate(candidates, sceneMode);
        List<BehaviorAuditResult> audit = new ArrayList<BehaviorAuditResult>();
        for (BehaviorCandidate candidate : candidates) {
            ShipBehaviorEvent event = byKey.get(candidate.getComparisonKey()).getEvent();
            SceneEvaluation scene = scenes.get(candidate.getComparisonKey());
            String decision = sceneMode == SceneMode.OFF ? "OFF"
                    : scene == null ? "UNKNOWN" : scene.getDecision().name();
            String disposition = disposition(sceneMode, scene);
            Map<String, Object> sceneEvidence = new LinkedHashMap<String, Object>();
            sceneEvidence.put("mode", sceneMode.name());
            sceneEvidence.put("decision", decision);
            sceneEvidence.put("coverageRatio", scene == null ? 0.0 : scene.getCoverageRatio());
            CounterpartyTypeEvidence counterparty = behaviorType == BehaviorType.ALONGSIDE
                    && shipTypes != null && profile != null ? shipTypes.counterparty(event.getTargetShipType(),
                    profile.getCounterpartyShipTypePolicy(), profile.getUnknownShipTypeAction()) : null;
            BehaviorRiskEvidence evidence = new BehaviorRiskEvidence(behaviorType,
                    event.getEventSubType(), event.getConfidenceLevel(),
                    new LegacyRuleEvidenceFactory().accepted(event, ruleSet, scene), sceneEvidence,
                    counterparty, 0.5, event.getTrackCoverageRatio() == null ? 0.0 : event.getTrackCoverageRatio(),
                    "REVIEW".equals(disposition) ? Collections.singletonList(decision)
                            : Collections.<String>emptyList(),
                    Collections.singletonList(behaviorType.name() + "_EVIDENCE"));
            audit.add(new BehaviorAuditResult(candidate.getComparisonKey(), event.getMmsi(),
                    event.getTargetMmsi(), event.getStartTime(), event.getEndTime(), true,
                    decision, disposition, evidence));
        }
        return new BehaviorOrchestrationResult(comparison, audit);
    }

    private String disposition(SceneMode mode, SceneEvaluation scene) {
        if (mode != SceneMode.FILTER) return "ACCEPTED";
        if (scene != null && scene.getDecision() == SceneEvaluation.Decision.OPEN_WATER_CONFIRMED)
            return "ACCEPTED";
        if (scene != null && scene.getDecision() == SceneEvaluation.Decision.REJECT_INLAND)
            return "REJECTED";
        return "REVIEW";
    }
}
