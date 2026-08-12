package com.hifleet.xxljob;

import com.alibaba.fastjson.JSONObject;
import com.hifleet.regionbehavior.RegionAisDiscoveryResult;
import com.hifleet.regionbehavior.RegionBehaviorRunResult;
import com.hifleet.regionbehavior.RegionBehaviorRunService;
import com.hifleet.regionbehavior.RegionBehaviorStatus;
import com.xxl.job.core.biz.model.ReturnT;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RegionBehaviorJobsTest {

    @Test
    public void partialDryRunReturnsXxlFailure() {
        RegionBehaviorRunService service = mock(RegionBehaviorRunService.class);
        RegionAisDiscoveryResult discovery = new RegionAisDiscoveryResult();
        RegionBehaviorRunResult result = RegionBehaviorRunResult.fromDiscovery(discovery);
        result.markPartial();
        when(service.run("{}")).thenReturn(result);

        ReturnT<String> response = new RegionBehaviorJobs(service).dryRun("{}");

        assertEquals(ReturnT.FAIL_CODE, response.getCode());
    }

    @Test
    public void blockedPreflightReturnsXxlFailure() {
        RegionBehaviorRunService service = mock(RegionBehaviorRunService.class);
        JSONObject result = new JSONObject();
        result.put("status", RegionBehaviorStatus.BLOCKED_INVALID_GEOMETRY.name());
        when(service.preflight("{}")).thenReturn(result);

        ReturnT<String> response = new RegionBehaviorJobs(service).preflight("{}");

        assertEquals(ReturnT.FAIL_CODE, response.getCode());
    }
}
