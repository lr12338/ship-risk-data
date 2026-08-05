package com.hifleet.behaviorplatform;

public final class SceneEvaluation {
    public enum Decision { OPEN_WATER_CONFIRMED, REJECT_INLAND, COASTAL_REVIEW, UNKNOWN, CONFLICT }
    private final Decision decision;
    private final double coverageRatio;
    private SceneEvaluation(Decision decision, double coverageRatio) {
        this.decision = decision;
        this.coverageRatio = coverageRatio;
    }
    public static SceneEvaluation openWater(double coverage) { return new SceneEvaluation(Decision.OPEN_WATER_CONFIRMED, coverage); }
    public static SceneEvaluation rejectInland(double coverage) { return new SceneEvaluation(Decision.REJECT_INLAND, coverage); }
    public static SceneEvaluation coastalReview(double coverage) { return new SceneEvaluation(Decision.COASTAL_REVIEW, coverage); }
    public static SceneEvaluation unknown(double coverage) { return new SceneEvaluation(Decision.UNKNOWN, coverage); }
    public static SceneEvaluation conflict(double coverage) { return new SceneEvaluation(Decision.CONFLICT, coverage); }
    public Decision getDecision() { return decision; }
    public double getCoverageRatio() { return coverageRatio; }
}
