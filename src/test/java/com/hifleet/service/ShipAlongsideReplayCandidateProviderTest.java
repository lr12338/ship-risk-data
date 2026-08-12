package com.hifleet.service;

import com.hifleet.domain.AlongsideCandidateTarget;
import com.hifleet.domain.AlongsideReplaySearchResult;
import com.hifleet.domain.ReplayQueryRequest;
import com.hifleet.domain.ReplayQueryResult;
import com.hifleet.repository.AIS41GeneralQueryRepository;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ShipAlongsideReplayCandidateProviderTest {

    @Test
    public void filtersReplayTargetsBeforeDetector() {
        ShipAlongsideReplayCandidateProvider provider = new ShipAlongsideReplayCandidateProvider();
        configure(provider);
        provider.setSpatialAisReplayClient(new FakeReplayClient());

        List<TrajectoryPoint> ownPoints = points("413433610", 0, 7, 5, 0.0, 0.0, 0.5);
        ShipAlongsideDetector detector = new ShipAlongsideDetector();
        List<ShipAlongsideDetector.LowSpeedWindow> windows = detector.extractLowSpeedWindows(
                "413433610", ownPoints, 4.0, 10, 30);
        Map<String, List<TrajectoryPoint>> ownPointsByWindow = new HashMap<String, List<TrajectoryPoint>>();
        ownPointsByWindow.put(provider.windowKey(windows.get(0)), ownPoints);

        AlongsideReplaySearchResult result = provider.findCandidates(
                "413433610", windows, ownPointsByWindow, "test-batch");

        assertEquals(7, result.getCandidates().size());
        assertEquals(provider.planQueries(windows, ownPointsByWindow, "test-batch").size(),
                result.getAttemptedReplayQueryCount());
        assertEquals(1, result.getReplayQueryCount());
        assertEquals(10, result.getReturnedVessels());
        assertEquals(7, result.getPrefilteredTargets());
        Set<String> recalled = new java.util.HashSet<String>();
        for (AlongsideCandidateTarget candidate : result.getCandidates()) {
            recalled.add(candidate.getTargetMmsi());
        }
        assertTrue(recalled.contains("999000003")); // fishing
        assertTrue(recalled.contains("999000004")); // supply
        assertTrue(recalled.contains("999000005")); // barge
        assertTrue(recalled.contains("999000006")); // tug
        assertTrue(recalled.contains("999000007")); // small craft
        assertTrue(recalled.contains("999000008")); // missing type
    }

    @Test
    public void recursivelySplitsTruncatedReplayAndMergesCrossSliceCandidate() {
        ShipAlongsideReplayCandidateProvider provider = new ShipAlongsideReplayCandidateProvider();
        configure(provider);
        ReflectionTestUtils.setField(provider, "splitOnTruncated", true);
        ReflectionTestUtils.setField(provider, "replayMinWindowMinutes", 10L);
        List<TrajectoryPoint> targetPoints = points("999000003", 0, 7, 5,
                0.0005, 0.0005, 0.6);
        SplittingReplayClient client = new SplittingReplayClient(targetPoints, false, false);
        provider.setSpatialAisReplayClient(client);

        List<TrajectoryPoint> ownPoints = points("413433610", 0, 7, 5,
                0.0, 0.0, 0.5);
        List<ShipAlongsideDetector.LowSpeedWindow> windows = new ShipAlongsideDetector()
                .extractLowSpeedWindows("413433610", ownPoints, 4.0, 20, 30);
        Map<String, List<TrajectoryPoint>> byWindow = new HashMap<String, List<TrajectoryPoint>>();
        byWindow.put(provider.windowKey(windows.get(0)), ownPoints);

        AlongsideReplaySearchResult result = provider.findCandidates(
                "413433610", windows, byWindow, "split-batch", false);

        assertEquals(5, result.getAttemptedReplayQueryCount());
        assertEquals(3, result.getSuccessfulReplayQueryCount());
        assertEquals(0, result.getTruncatedReplayQueryCount());
        assertEquals(0, result.getFailedReplayQueryCount());
        assertEquals(7, result.getRawPoints());
        assertEquals(1, result.getReturnedVessels());
        assertEquals(1, result.getCandidates().size());
        assertEquals(7, result.getCandidates().get(0).getTargetPoints().size());
        assertEquals(30L, Duration.between(
                result.getCandidates().get(0).getTargetPoints().get(0).getTime(),
                result.getCandidates().get(0).getTargetPoints().get(6).getTime()).toMinutes());
    }

    @Test
    public void overlappingTenMinuteSlicesDeduplicateBoundaryPoints() {
        ShipAlongsideReplayCandidateProvider provider = new ShipAlongsideReplayCandidateProvider();
        configure(provider);
        ReflectionTestUtils.setField(provider, "splitOnTruncated", true);
        ReflectionTestUtils.setField(provider, "replayMinWindowMinutes", 10L);
        ReflectionTestUtils.setField(provider, "candidateMinDurationMinutes", 10L);
        List<TrajectoryPoint> targetPoints = points("999000003", 0, 4, 5,
                0.0005, 0.0005, 0.6);
        provider.setSpatialAisReplayClient(new SplittingReplayClient(targetPoints, false, false));

        List<TrajectoryPoint> ownPoints = points("413433610", 0, 4, 5,
                0.0, 0.0, 0.5);
        List<ShipAlongsideDetector.LowSpeedWindow> windows = new ShipAlongsideDetector()
                .extractLowSpeedWindows("413433610", ownPoints, 4.0, 10, 30);
        Map<String, List<TrajectoryPoint>> byWindow = new HashMap<String, List<TrajectoryPoint>>();
        byWindow.put(provider.windowKey(windows.get(0)), ownPoints);

        AlongsideReplaySearchResult result = provider.findCandidates(
                "413433610", windows, byWindow, "overlap-batch", false);

        assertEquals(3, result.getAttemptedReplayQueryCount());
        assertEquals(2, result.getSuccessfulReplayQueryCount());
        assertEquals(0, result.getTruncatedReplayQueryCount());
        assertEquals(4, result.getRawPoints());
        assertEquals(4, result.getCandidates().get(0).getTargetPoints().size());
    }

    @Test
    public void keepsFailClosedWhenMinimumSliceIsStillTruncated() {
        ShipAlongsideReplayCandidateProvider provider = new ShipAlongsideReplayCandidateProvider();
        configure(provider);
        ReflectionTestUtils.setField(provider, "splitOnTruncated", true);
        ReflectionTestUtils.setField(provider, "replayMinWindowMinutes", 10L);
        provider.setSpatialAisReplayClient(new SplittingReplayClient(
                points("999000003", 0, 3, 5, 0.0005, 0.0005, 0.6), true, false));

        List<TrajectoryPoint> ownPoints = points("413433610", 0, 3, 5,
                0.0, 0.0, 0.5);
        List<ShipAlongsideDetector.LowSpeedWindow> windows = new ShipAlongsideDetector()
                .extractLowSpeedWindows("413433610", ownPoints, 4.0, 10, 30);
        Map<String, List<TrajectoryPoint>> byWindow = new HashMap<String, List<TrajectoryPoint>>();
        byWindow.put(provider.windowKey(windows.get(0)), ownPoints);

        AlongsideReplaySearchResult result = provider.findCandidates(
                "413433610", windows, byWindow, "terminal-batch", false);

        assertEquals(1, result.getAttemptedReplayQueryCount());
        assertEquals(0, result.getSuccessfulReplayQueryCount());
        assertEquals(1, result.getTruncatedReplayQueryCount());
        assertEquals(0, result.getRawPoints());
        assertTrue(result.getCandidates().isEmpty());
    }

    @Test
    public void splitCanBeDisabledAndChildFailureDoesNotUsePartialData() {
        List<TrajectoryPoint> ownPoints = points("413433610", 0, 7, 5,
                0.0, 0.0, 0.5);
        List<TrajectoryPoint> targetPoints = points("999000003", 0, 7, 5,
                0.0005, 0.0005, 0.6);
        List<ShipAlongsideDetector.LowSpeedWindow> windows = new ShipAlongsideDetector()
                .extractLowSpeedWindows("413433610", ownPoints, 4.0, 20, 30);

        ShipAlongsideReplayCandidateProvider disabled = new ShipAlongsideReplayCandidateProvider();
        configure(disabled);
        ReflectionTestUtils.setField(disabled, "splitOnTruncated", false);
        ReflectionTestUtils.setField(disabled, "replayMinWindowMinutes", 10L);
        disabled.setSpatialAisReplayClient(new SplittingReplayClient(targetPoints, false, false));
        Map<String, List<TrajectoryPoint>> byWindow = new HashMap<String, List<TrajectoryPoint>>();
        byWindow.put(disabled.windowKey(windows.get(0)), ownPoints);
        AlongsideReplaySearchResult disabledResult = disabled.findCandidates(
                "413433610", windows, byWindow, "disabled-batch", false);
        assertEquals(1, disabledResult.getAttemptedReplayQueryCount());
        assertEquals(1, disabledResult.getTruncatedReplayQueryCount());

        ShipAlongsideReplayCandidateProvider failed = new ShipAlongsideReplayCandidateProvider();
        configure(failed);
        ReflectionTestUtils.setField(failed, "splitOnTruncated", true);
        ReflectionTestUtils.setField(failed, "replayMinWindowMinutes", 10L);
        failed.setSpatialAisReplayClient(new SplittingReplayClient(targetPoints, false, true));
        byWindow.clear();
        byWindow.put(failed.windowKey(windows.get(0)), ownPoints);
        AlongsideReplaySearchResult failedResult = failed.findCandidates(
                "413433610", windows, byWindow, "failed-batch", false);
        assertEquals(2, failedResult.getAttemptedReplayQueryCount());
        assertEquals(0, failedResult.getSuccessfulReplayQueryCount());
        assertEquals(1, failedResult.getFailedReplayQueryCount());
        assertEquals(0, failedResult.getRawPoints());
        assertTrue(failedResult.getCandidates().isEmpty());
    }

    private void configure(ShipAlongsideReplayCandidateProvider provider) {
        ReflectionTestUtils.setField(provider, "replayEnabled", true);
        ReflectionTestUtils.setField(provider, "replayDevSize", 50000);
        ReflectionTestUtils.setField(provider, "replayWindowMinutes", 30L);
        ReflectionTestUtils.setField(provider, "splitOnTruncated", true);
        ReflectionTestUtils.setField(provider, "replayMinWindowMinutes", 10L);
        ReflectionTestUtils.setField(provider, "replayBboxBufferNm", 0.5);
        ReflectionTestUtils.setField(provider, "candidateMaxDistanceNm", 0.5);
        ReflectionTestUtils.setField(provider, "candidateMaxSpeed", 4.0);
        ReflectionTestUtils.setField(provider, "candidateMaxTimeGapMinutes", 10L);
        ReflectionTestUtils.setField(provider, "candidateMinPoints", 3);
        ReflectionTestUtils.setField(provider, "candidateMinDurationMinutes", 20L);
        ReflectionTestUtils.setField(provider, "merchantFilterEnabled", true);
        ReflectionTestUtils.setField(provider, "targetShipFilterMode", "NONE");
        ReflectionTestUtils.setField(provider, "shipTypeClassifier", new ShipTypeClassifier());
        ReflectionTestUtils.setField(provider, "ais41GeneralQueryRepository", new FakeShipTypeRepository());
    }

    private List<TrajectoryPoint> points(String mmsi, int startMinute, int count, int stepMinutes,
                                         double lon, double lat, double speed) {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 6, 28, 18, 0).plusMinutes(startMinute);
        for (int i = 0; i < count; i++) {
            points.add(new TrajectoryPoint(mmsi, start.plusMinutes(i * stepMinutes),
                    lon + i * 0.00001, lat + i * 0.00001, speed, 90.0, null));
        }
        return points;
    }

    private class FakeReplayClient extends SpatialAisReplayClient {
        @Override
        public ReplayQueryResult query(ReplayQueryRequest request) {
            ReplayQueryResult result = new ReplayQueryResult();
            result.setSuccess(true);
            result.setRequest(request);
            Map<String, List<TrajectoryPoint>> tracks = new HashMap<String, List<TrajectoryPoint>>();
            tracks.put("413433610", points("413433610", 0, 7, 5, 0.0, 0.0, 0.5));
            tracks.put("413193000", points("413193000", 0, 7, 5, 0.0005, 0.0005, 0.6));
            tracks.put("999000001", points("999000001", 0, 7, 5, 1.0, 1.0, 0.6));
            tracks.put("999000002", points("999000002", 0, 2, 5, 0.0005, 0.0005, 0.6));
            tracks.put("999000003", points("999000003", 0, 7, 5, 0.0005, 0.0005, 0.6));
            tracks.put("999000004", points("999000004", 0, 7, 5, 0.0005, 0.0005, 0.2));
            tracks.put("999000005", points("999000005", 0, 7, 5, 0.0005, 0.0005, 0.4));
            tracks.put("999000006", points("999000006", 0, 7, 5, 0.0005, 0.0005, 0.5));
            tracks.put("999000007", points("999000007", 0, 7, 5, 0.0005, 0.0005, 0.3));
            tracks.put("999000008", points("999000008", 0, 7, 5, 0.0005, 0.0005, 0.6));
            result.setTracksByMmsi(tracks);
            result.setRawVesselCount(10);
            result.setRawPointCount(65);
            result.setValidPointCount(65);
            return result;
        }
    }

    private class FakeShipTypeRepository extends AIS41GeneralQueryRepository {
        @Override
        public Map<String, String> selectShipTypes(Set<String> mmsis) {
            Map<String, String> result = new HashMap<String, String>();
            result.put("413193000", "油轮");
            result.put("999000003", "渔船");
            result.put("999000004", "补给船");
            result.put("999000005", "驳船");
            result.put("999000006", "拖轮");
            result.put("999000007", "小艇");
            return result;
        }
    }

    private class SplittingReplayClient extends SpatialAisReplayClient {
        private final List<TrajectoryPoint> targetPoints;
        private final boolean truncateMinimum;
        private final boolean failFirstChild;

        private SplittingReplayClient(List<TrajectoryPoint> targetPoints,
                                      boolean truncateMinimum, boolean failFirstChild) {
            this.targetPoints = targetPoints;
            this.truncateMinimum = truncateMinimum;
            this.failFirstChild = failFirstChild;
        }

        @Override
        public ReplayQueryResult query(ReplayQueryRequest request) {
            ReplayQueryResult result = new ReplayQueryResult();
            result.setRequest(request);
            LocalDateTime start = LocalDateTime.parse(request.getStartTime(),
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            LocalDateTime end = LocalDateTime.parse(request.getEndTime(),
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            long duration = Duration.between(start, end).toMinutes();
            if (failFirstChild && duration <= 10L) {
                result.setSuccess(false);
                result.setErrorMessage("TEST_CHILD_FAILURE");
                return result;
            }
            if (duration > 10L || truncateMinimum) {
                result.setSuccess(true);
                result.setTruncated(true);
                result.setRawPointCount(request.getDevSize());
                return result;
            }
            Map<String, List<TrajectoryPoint>> tracks = new HashMap<String, List<TrajectoryPoint>>();
            List<TrajectoryPoint> selected = new ArrayList<TrajectoryPoint>();
            for (TrajectoryPoint point : targetPoints) {
                if (!point.getTime().isBefore(start) && !point.getTime().isAfter(end)) {
                    selected.add(point);
                }
            }
            tracks.put("999000003", selected);
            result.setSuccess(true);
            result.setTracksByMmsi(tracks);
            result.setRawVesselCount(1);
            result.setRawPointCount(selected.size());
            result.setValidPointCount(selected.size());
            return result;
        }
    }
}
