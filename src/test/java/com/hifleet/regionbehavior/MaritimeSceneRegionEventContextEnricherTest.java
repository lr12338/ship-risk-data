package com.hifleet.regionbehavior;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.maritime.MaritimeSceneClass;
import com.hifleet.maritime.MaritimeSceneDecision;
import com.hifleet.maritime.MaritimeSceneEvidence;
import com.hifleet.maritime.MaritimeScenePolicy;
import com.hifleet.maritime.MaritimeSceneService;
import com.hifleet.maritime.SceneConfidence;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class MaritimeSceneRegionEventContextEnricherTest {
    @Test
    public void sceneOffLeavesStrictEventUnchanged() {
        RegionMatchedEvent event = matched("LOITERING", RegionMatchStatus.STRICT_CORE);
        MaritimeSceneRegionEventContextEnricher enricher =
                new MaritimeSceneRegionEventContextEnricher(new MaritimeSceneService());

        RegionMatchedEvent result = enricher.enrich(event, trace("LOITERING", ownPoints(), null));

        assertSame(event, result);
        assertNull(result.getDominantMaritimeScene());
        assertNull(result.getShadowDecision());
    }

    @Test
    public void loiteringAggregatesTheCompleteOwnTrajectory() {
        RegionMatchedEvent event = matched("LOITERING", RegionMatchStatus.STRICT_CORE);
        MaritimeSceneRegionEventContextEnricher enricher =
                new MaritimeSceneRegionEventContextEnricher(new StubSceneService());

        enricher.enrich(event, trace("LOITERING", ownPoints(), null));

        assertEquals("HIGH_SEAS", event.getDominantMaritimeScene());
        assertEquals("HIGH", event.getSceneConfidence());
        assertEquals("OPEN_WATER_CONFIRMED", event.getShadowDecision());
        assertEquals("phase1-global-base-v1", event.getSceneSnapshotVersion());
        assertEquals("test-fingerprint", event.getSceneSnapshotFingerprint());
        assertEquals(10.0, event.getCoastDistanceMinNm(), 0.0);
        assertEquals(20.0, event.getCoastDistanceP50Nm(), 0.0);
        assertEquals(20.0, event.getCoastDistanceMaxNm(), 0.0);
        assertEquals(0.0, event.getSceneUnknownRatio(), 0.0);
        assertEquals(0.0, event.getSceneConflictRatio(), 0.0);
    }

    @Test
    public void alongsideKeepsBothScenesAndReportsConflict() {
        RegionMatchedEvent event = matched("ALONGSIDE", RegionMatchStatus.STRICT_CORE);
        MaritimeSceneRegionEventContextEnricher enricher =
                new MaritimeSceneRegionEventContextEnricher(new StubSceneService());

        enricher.enrich(event, trace("ALONGSIDE", ownPoints(), targetPoints()));

        assertEquals("HIGH_SEAS", event.getOwnScene());
        assertEquals("INLAND_RIVER", event.getTargetScene());
        assertEquals("SCENE_CONFLICT", event.getSynchronizedScene());
        assertEquals("UNKNOWN", event.getSceneConfidence());
        assertEquals("SCENE_CONFLICT", event.getShadowDecision());
    }

    @Test
    public void boundaryEventIsNotEnriched() {
        RegionMatchedEvent event = matched("LOITERING", RegionMatchStatus.REGION_BOUNDARY_PARTIAL);
        StubSceneService service = new StubSceneService();
        MaritimeSceneRegionEventContextEnricher enricher =
                new MaritimeSceneRegionEventContextEnricher(service);

        enricher.enrich(event, trace("LOITERING", ownPoints(), null));

        assertEquals(0, service.classifications);
        assertNull(event.getDominantMaritimeScene());
    }

    private RegionMatchedEvent matched(String type, RegionMatchStatus status) {
        RegionMatchedEvent event = RegionMatchedEvent.from(event(type));
        event.setRegionMatchStatus(status);
        return event;
    }

    private ShipBehaviorTrace trace(String type, java.util.List<TrajectoryPoint> own,
                                    java.util.List<TrajectoryPoint> target) {
        return new ShipBehaviorTrace(event(type), own,
                target == null ? Collections.<TrajectoryPoint>emptyList() : target);
    }

    private ShipBehaviorEvent event(String type) {
        ShipBehaviorEvent event = new ShipBehaviorEvent();
        event.setBehaviorType(type);
        event.setMmsi("413000001");
        event.setTargetMmsi("ALONGSIDE".equals(type) ? "413000002" : null);
        event.setStartTime("2026-06-01 00:00:00");
        event.setEndTime("2026-06-01 00:10:00");
        event.setRuleVersion("test-v1");
        return event;
    }

    private java.util.List<TrajectoryPoint> ownPoints() {
        return Arrays.asList(point("413000001", 0, 1.0), point("413000001", 10, 1.0));
    }

    private java.util.List<TrajectoryPoint> targetPoints() {
        return Arrays.asList(point("413000002", 0, 2.0), point("413000002", 10, 2.0));
    }

    private TrajectoryPoint point(String mmsi, int minute, double lon) {
        return new TrajectoryPoint(mmsi, LocalDateTime.of(2026, 6, 1, 0, minute),
                lon, 30.0, 1.0, 0.0, "0");
    }

    private static final class StubSceneService extends MaritimeSceneService {
        private int classifications;

        private StubSceneService() {
            super(null, MaritimeScenePolicy.defaults("SHADOW"));
        }

        @Override
        public MaritimeSceneDecision classify(double longitude, double latitude) {
            classifications++;
            MaritimeSceneClass scene = longitude < 2.0
                    ? MaritimeSceneClass.HIGH_SEAS : MaritimeSceneClass.INLAND_RIVER;
            double coast = longitude < 2.0 ? classifications * 10.0 : 1.0;
            return new MaritimeSceneDecision(scene, SceneConfidence.HIGH,
                    MaritimeSceneEvidence.unavailable("test"), coast, null,
                    "phase1-global-base-v1", "test-fingerprint",
                    MaritimeScenePolicy.VERSION, false);
        }
    }
}
