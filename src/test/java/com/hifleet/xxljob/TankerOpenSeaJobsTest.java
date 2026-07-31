package com.hifleet.xxljob;

import com.xxl.job.core.handler.annotation.XxlJob;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class TankerOpenSeaJobsTest {

    @Test
    public void isolatedHandlersHaveDedicatedNames() throws Exception {
        assertHandler("targetList", "shipLiquidContainerTargetListTestJobHandler");
        assertHandler("alongside", "shipLiquidContainerAlongsideTestJobHandler");
        assertHandler("loitering", "shipLiquidContainerLoiteringTestJobHandler");
        assertHandler("alongsideRange",
                "shipLiquidContainerAlongsideRangeJobHandler");
        assertHandler("loiteringRange",
                "shipLiquidContainerLoiteringRangeJobHandler");
    }

    @Test
    public void existingProductionHandlerNamesRemainUnchanged() throws Exception {
        assertEquals("shipAlongsideProductionTestJobHandler",
                ShipBehaviorProductionJobs.class
                        .getMethod("shipAlongsideProductionTestJobHandler", String.class)
                        .getAnnotation(XxlJob.class).value());
        assertEquals("shipLoiteringProductionTestJobHandler",
                ShipBehaviorProductionJobs.class
                        .getMethod("shipLoiteringProductionTestJobHandler", String.class)
                        .getAnnotation(XxlJob.class).value());
    }

    private void assertHandler(String methodName, String expected) throws Exception {
        Method method = TankerOpenSeaJobs.class.getMethod(methodName, String.class);
        assertEquals(expected, method.getAnnotation(XxlJob.class).value());
    }
}
