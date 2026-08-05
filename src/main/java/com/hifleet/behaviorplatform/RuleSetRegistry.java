package com.hifleet.behaviorplatform;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RuleSetRegistry {
    private final Map<String, RuleSetDefinition> values = new LinkedHashMap<String, RuleSetDefinition>();
    public void register(RuleSetDefinition value) {
        value.validateAndHash();
        String key = key(value.getRuleSetId(), value.getRuleSetVersion());
        if (values.put(key, value) != null) throw new IllegalArgumentException("duplicate rule set: " + key);
    }
    public RuleSetDefinition require(String id, int version) {
        RuleSetDefinition value = values.get(key(id, version));
        if (value == null) throw new IllegalArgumentException("unknown rule set: " + key(id, version));
        return value;
    }
    public Collection<RuleSetDefinition> all() { return Collections.unmodifiableCollection(values.values()); }
    private String key(String id, int version) { return id + ":" + version; }
}
