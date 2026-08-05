package com.hifleet.behaviorplatform;

import com.hifleet.domain.ShipBehaviorEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LegacyRuleEvidenceFactory {
    List<RuleDecision> accepted(ShipBehaviorEvent event, RuleSetDefinition ruleSet,
                                SceneEvaluation scene) {
        List<RuleDecision> result = new ArrayList<RuleDecision>();
        for (RuleDefinition rule : ruleSet.getRules()) {
            Object observed = observed(event, rule.getRuleId(), scene);
            result.add(new RuleDecision(rule.getRuleId(), rule.getRuleVersion(), true,
                    observed, rule.getThreshold(), rule.getUnit(),
                    "LEGACY_DETECTOR_ACCEPTED",
                    Collections.<String, Object>singletonMap("adapter", "STRANGLER_V1")));
        }
        return result;
    }

    private Object observed(ShipBehaviorEvent event, String ruleId, SceneEvaluation scene) {
        if (ruleId.contains("duration")) return event.getDurationMinutes();
        if (ruleId.contains("coverage")) return event.getTrackCoverageRatio();
        if (ruleId.contains("relative-speed")) return event.getSpeedDifferenceKnots();
        if (ruleId.contains("heading")) return event.getCourseDifferenceDegree();
        if (ruleId.contains("distance")) return event.getDistanceVarianceNm2();
        if (ruleId.contains("radius")) return event.getRadiusNm();
        if (ruleId.contains("turn")) return event.getTurnCountPerHour();
        if (ruleId.contains("tortuosity") || ruleId.contains("straight")) return event.getTortuosity();
        if (ruleId.contains("speed")) return event.getAvgSpeed();
        if (ruleId.contains("scene")) return scene == null ? "OFF" : scene.getDecision().name();
        return "PRESENT_IN_LEGACY_ACCEPTED_EVENT";
    }
}
