package com.hifleet.regionbehavior;

import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class TrajectoryPairSynchronizerTest {
    @Test
    public void doesNotReuseTargetAndRejectsPointsBeyondThreeMinutes() {
        TrajectoryPairSynchronizer.Result result = new TrajectoryPairSynchronizer(3)
                .synchronize(Arrays.asList(point("A", 0, 10.0), point("A", 1, 10.0),
                                point("A", 10, 10.0)),
                        Arrays.asList(point("B", 0, 11.0), point("B", 20, 11.0)));

        assertEquals(1, result.getPairs().size());
        assertEquals(1.0 / 3.0, result.getOwnCoverageRatio(), 0.0001);
        assertEquals(0.5, result.getTargetCoverageRatio(), 0.0);
    }

    @Test
    public void midpointCrossingDateLineStaysOnDateLine() {
        TrajectoryPairSynchronizer.Result result = new TrajectoryPairSynchronizer(3)
                .synchronize(Arrays.asList(point("A", 0, 179.0)),
                        Arrays.asList(point("B", 0, -179.0)));

        assertEquals(180.0, Math.abs(result.getPairs().get(0).getMidpointLon()), 0.0001);
    }

    private TrajectoryPoint point(String mmsi, int minute, double lon) {
        return new TrajectoryPoint(mmsi, LocalDateTime.of(2026, 6, 1, 0, 0).plusMinutes(minute),
                lon, 30.0, 2.0, 0.0, "0");
    }
}
