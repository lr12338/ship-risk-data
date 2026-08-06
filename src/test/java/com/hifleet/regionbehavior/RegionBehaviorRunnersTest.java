package com.hifleet.regionbehavior;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.job.services.ShipBehaviorAnalysisService;
import com.hifleet.service.ShipAlongsideDetector;
import com.hifleet.service.ShipLoiteringDetector;
import com.hifleet.trajectory.TrajectoryLoaderService;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RegionBehaviorRunnersTest {

    @Test
    public void loiteringLoadsFullContextThenUsesExistingV2ProductionFilter() {
        TrajectoryLoaderService loader = mock(TrajectoryLoaderService.class);
        ShipBehaviorAnalysisService analysis = mock(ShipBehaviorAnalysisService.class);
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        TrajectoryPoint point = point("413000001");
        when(loader.loadSafely(eq("413000001"), anyString(), anyString(),
                anyInt(), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(point));
        ShipBehaviorTrace trace = trace("LOITERING", null,
                ShipLoiteringDetector.VALID_RISK_EVENT, "VALID");
        when(analysis.analysisLoiteringV2Production(eq("413000001"), anyList(), eq("batch")))
                .thenReturn(Collections.singletonList(trace));

        RegionBehaviorRunnerResult result = new RegionLoiteringRunner(loader, analysis, config)
                .run("413000001", params());

        assertEquals(1, result.getTraces().size());
        assertEquals(1, result.getRawCandidates());
        assertFalse(trace.getEvent().getScoreDetailJson().contains("persistPolicy"));
        verify(loader).loadSafely("413000001", "2026-05-31 18:00:00",
                "2026-06-01 12:00:00", 15, 6, 200000);
        verify(analysis).analysisLoiteringV2Production(eq("413000001"), anyList(), eq("batch"));
    }

    @Test
    public void alongsidePreservesReplayFailureAndTruncationAsIncomplete() {
        ShipBehaviorAnalysisService analysis = mock(ShipBehaviorAnalysisService.class);
        ShipBehaviorTrace trace = trace("ALONGSIDE", "413000002",
                ShipAlongsideDetector.VALID_RISK_EVENT, "VALID");
        ShipBehaviorAnalysisService.AlongsideWindowResult computed =
                new ShipBehaviorAnalysisService.AlongsideWindowResult(
                        10, 1, 1, 1, 2, 1, 1, 1, 1, 0, 0,
                        Collections.singletonList(trace));
        when(analysis.analysisAlongsideProductionWindow("413000001",
                "2026-05-31 18:00:00", "2026-06-01 12:00:00", "batch"))
                .thenReturn(computed);

        RegionBehaviorRunnerResult result = new RegionAlongsideRunner(
                analysis, RegionBehaviorConfig.defaults()).run("413000001", params());

        assertFalse(result.isComplete());
        assertEquals(1, result.getReplayFailures());
        assertEquals(1, result.getTruncatedQueries());
        assertEquals(1, result.getTraces().size());
    }

    private RegionBehaviorJobParams params() {
        RegionBehaviorJobParams params = new RegionBehaviorJobParams();
        params.setRegionId("region");
        params.setRegionName("name");
        params.setBatchNo("batch");
        params.setStartTime("2026-06-01 00:00:00");
        params.setEndTime("2026-06-01 06:00:00");
        return params;
    }

    private TrajectoryPoint point(String mmsi) {
        return new TrajectoryPoint(mmsi, LocalDateTime.of(2026, 6, 1, 1, 0),
                121.5, 30.5, 0, 0, "");
    }

    private ShipBehaviorTrace trace(String type, String target, String exclusion, String subtype) {
        ShipBehaviorEvent event = new ShipBehaviorEvent();
        event.setBehaviorType(type);
        event.setMmsi("413000001");
        event.setTargetMmsi(target);
        event.setStartTime("2026-06-01 01:00:00");
        event.setEndTime("2026-06-01 02:00:00");
        event.setExclusionFlag(exclusion);
        event.setEventSubType(subtype);
        event.setScoreDetailJson("{\"traceRecipe\":{}}");
        return new ShipBehaviorTrace(event, Collections.singletonList(point("413000001")),
                target == null ? null : Collections.singletonList(point(target)));
    }
}
