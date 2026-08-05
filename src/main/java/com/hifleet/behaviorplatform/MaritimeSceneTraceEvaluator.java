package com.hifleet.behaviorplatform;

import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.maritime.MaritimeSceneService;
import com.hifleet.maritime.SceneEpisodeAggregator;
import com.hifleet.maritime.TimedSceneObservation;

import java.util.List;

public final class MaritimeSceneTraceEvaluator implements BehaviorTraceSceneEvaluator {
    private final MaritimeSceneService service;
    private final SceneRuleConfig rules;
    public MaritimeSceneTraceEvaluator(MaritimeSceneService service) { this(service, null); }
    public MaritimeSceneTraceEvaluator(MaritimeSceneService service, SceneRuleConfig rules) {
        this.service = service; this.rules = rules;
    }
    public SceneEvaluation evaluate(ShipBehaviorTrace trace) {
        List<TimedSceneObservation> points = service.classifyTimed(trace.getOwnPoints());
        long gap = rules == null ? 30 : rules.getGapCapMinutes();
        SceneEpisodeAggregator.Summary summary = service.aggregateTimed(points, gap);
        String decision = service.shadowDecisionV2(summary,
                rules == null ? 0.8 : rules.getMinTemporalCoverage(),
                rules == null ? 0.9 : rules.getInlandTimeRatio(),
                rules == null ? 60 : rules.getInlandMinDurationMinutes(),
                rules == null ? 0.9 : rules.getOpenWaterTimeRatio(),
                rules == null ? 0.05 : rules.getMaxUnknownTimeRatio());
        double coverage = summary == null ? 0.0 : summary.getTemporalCoverageRatio();
        if ("WOULD_REJECT_INLAND".equals(decision)) return SceneEvaluation.rejectInland(coverage);
        if ("OPEN_WATER_CONFIRMED".equals(decision)) return SceneEvaluation.openWater(coverage);
        if ("SCENE_CONFLICT".equals(decision)) return SceneEvaluation.conflict(coverage);
        if ("COASTAL_OPERATION_REVIEW".equals(decision)) return SceneEvaluation.coastalReview(coverage);
        return SceneEvaluation.unknown(coverage);
    }
}
