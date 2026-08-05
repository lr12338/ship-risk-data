package com.hifleet.behaviorplatform;

public final class RuleDefinition {
    private final String ruleId;
    private final String ruleVersion;
    private final double threshold;
    private final String unit;

    public RuleDefinition(String ruleId, String ruleVersion, double threshold, String unit) {
        this.ruleId = ruleId;
        this.ruleVersion = ruleVersion;
        this.threshold = threshold;
        this.unit = unit;
    }
    public String getRuleId() { return ruleId; }
    public String getRuleVersion() { return ruleVersion; }
    public double getThreshold() { return threshold; }
    public String getUnit() { return unit; }
}
