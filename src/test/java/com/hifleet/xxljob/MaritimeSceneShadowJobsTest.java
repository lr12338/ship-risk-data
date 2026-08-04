package com.hifleet.xxljob;

import com.alibaba.fastjson.JSONObject;
import com.hifleet.maritime.MaritimeSceneShadowAuditService;
import com.xxl.job.core.biz.model.ReturnT;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MaritimeSceneShadowJobsTest {
    private static final String PARAMS = "{\"listBatchNo\":\"liquid-container-5000-2025h2-2026h1-v1\","
            + "\"batchNo\":\"maritime-scene-shadow-test\",\"behaviorTypes\":[\"LOITERING\"],"
            + "\"startTime\":\"2026-01-01 00:00:00\",\"endTime\":\"2026-01-02 00:00:00\","
            + "\"shipLimit\":1,\"dryRun\":true,\"sceneSnapshotVersion\":\"release\","
            + "\"expectedSceneFingerprint\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
            + "\"scenePolicyVersion\":\"MARITIME_SCENE_SHADOW_V1\"}";

    @Test
    public void preflightSucceedsOnlyForInternalPass() {
        MaritimeSceneShadowAuditService service = mock(MaritimeSceneShadowAuditService.class);
        JSONObject blocked = new JSONObject();
        blocked.put("status", "SHADOW_BLOCKED");
        when(service.preflight(any())).thenReturn(blocked);

        ReturnT<String> result = new MaritimeSceneShadowJobs(service).preflight(PARAMS);

        assertEquals(ReturnT.FAIL_CODE, result.getCode());

        JSONObject disabled = new JSONObject();
        disabled.put("status", "SHADOW_DISABLED");
        when(service.preflight(any())).thenReturn(disabled);
        assertEquals(ReturnT.FAIL_CODE,
                new MaritimeSceneShadowJobs(service).preflight(PARAMS).getCode());

        JSONObject pass = new JSONObject();
        pass.put("status", "PASS");
        when(service.preflight(any())).thenReturn(pass);
        assertEquals(ReturnT.SUCCESS_CODE,
                new MaritimeSceneShadowJobs(service).preflight(PARAMS).getCode());
    }
}
