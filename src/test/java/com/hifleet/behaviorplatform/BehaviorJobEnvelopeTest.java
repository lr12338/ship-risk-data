package com.hifleet.behaviorplatform;

import org.junit.Test;

import java.util.Arrays;

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

    @Test
    public void parsesShipsAndPreservesDeduplicatedOrder() {
        BehaviorJobEnvelope value = BehaviorJobEnvelope.parse("{\"profile\":\"x-v1\","
                + "\"source\":\"SHIPS\",\"ships\":\"413123456,412123456,413123456\","
                + "\"from\":\"2026-01-01\",\"to\":\"2026-01-02\",\"batch\":\"b\"}");
        assertEquals(BehaviorTargetSourceType.SHIPS, value.getSourceType());
        assertEquals(Arrays.asList("413123456", "412123456"), value.getShips());
        assertNull(value.getListBatchNo());
    }

    @Test
    public void legacyListInfersListSource() {
        BehaviorJobEnvelope value = BehaviorJobEnvelope.parse("{\"profile\":\"x-v1\","
                + "\"list\":\"fleet-v1\",\"from\":\"2026-01-01\",\"to\":\"2026-01-02\","
                + "\"batch\":\"b\"}");
        assertEquals(BehaviorTargetSourceType.LIST, value.getSourceType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidMmsi() {
        BehaviorJobEnvelope.parse("{\"profile\":\"x-v1\",\"source\":\"SHIPS\","
                + "\"ships\":\"123\",\"from\":\"2026-01-01\",\"to\":\"2026-01-02\",\"batch\":\"b\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMixedSourceFields() {
        BehaviorJobEnvelope.parse("{\"profile\":\"x-v1\",\"source\":\"SHIPS\","
                + "\"ships\":\"413123456\",\"list\":\"fleet\",\"from\":\"2026-01-01\","
                + "\"to\":\"2026-01-02\",\"batch\":\"b\"}");
    }

    @Test
    public void parsesValidBbox() {
        BehaviorJobEnvelope value = BehaviorJobEnvelope.parse("{\"profile\":\"x-v1\","
                + "\"source\":\"REGION\",\"bbox\":\"120.0,30.0,120.05,30.05\","
                + "\"from\":\"2026-01-01\",\"to\":\"2026-01-02\",\"limit\":20,\"batch\":\"b\"}");
        assertEquals(BehaviorTargetSourceType.REGION, value.getSourceType());
        assertArrayEquals(new double[]{120.0, 30.0, 120.05, 30.05}, value.getBbox(), 0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsReversedBbox() {
        BehaviorJobEnvelope.parse("{\"profile\":\"x-v1\",\"source\":\"REGION\","
                + "\"bbox\":\"120,30,119,31\",\"from\":\"2026-01-01\",\"to\":\"2026-01-02\",\"batch\":\"b\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInlinePolygonAndOversizedEnvelope() {
        BehaviorJobEnvelope.parse("{\"profile\":\"x-v1\",\"source\":\"REGION\","
                + "\"polygonWkt\":\"POLYGON((0 0,1 0,1 1,0 0))\",\"from\":\"2026-01-01\","
                + "\"to\":\"2026-01-02\",\"batch\":\"b\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMoreThanConfiguredInlineShips() {
        BehaviorJobEnvelope.parse("{\"profile\":\"x-v1\",\"source\":\"SHIPS\","
                + "\"ships\":\"413000001,413000002,413000003\",\"from\":\"2026-01-01\","
                + "\"to\":\"2026-01-02\",\"batch\":\"b\"}", 2, 5000, 420);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsListAndRegionMix() {
        BehaviorJobEnvelope.parse("{\"profile\":\"x-v1\",\"source\":\"LIST\",\"list\":\"fleet\","
                + "\"region\":\"r-v1\",\"from\":\"2026-01-01\",\"to\":\"2026-01-02\",\"batch\":\"b\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsRegionIdAndBboxTogether() {
        BehaviorJobEnvelope.parse("{\"profile\":\"x-v1\",\"source\":\"REGION\",\"region\":\"r-v1\","
                + "\"bbox\":\"120,30,121,31\",\"from\":\"2026-01-01\",\"to\":\"2026-01-02\",\"batch\":\"b\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSqlOrTableField() {
        BehaviorJobEnvelope.parse("{\"profile\":\"x-v1\",\"source\":\"LIST\",\"list\":\"fleet\","
                + "\"table\":\"other\",\"from\":\"2026-01-01\",\"to\":\"2026-01-02\",\"batch\":\"b\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsConfiguredByteLimit() {
        BehaviorJobEnvelope.parse("{\"profile\":\"x-v1\",\"source\":\"LIST\",\"list\":\"fleet\","
                + "\"from\":\"2026-01-01\",\"to\":\"2026-01-02\",\"batch\":\"b\"}", 10, 5000, 20);
    }
}
