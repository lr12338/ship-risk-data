package com.hifleet.xxljob;

import com.xxl.job.core.handler.annotation.XxlJob;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class ShipLoiteringV3BatchJobTest {

    @Test
    public void registersExpectedXxlJobHandlerName() throws Exception {
        Method method = ShipLoiteringV3BatchJob.class.getMethod(
                "shipLoiteringV3BatchJobHandler", String.class);

        XxlJob annotation = method.getAnnotation(XxlJob.class);

        assertEquals("shipLoiteringV3BatchJobHandler", annotation.value());
    }
}
