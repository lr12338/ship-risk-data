package com.hifleet.behaviorplatform;

import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.maritime.MaritimeSceneService;
import com.hifleet.maritime.SceneEpisodeAggregator;
import com.hifleet.maritime.TimedSceneObservation;

import java.util.List;

public final class MaritimeSceneTraceEvaluator implements BehaviorTraceSceneEvaluator {
    private final MaritimeSceneService service;
    public MaritimeSceneTraceEvaluator(MaritimeSceneService service) { this.service = service; }
    public SceneEvaluation evaluate(ShipBehaviorTrace trace) {
        List<TimedSceneObservation> points = service.classifyTimed(trace.getOwnPoints());
        SceneEpisodeAggregator.Summary summary = service.aggregateTimed(points, 30);
        String decision = service.shadowDecisionV2(summary, 0.8, 0.9, 60, 0.9, 0.05);
        double coverage = summary == null ? 0.0 : summary.getTemporalCoverageRatio();
        if ("WOULD_REJECT_INLAND".equals(decision)) return SceneEvaluation.rejectInland(coverage);
        if ("OPEN_WATER_CONFIRMED".equals(decision)) return SceneEvaluation.openWater(coverage);
        if ("SCENE_CONFLICT".equals(decision)) return SceneEvaluation.conflict(coverage);
        if ("COASTAL_OPERATION_REVIEW".equals(decision)) return SceneEvaluation.coastalReview(coverage);
        return SceneEvaluation.unknown(coverage);
    }
}
