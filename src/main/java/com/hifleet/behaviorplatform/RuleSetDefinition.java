package com.hifleet.behaviorplatform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RuleSetDefinition {
    private final String ruleSetId;
    private final int ruleSetVersion;
    private final boolean productionApproved;
    private final List<RuleDefinition> rules;
    private String ruleSetHash;

    public RuleSetDefinition(String ruleSetId, int ruleSetVersion, boolean productionApproved,
                             List<RuleDefinition> rules) {
        this.ruleSetId = ruleSetId;
        this.ruleSetVersion = ruleSetVersion;
        this.productionApproved = productionApproved;
        this.rules = Collections.unmodifiableList(new ArrayList<RuleDefinition>(rules));
    }

    public void validateAndHash() {
        if (ruleSetId == null || ruleSetVersion <= 0 || rules.isEmpty())
            throw new IllegalArgumentException("rule set id, version and rules are required");
        for (RuleDefinition rule : rules) {
            if (rule.getRuleId() == null || rule.getRuleVersion() == null || rule.getUnit() == null)
                throw new IllegalArgumentException("rule definition is incomplete");
        }
        Map<String, Object> hashInput = new LinkedHashMap<String, Object>();
        hashInput.put("ruleSetId", ruleSetId);
        hashInput.put("ruleSetVersion", ruleSetVersion);
        hashInput.put("productionApproved", productionApproved);
        hashInput.put("rules", rules);
        ruleSetHash = BehaviorHashes.sha256(BehaviorHashes.canonicalJson(hashInput));
    }
    public String getRuleSetId() { return ruleSetId; }
    public int getRuleSetVersion() { return ruleSetVersion; }
    public boolean isProductionApproved() { return productionApproved; }
    public List<RuleDefinition> getRules() { return rules; }
    public String getRuleSetHash() { return ruleSetHash; }
}
