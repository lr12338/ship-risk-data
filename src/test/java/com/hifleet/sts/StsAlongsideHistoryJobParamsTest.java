package com.hifleet.sts;

import com.hifleet.sts.domain.StsAlongsideHistoryJobParams;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StsAlongsideHistoryJobParamsTest {

    @Test
    public void parsesExplicitImosAndSafeDefaults() {
        StsAlongsideHistoryJobParams params = StsAlongsideHistoryJobParams.parse("{"
                + "\"mode\":\"STS_ALONGSIDE_HISTORY_V1\","
                + "\"seedBatchNo\":\"seed\",\"regionSetCode\":\"regions\","
                + "\"imos\":[\"9000001\",\"9000002\"],"
                + "\"from\":\"2024-01-01 00:00:00\",\"to\":\"2024-01-02 00:00:00\","
                + "\"batchNo\":\"batch\"}");

        assertEquals(Arrays.asList("9000001", "9000002"), params.getImos());
        assertEquals(1, params.getSliceDays());
        assertEquals(1, params.getParallelism());
        assertEquals("SHADOW_ONLY", params.getWriteMode());
        assertTrue(params.isResume());
        assertFalse(params.isAutoContinue());
        assertEquals(8, params.getMaxSlicesPerInvocation());
        assertEquals(90, params.getMaxRuntimeMinutes());
    }

    @Test
    public void parsesAutoContinueBoundsAndZeroUsesSafeDefaults() {
        StsAlongsideHistoryJobParams explicit = StsAlongsideHistoryJobParams.parse(baseJson(
                "\"autoContinue\":true,\"maxSlicesPerInvocation\":3,\"maxRuntimeMinutes\":120"));
        explicit.validate();
        assertTrue(explicit.isAutoContinue());
        assertEquals(3, explicit.getMaxSlicesPerInvocation());
        assertEquals(120, explicit.getMaxRuntimeMinutes());

        StsAlongsideHistoryJobParams zero = StsAlongsideHistoryJobParams.parse(baseJson(
                "\"autoContinue\":true,\"maxSlicesPerInvocation\":0,\"maxRuntimeMinutes\":0"));
        zero.validate();
        assertEquals(8, zero.getMaxSlicesPerInvocation());
        assertEquals(90, zero.getMaxRuntimeMinutes());
    }

    @Test
    public void rejectsNegativeAutoContinueBounds() {
        assertRejected("\"maxSlicesPerInvocation\":-1", "maxSlicesPerInvocation");
        assertRejected("\"maxRuntimeMinutes\":-1", "maxRuntimeMinutes");
    }

    @Test
    public void rejectsUnsafeWriteModeAndParallelism() {
        assertRejected("\"writeMode\":\"FORMAL\"", "SHADOW_ONLY");
        assertRejected("\"parallelism\":2", "parallelism");
    }

    @Test
    public void validatesCandidateFunnelPositiveAndZeroControlParams() {
        StsAlongsideHistoryJobParams positive = StsAlongsideHistoryJobParams.parse(
                funnelJson("sts-candidate-funnel-positive-20240902-0909-v1",
                        "2024-09-02 00:00:00", "2024-09-09 00:00:00"));
        StsAlongsideHistoryJobParams zero = StsAlongsideHistoryJobParams.parse(
                funnelJson("sts-candidate-funnel-zero-20260813-0820-v1",
                        "2026-08-13 00:00:00", "2026-08-20 00:00:00"));

        positive.validate();
        zero.validate();
        assertEquals(786, positive.getShipCount());
        assertEquals(7, positive.getSliceDays());
        assertFalse(positive.isAutoContinue());
        assertEquals("SHADOW_ONLY", positive.getWriteMode());
        assertEquals(786, zero.getShipCount());
        assertEquals(7, zero.getSliceDays());
        assertFalse(zero.isAutoContinue());
        assertEquals("SHADOW_ONLY", zero.getWriteMode());
    }

    @Test
    public void validatesMixedFiveThousandSmokeAndOneYearShapes() {
        StsAlongsideHistoryJobParams smoke = StsAlongsideHistoryJobParams.parse(
                mixedFiveThousandJson("sts-hev1-mixed5k-scale-smoke-7d-20260911-v1",
                        "2026-08-13 00:00:00", "2026-08-20 00:00:00", false,
                        ",\"maxSlicesPerInvocation\":1,\"maxRuntimeMinutes\":360"));
        StsAlongsideHistoryJobParams oneYear = StsAlongsideHistoryJobParams.parse(
                mixedFiveThousandJson("sts-hev1-mixed5k-full-1y-safe-20260911-v1",
                        "2025-09-02 00:00:00", "2026-09-03 00:00:00", true, ""));

        smoke.validate();
        oneYear.validate();
        assertEquals(5000, smoke.getShipCount());
        assertFalse(smoke.isAutoContinue());
        assertEquals(1, smoke.getMaxSlicesPerInvocation());
        assertEquals(5000, oneYear.getShipCount());
        assertTrue(oneYear.isAutoContinue());
    }

    private void assertRejected(String override, String message) {
        String json = "{\"mode\":\"STS_ALONGSIDE_HISTORY_V1\","
                + "\"seedBatchNo\":\"seed\",\"regionSetCode\":\"regions\","
                + "\"shipOffset\":0,\"shipCount\":20,"
                + "\"from\":\"2024-01-01 00:00:00\",\"to\":\"2024-01-02 00:00:00\","
                + "\"batchNo\":\"batch\"," + override + "}";
        try {
            StsAlongsideHistoryJobParams.parse(json).validate();
            fail("expected rejection");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(message));
        }
    }

    private String baseJson(String fields) {
        return "{\"mode\":\"STS_ALONGSIDE_HISTORY_V1\","
                + "\"seedBatchNo\":\"seed\",\"regionSetCode\":\"regions\","
                + "\"shipOffset\":0,\"shipCount\":20,"
                + "\"from\":\"2024-01-01 00:00:00\",\"to\":\"2024-01-02 00:00:00\","
                + "\"batchNo\":\"batch\"," + fields + "}";
    }

    private String funnelJson(String batchNo, String from, String to) {
        return "{\"mode\":\"STS_ALONGSIDE_HISTORY_V1\","
                + "\"seedBatchNo\":\"sts-crude-identity-20260902-v1\","
                + "\"regionSetCode\":\"STS_MEETING_PRIORITY_REGION_SET_V1\","
                + "\"shipOffset\":0,\"shipCount\":786,"
                + "\"from\":\"" + from + "\",\"to\":\"" + to + "\","
                + "\"sliceDays\":7,\"batchNo\":\"" + batchNo + "\","
                + "\"parallelism\":1,\"writeMode\":\"SHADOW_ONLY\","
                + "\"resume\":true,\"autoContinue\":false}";
    }

    private String mixedFiveThousandJson(String batchNo, String from, String to,
                                          boolean autoContinue, String optionalBounds) {
        return "{\"mode\":\"STS_ALONGSIDE_HISTORY_V1\","
                + "\"seedBatchNo\":\"sts-mixed-5000-identity-20260911-v1\","
                + "\"regionSetCode\":\"STS_MEETING_PRIORITY_REGION_SET_V1\","
                + "\"shipOffset\":0,\"shipCount\":5000,"
                + "\"from\":\"" + from + "\",\"to\":\"" + to + "\","
                + "\"sliceDays\":7,\"batchNo\":\"" + batchNo + "\","
                + "\"parallelism\":1,\"writeMode\":\"SHADOW_ONLY\","
                + "\"resume\":true,\"autoContinue\":" + autoContinue + optionalBounds + "}";
    }
}
