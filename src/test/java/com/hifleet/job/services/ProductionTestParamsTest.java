package com.hifleet.job.services;

import org.junit.Test;

import java.time.format.DateTimeParseException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProductionTestParamsTest {

    @Test
    public void parsesSharedProductionParameters() {
        ProductionTestParams params = ProductionTestParams.parse("{"
                + "\"mode\":\"production_test\","
                + "\"listBatchNo\":\"merchant-50k-2026h1-v1\","
                + "\"expectedShipCount\":50000,\"shipLimit\":5000,"
                + "\"pageSize\":200,\"parallelism\":2,"
                + "\"startTime\":\"2026-01-01 00:00:00\","
                + "\"endTime\":\"2026-07-01 00:00:00\","
                + "\"resume\":true,\"retryCount\":2,"
                + "\"batchNo\":\"prod-batch\"}");

        assertEquals(50000, params.getExpectedShipCount());
        assertEquals(5000, params.getShipLimit());
        assertEquals(200, params.getPageSize());
        assertEquals(2, params.getParallelism());
        assertTrue(params.isResume());
        assertFalse(params.isEnableInsert());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsShipLimitAboveValidatedList() {
        ProductionTestParams.parse("{"
                + "\"mode\":\"production_test\","
                + "\"listBatchNo\":\"list\","
                + "\"expectedShipCount\":100,\"shipLimit\":101,"
                + "\"startTime\":\"2026-01-01 00:00:00\","
                + "\"endTime\":\"2026-07-01 00:00:00\","
                + "\"batchNo\":\"batch\"}");
    }

    @Test
    public void acceptsCustomRangeLongerThan366Days() {
        ProductionTestParams params = ProductionTestParams.parse("{"
                + "\"mode\":\"production_test\","
                + "\"listBatchNo\":\"list\","
                + "\"expectedShipCount\":1,\"shipLimit\":1,"
                + "\"startTime\":\"2025-01-01 00:00:00\","
                + "\"endTime\":\"2026-08-01 00:00:00\","
                + "\"batchNo\":\"batch\"}");

        params.validateAlongside();
        assertEquals("2025-01-01 00:00:00", params.getStartTime());
        assertEquals("2026-08-01 00:00:00", params.getEndTime());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsReversedRange() {
        ProductionTestParams.parse("{"
                + "\"mode\":\"production_test\","
                + "\"listBatchNo\":\"list\","
                + "\"expectedShipCount\":1,\"shipLimit\":1,"
                + "\"startTime\":\"2026-08-01 00:00:00\","
                + "\"endTime\":\"2025-01-01 00:00:00\","
                + "\"batchNo\":\"batch\"}");
    }

    @Test(expected = DateTimeParseException.class)
    public void rejectsInvalidTimeFormat() {
        ProductionTestParams.parse("{"
                + "\"mode\":\"production_test\","
                + "\"listBatchNo\":\"list\","
                + "\"expectedShipCount\":1,\"shipLimit\":1,"
                + "\"startTime\":\"2025/01/01\","
                + "\"endTime\":\"2026-08-01 00:00:00\","
                + "\"batchNo\":\"batch\"}");
    }
}
