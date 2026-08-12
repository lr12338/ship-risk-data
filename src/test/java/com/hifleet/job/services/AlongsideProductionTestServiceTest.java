package com.hifleet.job.services;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.domain.TargetShip;
import com.hifleet.repository.ShipBehaviorAlongsideBatchStatusRepository;
import com.hifleet.repository.TargetShipRepository;
import com.hifleet.service.BehaviorEventTransactionStore;
import com.hifleet.service.ShipAlongsideDetector;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AlongsideProductionTestServiceTest {

    @Test
    public void dryRunResumeUsesConfiguredBatchAndSkipsCompletedWindow() {
        Fixture fixture = fixture();
        when(fixture.status.findCompletedWindows(eq("dry-batch"), any(Set.class)))
                .thenReturn(completedWindows());

        AlongsideProductionTestService.Result result = fixture.service.run(
                params(false, "dry-batch"));

        assertTrue(result.isSuccess());
        verify(fixture.status).findCompletedWindows(eq("dry-batch"), any(Set.class));
        verify(fixture.analysis, never()).analysisAlongsideProductionWindow(
                anyString(), anyString(), anyString(), anyString());
        verify(fixture.store, never()).replace(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyList(), anyList(), anyBoolean());
    }

    @Test
    public void writesOnlyValidNonWeakEventsWithoutDetails() {
        Fixture fixture = fixture();
        when(fixture.status.findCompletedWindows(eq("write-batch"), any(Set.class)))
                .thenReturn(Collections.<String>emptySet());
        List<ShipBehaviorTrace> traces = Arrays.asList(
                trace(ShipAlongsideDetector.SUB_TYPE_WEAK,
                        ShipAlongsideDetector.VALID_RISK_EVENT),
                trace("ALONGSIDE_CONFIRMED",
                        ShipAlongsideDetector.EXCLUDED_NORMAL),
                trace("ALONGSIDE_CONFIRMED",
                        ShipAlongsideDetector.VALID_RISK_EVENT));
        when(fixture.analysis.analysisAlongsideProductionWindow(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ShipBehaviorAnalysisService.AlongsideWindowResult(
                        10, 1, 2, 2, 1, 0, 0,
                        3, 2, 0, 0, traces));

        AlongsideProductionTestService.Result result = fixture.service.run(
                params(true, "write-batch"));

        assertTrue(result.isSuccess());
        ArgumentCaptor<List> tracesCaptor = ArgumentCaptor.forClass(List.class);
        verify(fixture.store, org.mockito.Mockito.times(7)).replace(
                eq(ShipBehaviorEvent.TYPE_ALONGSIDE), eq("write-batch"),
                eq("413000001"), anyString(),
                anyString(), anyList(),
                tracesCaptor.capture(), eq(false), any(Runnable.class));
        for (List captured : tracesCaptor.getAllValues()) {
            assertEquals(1, captured.size());
        }
    }

    @Test
    public void terminalTruncationFailsWindowAndDoesNotWriteEvents() {
        Fixture fixture = fixture();
        when(fixture.status.findCompletedWindows(eq("truncated-batch"), any(Set.class)))
                .thenReturn(Collections.<String>emptySet());
        when(fixture.analysis.analysisAlongsideProductionWindow(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ShipBehaviorAnalysisService.AlongsideWindowResult(
                        10, 1, 0, 0, 1, 0, 1,
                        0, 0, 0, 0, Collections.<ShipBehaviorTrace>emptyList()));

        AlongsideProductionTestService.Result result = fixture.service.run(
                params(true, "truncated-batch"));

        assertFalse(result.isSuccess());
        assertTrue(result.summary().contains("windowsFailed=7"));
        verify(fixture.store, never()).replace(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyList(), anyList(), anyBoolean(), any(Runnable.class));
    }

    @Test
    public void custom577DayRangeCreatesTwentyWindows() {
        Fixture fixture = fixture();
        when(fixture.status.findCompletedWindows(eq("custom-range-batch"), any(Set.class)))
                .thenReturn(Collections.<String>emptySet());
        when(fixture.analysis.analysisAlongsideProductionWindow(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ShipBehaviorAnalysisService.AlongsideWindowResult(
                        0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, Collections.<ShipBehaviorTrace>emptyList()));

        AlongsideProductionTestService.Result result = fixture.service.run(
                customRangeParams());

        assertTrue(result.isSuccess());
        assertTrue(result.summary().contains("windowsDone=20"));
        verify(fixture.analysis, times(20)).analysisAlongsideProductionWindow(
                eq("413000001"), anyString(), anyString(), eq("custom-range-batch"));
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.service = new AlongsideProductionTestService();
        fixture.targets = mock(TargetShipRepository.class);
        fixture.status = mock(ShipBehaviorAlongsideBatchStatusRepository.class);
        fixture.analysis = mock(ShipBehaviorAnalysisService.class);
        fixture.store = mock(BehaviorEventTransactionStore.class);
        ReflectionTestUtils.setField(fixture.service, "targetShipRepository", fixture.targets);
        ReflectionTestUtils.setField(fixture.service, "statusRepository", fixture.status);
        ReflectionTestUtils.setField(fixture.service, "analysisService", fixture.analysis);
        ReflectionTestUtils.setField(fixture.service, "eventStore", fixture.store);
        ReflectionTestUtils.setField(fixture.service, "ruleVersion", "ALONGSIDE_V3_INSURANCE");
        TargetShip ship = new TargetShip(
                "413000001", "IMO1", "SHIP", "油轮", null);
        ship.setSeqNo(1);
        when(fixture.targets.selectPage("list", 0, 1))
                .thenReturn(Collections.singletonList(ship));
        return fixture;
    }

    private ShipBehaviorTrace trace(String subType, String exclusionFlag) {
        ShipBehaviorEvent event = new ShipBehaviorEvent();
        event.setBehaviorType(ShipBehaviorEvent.TYPE_ALONGSIDE);
        event.setMmsi("413000001");
        event.setTargetMmsi("413000002");
        event.setEventSubType(subType);
        event.setExclusionFlag(exclusionFlag);
        event.setRuleVersion("ALONGSIDE_V3_INSURANCE");
        return new ShipBehaviorTrace(event, Collections.emptyList(), Collections.emptyList());
    }

    private String params(boolean enableInsert, String batchNo) {
        return "{"
                + "\"mode\":\"production_test\","
                + "\"listBatchNo\":\"list\","
                + "\"expectedShipCount\":1,\"shipLimit\":1,"
                + "\"pageSize\":1,\"parallelism\":1,"
                + "\"startTime\":\"2026-01-01 00:00:00\","
                + "\"endTime\":\"2026-07-01 00:00:00\","
                + "\"windowDays\":30,\"resume\":true,\"retryCount\":0,"
                + "\"enableInsert\":" + enableInsert + ","
                + "\"batchNo\":\"" + batchNo + "\"}";
    }

    private String customRangeParams() {
        return "{"
                + "\"mode\":\"production_test\","
                + "\"listBatchNo\":\"list\","
                + "\"expectedShipCount\":1,\"shipLimit\":1,"
                + "\"pageSize\":1,\"parallelism\":1,"
                + "\"startTime\":\"2025-01-01 00:00:00\","
                + "\"endTime\":\"2026-08-01 00:00:00\","
                + "\"windowDays\":30,\"resume\":true,\"retryCount\":0,"
                + "\"enableInsert\":false,"
                + "\"batchNo\":\"custom-range-batch\"}";
    }

    private Set<String> completedWindows() {
        Set<String> values = new HashSet<String>();
        String[] starts = {
                "2026-01-01 00:00:00", "2026-01-31 00:00:00",
                "2026-03-02 00:00:00", "2026-04-01 00:00:00",
                "2026-05-01 00:00:00", "2026-05-31 00:00:00",
                "2026-06-30 00:00:00"
        };
        String[] ends = {
                "2026-01-31 00:00:00", "2026-03-02 00:00:00",
                "2026-04-01 00:00:00", "2026-05-01 00:00:00",
                "2026-05-31 00:00:00", "2026-06-30 00:00:00",
                "2026-07-01 00:00:00"
        };
        for (int i = 0; i < starts.length; i++) {
            values.add(ShipBehaviorAlongsideBatchStatusRepository.windowKey(
                    "413000001", starts[i], ends[i]));
        }
        return values;
    }

    private static class Fixture {
        private AlongsideProductionTestService service;
        private TargetShipRepository targets;
        private ShipBehaviorAlongsideBatchStatusRepository status;
        private ShipBehaviorAnalysisService analysis;
        private BehaviorEventTransactionStore store;
    }
}
