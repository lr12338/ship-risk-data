package com.hifleet.behaviorplatform;

import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.service.ShipAlongsideDetector;
import com.hifleet.service.ShipLoiteringDetector;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class BehaviorDetectorAdapterCharacterizationTest {
    @Test
    public void alongsideAdapterMatchesLegacyDetector() {
        List<TrajectoryPoint> own = points("413433610", 0.0, 1.5);
        List<TrajectoryPoint> target = points("413193000", 0.001, 1.4);
        List<ShipBehaviorTrace> legacy = new ShipAlongsideDetector().detectWithTrace(
                "413433610", own, "413193000", target, ShipAlongsideDetector.AlongsideRule.defaultRule());
        List<ShipBehaviorTrace> adapted = new AlongsideDetectorAdapter().detect(
                new BehaviorDetectorRequest("413433610", "413193000", own, target));
        assertEventsEqual(legacy, adapted);
    }

    @Test
    public void loiteringAdapterMatchesLegacyDetector() {
        List<TrajectoryPoint> own = points("413433610", 0.0, 1.0);
        List<ShipBehaviorTrace> legacy = new ShipLoiteringDetector().detectWithTrace(
                "413433610", own, ShipLoiteringDetector.LoiteringConfig.defaultConfig());
        List<ShipBehaviorTrace> adapted = new LoiteringDetectorAdapter().detect(
                new BehaviorDetectorRequest("413433610", null, own, null));
        assertEventsEqual(legacy, adapted);
    }

    private void assertEventsEqual(List<ShipBehaviorTrace> expected, List<ShipBehaviorTrace> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).getEvent().getBehaviorType(), actual.get(i).getEvent().getBehaviorType());
            assertEquals(expected.get(i).getEvent().getEventSubType(), actual.get(i).getEvent().getEventSubType());
            assertEquals(expected.get(i).getEvent().getStartTime(), actual.get(i).getEvent().getStartTime());
            assertEquals(expected.get(i).getEvent().getEndTime(), actual.get(i).getEvent().getEndTime());
            assertEquals(expected.get(i).getEvent().getRuleVersion(), actual.get(i).getEvent().getRuleVersion());
        }
    }

    private List<TrajectoryPoint> points(String mmsi, double offset, double speed) {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        for (int i = 0; i < 13; i++) result.add(new TrajectoryPoint(mmsi,
                start.plusMinutes(i * 20), 121.0 + offset + (i % 2) * 0.0001,
                31.0 + offset + (i % 3) * 0.0001, speed, 90.0, null));
        return result;
    }
}
