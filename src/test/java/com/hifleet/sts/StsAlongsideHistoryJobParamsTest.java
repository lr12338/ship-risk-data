package com.hifleet.sts;

import com.hifleet.sts.domain.StsAlongsideHistoryJobParams;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
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
    }

    @Test
    public void rejectsUnsafeWriteModeAndParallelism() {
        assertRejected("\"writeMode\":\"FORMAL\"", "SHADOW_ONLY");
        assertRejected("\"parallelism\":2", "parallelism");
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
}
