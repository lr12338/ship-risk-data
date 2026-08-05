package com.hifleet.behaviorplatform;

import java.util.List;

public final class BehaviorSceneEvaluator {
    private final SceneEvidenceProvider provider;
    public BehaviorSceneEvaluator(SceneEvidenceProvider provider) { this.provider = provider; }

    public BehaviorComparison evaluate(List<BehaviorCandidate> candidates, SceneMode mode) {
        BehaviorComparison result = new BehaviorComparison();
        if (candidates == null) return result;
        double coverage = 0.0;
        int evaluated = 0;
        for (BehaviorCandidate candidate : candidates) {
            if (!candidate.isAcceptedByBehaviorRules()) continue;
            result.controlCandidateCount++;
            if (mode == SceneMode.OFF) {
                result.retainedCount++;
                result.filteredCandidateCount++;
                continue;
            }
            if (provider == null) throw new IllegalStateException("scene provider required for " + mode);
            SceneEvaluation scene = provider.evaluate(candidate.getComparisonKey());
            if (scene == null) scene = SceneEvaluation.unknown(0.0);
            evaluated++;
            coverage += scene.getCoverageRatio();
            switch (scene.getDecision()) {
                case OPEN_WATER_CONFIRMED: result.openWaterConfirmedCount++; break;
                case REJECT_INLAND:
                    if (mode == SceneMode.FILTER) result.rejectedByInlandCount++;
                    break;
                case COASTAL_REVIEW: result.coastalReviewCount++; break;
                case CONFLICT: result.conflictCount++; break;
                default: result.unknownCount++;
            }
            if (mode == SceneMode.SHADOW) {
                result.retainedCount++;
                result.filteredCandidateCount++;
            } else if (scene.getDecision() == SceneEvaluation.Decision.OPEN_WATER_CONFIRMED) {
                result.retainedCount++;
                result.filteredCandidateCount++;
            } else if (scene.getDecision() == SceneEvaluation.Decision.REJECT_INLAND) {
                // rejected only in the isolated filtered result
            } else {
                result.reviewCount++;
            }
        }
        result.sceneCoverageRatio = evaluated == 0 ? 0.0 : coverage / evaluated;
        return result;
    }
}
