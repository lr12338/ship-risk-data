package com.hifleet.maritime;

import com.alibaba.fastjson.JSONObject;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class MaritimeSceneShadowAuditServiceTest {
    @Test
    public void writeRunIsBlockedWhileAuditStorageIsDisabled() {
        MaritimeSceneShadowAuditService service =
                new MaritimeSceneShadowAuditService(new MaritimeSceneService());
        MaritimeSceneShadowAuditParams params = MaritimeSceneShadowAuditParams.parse("{"
                + "\"listBatchNo\":\"liquid-container-5000-2025h2-2026h1-v1\","
                + "\"startSeq\":1,\"shipLimit\":100,"
                + "\"startTime\":\"2025-07-01 00:00:00\","
                + "\"endTime\":\"2026-07-01 00:00:00\","
                + "\"batchNo\":\"maritime-scene-shadow-test-v1\","
                + "\"sceneSnapshotVersion\":\"phase1-smoke-shanghai-autocross-v1\","
                + "\"expectedSceneFingerprint\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
                + "\"scenePolicyVersion\":\"MARITIME_SCENE_SHADOW_V1\","
                + "\"dryRun\":false,\"resume\":true}");

        JSONObject result = service.preflight(params);

        assertEquals("SHADOW_BLOCKED", result.getString("status"));
        assertEquals("write run requires audit.store-enabled=true", result.getString("reason"));
    }

    @Test
    public void passingPreflightDoesNotPretendMissingRunnerExecuted() {
        MaritimeSceneShadowAuditService service =
                new MaritimeSceneShadowAuditService(new ReadySceneService());

        JSONObject result = service.run(params(true));

        assertEquals("SHADOW_RUNNER_NOT_IMPLEMENTED", result.getString("status"));
        assertEquals(false, result.getBooleanValue("formalEventsChanged"));
    }

    private static String params(boolean dryRun) {
        return "{"
                + "\"listBatchNo\":\"liquid-container-5000-2025h2-2026h1-v1\","
                + "\"startSeq\":1,\"shipLimit\":100,"
                + "\"startTime\":\"2025-07-01 00:00:00\","
                + "\"endTime\":\"2026-07-01 00:00:00\","
                + "\"batchNo\":\"maritime-scene-shadow-test-v1\","
                + "\"sceneSnapshotVersion\":\"phase1-smoke-shanghai-autocross-v1\","
                + "\"expectedSceneFingerprint\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
                + "\"scenePolicyVersion\":\"MARITIME_SCENE_SHADOW_V1\","
                + "\"dryRun\":" + dryRun + ",\"resume\":true}";
    }

    private static final class ReadySceneService extends MaritimeSceneService {
        @Override
        public Map<String, Object> preflight() {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("available", true);
            result.put("releaseVersion", "phase1-smoke-shanghai-autocross-v1");
            result.put("snapshotFingerprint", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            result.put("policyVersion", MaritimeScenePolicy.VERSION);
            return result;
        }
    }
}
