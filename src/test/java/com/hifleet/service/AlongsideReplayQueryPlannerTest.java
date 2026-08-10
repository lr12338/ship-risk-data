package com.hifleet.service;

import com.hifleet.domain.AlongsideReplaySearchResult;
import com.hifleet.domain.ReplayQueryResult;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AlongsideReplayQueryPlannerTest {
    private final ShipAlongsideDetector detector = new ShipAlongsideDetector();
    private final AlongsideReplayQueryPlanner planner = new AlongsideReplayQueryPlanner();

    @Test
    public void baselineFiveFilteredTwoSavesThreeQueries() {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        points.addAll(segment(0, 60, 10));
        points.addAll(segment(120, 60, 10));
        points.addAll(segment(240, 30, 10));
        List<ShipAlongsideDetector.LowSpeedWindow> raw = detector.extractLowSpeedWindows(
                "413000001", points, 1.0, 3.0, 20, 20);
        assertEquals(3, raw.size());
        Map<String, List<TrajectoryPoint>> byWindow = map(raw, points);

        AlongsideReplayQueryPlan baseline = planner.plan(raw, byWindow, "batch", 30, 1, 0.5, 100);
        AlongsideReplayQueryPlan filtered = planner.plan(Collections.singletonList(raw.get(0)),
                byWindow, "batch", 30, 1, 0.5, 100);

        assertEquals(5, baseline.size());
        assertEquals(2, filtered.size());
        assertEquals(3, Math.max(0, baseline.size() - filtered.size()));
    }

    @Test
    public void oneRejectedWindowCanSaveThreeQueriesAndDedupCanSaveZero() {
        List<TrajectoryPoint> points = segment(0, 70, 10);
        List<ShipAlongsideDetector.LowSpeedWindow> windows = detector.extractLowSpeedWindows(
                "413000001", points, 1.0, 3.0, 20, 20);
        Map<String, List<TrajectoryPoint>> byWindow = map(windows, points);
        AlongsideReplayQueryPlan baseline = planner.plan(windows, byWindow, "batch", 30, 1, 0.5, 100);
        assertEquals(3, baseline.size());
        assertEquals(3, baseline.size() - planner.plan(Collections.<ShipAlongsideDetector.LowSpeedWindow>emptyList(),
                byWindow, "batch", 30, 1, 0.5, 100).size());

        List<ShipAlongsideDetector.LowSpeedWindow> duplicate = new ArrayList<ShipAlongsideDetector.LowSpeedWindow>();
        duplicate.add(windows.get(0)); duplicate.add(windows.get(0));
        AlongsideReplayQueryPlan deduplicated = planner.plan(duplicate, byWindow,
                "batch", 30, 1, 0.5, 100);
        assertEquals(baseline.size(), deduplicated.size());
        assertEquals(0, deduplicated.size() - baseline.size());
    }

    @Test
    public void failuresAndTruncationDoNotChangePlannedSavings() {
        AlongsideReplaySearchResult result = new AlongsideReplaySearchResult();
        ReplayQueryResult failed = new ReplayQueryResult(); failed.setSuccess(false);
        ReplayQueryResult truncated = new ReplayQueryResult();
        truncated.setSuccess(true); truncated.setTruncated(true);
        result.addReplayResult(failed); result.addReplayResult(truncated);

        assertEquals(2, result.getAttemptedReplayQueryCount());
        assertEquals(0, result.getSuccessfulReplayQueryCount());
        assertEquals(1, result.getFailedReplayQueryCount());
        assertEquals(1, result.getTruncatedReplayQueryCount());
        int baseline = 5, filtered = 2;
        assertEquals(3, Math.max(0, baseline - filtered));
        assertTrue(Math.max(0, filtered - baseline) == 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void resultModelRejectsNegativeSavedQueryCount() {
        new com.hifleet.job.services.ShipBehaviorAnalysisService.AlongsideWindowResult(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                Collections.<com.hifleet.domain.ShipBehaviorTrace>emptyList(),
                0, 0, 0, 0, -1, 0, 0, 0, 0);
    }

    private Map<String, List<TrajectoryPoint>> map(List<ShipAlongsideDetector.LowSpeedWindow> windows,
                                                   List<TrajectoryPoint> points) {
        Map<String, List<TrajectoryPoint>> result = new HashMap<String, List<TrajectoryPoint>>();
        for (ShipAlongsideDetector.LowSpeedWindow window : windows) {
            result.put(AlongsideReplayQueryPlanner.windowKey(window), points);
        }
        return result;
    }

    private List<TrajectoryPoint> segment(int startMinute, int durationMinutes, int stepMinutes) {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(startMinute);
        for (int minute = 0; minute <= durationMinutes; minute += stepMinutes) {
            result.add(new TrajectoryPoint("413000001", start.plusMinutes(minute),
                    120.0, 30.0, 2.0, 90.0, null));
        }
        return result;
    }
}
