package com.hifleet.regionbehavior;

import com.hifleet.domain.ReplayQueryRequest;
import com.hifleet.domain.ReplayQueryResult;
import com.hifleet.service.SpatialAisReplayClient;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RegionAisDiscoveryServiceTest {

    @Test
    public void recursivelySplitsTruncationByTimeAndCompletes() {
        RegionBehaviorConfig config = config();
        RegionAisDiscoveryService service = new RegionAisDiscoveryService(new FakeClient() {
            @Override
            public ReplayQueryResult query(ReplayQueryRequest request) {
                long minutes = minutes(request);
                return result(request, true, minutes > 5, false, 0, insidePoints());
            }
        }, config);

        RegionAisDiscoveryResult result = service.discover(region(config),
                new RegionQueryPlan(Collections.singletonList(slice(10))), "batch", 10);

        assertEquals(RegionBehaviorStatus.COMPLETE, result.getStatus());
        assertEquals(3, result.getQuerySlices());
        assertEquals(2, result.getCompletedSlices());
        assertEquals(1, result.getTruncatedSlices());
    }

    @Test
    public void splitsSpaceAtMinimumTimeAndFailsClosedWhenStillTruncated() {
        RegionBehaviorConfig config = config();
        config.setMaxSpatialRefinementDepth(1);
        RegionAisDiscoveryService service = new RegionAisDiscoveryService(new FakeClient() {
            @Override
            public ReplayQueryResult query(ReplayQueryRequest request) {
                return result(request, true, true, false, 0, insidePoints());
            }
        }, config);

        RegionAisDiscoveryResult result = service.discover(region(config),
                new RegionQueryPlan(Collections.singletonList(slice(5))), "batch", 10);

        assertEquals(RegionBehaviorStatus.PARTIAL, result.getStatus());
        assertEquals(5, result.getQuerySlices());
        assertEquals(5, result.getTruncatedSlices());
        assertFalse(result.isQueryComplete());
    }

    @Test
    public void timeoutAndParseErrorNeverReportComplete() {
        RegionBehaviorConfig config = config();
        RegionAisDiscoveryService timeoutService = new RegionAisDiscoveryService(new FakeClient() {
            @Override
            public ReplayQueryResult query(ReplayQueryRequest request) {
                return result(request, false, false, true, 0, Collections.<TrajectoryPoint>emptyList());
            }
        }, config);
        RegionAisDiscoveryResult timeout = timeoutService.discover(region(config),
                new RegionQueryPlan(Collections.singletonList(slice(5))), "batch", 10);
        assertEquals(1, timeout.getTimeoutSlices());
        assertEquals(RegionBehaviorStatus.PARTIAL, timeout.getStatus());

        RegionAisDiscoveryService parseService = new RegionAisDiscoveryService(new FakeClient() {
            @Override
            public ReplayQueryResult query(ReplayQueryRequest request) {
                return result(request, true, false, false, 2, insidePoints());
            }
        }, config);
        RegionAisDiscoveryResult parse = parseService.discover(region(config),
                new RegionQueryPlan(Collections.singletonList(slice(5))), "batch", 10);
        assertEquals(1, parse.getParseErrorSlices());
        assertFalse(parse.isQueryComplete());
    }

    @Test
    public void exactFiltersAndDeduplicatesPoints() {
        RegionBehaviorConfig config = config();
        RegionAisDiscoveryService service = new RegionAisDiscoveryService(new FakeClient() {
            @Override
            public ReplayQueryResult query(ReplayQueryRequest request) {
                List<TrajectoryPoint> points = Arrays.asList(
                        point("413000001", 121.5, 30.5),
                        point("413000001", 121.5, 30.5),
                        point("413000002", 125.0, 35.0));
                return result(request, true, false, false, 0, points);
            }
        }, config);

        RegionAisDiscoveryResult result = service.discover(region(config),
                new RegionQueryPlan(Collections.singletonList(slice(5))), "batch", 10);

        assertEquals(3, result.getRawPoints());
        assertEquals(1, result.getUniquePoints());
        assertEquals(1, result.getDuplicatePoints());
        assertEquals(1, result.getCandidates().size());
    }

    @Test
    public void candidateLimitIsExplicitFailure() {
        RegionBehaviorConfig config = config();
        RegionAisDiscoveryService service = new RegionAisDiscoveryService(new FakeClient() {
            @Override
            public ReplayQueryResult query(ReplayQueryRequest request) {
                return result(request, true, false, false, 0, Arrays.asList(
                        point("413000001", 121.2, 30.2),
                        point("413000002", 121.3, 30.3)));
            }
        }, config);

        RegionAisDiscoveryResult result = service.discover(region(config),
                new RegionQueryPlan(Collections.singletonList(slice(5))), "batch", 1);

        assertEquals(RegionBehaviorStatus.CANDIDATE_LIMIT_EXCEEDED, result.getStatus());
        assertTrue(result.isCappedByMaxCandidates());
    }

    @Test
    public void recursiveQueriesRespectActualQueryCap() {
        RegionBehaviorConfig config = config();
        config.setMaxQuerySlices(2);
        RegionAisDiscoveryService service = new RegionAisDiscoveryService(new FakeClient() {
            @Override
            public ReplayQueryResult query(ReplayQueryRequest request) {
                return result(request, true, true, false, 0, insidePoints());
            }
        }, config);

        RegionAisDiscoveryResult result = service.discover(region(config),
                new RegionQueryPlan(Collections.singletonList(slice(10))), "batch", 10);

        assertEquals(2, result.getQuerySlices());
        assertEquals(RegionBehaviorStatus.PARTIAL, result.getStatus());
        assertTrue(result.getFailedSlices() > 0);
    }

    @Test
    public void keepsInvalidMmsiForExplicitEligibilityAccounting() {
        RegionBehaviorConfig config = config();
        RegionAisDiscoveryService service = new RegionAisDiscoveryService(new FakeClient() {
            @Override
            public ReplayQueryResult query(ReplayQueryRequest request) {
                return result(request, true, false, false, 0,
                        Collections.singletonList(point("INVALID", 121.5, 30.5)));
            }
        }, config);

        RegionAisDiscoveryResult result = service.discover(region(config),
                new RegionQueryPlan(Collections.singletonList(slice(5))), "batch", 10);

        assertEquals(1, result.getCandidates().size());
        RegionShipMetadataSummary metadata = new StubInvalidMetadataService(config)
                .filter(result.candidates);
        assertEquals(1, metadata.getDiscoveredVessels());
        assertEquals(0, metadata.getValidMmsiVessels());
    }

    private RegionBehaviorConfig config() {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        config.setMinTimeSliceMinutes(5);
        config.setMaxParseErrorRatio(0.10);
        return config;
    }

    private RegionGeometry region(RegionBehaviorConfig config) {
        return RegionGeometry.parse("POLYGON((121 30,122 30,122 31,121 31,121 30))", 0, config);
    }

    private RegionReplaySlice slice(int minutes) {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0);
        return RegionReplaySlice.root("fp", "cell", 121, 122, 30, 31,
                start, start.plusMinutes(minutes));
    }

    private List<TrajectoryPoint> insidePoints() {
        return Collections.singletonList(point("413000001", 121.5, 30.5));
    }

    private TrajectoryPoint point(String mmsi, double lon, double lat) {
        return new TrajectoryPoint(mmsi, LocalDateTime.of(2026, 6, 1, 0, 1),
                lon, lat, 0, 0, "");
    }

    private ReplayQueryResult result(ReplayQueryRequest request, boolean success,
                                     boolean truncated, boolean timeout, int parseErrors,
                                     List<TrajectoryPoint> points) {
        ReplayQueryResult result = new ReplayQueryResult();
        result.setRequest(request);
        result.setSuccess(success);
        result.setTruncated(truncated);
        result.setTimeout(timeout);
        result.setRawPointCount(points.size() + parseErrors);
        result.setValidPointCount(points.size());
        result.setParseErrorCount(parseErrors);
        Map<String, List<TrajectoryPoint>> tracks = new HashMap<String, List<TrajectoryPoint>>();
        for (TrajectoryPoint point : points) {
            List<TrajectoryPoint> track = tracks.get(point.getMmsi());
            if (track == null) {
                track = new java.util.ArrayList<TrajectoryPoint>();
                tracks.put(point.getMmsi(), track);
            }
            track.add(point);
        }
        result.setRawVesselCount(tracks.size());
        result.setTracksByMmsi(tracks);
        return result;
    }

    private abstract static class FakeClient extends SpatialAisReplayClient {
        long minutes(ReplayQueryRequest request) {
            return Duration.between(RegionBehaviorTimes.parse(request.getStartTime()),
                    RegionBehaviorTimes.parse(request.getEndTime())).toMinutes();
        }
    }

    private static class StubInvalidMetadataService extends RegionShipMetadataService {
        StubInvalidMetadataService(RegionBehaviorConfig config) {
            super(null, new com.hifleet.service.ShipTypeClassifier(), config);
        }

        @Override
        protected List<ShipMetadata> queryBatch(List<String> mmsis) {
            return Collections.emptyList();
        }
    }
}
