package com.hifleet.sts;

import com.hifleet.sts.domain.StsFleetBackfillJobParams;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test — StsFleetBackfillJobParams parsing.
 */
public class StsFleetBackfillParamsTest {

    private static String baseJson() {
        return "{\"targetListBatchNo\":\"merchant-50k-2026h1-v1\"," +
                "\"expectedTargetCount\":44760," +
                "\"shipOffset\":0," +
                "\"shipCount\":500," +
                "\"regionSetCode\":\"STS_HOTSPOT_HIGH_YIELD_V2\"," +
                "\"from\":\"2024-01-01 00:00:00\"," +
                "\"to\":\"2024-02-01 00:00:00\"," +
                "\"sliceDays\":7," +
                "\"batchNo\":\"sts-test-001\"}";
    }

    @Test
    public void parsesAllFields() {
        StsFleetBackfillJobParams p = StsFleetBackfillJobParams.parse(baseJson());
        assertEquals("merchant-50k-2026h1-v1", p.getTargetListBatchNo());
        assertEquals(44760, p.getExpectedTargetCount());
        assertEquals(0, p.getShipOffset());
        assertEquals(500, p.getShipCount());
        assertEquals("STS_HOTSPOT_HIGH_YIELD_V2", p.getRegionSetCode());
        assertEquals("2024-01-01 00:00:00", p.getFrom());
        assertEquals("2024-02-01 00:00:00", p.getTo());
        assertEquals(7, p.getSliceDays());
        assertEquals("sts-test-001", p.getBatchNo());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlank() {
        StsFleetBackfillJobParams.parse("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNull() {
        StsFleetBackfillJobParams.parse(null);
    }

    @Test
    public void offsetIsInt() {
        String json = baseJson().replace("\"shipOffset\":0", "\"shipOffset\":5000");
        StsFleetBackfillJobParams p = StsFleetBackfillJobParams.parse(json);
        assertEquals(5000, p.getShipOffset());
    }

    @Test
    public void shipCountAtMax5000IsOk() {
        String json = baseJson().replace("\"shipCount\":500", "\"shipCount\":5000");
        StsFleetBackfillJobParams p = StsFleetBackfillJobParams.parse(json);
        assertEquals(5000, p.getShipCount());
    }
}
