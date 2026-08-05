package com.hifleet.behaviorplatform;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class RuleSetDefinitionTest {
    @Test
    public void ruleSetHasStableVersionAndHashAndBatchCannotMix() {
        RuleSetDefinition rules = new RuleSetDefinition("alongside-v1", 1, false,
                Arrays.asList(new RuleDefinition("duration", "1", 30, "min")));
        rules.validateAndHash();
        assertEquals(64, rules.getRuleSetHash().length());
        BatchRuleSetGuard guard = new BatchRuleSetGuard();
        guard.assertCompatible("batch-1", rules);
        guard.assertCompatible("batch-1", rules);
        RuleSetDefinition changed = new RuleSetDefinition("alongside-v1", 2, false,
                Arrays.asList(new RuleDefinition("duration", "2", 40, "min")));
        changed.validateAndHash();
        try {
            guard.assertCompatible("batch-1", changed);
            fail("expected mixed rules to be rejected");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("batch-1"));
        }
    }
}
