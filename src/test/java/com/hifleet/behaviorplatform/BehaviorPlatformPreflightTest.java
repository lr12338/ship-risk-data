package com.hifleet.behaviorplatform;

import com.alibaba.fastjson.JSONObject;
import com.hifleet.maritime.MaritimeSceneService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BehaviorPlatformPreflightTest {
    @Test
    public void preflightReadsNoTrajectoryAndDryRunForcesZeroWriteMode() {
        BehaviorPlatformRegistries registries = new BehaviorPlatformRegistries();
        ReflectionTestUtils.setField(registries, "externalProfileDirectory", "");
        registries.load();
        BehaviorScopeExecutor executor = new BehaviorScopeExecutor() {
            public boolean supports(BehaviorTargetSourceType type) { return type == BehaviorTargetSourceType.LIST; }
            public JSONObject preflight(BehaviorScopeContext context) {
                JSONObject value = new JSONObject(); value.put("sourceResolvedCount", 100); return value;
            }
            public BehaviorScopeExecutionResult execute(BehaviorScopeContext context) {
                throw new AssertionError("preflight must not execute scope");
            }
        };
        BehaviorPlatformFleetAuditService service = new BehaviorPlatformFleetAuditService(
                registries, new MaritimeSceneService(), (manifest, value) -> true,
                java.util.Collections.singletonList(executor), null);
        ReflectionTestUtils.setField(service, "outputDirectory", "var/behavior-runs");
        ReflectionTestUtils.setField(service, "shadowWriteEnabled", false);
        ReflectionTestUtils.setField(service, "formalWriteEnabled", false);
        ReflectionTestUtils.setField(service, "maxInlineShips", 10);
        ReflectionTestUtils.setField(service, "maxListShips", 5000);
        ReflectionTestUtils.setField(service, "maxParamBytes", 420);
        ReflectionTestUtils.setField(service, "maxParallelism", 1);
        String raw = "{\"profile\":\"alongside-scene-shadow-v1\",\"list\":\"fleet-v1\","
                + "\"from\":\"2026-01-01\",\"to\":\"2026-01-02\",\"batch\":\"b1\","
                + "\"dryRun\":true,\"sceneModeOverride\":\"OFF\"}";

        JSONObject result = service.preflight(raw, false);

        assertEquals("PASS", result.getString("status"));
        assertEquals("NONE", result.getString("writeModeEffective"));
        assertEquals(0, result.getIntValue("databaseWriteCount"));
    }
}
