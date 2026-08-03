package com.hifleet.maritime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MaritimeSceneShadowAuditParamsTest {
    @Test
    public void parsesFiveThousandTargetAndDefaultsDryRun() {
        MaritimeSceneShadowAuditParams params = MaritimeSceneShadowAuditParams.parse(base());
        assertEquals(5000, params.getShipLimit());
        assertTrue(params.isDryRun());
        assertTrue(params.isResume());
        assertEquals(2, params.getBehaviorTypes().size());
    }

    @Test
    public void writeModeAndWrongTargetFailClosed() {
        assertInvalid(base().replace("\"dryRun\":true", "\"dryRun\":false"));
        assertInvalid(base().replace("liquid-container-5000-2025h2-2026h1-v1", "liquid-container-100-2026-v1"));
        assertInvalid(base().replace("\"shipLimit\":5000", "\"shipLimit\":5001"));
    }

    private void assertInvalid(String raw) {
        try {
            MaritimeSceneShadowAuditParams.parse(raw);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected invalid shadow params");
    }

    private String base() {
        return "{"
                + "\"listBatchNo\":\"liquid-container-5000-2025h2-2026h1-v1\","
                + "\"startSeq\":1,\"shipLimit\":5000,"
                + "\"startTime\":\"2025-07-01 00:00:00\","
                + "\"endTime\":\"2026-07-01 00:00:00\","
                + "\"batchNo\":\"maritime-scene-shadow-202608-v1\","
                + "\"sceneSnapshotVersion\":\"phase1-smoke-shanghai-autocross-v1\","
                + "\"expectedSceneFingerprint\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
                + "\"scenePolicyVersion\":\"MARITIME_SCENE_SHADOW_V1\","
                + "\"behaviorTypes\":[\"LOITERING\",\"ALONGSIDE\"],\"dryRun\":true} ";
    }
}
