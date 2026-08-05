package com.hifleet.behaviorplatform;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RuleDecision {
    private final String ruleId;
    private final String ruleVersion;
    private final boolean passed;
    private final Object observedValue;
    private final Object threshold;
    private final String unit;
    private final String reasonCode;
    private final Map<String, Object> evidence;

    public RuleDecision(String ruleId, String ruleVersion, boolean passed, Object observedValue,
                        Object threshold, String unit, String reasonCode, Map<String, Object> evidence) {
        this.ruleId = ruleId;
        this.ruleVersion = ruleVersion;
        this.passed = passed;
        this.observedValue = observedValue;
        this.threshold = threshold;
        this.unit = unit;
        this.reasonCode = reasonCode;
        this.evidence = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(
                evidence == null ? Collections.<String, Object>emptyMap() : evidence));
    }
    public String getRuleId() { return ruleId; }
    public String getRuleVersion() { return ruleVersion; }
    public boolean isPassed() { return passed; }
    public Object getObservedValue() { return observedValue; }
    public Object getThreshold() { return threshold; }
    public String getUnit() { return unit; }
    public String getReasonCode() { return reasonCode; }
    public Map<String, Object> getEvidence() { return evidence; }
}
