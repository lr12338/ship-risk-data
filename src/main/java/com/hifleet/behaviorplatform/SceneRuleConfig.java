package com.hifleet.behaviorplatform;

public final class SceneRuleConfig {
    private final long gapCapMinutes;
    private final double minTemporalCoverage;
    private final double inlandTimeRatio;
    private final long inlandMinDurationMinutes;
    private final double openWaterTimeRatio;
    private final double maxUnknownTimeRatio;

    private SceneRuleConfig(RuleSetDefinition rules) {
        gapCapMinutes = (long) rules.threshold("gap-cap", 10.0);
        minTemporalCoverage = rules.threshold("min-temporal-coverage", 0.75);
        inlandTimeRatio = rules.threshold("inland-time-ratio", 0.90);
        inlandMinDurationMinutes = (long) rules.threshold("inland-min-duration", 60.0);
        openWaterTimeRatio = rules.threshold("open-water-time-ratio", 0.90);
        maxUnknownTimeRatio = rules.threshold("max-unknown-time-ratio", 0.05);
        if (gapCapMinutes <= 0 || inlandMinDurationMinutes <= 0
                || !ratio(minTemporalCoverage) || !ratio(inlandTimeRatio)
                || !ratio(openWaterTimeRatio) || !ratio(maxUnknownTimeRatio))
            throw new IllegalArgumentException("invalid scene RuleSet thresholds");
    }
    public static SceneRuleConfig from(RuleSetDefinition rules) { return new SceneRuleConfig(rules); }
    private boolean ratio(double value) { return value >= 0.0 && value <= 1.0 && Double.isFinite(value); }
    public long getGapCapMinutes() { return gapCapMinutes; }
    public double getMinTemporalCoverage() { return minTemporalCoverage; }
    public double getInlandTimeRatio() { return inlandTimeRatio; }
    public long getInlandMinDurationMinutes() { return inlandMinDurationMinutes; }
    public double getOpenWaterTimeRatio() { return openWaterTimeRatio; }
    public double getMaxUnknownTimeRatio() { return maxUnknownTimeRatio; }
}
