package com.hifleet.behaviorplatform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BehaviorOrchestrationResult {
    private final BehaviorComparison comparison;
    private final List<BehaviorAuditResult> auditResults;
    public BehaviorOrchestrationResult(BehaviorComparison comparison, List<BehaviorAuditResult> auditResults) {
        this.comparison = comparison;
        this.auditResults = Collections.unmodifiableList(new ArrayList<BehaviorAuditResult>(auditResults));
    }
    public BehaviorComparison getComparison() { return comparison; }
    public List<BehaviorAuditResult> getAuditResults() { return auditResults; }
}
