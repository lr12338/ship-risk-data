package com.hifleet.regionbehavior;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RegionEventMatcherTest {

    @Test
    public void strictLoiteringRequiresCenterAndMajority() {
        RegionEventMatcher matcher = matcher();
        RegionMatchedEvent inside = matcher.matchLoitering(trace("LOITERING", "A", null,
                points("A", 121.2, 30.2, 121.8, 30.8), null, 121.5, 30.5));
        RegionMatchedEvent centerOnly = matcher.matchLoitering(trace("LOITERING", "A", null,
                points("A", 120.0, 30.2, 121.2, 30.2), null, 121.2, 30.2));
        RegionMatchedEvent majorityOnly = matcher.matchLoitering(trace("LOITERING", "A", null,
                points("A", 121.2, 30.2, 121.3, 30.3), null, 120.9, 30.2));

        assertEquals(RegionMatchStatus.STRICT_CORE, inside.getRegionMatchStatus());
        assertEquals(RegionMatchStatus.REGION_BOUNDARY_PARTIAL, centerOnly.getRegionMatchStatus());
        assertEquals(RegionMatchStatus.REGION_BOUNDARY_PARTIAL, majorityOnly.getRegionMatchStatus());
    }

    @Test
    public void strictAlongsideRequiresBothShipsAndPairMidpoints() {
        RegionEventMatcher matcher = matcher();
        RegionMatchedEvent inside = matcher.matchAlongside(trace("ALONGSIDE", "A", "B",
                points("A", 121.2, 30.2, 121.3, 30.3),
                points("B", 121.21, 30.21, 121.31, 30.31), 121.25, 30.25));
        RegionMatchedEvent targetOutside = matcher.matchAlongside(trace("ALONGSIDE", "A", "B",
                points("A", 121.2, 30.2, 121.3, 30.3),
                points("B", 120.0, 30.2, 120.1, 30.3), 121.25, 30.25));

        assertEquals(RegionMatchStatus.STRICT_CORE, inside.getRegionMatchStatus());
        assertEquals(RegionMatchStatus.REGION_BOUNDARY_PARTIAL, targetOutside.getRegionMatchStatus());
    }

    @Test
    public void deduplicatesReversePairsWithSmallTimeDrift() {
        RegionEventMatcher matcher = matcher();
        RegionMatchedEvent ab = matcher.matchAlongside(trace("ALONGSIDE", "413000001", "413000002",
                points("413000001", 121.2, 30.2, 121.3, 30.3),
                points("413000002", 121.21, 30.21, 121.31, 30.31), 121.25, 30.25));
        ShipBehaviorTrace reverse = trace("ALONGSIDE", "413000002", "413000001",
                points("413000002", 121.21, 30.21, 121.31, 30.31),
                points("413000001", 121.2, 30.2, 121.3, 30.3), 121.25, 30.25);
        reverse.getEvent().setStartTime("2026-06-01 00:02:00");
        reverse.getEvent().setEndTime("2026-06-01 00:32:00");
        RegionMatchedEvent ba = matcher.matchAlongside(reverse);

        List<RegionMatchedEvent> deduplicated = matcher.deduplicateAlongside(Arrays.asList(ab, ba));

        assertEquals(1, deduplicated.size());
        assertEquals(1, matcher.getLastDuplicateCount());
        assertEquals("413000001", deduplicated.get(0).getPairMmsi1());
    }

    @Test
    public void outsideEndpointsWhoseTrackCrossesCoreAreBoundaryPartial() {
        RegionMatchedEvent crossing = matcher().matchLoitering(trace("LOITERING", "A", null,
                points("A", 120.5, 30.5, 122.5, 30.5), null, 120.5, 30.5));
        assertEquals(RegionMatchStatus.REGION_BOUNDARY_PARTIAL, crossing.getRegionMatchStatus());
        assertEquals(0, crossing.getOwnPointInsideCount());
    }

    private RegionEventMatcher matcher() {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        config.setLoiteringMinInsideRatio(0.60);
        config.setAlongsideMinInsideRatio(0.60);
        RegionGeometry geometry = RegionGeometry.parse(
                "POLYGON((121 30,122 30,122 31,121 31,121 30))", 0.0, config);
        return new RegionEventMatcher(geometry, config, "batch", "region", "name");
    }

    private ShipBehaviorTrace trace(String type, String mmsi, String target,
                                    List<TrajectoryPoint> own, List<TrajectoryPoint> targetPoints,
                                    double centerLon, double centerLat) {
        ShipBehaviorEvent event = new ShipBehaviorEvent();
        event.setBehaviorType(type);
        event.setMmsi(mmsi);
        event.setTargetMmsi(target);
        event.setStartTime("2026-06-01 00:00:00");
        event.setEndTime("2026-06-01 00:30:00");
        event.setCenterLon(centerLon);
        event.setCenterLat(centerLat);
        event.setRuleVersion(type + "_RULE");
        return new ShipBehaviorTrace(event, own, targetPoints);
    }

    private List<TrajectoryPoint> points(String mmsi, double lon1, double lat1,
                                         double lon2, double lat2) {
        return Arrays.asList(
                new TrajectoryPoint(mmsi, LocalDateTime.of(2026, 6, 1, 0, 0), lon1, lat1, 0, 0, ""),
                new TrajectoryPoint(mmsi, LocalDateTime.of(2026, 6, 1, 0, 30), lon2, lat2, 0, 0, ""));
    }
}
