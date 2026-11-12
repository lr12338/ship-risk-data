package com.hifleet.sts;

import com.hifleet.sts.domain.StsRegionSetJobParams;
import com.hifleet.sts.service.StsRegionSetShadowService;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * Test — StsRegionSetShadowService parameter validation.
 *
 * Uses reflection to invoke the private validateParams method
 * since the public runRegionSetShadow method requires DB access.
 */
public class StsRegionSetShadowValidationTest {

    private static StsRegionSetJobParams validParams() {
        String json = "{\"seedBatchNo\":\"test-batch-v1\"," +
                "\"expectedShipCount\":10," +
                "\"shipOffset\":0," +
                "\"shipLimit\":50," +
                "\"regionSetCode\":\"GLOBAL_TEST_V1\"," +
                "\"from\":\"2026-08-01 00:00:00\"," +
                "\"to\":\"2026-08-08 00:00:00\"," +
                "\"batchNo\":\"test-001\"}";
        return StsRegionSetJobParams.parse(json);
    }

    private void invokeValidate(StsRegionSetJobParams params) throws Exception {
        StsRegionSetShadowService service = new StsRegionSetShadowService();
        Method m = StsRegionSetShadowService.class.getDeclaredMethod(
                "validateParams", StsRegionSetJobParams.class);
        m.setAccessible(true);
        try {
            m.invoke(service, params);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw e;
        }
    }

    @Test
    public void validParamsPassValidation() throws Exception {
        invokeValidate(validParams());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankSeedBatchNo() throws Exception {
        StsRegionSetJobParams p = validParams();
        p.setSeedBatchNo("");
        invokeValidate(p);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullSeedBatchNo() throws Exception {
        StsRegionSetJobParams p = validParams();
        p.setSeedBatchNo(null);
        invokeValidate(p);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroExpectedShipCount() throws Exception {
        StsRegionSetJobParams p = validParams();
        p.setExpectedShipCount(0);
        invokeValidate(p);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeExpectedShipCount() throws Exception {
        StsRegionSetJobParams p = validParams();
        p.setExpectedShipCount(-5);
        invokeValidate(p);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeOffset() throws Exception {
        StsRegionSetJobParams p = validParams();
        p.setShipOffset(-1);
        invokeValidate(p);
    }

    @Test
    public void zeroOffsetIsValid() throws Exception {
        StsRegionSetJobParams p = validParams();
        p.setShipOffset(0);
        invokeValidate(p);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroShipLimit() throws Exception {
        StsRegionSetJobParams p = validParams();
        p.setShipLimit(0);
        invokeValidate(p);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsShipLimitAbove100() throws Exception {
        StsRegionSetJobParams p = validParams();
        p.setShipLimit(101);
        invokeValidate(p);
    }

    @Test
    public void shipLimitOf100IsValid() throws Exception {
        StsRegionSetJobParams p = validParams();
        p.setShipLimit(100);
        invokeValidate(p);
    }

    @Test
    public void shipLimitOf1IsValid() throws Exception {
        StsRegionSetJobParams p = validParams();
        p.setShipLimit(1);
        invokeValidate(p);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankRegionSetCode() throws Exception {
        StsRegionSetJobParams p = validParams();
        p.setRegionSetCode("");
        invokeValidate(p);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankFrom() throws Exception {
        StsRegionSetJobParams p = validParams();
        p.setFrom("");
        invokeValidate(p);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankTo() throws Exception {
        StsRegionSetJobParams p = validParams();
        p.setTo("");
        invokeValidate(p);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTimeSpanOver7Days() throws Exception {
        StsRegionSetJobParams p = validParams();
        p.setTo("2026-08-09 00:00:00");
        invokeValidate(p);
    }

    @Test
    public void exactly7DaysIsValid() throws Exception {
        StsRegionSetJobParams p = validParams();
        p.setTo("2026-08-08 00:00:00");
        invokeValidate(p);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankBatchNo() throws Exception {
        StsRegionSetJobParams p = validParams();
        p.setBatchNo("");
        invokeValidate(p);
    }
}
