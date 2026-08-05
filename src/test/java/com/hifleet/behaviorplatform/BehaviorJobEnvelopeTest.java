package com.hifleet.behaviorplatform;

import org.junit.Test;

import static org.junit.Assert.*;

public class BehaviorJobEnvelopeTest {
    @Test
    public void parsesShortEnvelopeAndNormalizesDates() {
        String raw = "{\"profile\":\"alongside-control-off-v1\",\"list\":\"fleet-v1\","
                + "\"from\":\"2025-07-01\",\"to\":\"2026-01-01\",\"limit\":100,"
                + "\"batch\":\"as-ab-20260805-01\"}";
        BehaviorJobEnvelope value = BehaviorJobEnvelope.parse(raw);
        assertEquals("alongside-control-off-v1", value.getProfileId());
        assertEquals("fleet-v1", value.getListBatchNo());
        assertEquals("2025-07-01 00:00:00", value.getStartTime());
        assertEquals("2026-01-01 00:00:00", value.getEndTime());
        assertEquals(100, value.getShipLimit());
        assertTrue(value.utf8Length(raw) < 300);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownFields() {
        BehaviorJobEnvelope.parse("{\"profile\":\"x-v1\",\"list\":\"l\",\"from\":\"2026-01-01\","
                + "\"to\":\"2026-01-02\",\"batch\":\"b\",\"databaseWriteEnabled\":true}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTruncatedJson() {
        BehaviorJobEnvelope.parse("{\"profile\":\"x-v1\"");
    }
}
