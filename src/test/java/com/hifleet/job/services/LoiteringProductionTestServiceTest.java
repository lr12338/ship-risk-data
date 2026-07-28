package com.hifleet.job.services;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.domain.TargetShip;
import com.hifleet.repository.ShipBehaviorLoiteringBatchStatusRepository;
import com.hifleet.repository.TargetShipRepository;
import com.hifleet.service.BehaviorEventTransactionStore;
import com.hifleet.trajectory.TrajectoryLoaderService;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LoiteringProductionTestServiceTest {

    @Test
    public void loadsHalfYearOnceAndRunsBothEnginesBeforeEventOnlyReplace() {
        Fixture fixture = fixture();
        List<TrajectoryPoint> points = Arrays.asList(
                point(0), point(10));
        when(fixture.loader.loadSafely(
                anyString(), anyString(), anyString(),
                anyInt(), anyInt(), anyInt())).thenReturn(points);
        when(fixture.analysis.analysisLoiteringV2Production(
                anyString(), anyList(), anyString()))
                .thenReturn(Collections.singletonList(trace(
                        "LOITERING_V2_INSURANCE")));
        when(fixture.v3.analyzeV3Production(
                org.mockito.Matchers.any(ShipLoiteringV3BatchService.TargetShipAdapter.class),
                anyList(), anyString(), anyInt()))
                .thenReturn(Collections.singletonList(trace(
                        "LOITERING_V3_PROD_TEST_2026H1")));

        LoiteringProductionTestService.Result result = fixture.service.run(params(false));

        assertTrue(result.isSuccess());
        verify(fixture.loader, times(1)).loadSafely(
                eq("413000001"), eq("2026-01-01 00:00:00"),
                eq("2026-07-01 00:00:00"), eq(15), eq(6), eq(200000));
        ArgumentCaptor<List> traces = ArgumentCaptor.forClass(List.class);
        verify(fixture.store).replace(
                eq(ShipBehaviorEvent.TYPE_LOITERING), eq("loiter-batch"),
                eq("413000001"), eq("2026-01-01 00:00:00"),
                eq("2026-07-01 00:00:00"), anyList(),
                traces.capture(), eq(false));
        assertEquals(2, traces.getValue().size());
    }

    @Test
    public void resumeSkipsCompletedWholeShipWindow() {
        Fixture fixture = fixture();
        String key = ShipBehaviorLoiteringBatchStatusRepository.windowKey(
                "413000001", "2026-01-01 00:00:00",
                "2026-07-01 00:00:00");
        when(fixture.status.findCompletedWindows("loiter-batch"))
                .thenReturn(Collections.singleton(key));

        LoiteringProductionTestService.Result result = fixture.service.run(params(false));

        assertTrue(result.isSuccess());
        verify(fixture.loader, never()).loadSafely(
                anyString(), anyString(), anyString(),
                anyInt(), anyInt(), anyInt());
        verify(fixture.store, never()).replace(
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyList(), anyList(),
                org.mockito.Matchers.anyBoolean());
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.service = new LoiteringProductionTestService();
        fixture.targets = mock(TargetShipRepository.class);
        fixture.status = mock(ShipBehaviorLoiteringBatchStatusRepository.class);
        fixture.loader = mock(TrajectoryLoaderService.class);
        fixture.analysis = mock(ShipBehaviorAnalysisService.class);
        fixture.v3 = mock(ShipLoiteringV3BatchService.class);
        fixture.store = mock(BehaviorEventTransactionStore.class);
        ReflectionTestUtils.setField(fixture.service, "targetShipRepository", fixture.targets);
        ReflectionTestUtils.setField(fixture.service, "statusRepository", fixture.status);
        ReflectionTestUtils.setField(fixture.service, "trajectoryLoaderService", fixture.loader);
        ReflectionTestUtils.setField(fixture.service, "analysisService", fixture.analysis);
        ReflectionTestUtils.setField(fixture.service, "v3Service", fixture.v3);
        ReflectionTestUtils.setField(fixture.service, "eventStore", fixture.store);
        ReflectionTestUtils.setField(fixture.service, "simplifyZoom", 15);
        ReflectionTestUtils.setField(fixture.service, "maxPointsPerShip", 200000);
        TargetShip ship = new TargetShip(
                "413000001", "IMO1", "SHIP", "油轮", null);
        ship.setSeqNo(1);
        when(fixture.targets.selectPage("list", 0, 1))
                .thenReturn(Collections.singletonList(ship));
        when(fixture.status.findCompletedWindows("loiter-batch"))
                .thenReturn(Collections.<String>emptySet());
        return fixture;
    }

    private String params(boolean storeDetails) {
        return "{"
                + "\"mode\":\"production_test\","
                + "\"listBatchNo\":\"list\","
                + "\"expectedShipCount\":1,\"shipLimit\":1,"
                + "\"pageSize\":1,\"parallelism\":1,"
                + "\"startTime\":\"2026-01-01 00:00:00\","
                + "\"endTime\":\"2026-07-01 00:00:00\","
                + "\"engines\":[\"V2\",\"V3\"],"
                + "\"trajectorySliceHours\":6,\"resume\":true,\"retryCount\":0,"
                + "\"storeDetails\":" + storeDetails + ","
                + "\"batchNo\":\"loiter-batch\"}";
    }

    private ShipBehaviorTrace trace(String ruleVersion) {
        ShipBehaviorEvent event = new ShipBehaviorEvent();
        event.setBehaviorType(ShipBehaviorEvent.TYPE_LOITERING);
        event.setMmsi("413000001");
        event.setRuleVersion(ruleVersion);
        return new ShipBehaviorTrace(
                event, Collections.emptyList(), Collections.emptyList());
    }

    private TrajectoryPoint point(int minute) {
        return new TrajectoryPoint("413000001",
                LocalDateTime.of(2026, 1, 1, 0, minute),
                120.0, 30.0, 0.2, 90.0, "0");
    }

    private static class Fixture {
        private LoiteringProductionTestService service;
        private TargetShipRepository targets;
        private ShipBehaviorLoiteringBatchStatusRepository status;
        private TrajectoryLoaderService loader;
        private ShipBehaviorAnalysisService analysis;
        private ShipLoiteringV3BatchService v3;
        private BehaviorEventTransactionStore store;
    }
}
