package com.hifleet.regionbehavior;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RegionEventMatcherTimeRatioTest {
    @Test
    public void pointRatioPassButTimeRatioFailIsBoundary() {
        RegionMatchedEvent event = matcher().matchLoitering(trace(Arrays.asList(
                point(0, 121.2), point(1, 121.2), point(2, 121.2),
                point(60, 120.0), point(120, 120.0))));

        assertEquals(0.6, event.getOwnPointInsideRatio(), 0.0);
        assertEquals(RegionMatchStatus.REGION_BOUNDARY_PARTIAL, event.getRegionMatchStatus());
    }

    @Test
    public void timeRatioPassButPointRatioFailIsBoundary() {
        RegionMatchedEvent event = matcher().matchLoitering(trace(Arrays.asList(
                point(0, 121.2), point(10, 121.2), point(20, 120.0),
                point(21, 120.0), point(22, 120.0))));

        assertEquals(0.4, event.getOwnPointInsideRatio(), 0.0);
        assertEquals(RegionMatchStatus.REGION_BOUNDARY_PARTIAL, event.getRegionMatchStatus());
    }

    private RegionEventMatcher matcher() {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        ShadowRulesConfig shadow = new ShadowRulesConfig();
        shadow.setEnabled(true);
        RegionGeometry geometry = RegionGeometry.parse(
                "POLYGON((121 30,122 30,122 31,121 31,121 30))", 0.0, config);
        return new RegionEventMatcher(geometry, config, "batch", "region", "name", shadow);
    }

    private ShipBehaviorTrace trace(List<TrajectoryPoint> points) {
        ShipBehaviorEvent event = new ShipBehaviorEvent();
        event.setBehaviorType("LOITERING");
        event.setMmsi("413000001");
        event.setStartTime("2026-06-01 00:00:00");
        event.setEndTime("2026-06-01 02:00:00");
        event.setCenterLon(121.2);
        event.setCenterLat(30.2);
        event.setRuleVersion("LOITERING_V2_INSURANCE");
        return new ShipBehaviorTrace(event, points, null);
    }

    private TrajectoryPoint point(int minute, double lon) {
        return new TrajectoryPoint("413000001",
                LocalDateTime.of(2026, 6, 1, 0, 0).plusMinutes(minute),
                lon, 30.2, 2.0, 0.0, "0");
    }
}
