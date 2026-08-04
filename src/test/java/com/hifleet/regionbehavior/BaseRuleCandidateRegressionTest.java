package com.hifleet.regionbehavior;

import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.service.ShipAlongsideDetector;
import com.hifleet.service.ShipLoiteringDetector;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class BaseRuleCandidateRegressionTest {
    @Test
    public void existingV3CanEmitNormalFollowingAsCandidate() {
        List<TrajectoryPoint> own = new ArrayList<TrajectoryPoint>();
        List<TrajectoryPoint> target = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0);
        for (int i = 0; i < 10; i++) {
            own.add(point("A", start.plusMinutes(i * 5L), 120.0, 20.0 + i * 0.001));
            target.add(point("B", start.plusMinutes(i * 5L), 120.0, 20.001 + i * 0.001));
        }

        List<ShipBehaviorTrace> traces = new ShipAlongsideDetector().detectWithTrace(
                "A", own, "B", target, ShipAlongsideDetector.AlongsideRule.defaultRule());

        assertFalse(traces.isEmpty());
        assertEquals("ALONGSIDE_V3_INSURANCE", traces.get(0).getEvent().getRuleVersion());
    }

    @Test
    public void existingV2CanEmitStraightFourHourLowSpeedCandidate() {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0);
        for (int i = 0; i <= 24; i++) {
            points.add(point("A", start.plusMinutes(i * 10L),
                    120.0, 20.0 + i * 0.001));
        }

        List<ShipBehaviorTrace> traces = new ShipLoiteringDetector().detectWithTrace(
                "A", points, ShipLoiteringDetector.LoiteringConfig.defaultConfig());

        assertFalse(traces.isEmpty());
        assertEquals("LOITERING_V2_INSURANCE", traces.get(0).getEvent().getRuleVersion());
    }

    private TrajectoryPoint point(String mmsi, LocalDateTime time, double lon, double lat) {
        return new TrajectoryPoint(mmsi, time, lon, lat, 2.0, 0.0, "0");
    }
}
