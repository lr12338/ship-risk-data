package com.hifleet.regionbehavior;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CombinedShadowDecisionEngine {
    public Result decide(RegionMatchStatus regionStatus, RegionBehaviorStatus completeness,
                         String sceneDecision, String behaviorDecision,
                         OperationalAreaContext operationalArea) {
        List<String> reasons = new ArrayList<String>();
        if (completeness != RegionBehaviorStatus.COMPLETE) {
            reasons.add("DATA_PARTIAL");
            return new Result("DATA_PARTIAL_FAIL_CLOSED", reasons);
        }
        if (regionStatus == RegionMatchStatus.REGION_BOUNDARY_PARTIAL) {
            reasons.add("REGION_BOUNDARY_PARTIAL");
            return new Result("REVIEW_REGION_BOUNDARY", reasons);
        }
        if ("WOULD_REJECT_INLAND".equals(sceneDecision)) {
            reasons.add("HIGH_CONFIDENCE_INLAND");
            return new Result("WOULD_REJECT_INLAND", reasons);
        }
        if ("SCENE_CONFLICT".equals(sceneDecision)) {
            reasons.add("SCENE_CONFLICT");
            return new Result("SCENE_REVIEW_REQUIRED", reasons);
        }
        if ("SCENE_REVIEW_REQUIRED".equals(sceneDecision)
                || "SCENE_UNKNOWN".equals(sceneDecision)) {
            reasons.add("SCENE_UNKNOWN");
            return new Result("SCENE_REVIEW_REQUIRED", reasons);
        }
        if (operationalArea == null || operationalArea.requiresReview()) {
            reasons.add(operationalArea == null || !operationalArea.isOperationalAreaDataAvailable()
                    ? "OPERATIONAL_AREA_DATA_UNAVAILABLE" : "OPERATIONAL_AREA_HIT");
            return new Result("OPERATIONAL_AREA_REVIEW", reasons);
        }
        if ("WOULD_REJECT_STATIONARY_WAITING".equals(behaviorDecision)) {
            reasons.add("STATIONARY_WAITING");
            return new Result("STATIONARY_WAITING_REVIEW", reasons);
        }
        if (behaviorDecision != null && behaviorDecision.startsWith("WOULD_REJECT_")) {
            reasons.add(reasonForBehavior(behaviorDecision));
            return new Result(behaviorDecision, reasons);
        }
        if ("STATIONARY_ALONGSIDE_CANDIDATE".equals(behaviorDecision)) {
            reasons.add("STATIONARY_ALONGSIDE_DISABLED");
            return new Result("INSUFFICIENT_GEOMETRY_EVIDENCE", reasons);
        }
        if ("COASTAL_OPERATION_REVIEW".equals(sceneDecision)) {
            reasons.add("COASTAL_OR_NEARSHORE_SCENE");
            return new Result("COASTAL_OPERATION_REVIEW", reasons);
        }
        if ("OPEN_WATER_CONFIRMED".equals(sceneDecision)
                && ("SIDE_BY_SIDE_CONFIRMED".equals(behaviorDecision)
                || "SHAPE_CONFIRMED".equals(behaviorDecision))) {
            return new Result("WOULD_ACCEPT_SHADOW", reasons);
        }
        reasons.add(behaviorDecision == null ? "BEHAVIOR_DIAGNOSTIC_MISSING" : behaviorDecision);
        return new Result("COMBINED_REVIEW_REQUIRED", reasons);
    }

    private String reasonForBehavior(String decision) {
        if ("WOULD_REJECT_FOLLOWING".equals(decision)) return "FOLLOWING_GEOMETRY";
        if ("WOULD_REJECT_OVERTAKING".equals(decision)) return "OVERTAKING_GEOMETRY";
        if ("WOULD_REJECT_CROSSING".equals(decision)) return "CROSSING_GEOMETRY";
        if ("WOULD_REJECT_STRAIGHT_TRANSIT".equals(decision)) return "STRAIGHT_TRANSIT";
        if ("WOULD_REJECT_STATIONARY_WAITING".equals(decision)) return "STATIONARY_WAITING";
        return decision.substring("WOULD_REJECT_".length());
    }

    public static final class Result {
        private final String decision;
        private final List<String> rejectReasons;

        private Result(String decision, List<String> rejectReasons) {
            this.decision = decision;
            this.rejectReasons = Collections.unmodifiableList(new ArrayList<String>(rejectReasons));
        }

        public String getDecision() { return decision; }
        public List<String> getRejectReasons() { return rejectReasons; }
    }
}
