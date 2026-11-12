package com.hifleet.worldsea;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.xxljob.WorldSeaMaskJobs;
import com.xxl.job.core.biz.model.ReturnT;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;

public class WorldSeaMaskPreflightOutputTest {
    @Test
    public void reportsStrictValidationAndIndexBuildMetrics() {
        WorldSeaMaskJobs jobs = new WorldSeaMaskJobs();
        ReflectionTestUtils.setField(jobs, "worldSeaMaskService", WorldSeaTestFixtures.service());
        ReturnT<String> response = jobs.preflight("{\"mode\":\"WORLD_SEA_MASK_PREFLIGHT\"}");
        assertEquals(ReturnT.SUCCESS_CODE, response.getCode());
        JSONObject result = JSON.parseObject(response.getMsg());
        assertEquals(0, result.getIntValue("invalidGeometryCount"));
        assertEquals(1, result.getIntValue("activeDatasetCount"));
        assertEquals(true, result.getBooleanValue("indexReady"));
        assertEquals(0L, result.getLongValue("preparedGeometryBuildElapsedMs"));
        assertEquals(0L, result.getLongValue("strtreeBuildElapsedMs"));
    }
}
