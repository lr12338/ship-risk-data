package com.hifleet.tankeropensea;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class TankerOpenSeaRangeServiceTest {

    @Test
    public void alongsideHalfYearIsSplitIntoSixCalendarMonthBatches() {
        Fixture fixture = fixture();
        when(fixture.alongside.run(anyString())).thenReturn(success("ALONGSIDE"));

        TankerOpenSeaRangeResult result = fixture.service.run(
                params("2026-01-01 00:00:00", "2026-07-01 00:00:00", true),
                TankerOpenSeaJobParams.Behavior.ALONGSIDE);

        assertTrue(result.isSuccess());
        assertEquals(6, result.getSegmentsCompleted());
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(fixture.alongside, times(6)).run(captor.capture());
        List<String> calls = captor.getAllValues();
        assertSegment(calls.get(0), "2026-01-01 00:00:00",
                "2026-02-01 00:00:00",
                "alongside-lc-s200-202601010000-202602010000-v2",
                "ALONGSIDE_LIQUID_CONTAINER_V2", "windowDays", 7);
        assertSegment(calls.get(5), "2026-06-01 00:00:00",
                "2026-07-01 00:00:00",
                "alongside-lc-s200-202606010000-202607010000-v2",
                "ALONGSIDE_LIQUID_CONTAINER_V2", "windowDays", 7);
        verifyNoMoreInteractions(fixture.loitering);
    }

    @Test
    public void fiveThousandShipYearIsSplitIntoTwelveStableMonthlyBatches() {
        Fixture fixture = fixture();
        when(fixture.loitering.run(anyString())).thenReturn(success("LOITERING"));
        JSONObject annual = JSON.parseObject(params(
                "2025-07-01 00:00:00", "2026-07-01 00:00:00", true));
        annual.put("listBatchNo",
                "liquid-container-5000-2025h2-2026h1-v1");
        annual.put("shipLimit", 5000);
        annual.put("logIntervalShips", 50);
        annual.put("batchVersion", "v3");

        TankerOpenSeaRangeResult result = fixture.service.run(
                annual.toJSONString(),
                TankerOpenSeaJobParams.Behavior.LOITERING);

        assertTrue(result.isSuccess());
        assertEquals(12, result.getSegmentsCompleted());
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(fixture.loitering, times(12)).run(captor.capture());
        List<String> calls = captor.getAllValues();
        assertSegment(calls.get(0), "2025-07-01 00:00:00",
                "2025-08-01 00:00:00",
                "loitering-lc-s5000-202507010000-202508010000-v3",
                "LOITERING_LIQUID_CONTAINER_V2", "windowHours", 24);
        assertSegment(calls.get(11), "2026-06-01 00:00:00",
                "2026-07-01 00:00:00",
                "loitering-lc-s5000-202606010000-202607010000-v3",
                "LOITERING_LIQUID_CONTAINER_V2", "windowHours", 24);
    }

    @Test
    public void rangeRejectsMoreThanFiveThousandShips() {
        Fixture fixture = fixture();
        JSONObject json = JSON.parseObject(params(
                "2025-07-01 00:00:00", "2026-07-01 00:00:00", true));
        json.put("shipLimit", 5001);

        try {
            fixture.service.run(json.toJSONString(),
                    TankerOpenSeaJobParams.Behavior.ALONGSIDE);
            fail("expected ship limit rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains(
                    "shipLimit must be <= 5000"));
        }
    }

    @Test
    public void loiteringCustomRangeKeepsPartialFirstAndLastMonths() {
        Fixture fixture = fixture();
        when(fixture.loitering.run(anyString())).thenReturn(success("LOITERING"));

        TankerOpenSeaRangeResult result = fixture.service.run(
                params("2026-01-15 06:00:00", "2026-03-10 18:00:00", true),
                TankerOpenSeaJobParams.Behavior.LOITERING);

        assertTrue(result.isSuccess());
        assertEquals(3, result.getSegmentsCompleted());
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(fixture.loitering, times(3)).run(captor.capture());
        List<String> calls = captor.getAllValues();
        assertSegment(calls.get(0), "2026-01-15 06:00:00",
                "2026-02-01 00:00:00",
                "loitering-lc-s200-202601150600-202602010000-v2",
                "LOITERING_LIQUID_CONTAINER_V2", "windowHours", 24);
        assertSegment(calls.get(2), "2026-03-01 00:00:00",
                "2026-03-10 18:00:00",
                "loitering-lc-s200-202603010000-202603101800-v2",
                "LOITERING_LIQUID_CONTAINER_V2", "windowHours", 24);
        verifyNoMoreInteractions(fixture.alongside);
    }

    @Test
    public void failedSegmentStopsLaterMonths() {
        Fixture fixture = fixture();
        TankerOpenSeaBatchResult failed = success("ALONGSIDE");
        failed.shipFailed();
        when(fixture.alongside.run(anyString()))
                .thenReturn(success("ALONGSIDE"))
                .thenReturn(failed);

        TankerOpenSeaRangeResult result = fixture.service.run(
                params("2026-01-01 00:00:00", "2026-04-01 00:00:00", true),
                TankerOpenSeaJobParams.Behavior.ALONGSIDE);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getSegmentsCompleted());
        assertEquals(1, result.getSegmentsFailed());
        verify(fixture.alongside, times(2)).run(anyString());
    }

    @Test
    public void dryRunGetsDedicatedBatchNames() {
        Fixture fixture = fixture();
        when(fixture.alongside.run(anyString())).thenReturn(success("ALONGSIDE"));

        fixture.service.run(
                params("2026-01-01 00:00:00", "2026-02-01 00:00:00", false),
                TankerOpenSeaJobParams.Behavior.ALONGSIDE);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(fixture.alongside).run(captor.capture());
        JSONObject child = JSON.parseObject(captor.getValue());
        assertEquals(false, child.getBooleanValue("enableInsert"));
        assertEquals("alongside-lc-dryrun-s200-202601010000-202602010000-v2",
                child.getString("batchNo"));
    }

    @Test
    public void multiYearRangeIsContinuousAndEverySegmentIsAtMostThirtyOneDays() {
        Fixture fixture = fixture();
        when(fixture.alongside.run(anyString())).thenReturn(success("ALONGSIDE"));

        TankerOpenSeaRangeResult result = fixture.service.run(
                params("2024-03-15 06:00:00", "2027-08-10 18:00:00", true),
                TankerOpenSeaJobParams.Behavior.ALONGSIDE);

        assertTrue(result.isSuccess());
        assertEquals(42, result.getSegmentsCompleted());
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(fixture.alongside, times(42)).run(captor.capture());
        List<String> calls = captor.getAllValues();
        assertSegment(calls.get(0), "2024-03-15 06:00:00",
                "2024-04-01 00:00:00",
                "alongside-lc-s200-202403150600-202404010000-v2",
                "ALONGSIDE_LIQUID_CONTAINER_V2", "windowDays", 7);
        assertSegment(calls.get(41), "2027-08-01 00:00:00",
                "2027-08-10 18:00:00",
                "alongside-lc-s200-202708010000-202708101800-v2",
                "ALONGSIDE_LIQUID_CONTAINER_V2", "windowDays", 7);
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime previousEnd = null;
        for (String call : calls) {
            JSONObject child = JSON.parseObject(call);
            LocalDateTime start = LocalDateTime.parse(
                    child.getString("startTime"), formatter);
            LocalDateTime end = LocalDateTime.parse(
                    child.getString("endTime"), formatter);
            assertTrue(start.isBefore(end));
            assertTrue(!end.isAfter(start.plusDays(31)));
            if (previousEnd != null) assertEquals(previousEnd, start);
            previousEnd = end;
        }
        assertEquals(LocalDateTime.parse("2027-08-10 18:00:00", formatter),
                previousEnd);
    }

    @Test
    public void leapYearFebruaryHasNoGapOrOverlap() {
        Fixture fixture = fixture();
        when(fixture.loitering.run(anyString())).thenReturn(success("LOITERING"));

        fixture.service.run(
                params("2024-02-10 00:00:00", "2024-03-10 00:00:00", true),
                TankerOpenSeaJobParams.Behavior.LOITERING);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(fixture.loitering, times(2)).run(captor.capture());
        List<String> calls = captor.getAllValues();
        JSONObject first = JSON.parseObject(calls.get(0));
        JSONObject second = JSON.parseObject(calls.get(1));
        assertEquals("2024-03-01 00:00:00", first.getString("endTime"));
        assertEquals(first.getString("endTime"), second.getString("startTime"));
        assertEquals("2024-03-10 00:00:00", second.getString("endTime"));
    }

    @Test
    public void equalOrReversedRangeIsRejected() {
        Fixture fixture = fixture();
        assertInvalidRange(fixture,
                "2026-01-01 00:00:00", "2026-01-01 00:00:00");
        assertInvalidRange(fixture,
                "2026-01-02 00:00:00", "2026-01-01 00:00:00");
    }

    @Test
    public void identicalRangeProducesStableBatchNames() {
        Fixture fixture = fixture();
        when(fixture.alongside.run(anyString())).thenReturn(success("ALONGSIDE"));
        String raw = params(
                "2026-01-15 06:00:00", "2026-02-10 18:00:00", true);

        fixture.service.run(raw, TankerOpenSeaJobParams.Behavior.ALONGSIDE);
        fixture.service.run(raw, TankerOpenSeaJobParams.Behavior.ALONGSIDE);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(fixture.alongside, times(4)).run(captor.capture());
        List<String> calls = captor.getAllValues();
        assertEquals(JSON.parseObject(calls.get(0)).getString("batchNo"),
                JSON.parseObject(calls.get(2)).getString("batchNo"));
        assertEquals(JSON.parseObject(calls.get(1)).getString("batchNo"),
                JSON.parseObject(calls.get(3)).getString("batchNo"));
    }

    private void assertSegment(String raw, String start, String end,
                               String batchNo, String ruleVersion,
                               String windowField, int windowValue) {
        JSONObject json = JSON.parseObject(raw);
        assertEquals("liquid_container_exclusion_test", json.getString("mode"));
        assertEquals(start, json.getString("startTime"));
        assertEquals(end, json.getString("endTime"));
        assertEquals(batchNo, json.getString("batchNo"));
        assertTrue(json.getString("batchNo").length() <= 64);
        assertEquals(ruleVersion, json.getString("ruleVersion"));
        assertEquals(windowValue, json.getIntValue(windowField));
        assertEquals(1, json.getIntValue("parallelism"));
        assertEquals("7470be70e955bc79064bc402586c592a8a8da7a241e00c61997b1fa1bc0df50f",
                json.getString("expectedExclusionFingerprint"));
    }

    private String params(String start, String end, boolean insert) {
        JSONObject json = new JSONObject();
        json.put("mode", "liquid_container_exclusion_range");
        json.put("listBatchNo", "liquid-container-2026h1-v1");
        json.put("shipLimit", 200);
        json.put("startTime", start);
        json.put("endTime", end);
        json.put("enableInsert", insert);
        json.put("storeDetails", false);
        json.put("resume", true);
        json.put("retryCount", 1);
        json.put("logIntervalShips", 10);
        json.put("batchVersion", "v2");
        if (insert) {
            json.put("expectedExclusionFingerprint",
                    "7470be70e955bc79064bc402586c592a8a8da7a241e00c61997b1fa1bc0df50f");
        }
        return json.toJSONString();
    }

    private TankerOpenSeaBatchResult success(String behavior) {
        return new TankerOpenSeaBatchResult(behavior, "test");
    }

    private void assertInvalidRange(Fixture fixture, String start, String end) {
        try {
            fixture.service.run(params(start, end, true),
                    TankerOpenSeaJobParams.Behavior.ALONGSIDE);
            fail("expected invalid range");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains(
                    "time range must be positive"));
        }
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.alongside = mock(TankerOpenSeaAlongsideService.class);
        fixture.loitering = mock(TankerOpenSeaLoiteringService.class);
        fixture.service = new TankerOpenSeaRangeService();
        ReflectionTestUtils.setField(
                fixture.service, "alongsideService", fixture.alongside);
        ReflectionTestUtils.setField(
                fixture.service, "loiteringService", fixture.loitering);
        return fixture;
    }

    private static class Fixture {
        private TankerOpenSeaRangeService service;
        private TankerOpenSeaAlongsideService alongside;
        private TankerOpenSeaLoiteringService loitering;
    }
}
