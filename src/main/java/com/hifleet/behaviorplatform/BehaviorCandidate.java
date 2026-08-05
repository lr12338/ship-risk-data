package com.hifleet.behaviorplatform;

public final class BehaviorCandidate {
    private final String comparisonKey;
    private final boolean acceptedByBehaviorRules;
    public BehaviorCandidate(String comparisonKey, boolean acceptedByBehaviorRules) {
        this.comparisonKey = comparisonKey;
        this.acceptedByBehaviorRules = acceptedByBehaviorRules;
    }
    public String getComparisonKey() { return comparisonKey; }
    public boolean isAcceptedByBehaviorRules() { return acceptedByBehaviorRules; }
}
