package com.hifleet.tankeropensea;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TankerOpenSeaJobParamsTest {
    @Test
    public void rangeGeneratedShortPrefixIsAccepted() {
        TankerOpenSeaJobParams params = TankerOpenSeaJobParams.parse(
                "{\"mode\":\"liquid_container_exclusion_test\","
                        + "\"listBatchNo\":\"liquid-container-2026h1-v1\","
                        + "\"shipLimit\":200,"
                        + "\"startTime\":\"2026-01-01 00:00:00\","
                        + "\"endTime\":\"2026-02-01 00:00:00\","
                        + "\"windowDays\":7,"
                        + "\"enableInsert\":true,"
                        + "\"storeDetails\":false,"
                        + "\"resume\":true,"
                        + "\"parallelism\":1,"
                        + "\"retryCount\":1,"
                        + "\"logIntervalShips\":10,"
                        + "\"ruleVersion\":\"ALONGSIDE_LIQUID_CONTAINER_V2\","
                        + "\"expectedExclusionFingerprint\":"
                        + "\"7470be70e955bc79064bc402586c592a8a8da7a241e00c61997b1fa1bc0df50f\","
                        + "\"batchNo\":\"alongside-lc-s200-202601010000-202602010000-v2\"}",
                TankerOpenSeaJobParams.Behavior.ALONGSIDE);

        assertEquals("alongside-lc-s200-202601010000-202602010000-v2",
                params.getBatchNo());
    }


    @Test
    public void validAlongsideDryRunIsAcceptedAndInsertDefaultsFalse() {
        TankerOpenSeaJobParams params = TankerOpenSeaJobParams.parse(
                "{\"mode\":\"liquid_container_exclusion_test\","
                        + "\"listBatchNo\":\"liquid-container-2026h1-v1\","
                        + "\"shipLimit\":50,"
                        + "\"startTime\":\"2026-01-01 00:00:00\","
                        + "\"endTime\":\"2026-02-01 00:00:00\","
                        + "\"windowDays\":7,"
                        + "\"parallelism\":1,"
                        + "\"ruleVersion\":\"ALONGSIDE_LIQUID_CONTAINER_V2\","
                        + "\"batchNo\":\"alongside-liquid-container-dryrun-50-202601-v2\"}",
                TankerOpenSeaJobParams.Behavior.ALONGSIDE);

        assertFalse(params.isEnableInsert());
        assertTrue(params.isResume());
        assertTrue(params.getExpectedExclusionFingerprint() == null);
    }

    @Test
    public void validLoiteringWriteRequiresExplicitInsertAndNonDryRunBatch() {
        TankerOpenSeaJobParams params = TankerOpenSeaJobParams.parse(
                "{\"mode\":\"liquid_container_exclusion_test\","
                        + "\"listBatchNo\":\"liquid-container-2026h1-v1\","
                        + "\"shipLimit\":200,"
                        + "\"startTime\":\"2026-01-01 00:00:00\","
                        + "\"endTime\":\"2026-02-01 00:00:00\","
                        + "\"windowHours\":24,"
                        + "\"parallelism\":1,"
                        + "\"enableInsert\":true,"
                        + "\"expectedExclusionFingerprint\":\""
                        + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
                        + "\"ruleVersion\":\"LOITERING_LIQUID_CONTAINER_V2\","
                        + "\"batchNo\":\"loitering-liquid-container-200-202601-v2\"}",
                TankerOpenSeaJobParams.Behavior.LOITERING);

        assertTrue(params.isEnableInsert());
        assertFalse(params.isStoreDetails());
    }

    @Test
    public void wrongModeRuleBatchParallelismAndLimitFailClosed() {
        assertInvalid("{\"mode\":\"production_test\"}");
        assertInvalid(base().replace(TankerOpenSeaAlongsideRule.RULE_VERSION,
                "ALONGSIDE_V3_INSURANCE"));
        assertInvalid(base().replace("\"parallelism\":1", "\"parallelism\":2"));
        assertInvalid(base().replace("\"shipLimit\":50", "\"shipLimit\":201"));
        assertInvalid(base().replace("alongside-liquid-container-dryrun-50-202601-v2",
                "alongside-prod-2026h1-50k-v1"));
        assertInvalid(base().replace("alongside-liquid-container-dryrun-50-202601-v2",
                "alongside-liquid-container-50-202601-v2"));
        assertInvalid(base().replace("\"enableInsert\":false",
                "\"enableInsert\":true"));
    }

    private void assertInvalid(String raw) {
        try {
            TankerOpenSeaJobParams.parse(raw, TankerOpenSeaJobParams.Behavior.ALONGSIDE);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected invalid params: " + raw);
    }

    private String base() {
        return "{\"mode\":\"liquid_container_exclusion_test\","
                + "\"listBatchNo\":\"liquid-container-2026h1-v1\","
                + "\"shipLimit\":50,"
                + "\"startTime\":\"2026-01-01 00:00:00\","
                + "\"endTime\":\"2026-02-01 00:00:00\","
                + "\"windowDays\":7,"
                + "\"parallelism\":1,"
                + "\"enableInsert\":false,"
                + "\"ruleVersion\":\"" + TankerOpenSeaAlongsideRule.RULE_VERSION + "\","
                + "\"batchNo\":\"alongside-liquid-container-dryrun-50-202601-v2\"}";
    }
}
