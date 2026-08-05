package com.hifleet.behaviorplatform;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BatchRuleSetGuard {
    private final ConcurrentMap<String, String> batches = new ConcurrentHashMap<String, String>();
    public void assertCompatible(String batchNo, RuleSetDefinition ruleSet) {
        if (ruleSet.getRuleSetHash() == null) ruleSet.validateAndHash();
        String identity = ruleSet.getRuleSetId() + ":" + ruleSet.getRuleSetVersion()
                + ":" + ruleSet.getRuleSetHash();
        String existing = batches.putIfAbsent(batchNo, identity);
        if (existing != null && !existing.equals(identity))
            throw new IllegalStateException("batch " + batchNo + " cannot mix rule sets");
    }
}
