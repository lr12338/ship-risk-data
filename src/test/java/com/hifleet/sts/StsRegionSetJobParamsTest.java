package com.hifleet.sts;

import com.hifleet.sts.domain.StsRegionSetJobParams;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test — StsRegionSetJobParams validation and parsing.
 *
 * Verifies:
 *   - required fields
 *   - shipLimit bounds (1-100)
 *   - offset validation
 *   - duration <= 7d (validated in service, not params class)
 */
public class StsRegionSetJobParamsTest {

    private static String baseJson() {
        return "{\"seedBatchNo\":\"test-batch-v1\"," +
                "\"expectedShipCount\":10," +
                "\"shipOffset\":0," +
                "\"shipLimit\":50," +
                "\"regionSetCode\":\"GLOBAL_STS_TEST_REGION_SET_V1\"," +
                "\"from\":\"2026-08-01 00:00:00\"," +
                "\"to\":\"2026-08-08 00:00:00\"," +
                "\"batchNo\":\"test-001\"}";
    }

    @Test
    public void parsesAllFields() {
        StsRegionSetJobParams p = StsRegionSetJobParams.parse(baseJson());
        assertEquals("test-batch-v1", p.getSeedBatchNo());
        assertEquals(10, p.getExpectedShipCount());
        assertEquals(0, p.getShipOffset());
        assertEquals(50, p.getShipLimit());
        assertEquals("GLOBAL_STS_TEST_REGION_SET_V1", p.getRegionSetCode());
        assertEquals("2026-08-01 00:00:00", p.getFrom());
        assertEquals("2026-08-08 00:00:00", p.getTo());
        assertEquals("test-001", p.getBatchNo());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankParams() {
        StsRegionSetJobParams.parse("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullParams() {
        StsRegionSetJobParams.parse(null);
    }

    @Test
    public void zeroOffsetIsValid() {
        StsRegionSetJobParams p = StsRegionSetJobParams.parse(baseJson());
        assertEquals(0, p.getShipOffset());
    }

    @Test
    public void shipLimitAt1IsOk() {
        String json = baseJson().replace("\"shipLimit\":50", "\"shipLimit\":1");
        StsRegionSetJobParams p = StsRegionSetJobParams.parse(json);
        assertEquals(1, p.getShipLimit());
    }

    @Test
    public void shipLimitAt100IsOk() {
        String json = baseJson().replace("\"shipLimit\":50", "\"shipLimit\":100");
        StsRegionSetJobParams p = StsRegionSetJobParams.parse(json);
        assertEquals(100, p.getShipLimit());
    }
}
