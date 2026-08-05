package com.hifleet.behaviorplatform;

import com.alibaba.fastjson.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class BehaviorScopeExecutionResult {
    private final List<BehaviorAuditResult> auditResults = new ArrayList<BehaviorAuditResult>();
    private final List<BehaviorFailureRecord> failures = new ArrayList<BehaviorFailureRecord>();
    private final JSONObject metrics = new JSONObject(true);
    private int processed;
    private int successful;
    private int skipped;
    private int retried;
    private int candidates;
    private int retained;
    private int reviews;
    public List<BehaviorAuditResult> getAuditResults() { return auditResults; }
    public List<BehaviorFailureRecord> getFailures() { return failures; }
    public JSONObject getMetrics() { return metrics; }
    public int getProcessed() { return processed; } public void processed() { processed++; }
    public int getSuccessful() { return successful; } public void successful() { successful++; }
    public int getSkipped() { return skipped; } public void skipped() { skipped++; }
    public int getRetried() { return retried; } public void retried() { retried++; }
    public int getCandidates() { return candidates; } public void addCandidates(int v) { candidates += v; }
    public int getRetained() { return retained; } public void addRetained(int v) { retained += v; }
    public int getReviews() { return reviews; } public void addReviews(int v) { reviews += v; }
}
