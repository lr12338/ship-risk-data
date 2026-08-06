package com.hifleet.worldsea;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
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
    public void missingEvidenceIsRejected() {
        ShipBehaviorTrace trace = trace(ShipBehaviorEvent.TYPE_ALONGSIDE,
                Collections.<TrajectoryPoint>emptyList(), Collections.<TrajectoryPoint>emptyList(), 1, 1);
        assertTrue(gate.filterAndAnnotate(Collections.singletonList(trace)).isEmpty());
        assertTrue(trace.getEvent().getScoreDetailJson().contains("WORLD_SEA_EVIDENCE_INSUFFICIENT"));
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

    private ShipBehaviorTrace trace(String type, List<TrajectoryPoint> own, List<TrajectoryPoint> target,
                                    double centerLon, double centerLat) {
        ShipBehaviorEvent event = new ShipBehaviorEvent();
        event.setBehaviorType(type); event.setCenterLon(centerLon); event.setCenterLat(centerLat);
        return new ShipBehaviorTrace(event, own, target);
    }
    private TrajectoryPoint point(double lon, double lat, int minute) {
        return new TrajectoryPoint("1", LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(minute), lon, lat, 1, 1, null);
    }
}
