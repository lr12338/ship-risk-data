package com.hifleet.job.services;

import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AlongsideV3HistoryTestParamsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 27, 12, 0, 0);

    @Test
    public void defaultsToSafeThirtyDayDryRun() {
        AlongsideV3HistoryTestParams params = AlongsideV3HistoryTestParams.parse(
                "{\"shipLimit\":1000,\"batchSize\":100}",
                1000, 100, 30, 30, 110000, 1000, 31, NOW);

        assertEquals("history_test", params.getMode());
        assertEquals(1000, params.getShipLimit());
        assertEquals(100, params.getBatchSize());
        assertEquals(30, params.getWindowDays());
        assertEquals("2026-06-27 12:00:00", params.getStartTime());
        assertEquals("2026-07-27 12:00:00", params.getEndTime());
        assertFalse(params.isEnableInsert());
        assertTrue(params.isResume());
        assertTrue(params.getStatusBatchNo().startsWith("dry-"));
    }

    @Test
    public void explicitTimeRangeAndInsertFlagTakePrecedence() {
        AlongsideV3HistoryTestParams params = AlongsideV3HistoryTestParams.parse(
                "{\"mode\":\"history_test\",\"shipLimit\":12,\"batchSize\":5,"
                        + "\"startTime\":\"2025-01-01 00:00:00\","
                        + "\"endTime\":\"2025-01-31 23:59:59\",\"enableInsert\":true,"
                        + "\"resume\":false,\"batchNo\":\"history-case-1\"}",
                1000, 100, 30, 30, 110000, 1000, 31, NOW);

        assertEquals("2025-01-01 00:00:00", params.getStartTime());
        assertEquals("2025-01-31 23:59:59", params.getEndTime());
        assertEquals("history-case-1", params.getBatchNo());
        assertTrue(params.isEnableInsert());
        assertFalse(params.isResume());
        assertEquals("write-history-case-1", params.getStatusBatchNo());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonHistoryMode() {
        AlongsideV3HistoryTestParams.parse(
                "{\"mode\":\"production\",\"shipLimit\":10}",
                1000, 100, 30, 30, 110000, 1000, 31, NOW);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPartialExplicitTimeRange() {
        AlongsideV3HistoryTestParams.parse(
                "{\"startTime\":\"2025-01-01 00:00:00\"}",
                1000, 100, 30, 30, 110000, 1000, 31, NOW);
    }
}
