package com.hifleet.maritime;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ScenePreflightResultTest {
    @Test
    public void typedPreflightNeverOverwritesRuntimeWithSnapshotPolicy() {
        SceneRuntimeInfo runtime = new SceneRuntimeInfo(true, "SHADOW", "V2");
        SceneSnapshotInfo snapshot = new SceneSnapshotInfo(true, "release-v1", "abc", "V1", "PASS");
        ScenePreflightResult result = new ScenePreflightResult(runtime, snapshot);
        assertEquals("V2", result.getRuntimePolicyVersion());
        assertEquals("V1", result.getSnapshotPolicyVersion());
        assertTrue(result.isCompatible("V1"));
        Map<String, Object> legacy = result.toLegacyMap();
        assertEquals("V2", legacy.get("policyVersion"));
        assertEquals("V1", legacy.get("snapshotPolicyVersion"));
    }

    @Test
    public void serviceExposesTypedPreflightWhileKeepingLegacyMap() {
        MaritimeSceneSnapshotManager manager = new MaritimeSceneSnapshotManager(null, null, "V1", 1) {
            @Override public synchronized Map<String, Object> preflight() {
                Map<String, Object> value = new LinkedHashMap<String, Object>();
                value.put("available", true);
                value.put("releaseVersion", "release-v1");
                value.put("snapshotFingerprint", "abc");
                value.put("policyVersion", "V1");
                value.put("checksumStatus", "PASS");
                return value;
            }
        };
        MaritimeSceneService service = new MaritimeSceneService(manager, MaritimeScenePolicy.defaults("SHADOW"));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "policyVersion", "V2");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "snapshotPolicyVersion", "V1");
        ScenePreflightResult result = service.preflightResult();
        assertEquals("V2", result.getRuntimePolicyVersion());
        assertEquals("V1", result.getSnapshotPolicyVersion());
        assertEquals("V2", service.preflight().get("policyVersion"));
    }
}
