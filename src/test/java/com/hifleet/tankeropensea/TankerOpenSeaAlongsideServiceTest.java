package com.hifleet.tankeropensea;

import com.hifleet.repository.ShipBehaviorAlongsideBatchStatusRepository;
import com.hifleet.service.BehaviorEventTransactionStore;
import com.hifleet.trajectory.TrajectoryLoaderService;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anySet;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

public class TankerOpenSeaAlongsideServiceTest {

    private static final String LIST_BATCH = "liquid-container-timeout-v1";
    private static final String BATCH =
            "alongside-lc-timeout-202601010000-202601020000-v1";
    private static final String FINGERPRINT =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    @SuppressWarnings("unchecked")
    public void partialReplayTimeoutCompletesWindowWithoutFormalEvents() {
        TankerOpenSeaTargetShipRepository targets =
                mock(TankerOpenSeaTargetShipRepository.class);
        PortAnchorBerthExclusionService exclusions =
                mock(PortAnchorBerthExclusionService.class);
        PortAnchorBerthExclusionService.Snapshot snapshot =
                mock(PortAnchorBerthExclusionService.Snapshot.class);
        TankerOpenSeaReplayProvider replayProvider =
                mock(TankerOpenSeaReplayProvider.class);
        TrajectoryLoaderService trajectoryLoader =
                mock(TrajectoryLoaderService.class);
        ShipBehaviorAlongsideBatchStatusRepository status =
                mock(ShipBehaviorAlongsideBatchStatusRepository.class);
        BehaviorEventTransactionStore eventStore =
                mock(BehaviorEventTransactionStore.class);
        DegradedCandidateRepository degraded =
                mock(DegradedCandidateRepository.class);

        TankerOpenSeaAlongsideService service =
                new TankerOpenSeaAlongsideService();
        setField(service, "targetRepository", targets);
        setField(service, "exclusionService", exclusions);
        setField(service, "replayProvider", replayProvider);
        setField(service, "trajectoryLoaderService", trajectoryLoader);
        setField(service, "statusRepository", status);
        setField(service, "eventStore", eventStore);
        setField(service, "degradedRepository", degraded);

        ShipMetadata metadata = new ShipMetadata(
                "123456789", null, "TIMEOUT-TEST", "集装箱船", "集装箱船",
                null, 200.0, 50000L,
                LocalDateTime.of(2026, 1, 1, 0, 0));
        ListedTanker ship = new ListedTanker(1, metadata,
                ShipEligibilityDecision.eligible(
                        StandardShipType.CONTAINER, "EXPLICIT_CONTAINER", "HIGH"));
        when(targets.selectPage(LIST_BATCH, 0, 1))
                .thenReturn(Collections.singletonList(ship));
        when(status.findCompletedWindows(eq(BATCH), anySet()))
                .thenReturn(Collections.<String>emptySet());

        when(exclusions.requireReady(FINGERPRINT)).thenReturn(snapshot);
        when(snapshot.getFingerprint()).thenReturn(FINGERPRINT);
        when(snapshot.getHealth()).thenReturn(ExclusionHealth.AVAILABLE);
        when(snapshot.getSourceCounts()).thenReturn(Collections.<String, Integer>emptyMap());
        when(snapshot.getHealthReportJson()).thenReturn("{}");
        when(snapshot.getRepairedGeometries()).thenReturn(0);
        when(snapshot.getUnparseableGeometries()).thenReturn(0);
        when(exclusions.classify(anyList(), eq(snapshot))).thenReturn(
                ExclusionDecision.accepted(FINGERPRINT,
                        Collections.<String, Integer>emptyMap()));
        when(trajectoryLoader.loadSafely(anyString(), anyString(), anyString(),
                anyInt(), anyInt(), anyInt())).thenReturn(points());

        TankerOpenSeaReplayProvider.SearchResult partial =
                mock(TankerOpenSeaReplayProvider.SearchResult.class);
        when(partial.getCompleteness()).thenReturn(ReplayCompleteness.PARTIAL_TIMEOUT);
        when(partial.getQueryCount()).thenReturn(2);
        when(partial.getFailedQueries()).thenReturn(1);
        when(partial.getTimeoutSkippedQueries()).thenReturn(1);
        when(replayProvider.search(anyString(), anyList(), eq(BATCH)))
                .thenReturn(partial);

        TankerOpenSeaBatchResult result = service.run(params());

        assertTrue(result.isSuccess());
        assertTrue(result.summary().contains("windowsFailed=0"));
        assertTrue(result.summary().contains("replayTimeoutSkipped=1"));
        assertTrue(result.summary().contains("eventsDetected=0"));
        assertTrue(result.summary().contains("eventsStored=0"));
        verify(status).markDone(eq(BATCH), eq("123456789"),
                eq("2026-01-01 00:00:00"), eq("2026-01-02 00:00:00"),
                anyInt(), anyInt(), anyInt(), anyInt(), eq(1), eq(0));
        verify(status, never()).markFailed(anyString(), anyString(),
                anyString(), anyString(), anyString());
        verify(eventStore).replace(eq("ALONGSIDE"), eq(BATCH),
                eq("123456789"), eq("2026-01-01 00:00:00"),
                eq("2026-01-02 00:00:00"), anyList(), anyList(), eq(false));
        verify(targets, never()).findSourceByMmsis(anySet());
    }

    @Test
    public void resumeSkipsWindowMarkedDoneAfterTimeout() {
        TankerOpenSeaTargetShipRepository targets =
                mock(TankerOpenSeaTargetShipRepository.class);
        PortAnchorBerthExclusionService exclusions =
                mock(PortAnchorBerthExclusionService.class);
        PortAnchorBerthExclusionService.Snapshot snapshot =
                mock(PortAnchorBerthExclusionService.Snapshot.class);
        TankerOpenSeaReplayProvider replayProvider =
                mock(TankerOpenSeaReplayProvider.class);
        TrajectoryLoaderService trajectoryLoader =
                mock(TrajectoryLoaderService.class);
        ShipBehaviorAlongsideBatchStatusRepository status =
                mock(ShipBehaviorAlongsideBatchStatusRepository.class);
        BehaviorEventTransactionStore eventStore =
                mock(BehaviorEventTransactionStore.class);
        DegradedCandidateRepository degraded =
                mock(DegradedCandidateRepository.class);
        TankerOpenSeaAlongsideService service =
                new TankerOpenSeaAlongsideService();
        setField(service, "targetRepository", targets);
        setField(service, "exclusionService", exclusions);
        setField(service, "replayProvider", replayProvider);
        setField(service, "trajectoryLoaderService", trajectoryLoader);
        setField(service, "statusRepository", status);
        setField(service, "eventStore", eventStore);
        setField(service, "degradedRepository", degraded);

        ShipMetadata metadata = new ShipMetadata(
                "123456789", null, "TIMEOUT-TEST", "集装箱船", "集装箱船",
                null, 200.0, 50000L,
                LocalDateTime.of(2026, 1, 1, 0, 0));
        ListedTanker ship = new ListedTanker(1, metadata,
                ShipEligibilityDecision.eligible(
                        StandardShipType.CONTAINER, "EXPLICIT_CONTAINER", "HIGH"));
        when(targets.selectPage(LIST_BATCH, 0, 1))
                .thenReturn(Collections.singletonList(ship));
        String windowKey = ShipBehaviorAlongsideBatchStatusRepository.windowKey(
                "123456789", "2026-01-01 00:00:00", "2026-01-02 00:00:00");
        when(status.findCompletedWindows(eq(BATCH), anySet()))
                .thenReturn(Collections.<String>emptySet())
                .thenReturn(Collections.singleton(windowKey));

        when(exclusions.requireReady(FINGERPRINT)).thenReturn(snapshot);
        when(snapshot.getFingerprint()).thenReturn(FINGERPRINT);
        when(snapshot.getHealth()).thenReturn(ExclusionHealth.AVAILABLE);
        when(snapshot.getSourceCounts()).thenReturn(Collections.<String, Integer>emptyMap());
        when(snapshot.getHealthReportJson()).thenReturn("{}");
        when(snapshot.getRepairedGeometries()).thenReturn(0);
        when(snapshot.getUnparseableGeometries()).thenReturn(0);
        when(exclusions.classify(anyList(), eq(snapshot))).thenReturn(
                ExclusionDecision.accepted(FINGERPRINT,
                        Collections.<String, Integer>emptyMap()));
        when(trajectoryLoader.loadSafely(anyString(), anyString(), anyString(),
                anyInt(), anyInt(), anyInt())).thenReturn(points());
        TankerOpenSeaReplayProvider.SearchResult partial =
                mock(TankerOpenSeaReplayProvider.SearchResult.class);
        when(partial.getCompleteness()).thenReturn(ReplayCompleteness.PARTIAL_TIMEOUT);
        when(partial.getQueryCount()).thenReturn(2);
        when(partial.getFailedQueries()).thenReturn(1);
        when(partial.getTimeoutSkippedQueries()).thenReturn(1);
        when(replayProvider.search(anyString(), anyList(), eq(BATCH)))
                .thenReturn(partial);

        service.run(params());
        TankerOpenSeaBatchResult resumed = service.run(params());

        assertTrue(resumed.summary().contains("windowsSkipped=1"));
        verify(replayProvider, times(1)).search(anyString(), anyList(), eq(BATCH));
        verify(eventStore, times(1)).replace(eq("ALONGSIDE"), eq(BATCH),
                eq("123456789"), eq("2026-01-01 00:00:00"),
                eq("2026-01-02 00:00:00"), anyList(), anyList(), eq(false));
    }

    private List<TrajectoryPoint> points() {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        for (int i = 0; i <= 9; i++) {
            result.add(new TrajectoryPoint("123456789", start.plusMinutes(i * 5L),
                    120.0 + i * 0.001, 20.0, 2.0, 90.0, "under way"));
        }
        return result;
    }

    private String params() {
        return "{\"mode\":\"liquid_container_exclusion_test\","
                + "\"listBatchNo\":\"" + LIST_BATCH + "\","
                + "\"shipLimit\":1,"
                + "\"startTime\":\"2026-01-01 00:00:00\","
                + "\"endTime\":\"2026-01-02 00:00:00\","
                + "\"windowDays\":7,\"enableInsert\":true,"
                + "\"storeDetails\":false,\"resume\":true,"
                + "\"parallelism\":1,\"retryCount\":1,"
                + "\"logIntervalShips\":1,"
                + "\"ruleVersion\":\"ALONGSIDE_LIQUID_CONTAINER_V2\","
                + "\"expectedExclusionFingerprint\":\""
                + FINGERPRINT + "\",\"batchNo\":\"" + BATCH + "\"}";
    }
}
