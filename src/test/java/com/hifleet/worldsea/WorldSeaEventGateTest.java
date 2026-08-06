package com.hifleet.worldsea;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.domain.AlongsideEvidencePair;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WorldSeaEventGateTest {
    private final WorldSeaEventGate gate = new WorldSeaEventGate(
            WorldSeaTestFixtures.service(), new WorldSeaMaskProperties());

    @Test
    public void loiteringRequiresAllEvidenceAndCenterInside() {
        ShipBehaviorTrace pass = trace(ShipBehaviorEvent.TYPE_LOITERING,
                Arrays.asList(point(1, 1, 0), point(2, 2, 1)), Collections.<TrajectoryPoint>emptyList(), 1, 1);
        assertEquals(1, gate.filterAndAnnotate(Collections.singletonList(pass)).size());
        JSONObject detail = JSON.parseObject(pass.getEvent().getScoreDetailJson());
        assertEquals("PASS", detail.getString("worldSeaDecision"));
        assertEquals(1.0d, detail.getDoubleValue("worldSeaEvidenceCoverage"), 0.0d);

        ShipBehaviorTrace reject = trace(ShipBehaviorEvent.TYPE_LOITERING,
                Arrays.asList(point(1, 1, 0), point(50, 50, 1)), Collections.<TrajectoryPoint>emptyList(), 1, 1);
        assertTrue(gate.filterAndAnnotate(Collections.singletonList(reject)).isEmpty());
    }

    @Test
    public void alongsideRequiresOwnTargetAndMidpointInside() {
        ShipBehaviorTrace pass = trace(ShipBehaviorEvent.TYPE_ALONGSIDE,
                Collections.singletonList(point(1, 1, 0)), Collections.singletonList(point(2, 2, 0)), 1.5, 1.5);
        assertEquals(1, gate.filterAndAnnotate(Collections.singletonList(pass)).size());

        ShipBehaviorTrace reject = trace(ShipBehaviorEvent.TYPE_ALONGSIDE,
                Collections.singletonList(point(1, 1, 0)), Collections.singletonList(point(50, 50, 0)), 1, 1);
        assertTrue(gate.filterAndAnnotate(Collections.singletonList(reject)).isEmpty());
    }

    @Test
    public void unmatchedOwnPointsDoNotReducePairCoverage() {
        List<TrajectoryPoint> own = new ArrayList<TrajectoryPoint>();
        List<TrajectoryPoint> target = new ArrayList<TrajectoryPoint>();
        for (int i = 0; i < 10; i++) own.add(point(1, 1, i));
        for (int i = 0; i < 8; i++) target.add(point(2, 2, i));
        ShipBehaviorTrace trace = alongsideTrace(own, target, 8, 600L, 1.5, 1.5);

        assertEquals(1, gate.filterAndAnnotate(Collections.singletonList(trace)).size());
        JSONObject detail = JSON.parseObject(trace.getEvent().getScoreDetailJson());
        assertEquals(8, detail.getIntValue("worldSeaTotalPairEvidenceCount"));
        assertEquals(1.0d, detail.getDoubleValue("worldSeaPairCoverage"), 0.0d);
        assertEquals(2, detail.getIntValue("alongsideUnmatchedOwnPointCount"));
        assertEquals(0, detail.getIntValue("alongsideUnmatchedTargetPointCount"));
    }

    @Test
    public void invalidOrMissingDetectorPairsFailClosed() {
        List<TrajectoryPoint> own = Arrays.asList(point(1, 1, 0), point(1, 1, 1));
        List<TrajectoryPoint> target = Arrays.asList(point(2, 2, 0), point(2, 2, 1));
        ShipBehaviorEvent event = event(ShipBehaviorEvent.TYPE_ALONGSIDE, 1.5, 1.5);
        List<AlongsideEvidencePair> duplicateTarget = Arrays.asList(
                new AlongsideEvidencePair(own.get(0), target.get(0), 0, 0, 0, 0.1),
                new AlongsideEvidencePair(own.get(1), target.get(0), 1, 0, 60, 0.1));
        ShipBehaviorTrace invalid = new ShipBehaviorTrace(event, own, target, duplicateTarget,
                2, 2, 600L, "DETECTOR_CONFIRMED_PAIRS");
        assertTrue(gate.filterAndAnnotate(Collections.singletonList(invalid)).isEmpty());
        assertTrue(event.getScoreDetailJson().contains("WORLD_SEA_PAIR_EVIDENCE_INVALID"));

        ShipBehaviorTrace missing = new ShipBehaviorTrace(
                event(ShipBehaviorEvent.TYPE_ALONGSIDE, 1.5, 1.5), own, target);
        assertTrue(gate.filterAndAnnotate(Collections.singletonList(missing)).isEmpty());
        assertTrue(missing.getEvent().getScoreDetailJson().contains("WORLD_SEA_PAIR_EVIDENCE_MISSING"));
    }

    @Test
    public void missingEvidenceIsRejected() {
        ShipBehaviorTrace trace = trace(ShipBehaviorEvent.TYPE_ALONGSIDE,
                Collections.<TrajectoryPoint>emptyList(), Collections.<TrajectoryPoint>emptyList(), 1, 1);
        assertTrue(gate.filterAndAnnotate(Collections.singletonList(trace)).isEmpty());
        assertTrue(trace.getEvent().getScoreDetailJson().contains("WORLD_SEA_PAIR_EVIDENCE_MISSING"));
    }

    @Test
    public void strictCoverageRejectsNinetyNinePercentAndAcceptsOneHundredPercent() {
        List<TrajectoryPoint> allInside = new ArrayList<TrajectoryPoint>();
        for (int i = 0; i < 100; i++) allInside.add(point(1, 1, i));
        assertEquals(1, gate.filterAndAnnotate(Collections.singletonList(trace(
                ShipBehaviorEvent.TYPE_LOITERING, allInside, Collections.<TrajectoryPoint>emptyList(), 1, 1))).size());
        allInside.set(99, point(50, 50, 99));
        assertTrue(gate.filterAndAnnotate(Collections.singletonList(trace(
                ShipBehaviorEvent.TYPE_LOITERING, allInside, Collections.<TrajectoryPoint>emptyList(), 1, 1))).isEmpty());
    }

    @Test
    public void eventCenterOutsideIsRejected() {
        assertTrue(gate.filterAndAnnotate(Collections.singletonList(trace(
                ShipBehaviorEvent.TYPE_LOITERING, Collections.singletonList(point(1, 1, 0)),
                Collections.<TrajectoryPoint>emptyList(), 50, 50))).isEmpty());
    }

    @Test
    public void alongsideMidpointOutsideIsRejectedEvenWhenBothVesselsAreInside() {
        WorldSeaEventGate disjointGate = new WorldSeaEventGate(
                WorldSeaTestFixtures.disjointService(), new WorldSeaMaskProperties());
        ShipBehaviorTrace trace = trace(ShipBehaviorEvent.TYPE_ALONGSIDE,
                Collections.singletonList(point(1, 1, 0)), Collections.singletonList(point(9, 1, 0)), 1, 1);
        assertTrue(disjointGate.filterAndAnnotate(Collections.singletonList(trace)).isEmpty());
    }

    @Test
    public void antimeridianMidpointUsesTheShortArc() {
        WorldSeaEventGate antimeridianGate = new WorldSeaEventGate(
                WorldSeaTestFixtures.antimeridianService(), new WorldSeaMaskProperties());
        ShipBehaviorTrace trace = alongsideTrace(
                Collections.singletonList(point(179.9, 0, 0)),
                Collections.singletonList(point(-179.9, 0, 0)), 1, 600L, 180, 0);
        assertEquals(1, antimeridianGate.filterAndAnnotate(Collections.singletonList(trace)).size());
    }

    private ShipBehaviorTrace trace(String type, List<TrajectoryPoint> own, List<TrajectoryPoint> target,
                                    double centerLon, double centerLat) {
        ShipBehaviorEvent event = event(type, centerLon, centerLat);
        if (ShipBehaviorEvent.TYPE_ALONGSIDE.equals(type) && !own.isEmpty() && !target.isEmpty()) {
            return alongsideTrace(own, target, Math.min(own.size(), target.size()), 600L,
                    centerLon, centerLat);
        }
        return new ShipBehaviorTrace(event, own, target);
    }

    private ShipBehaviorTrace alongsideTrace(List<TrajectoryPoint> own, List<TrajectoryPoint> target,
                                              int pairCount, long maxGapSeconds,
                                              double centerLon, double centerLat) {
        List<AlongsideEvidencePair> pairs = new ArrayList<AlongsideEvidencePair>();
        for (int i = 0; i < pairCount; i++) {
            long gap = Math.abs(java.time.Duration.between(own.get(i).getTime(), target.get(i).getTime()).getSeconds());
            pairs.add(new AlongsideEvidencePair(own.get(i), target.get(i), i, i, gap, 0.1));
        }
        return new ShipBehaviorTrace(event(ShipBehaviorEvent.TYPE_ALONGSIDE, centerLon, centerLat),
                own, target, pairs, own.size(), target.size(), maxGapSeconds,
                "DETECTOR_CONFIRMED_PAIRS");
    }

    private ShipBehaviorEvent event(String type, double centerLon, double centerLat) {
        ShipBehaviorEvent event = new ShipBehaviorEvent();
        event.setBehaviorType(type); event.setCenterLon(centerLon); event.setCenterLat(centerLat);
        return event;
    }
    private TrajectoryPoint point(double lon, double lat, int minute) {
        return new TrajectoryPoint("1", LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(minute), lon, lat, 1, 1, null);
    }
}
