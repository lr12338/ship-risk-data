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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ShipLoiteringDetectorTest {

    private final ShipLoiteringDetector detector = new ShipLoiteringDetector();

    @Test
    public void emptyTrackReturnsEmptyEvents() {
        assertTrue(detector.detectWithTrace(new ArrayList<TrajectoryPoint>()).isEmpty());
    }

    @Test
    public void insufficientPointsReturnsEmptyEvents() {
        List<TrajectoryPoint> points = slowPoints(5, 20, 1.0, null);

        assertTrue(detector.detectWithTrace("413193000", points, config()).isEmpty());
    }

    @Test
    public void gapGreaterThanSplitThresholdCutsEvents() {
        ShipLoiteringDetector.LoiteringConfig config = config();
        config.setWeakEnabled(false);
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        points.addAll(slowPoints(8, 20, 1.0, null));
        points.addAll(slowPoints(LocalDateTime.of(2026, 6, 30, 8, 0), 8, 20, 1.0, null));

        List<ShipBehaviorTrace> traces = detector.detectWithTrace("413193000", points, config);

        assertTrue(traces.isEmpty());
        assertEquals(2, detector.getLastStats().getSegments());
    }

    @Test
    public void slowFourHoursSmallRadiusEnoughPointsCreatesSlowLoitering() {
        List<TrajectoryPoint> points = slowPoints(13, 20, 1.0, null);

        List<ShipBehaviorTrace> traces = detector.detectWithTrace("413193000", points, config());

        assertFalse(traces.isEmpty());
        ShipBehaviorEvent event = traces.get(0).getEvent();
        assertEquals(ShipLoiteringDetector.SUB_TYPE_SLOW_LOITERING, event.getEventSubType());
        assertEquals(ShipLoiteringDetector.CONFIDENCE_HIGH, event.getConfidenceLevel());
        assertNotNull(event.getScoreDetailJson());
    }

    @Test
    public void frequentTurnSmallRadiusCreatesManeuverLoitering() {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 6, 30, 0, 0);
        double[] cogs = new double[]{0, 90, 180, 270, 0, 90, 180, 270, 0, 90, 180, 270, 0};
        for (int i = 0; i < cogs.length; i++) {
            points.add(point(start.plusMinutes(i * 10), 121.0 + (i % 2) * 0.001,
                    31.0 + (i % 3) * 0.001, 3.0, cogs[i], null));
        }

        List<ShipBehaviorTrace> traces = detector.detectWithTrace("413193000", points, config());

        assertFalse(traces.isEmpty());
        assertEquals(ShipLoiteringDetector.SUB_TYPE_MANEUVER_LOITERING, traces.get(0).getEvent().getEventSubType());
    }

    @Test
    public void cogDeltaWrapsAcrossNorth() {
        assertEquals(20.0, detector.circularCogDelta(350.0, 10.0), 0.0001);
    }

    @Test
    public void sparseLongEventIsLowConfidence() {
        ShipLoiteringDetector.LoiteringConfig config = config();
        config.setSlowMinPoints(6);
        List<TrajectoryPoint> points = slowPoints(6, 60, 1.0, null);

        List<ShipBehaviorTrace> traces = detector.detectWithTrace("413193000", points, config);

        assertFalse(traces.isEmpty());
        assertEquals(ShipLoiteringDetector.CONFIDENCE_LOW, traces.get(0).getEvent().getConfidenceLevel());
    }

    @Test
    public void lowConfidenceSeverityIsAtMostL2() {
        ShipLoiteringDetector.LoiteringConfig config = config();
        config.setSlowMinPoints(6);
        List<TrajectoryPoint> points = slowPoints(6, 60, 1.0, null);

        ShipBehaviorEvent event = detector.detectWithTrace("413193000", points, config).get(0).getEvent();

        assertTrue(rank(event.getSeverityLevel()) <= rank("L2"));
    }

    @Test
    public void anchoredOrMooredStatusHalvesScore() {
        List<TrajectoryPoint> normalLikely = slowPoints(13, 20, 1.0, "ANCHORED");
        List<TrajectoryPoint> risk = slowPoints(13, 20, 1.0, null);

        ShipBehaviorEvent normalEvent = detector.detectWithTrace("413193000", normalLikely, config()).get(0).getEvent();
        ShipBehaviorEvent riskEvent = detector.detectWithTrace("413193000", risk, config()).get(0).getEvent();

        assertEquals(ShipLoiteringDetector.DOWNGRADED_NORMAL_LIKELY, normalEvent.getExclusionFlag());
        assertTrue(normalEvent.getEventScore() < riskEvent.getEventScore());
    }

    @Test
    public void scoreDetailJsonIsNotEmpty() {
        ShipBehaviorEvent event = detector.detectWithTrace("413193000", slowPoints(13, 20, 1.0, null), config())
                .get(0).getEvent();

        assertNotNull(event.getScoreDetailJson());
        assertTrue(event.getScoreDetailJson().contains("durationScore"));
    }

    @Test
    public void overlappingSlowAndManeuverMergeToComposite() {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 6, 30, 0, 0);
        double[] cogs = new double[]{0, 90, 180, 270, 0, 90, 180, 270, 0, 90, 180, 270, 0};
        for (int i = 0; i < cogs.length; i++) {
            points.add(point(start.plusMinutes(i * 20), 121.0 + (i % 2) * 0.001,
                    31.0 + (i % 3) * 0.001, 1.5, cogs[i], null));
        }

        List<ShipBehaviorTrace> traces = detector.detectWithTrace("413193000", points, config());

        assertEquals(1, traces.size());
        ShipBehaviorEvent event = traces.get(0).getEvent();
        assertEquals(ShipLoiteringDetector.SUB_TYPE_COMPOSITE_LOITERING, event.getEventSubType());
        assertTrue(event.getScoreDetailJson().contains("SLOW_LOITERING"));
        assertTrue(event.getScoreDetailJson().contains("MANEUVER_LOITERING"));
        assertEquals(1, detector.getLastStats().getDuplicateMerged());
    }

    @Test
    public void noSceneDataCapsL3SeverityToL2() {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 6, 30, 0, 0);
        double[] cogs = new double[]{0, 90, 180, 270, 0, 90, 180, 270, 0, 90, 180, 270, 0, 90, 180, 270, 0, 90, 180, 270, 0, 90};
        for (int i = 0; i < cogs.length; i++) {
            points.add(point(start.plusMinutes(i * 31), 121.0 + (i % 2) * 0.001,
                    31.0 + (i % 3) * 0.001, 1.5, cogs[i], null));
        }

        ShipBehaviorEvent event = detector.detectWithTrace("413193000", points, config()).get(0).getEvent();

        assertEquals("L2", event.getSeverityLevel());
        assertTrue(event.getScoreDetailJson().contains("\"rawSeverity\":\"L3\""));
        assertTrue(event.getScoreDetailJson().contains("NO_SCENE_DATA_NO_EXTERNAL_EVIDENCE"));
    }

    @Test
    public void weakCandidateIsStoredAsLowL1() {
        ShipLoiteringDetector.LoiteringConfig config = config();
        List<TrajectoryPoint> points = slowPoints(7, 15, 2.5, null);

        ShipBehaviorEvent event = detector.detectWithTrace("413193000", points, config).get(0).getEvent();

        assertEquals(ShipLoiteringDetector.SUB_TYPE_WEAK_LOITERING_CANDIDATE, event.getEventSubType());
        assertEquals("L1", event.getSeverityLevel());
        assertEquals(ShipLoiteringDetector.CONFIDENCE_LOW, event.getConfidenceLevel());
        assertTrue(event.getEventScore() <= 20.0);
        assertTrue(event.getScoreDetailJson().contains("\"weakCandidate\":true"));
        assertTrue(event.getScoreDetailJson().contains("\"includeInAnnualScore\":false"));
    }

    @Test
    public void noCandidateTrackProducesRejectSummaryAndNearMiss() {
        ShipLoiteringDetector.LoiteringConfig config = config();
        config.setWeakEnabled(false);
        List<TrajectoryPoint> points = slowPoints(5, 20, 2.5, null);

        List<ShipBehaviorTrace> traces = detector.detectWithTrace("413193000", points, config);

        assertTrue(traces.isEmpty());
        assertTrue(detector.getLastStats().getRejectSummary().getRejectByPointCount() > 0);
        assertTrue(detector.getLastStats().getRejectSummary().getNearMisses().size() > 0);
    }

    private ShipLoiteringDetector.LoiteringConfig config() {
        return ShipLoiteringDetector.LoiteringConfig.defaultConfig();
    }

    private List<TrajectoryPoint> slowPoints(int count, int stepMinutes, double speed, String navStatus) {
        return slowPoints(LocalDateTime.of(2026, 6, 30, 0, 0), count, stepMinutes, speed, navStatus);
    }

    private List<TrajectoryPoint> slowPoints(LocalDateTime start, int count, int stepMinutes,
                                             double speed, String navStatus) {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        for (int i = 0; i < count; i++) {
            points.add(point(start.plusMinutes(i * stepMinutes), 121.0 + i * 0.0001,
                    31.0 + i * 0.0001, speed, 90.0, navStatus));
        }
        return points;
    }

    private TrajectoryPoint point(LocalDateTime time, double lon, double lat, double speed,
                                  double course, String navStatus) {
        return new TrajectoryPoint("413193000", time, lon, lat, speed, course, navStatus);
    }

    private int rank(String severity) {
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
