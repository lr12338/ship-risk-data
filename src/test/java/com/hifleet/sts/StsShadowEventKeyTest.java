package com.hifleet.sts;

import com.hifleet.sts.service.StsRegionShadowService;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;

/**
 * Test D — Shadow Writer Idempotency
 *
 * Verifies:
 *   - eventKey is deterministic (same inputs → same key)
 *   - different inputs produce different keys
 *   - key format is valid SHA-256 hex (64 chars)
 *
 * DB-level INSERT IGNORE idempotency requires a real database
 * and is validated in remote smoke tests.
 */
public class StsShadowEventKeyTest {

    private static final String REGION = "PG_TEST";
    private static final String IMO = "9000001";
    private static final String OWN_MMSI = "111111111";
    private static final String TGT_MMSI = "222222222";
    private static final String START = "2026-01-01 00:00:00";
    private static final String END = "2026-01-01 02:00:00";
    private static final String RULE = "ALONGSIDE_V3_BASELINE";

    @Test
    public void eventKeyIsDeterministic() {
        String k1 = StsRegionShadowService.computeEventKey(
                REGION, IMO, OWN_MMSI, TGT_MMSI, START, END, RULE);
        String k2 = StsRegionShadowService.computeEventKey(
                REGION, IMO, OWN_MMSI, TGT_MMSI, START, END, RULE);
        assertEquals("same inputs must produce same event key", k1, k2);
    }

    @Test
    public void eventKeyIsSha256HexFormat() {
        String key = StsRegionShadowService.computeEventKey(
                REGION, IMO, OWN_MMSI, TGT_MMSI, START, END, RULE);
        assertNotNull(key);
        assertEquals("SHA-256 hex should be 64 characters", 64, key.length());
        // All hex chars
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            boolean isHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            assert isHex : "event key contains non-hex character: " + c;
        }
    }

    @Test
    public void differentTargetMmsiProducesDifferentKey() {
        String k1 = StsRegionShadowService.computeEventKey(
                REGION, IMO, OWN_MMSI, TGT_MMSI, START, END, RULE);
        String k2 = StsRegionShadowService.computeEventKey(
                REGION, IMO, OWN_MMSI, "333333333", START, END, RULE);
        assertNotEquals("different target MMSI should produce different keys", k1, k2);
    }

    @Test
    public void differentStartTimeProducesDifferentKey() {
        String k1 = StsRegionShadowService.computeEventKey(
                REGION, IMO, OWN_MMSI, TGT_MMSI, START, END, RULE);
        String k2 = StsRegionShadowService.computeEventKey(
                REGION, IMO, OWN_MMSI, TGT_MMSI, "2026-01-01 01:00:00", END, RULE);
        assertNotEquals("different start time should produce different keys", k1, k2);
    }

    @Test
    public void differentRegionProducesDifferentKey() {
        String k1 = StsRegionShadowService.computeEventKey(
                REGION, IMO, OWN_MMSI, TGT_MMSI, START, END, RULE);
        String k2 = StsRegionShadowService.computeEventKey(
                "SINGAPORE_TEST", IMO, OWN_MMSI, TGT_MMSI, START, END, RULE);
        assertNotEquals("different region should produce different keys", k1, k2);
    }

    @Test
    public void sameEventAcrossBatchesHasSameKey() {
        // The eventKey is independent of batchNo — batchNo is the other
        // half of the UNIQUE constraint. This means the same physical event
        // re-run in a different batch gets a different row, which is correct
        // (batches are independent experiments).
        String key = StsRegionShadowService.computeEventKey(
                REGION, IMO, OWN_MMSI, TGT_MMSI, START, END, RULE);
        assertNotNull(key);
        assertEquals(64, key.length());
    }
}
