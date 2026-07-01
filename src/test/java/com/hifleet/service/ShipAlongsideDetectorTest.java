package com.hifleet.service;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShipAlongsideDetectorTest {

    private final ShipAlongsideDetector detector = new ShipAlongsideDetector();

    @Test
    public void detectsV2EventWithScoreAndNoSceneCap() {
        List<ShipBehaviorTrace> traces = detector.detectWithTrace("413433610",
                points("413433610", 0.0, 0.0, 7, 5, 0.6, 90.0, null),
                "413193000",
                points("413193000", 0.0005, 0.0005, 7, 5, 0.5, 92.0, null),
                rule());

        assertEquals(1, traces.size());
        ShipBehaviorEvent event = traces.get(0).getEvent();
        assertEquals("ALONGSIDE_V2_INSURANCE", event.getRuleVersion());
        assertEquals("HIGH", event.getConfidenceLevel());
        assertEquals("VALID_RISK_EVENT", event.getExclusionFlag());
        assertTrue(event.getEventScore() > 0.0);
        assertTrue(event.getScoreDetailJson().contains("avgRelativeSpeed"));
        assertTrue(severityRank(event.getSeverityLevel()) <= 2);
    }

    @Test
    public void downgradesAnchoredOrMooredScene() {
        List<ShipBehaviorTrace> traces = detector.detectWithTrace("413433610",
                points("413433610", 0.0, 0.0, 7, 5, 0.3, 90.0, "ANCHORED"),
                "413193000",
                points("413193000", 0.0005, 0.0005, 7, 5, 0.3, 90.0, null),
                rule());

        ShipBehaviorEvent event = traces.get(0).getEvent();
        assertEquals("DOWNGRADED_NORMAL_LIKELY", event.getExclusionFlag());
        assertEquals("NAV_STATUS_ANCHORED_OR_MOORED", event.getExclusionReason());
    }

    @Test
    public void rejectsLargeTimeGap() {
        List<TrajectoryPoint> own = points("413433610", 0.0, 0.0, 7, 5, 0.5, 90.0, null);
        List<TrajectoryPoint> target = pointsFromMinute("413193000", 11, 0.0005, 0.0005, 7, 30, 0.5, 90.0, null);

        assertTrue(detector.detectWithTrace("413433610", own, "413193000", target, rule()).isEmpty());
    }

    @Test
    public void rejectsShortPassingEncounter() {
        List<ShipBehaviorTrace> traces = detector.detectWithTrace("413433610",
                points("413433610", 0.0, 0.0, 2, 5, 0.5, 90.0, null),
                "413193000",
                points("413193000", 0.0005, 0.0005, 2, 5, 0.5, 90.0, null),
                rule());

        assertTrue(traces.isEmpty());
    }

    @Test
    public void mergesSamePairNearbySegments() {
        List<TrajectoryPoint> own = new ArrayList<TrajectoryPoint>();
        own.addAll(points("413433610", 0.0, 0.0, 4, 5, 0.5, 90.0, null));
        own.addAll(pointsFromMinute("413433610", 45, 0.0002, 0.0002, 4, 5, 0.5, 90.0, null));
        List<TrajectoryPoint> target = new ArrayList<TrajectoryPoint>();
        target.addAll(points("413193000", 0.0005, 0.0005, 4, 5, 0.5, 90.0, null));
        target.addAll(pointsFromMinute("413193000", 45, 0.0007, 0.0007, 4, 5, 0.5, 90.0, null));

        List<ShipBehaviorTrace> traces = detector.detectWithTrace("413433610", own, "413193000", target, rule());

        assertEquals(1, traces.size());
        assertEquals(8, traces.get(0).getEvent().getPointCount().intValue());
    }

    @Test
    public void extractsLowSpeedWindowsForAutoMode() {
        List<TrajectoryPoint> points = points("413433610", 0.0, 0.0, 5, 5, 3.5, 90.0, null);

        List<ShipAlongsideDetector.LowSpeedWindow> windows = detector.extractLowSpeedWindows(
                "413433610", points, 4.0, 10, 30);

        assertFalse(windows.isEmpty());
        assertEquals("413433610", windows.get(0).getMmsi());
    }

    private ShipAlongsideDetector.AlongsideRule rule() {
        ShipAlongsideDetector.AlongsideRule rule = ShipAlongsideDetector.AlongsideRule.defaultRule();
        rule.setRuleVersion("ALONGSIDE_V2_INSURANCE");
        rule.setConfirmMaxDistanceNm(0.15);
        rule.setConfirmMaxSpeed(2.0);
        rule.setMaxRelativeSpeed(1.0);
        rule.setMaxTimeGapMinutes(10);
        rule.setHighConfidenceTimeGapMinutes(5);
        rule.setWeakMinDurationMinutes(20);
        rule.setMinDurationMinutes(30);
        rule.setMinPoints(3);
        rule.setFormalMinPoints(6);
        rule.setMergeEnabled(true);
        rule.setMergeMaxGapMinutes(30);
        rule.setSceneDataAvailable(false);
        rule.setCounterpartyRiskAvailable(false);
        return rule;
    }

    private List<TrajectoryPoint> points(String mmsi, double lon, double lat, int count, int stepMinutes,
                                         double speed, double course, String navStatus) {
        return pointsFromMinute(mmsi, 0, lon, lat, count, stepMinutes, speed, course, navStatus);
    }

    private List<TrajectoryPoint> pointsFromMinute(String mmsi, int startMinute, double lon, double lat,
                                                   int count, int stepMinutes, double speed, double course,
                                                   String navStatus) {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 6, 28, 18, 0).plusMinutes(startMinute);
        for (int i = 0; i < count; i++) {
            points.add(new TrajectoryPoint(mmsi, start.plusMinutes(i * stepMinutes),
                    lon + i * 0.00001, lat + i * 0.00001, speed, course, navStatus));
        }
        return points;
    }

    private int severityRank(String severity) {
        if ("L4".equals(severity)) {
            return 4;
        }
        if ("L3".equals(severity)) {
            return 3;
        }
        if ("L2".equals(severity)) {
            return 2;
        }
        if ("L1".equals(severity)) {
            return 1;
        }
        return 0;
    }
}
