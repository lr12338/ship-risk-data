package com.hifleet.tankeropensea;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class RuleResult {
    private final boolean accepted;
    private final String reason;
    private final Map<String, Object> features;

    private RuleResult(boolean accepted, String reason, Map<String, Object> features) {
        this.accepted = accepted;
        this.reason = reason;
        this.features = Collections.unmodifiableMap(
                new LinkedHashMap<String, Object>(features));
    }

    public static RuleResult accepted(Map<String, Object> features) {
        return new RuleResult(true, "VALID_RISK_EVENT", features);
    }

    public static RuleResult rejected(String reason) {
        return new RuleResult(false, reason, Collections.<String, Object>emptyMap());
    }

    public static RuleResult rejected(String reason, Map<String, Object> features) {
        return new RuleResult(false, reason, features);
    }

    public boolean isAccepted() { return accepted; }
    public String getReason() { return reason; }
    public Map<String, Object> getFeatures() { return features; }
}
