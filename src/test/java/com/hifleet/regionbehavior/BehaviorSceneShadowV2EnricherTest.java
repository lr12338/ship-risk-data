package com.hifleet.regionbehavior;

import com.alibaba.fastjson.JSON;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BehaviorSceneShadowV2EnricherTest {
    @Test
    public void twoHourInlandLoiteringIsRejectedBySceneTime() {
        RegionMatchedEvent event = matched("LOITERING", RegionMatchStatus.STRICT_CORE);
        enricher(new CoordinateSceneService()).enrich(event,
                trace("LOITERING", track("A", 0.0), null));

        assertEquals("WOULD_REJECT_INLAND", event.getSceneShadowDecision());
        assertEquals("WOULD_REJECT_INLAND", event.getCombinedShadowDecision());
        assertTrue(event.getSceneInlandDurationMinutes() >= 60.0);
        assertNotNull(event.getBehaviorEvidence());
    }

    @Test
    public void alongsideUsesSynchronizedMidpointScene() {
        RegionMatchedEvent event = matched("ALONGSIDE", RegionMatchStatus.STRICT_CORE);
        enricher(new CoordinateSceneService()).enrich(event,
                trace("ALONGSIDE", track("A", -1.0), track("B", 1.0)));

        assertEquals("HIGH_SEAS", event.getOwnScene());
        assertEquals("HIGH_SEAS", event.getTargetScene());
        assertEquals("INLAND_RIVER", event.getSynchronizedScene());
        assertEquals("SCENE_CONFLICT", event.getSceneShadowDecision());
        assertNotNull(event.getPairMidpointSceneSummary());
    }

    @Test
    public void consistentInlandMidpointForSixtyMinutesIsRejected() {
        RegionMatchedEvent event = matched("ALONGSIDE", RegionMatchStatus.STRICT_CORE);
        enricher(new CoordinateSceneService()).enrich(event,
                trace("ALONGSIDE", track("A", -0.02), track("B", 0.02)));

        assertEquals("INLAND_RIVER", event.getOwnScene());
        assertEquals("INLAND_RIVER", event.getTargetScene());
        assertEquals("WOULD_REJECT_INLAND", event.getSceneShadowDecision());
    }

    @Test
    public void boundaryEventRunsDiagnosticsButCannotBeAccepted() {
        RegionMatchedEvent event = matched("LOITERING", RegionMatchStatus.REGION_BOUNDARY_PARTIAL);
        enricher(new OpenWaterSceneService()).enrich(event,
                trace("LOITERING", track("A", 20.0), null));

        assertTrue(event.isShadowRulesExecuted());
        assertEquals("REVIEW_REGION_BOUNDARY", event.getCombinedShadowDecision());
    }

    @Test
    public void unknownSceneCannotBeAccepted() {
        RegionMatchedEvent event = matched("LOITERING", RegionMatchStatus.STRICT_CORE);
        enricher(new UnknownSceneService()).enrich(event,
                trace("LOITERING", track("A", 20.0), null));

        assertEquals("SCENE_REVIEW_REQUIRED", event.getCombinedShadowDecision());
    }

    @Test
    public void shadowRulesOffPreservesV1FieldsAndSkipsV2Decision() {
        RegionMatchedEvent event = matched("LOITERING", RegionMatchStatus.STRICT_CORE);
        new MaritimeSceneRegionEventContextEnricher(new OpenWaterSceneService())
                .enrich(event, trace("LOITERING", track("A", 20.0), null));

        assertEquals("OPEN_WATER_CONFIRMED", event.getShadowDecision());
        assertTrue(!event.isShadowRulesExecuted());
        assertNull(event.getCombinedShadowDecision());
    }

    @Test
    public void enrichmentNeverMutatesFormalEventObject() {
        ShipBehaviorTrace trace = trace("LOITERING", track("A", 0.0), null);
        String before = JSON.toJSONString(trace.getEvent());
        enricher(new CoordinateSceneService()).enrich(
                matched("LOITERING", RegionMatchStatus.STRICT_CORE), trace);

        assertEquals(before, JSON.toJSONString(trace.getEvent()));
    }

    private MaritimeSceneRegionEventContextEnricher enricher(MaritimeSceneService service) {
        ShadowRulesConfig config = new ShadowRulesConfig();
        config.setEnabled(true);
        return new MaritimeSceneRegionEventContextEnricher(service, config,
                points -> new OperationalAreaContext(false, false, false, false, true));
    }

    private RegionMatchedEvent matched(String type, RegionMatchStatus status) {
        RegionMatchedEvent event = RegionMatchedEvent.from(event(type));
        event.setRegionMatchStatus(status);
        event.setShadowRulesRequested(true);
        return event;
    }

    private ShipBehaviorTrace trace(String type, List<TrajectoryPoint> own,
                                    List<TrajectoryPoint> target) {
        return new ShipBehaviorTrace(event(type), own, target);
    }

    private ShipBehaviorEvent event(String type) {
        ShipBehaviorEvent event = new ShipBehaviorEvent();
        event.setBehaviorType(type);
        event.setMmsi("413000001");
        event.setTargetMmsi("ALONGSIDE".equals(type) ? "413000002" : null);
        event.setStartTime("2026-06-01 00:00:00");
        event.setEndTime("2026-06-01 02:00:00");
        event.setRuleVersion("ALONGSIDE".equals(type)
                ? "ALONGSIDE_V3_INSURANCE" : "LOITERING_V2_INSURANCE");
        return event;
    }

    private List<TrajectoryPoint> track(String mmsi, double lon) {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0);
        for (int i = 0; i <= 12; i++) {
            result.add(new TrajectoryPoint(mmsi, start.plusMinutes(i * 10L),
                    lon, 30.0 + i * 0.001, 2.0, 0.0, "0"));
        }
        return result;
    }

    private abstract static class StubSceneService extends MaritimeSceneService {
        private StubSceneService() { super(null, MaritimeScenePolicy.defaults("SHADOW")); }

        MaritimeSceneDecision decision(MaritimeSceneClass scene, SceneConfidence confidence) {
            return new MaritimeSceneDecision(scene, confidence,
                    MaritimeSceneEvidence.unavailable("test"), 20.0, null,
                    "phase1-global-base-v1", "test-fingerprint",
                    ShadowRulesConfig.SCENE_POLICY_VERSION, false);
        }
    }

    private static final class CoordinateSceneService extends StubSceneService {
        @Override
        public MaritimeSceneDecision classify(double longitude, double latitude) {
            return decision(Math.abs(longitude) < 0.1
                    ? MaritimeSceneClass.INLAND_RIVER : MaritimeSceneClass.HIGH_SEAS,
                    SceneConfidence.HIGH);
        }
    }

    private static final class OpenWaterSceneService extends StubSceneService {
        @Override
        public MaritimeSceneDecision classify(double longitude, double latitude) {
            return decision(MaritimeSceneClass.HIGH_SEAS, SceneConfidence.HIGH);
        }
    }

    private static final class UnknownSceneService extends StubSceneService {
        @Override
        public MaritimeSceneDecision classify(double longitude, double latitude) {
            return decision(MaritimeSceneClass.SCENE_UNKNOWN, SceneConfidence.UNKNOWN);
        }
    }
}
