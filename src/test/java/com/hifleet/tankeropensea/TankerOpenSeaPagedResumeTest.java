package com.hifleet.tankeropensea;

import com.hifleet.repository.ShipBehaviorAlongsideBatchStatusRepository;
import com.hifleet.repository.ShipBehaviorLoiteringBatchStatusRepository;
import com.hifleet.service.BehaviorEventTransactionStore;
import com.hifleet.trajectory.TrajectoryLoaderService;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anySet;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TankerOpenSeaPagedResumeTest {
    private static final String LIST = "liquid-container-resume-test-v1";

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void alongsideLoadsCompletedWindowsForOnlyCurrentFiftyShipPage() {
        TankerOpenSeaTargetShipRepository targets =
                mock(TankerOpenSeaTargetShipRepository.class);
        ShipBehaviorAlongsideBatchStatusRepository status =
                mock(ShipBehaviorAlongsideBatchStatusRepository.class);
        preparePages(targets);
        when(status.findCompletedWindows(eq("alongside-liquid-container-dryrun-paged-v3"),
                anySet())).thenAnswer(completedAnswer(
                "2026-01-01 00:00:00", "2026-01-02 00:00:00", true));

        TankerOpenSeaAlongsideService service = new TankerOpenSeaAlongsideService();
        common(service, targets);
        ReflectionTestUtils.setField(service, "statusRepository", status);

        TankerOpenSeaBatchResult result = service.run(params(
                "alongside-liquid-container-dryrun-paged-v3"));

        assertTrue(result.summary().contains("windowsSkipped=51"));
        verify(status, never()).findCompletedWindows(
                "alongside-liquid-container-dryrun-paged-v3");
        ArgumentCaptor<Set> pages = ArgumentCaptor.forClass(Set.class);
        verify(status, times(2)).findCompletedWindows(
                eq("alongside-liquid-container-dryrun-paged-v3"),
                pages.capture());
        assertEquals(50, pages.getAllValues().get(0).size());
        assertEquals(1, pages.getAllValues().get(1).size());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void loiteringLoadsCompletedWindowsForOnlyCurrentFiftyShipPage() {
        TankerOpenSeaTargetShipRepository targets =
                mock(TankerOpenSeaTargetShipRepository.class);
        ShipBehaviorLoiteringBatchStatusRepository status =
                mock(ShipBehaviorLoiteringBatchStatusRepository.class);
        preparePages(targets);
        when(status.findCompletedWindows(eq("loitering-liquid-container-dryrun-paged-v3"),
                anySet())).thenAnswer(completedAnswer(
                "2026-01-01 00:00:00", "2026-01-02 00:00:00", false));

        TankerOpenSeaLoiteringService service = new TankerOpenSeaLoiteringService();
        common(service, targets);
        ReflectionTestUtils.setField(service, "statusRepository", status);

        TankerOpenSeaBatchResult result = service.run(params(
                "loitering-liquid-container-dryrun-paged-v3"));

        assertTrue(result.summary().contains("windowsSkipped=51"));
        verify(status, never()).findCompletedWindows(
                "loitering-liquid-container-dryrun-paged-v3");
        ArgumentCaptor<Set> pages = ArgumentCaptor.forClass(Set.class);
        verify(status, times(2)).findCompletedWindows(
                eq("loitering-liquid-container-dryrun-paged-v3"),
                pages.capture());
        assertEquals(50, pages.getAllValues().get(0).size());
        assertEquals(1, pages.getAllValues().get(1).size());
    }

    private void common(Object service, TankerOpenSeaTargetShipRepository targets) {
        PortAnchorBerthExclusionService exclusions =
                mock(PortAnchorBerthExclusionService.class);
        PortAnchorBerthExclusionService.Snapshot snapshot =
                mock(PortAnchorBerthExclusionService.Snapshot.class);
        when(exclusions.requireReady(any(String.class))).thenReturn(snapshot);
        ReflectionTestUtils.setField(service, "targetRepository", targets);
        ReflectionTestUtils.setField(service, "exclusionService", exclusions);
        ReflectionTestUtils.setField(service, "trajectoryLoaderService",
                mock(TrajectoryLoaderService.class));
        ReflectionTestUtils.setField(service, "eventStore",
                mock(BehaviorEventTransactionStore.class));
        ReflectionTestUtils.setField(service, "degradedRepository",
                mock(DegradedCandidateRepository.class));
    }

    private void preparePages(TankerOpenSeaTargetShipRepository targets) {
        List<ListedTanker> rows = new ArrayList<ListedTanker>();
        for (int i = 1; i <= 51; i++) {
            String mmsi = String.valueOf(100000000 + i);
            ShipMetadata metadata = new ShipMetadata(
                    mmsi, null, "SHIP-" + i, "集装箱船", "集装箱船",
                    null, 200.0, 50000L,
                    LocalDateTime.of(2026, 1, 1, 0, 0));
            rows.add(new ListedTanker(i, metadata,
                    ShipEligibilityDecision.eligible(
                            StandardShipType.CONTAINER,
                            "EXPLICIT_CONTAINER", "HIGH")));
        }
        when(targets.selectPage(LIST, 0, 50))
                .thenReturn(new ArrayList<ListedTanker>(rows.subList(0, 50)));
        when(targets.selectPage(LIST, 50, 1))
                .thenReturn(new ArrayList<ListedTanker>(rows.subList(50, 51)));
    }

    private Answer<Set<String>> completedAnswer(
            final String start, final String end, final boolean alongside) {
        return invocation -> {
            Set<String> result = new HashSet<String>();
            Set<String> mmsis = (Set<String>) invocation.getArguments()[1];
            for (String mmsi : mmsis) {
                result.add(alongside
                        ? ShipBehaviorAlongsideBatchStatusRepository.windowKey(
                        mmsi, start, end)
                        : ShipBehaviorLoiteringBatchStatusRepository.windowKey(
                        mmsi, start, end));
            }
            return result;
        };
    }

    private String params(String batchNo) {
        String behavior = batchNo.startsWith("alongside")
                ? "ALONGSIDE_LIQUID_CONTAINER_V2"
                : "LOITERING_LIQUID_CONTAINER_V2";
        return "{\"mode\":\"liquid_container_exclusion_test\","
                + "\"listBatchNo\":\"" + LIST + "\","
                + "\"shipLimit\":51,"
                + "\"startTime\":\"2026-01-01 00:00:00\","
                + "\"endTime\":\"2026-01-02 00:00:00\","
                + "\"windowDays\":7,\"windowHours\":24,"
                + "\"enableInsert\":false,\"storeDetails\":false,"
                + "\"resume\":true,\"parallelism\":1,\"retryCount\":1,"
                + "\"logIntervalShips\":50,"
                + "\"ruleVersion\":\"" + behavior + "\","
                + "\"batchNo\":\"" + batchNo + "\"}";
    }
}
