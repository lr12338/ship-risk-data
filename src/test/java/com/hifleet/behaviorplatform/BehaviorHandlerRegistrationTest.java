package com.hifleet.behaviorplatform;

import com.hifleet.xxljob.BehaviorPlatformJobs;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BehaviorHandlerRegistrationTest {
    @Test
    public void unifiedAndLegacyHandlersRemainRegisteredAndFormalRejects() throws Exception {
        assertEquals("shipBehaviorAuditJobHandler",
                BehaviorPlatformJobs.class.getMethod("audit", String.class).getAnnotation(XxlJob.class).value());
        assertEquals("shipBehaviorShadowV2FleetAuditJobHandler",
                BehaviorPlatformJobs.class.getMethod("fleetAudit", String.class).getAnnotation(XxlJob.class).value());
        ReturnT<String> formal = new BehaviorPlatformJobs(null).formal("{}");
        assertEquals(ReturnT.FAIL_CODE, formal.getCode());
    }
}
