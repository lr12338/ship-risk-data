package com.hifleet.service;

import com.hifleet.domain.AlongsideCandidateTarget;
import com.hifleet.domain.AlongsideReplaySearchResult;
import com.hifleet.domain.ReplayQueryRequest;
import com.hifleet.domain.ReplayQueryResult;
import com.hifleet.repository.AIS41GeneralQueryRepository;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

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

        assertEquals(1, result.getCandidates().size());
        AlongsideCandidateTarget candidate = result.getCandidates().get(0);
        assertEquals("413193000", candidate.getTargetMmsi());
        assertEquals(7, candidate.getTargetPoints().size());
        assertEquals(1, result.getReplayQueryCount());
        assertEquals(5, result.getReturnedVessels());
        assertEquals(1, result.getPrefilteredTargets());
    }

    private void configure(ShipAlongsideReplayCandidateProvider provider) {
        ReflectionTestUtils.setField(provider, "replayEnabled", true);
        ReflectionTestUtils.setField(provider, "replayDevSize", 50000);
        ReflectionTestUtils.setField(provider, "replayWindowMinutes", 30L);
        ReflectionTestUtils.setField(provider, "replayBboxBufferNm", 0.5);
        ReflectionTestUtils.setField(provider, "candidateMaxDistanceNm", 0.5);
        ReflectionTestUtils.setField(provider, "candidateMaxSpeed", 4.0);
        ReflectionTestUtils.setField(provider, "candidateMaxTimeGapMinutes", 10L);
        ReflectionTestUtils.setField(provider, "candidateMinPoints", 3);
        ReflectionTestUtils.setField(provider, "candidateMinDurationMinutes", 20L);
        ReflectionTestUtils.setField(provider, "merchantFilterEnabled", true);
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
            result.setTracksByMmsi(tracks);
            result.setRawVesselCount(5);
            result.setRawPointCount(30);
            result.setValidPointCount(30);
            return result;
        }
    }

    private class FakeShipTypeRepository extends AIS41GeneralQueryRepository {
        @Override
        public Map<String, String> selectShipTypes(Set<String> mmsis) {
            Map<String, String> result = new HashMap<String, String>();
            result.put("413193000", "油轮");
            result.put("999000003", "渔船");
            return result;
        }
    }
}
