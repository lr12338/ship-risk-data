package com.hifleet.tankeropensea;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class TankerOpenSeaEventFactoryTest {
    @Test
    public void acceptedAlongsideEventUsesActualTypesAndDoesNotClaimOpenSea() {
        ListedTanker own = listed(StandardShipType.CONTAINER, "123456789");
        ShipMetadata target = metadata("987654321", "化学品船");
        ShipEligibilityDecision targetEligibility = ShipEligibilityDecision.eligible(
                StandardShipType.TANKER_CHEMICAL,
                "EXPLICIT_LIQUID_CARGO", "HIGH");

        ShipBehaviorTrace trace = TankerOpenSeaEventFactory.alongside(
                "alongside-liquid-container-200-202601-v2",
                own, target, targetEligibility, points("123456789"),
                points("987654321"), acceptedResult());
        ShipBehaviorEvent event = trace.getEvent();

        assertEquals(StandardShipType.CONTAINER.name(), event.getOwnShipType());
        assertEquals(StandardShipType.TANKER_CHEMICAL.name(),
                event.getTargetShipType());
        assertFalse(event.getNearPort());
        assertFalse(event.getNearAnchor());
        assertNull(event.getOpenSea());
        assertEquals("OUTSIDE_PORT_ANCHORAGE_BERTH",
                event.getExclusionReason());
    }

    private ListedTanker listed(StandardShipType type, String mmsi) {
        return new ListedTanker(1, metadata(mmsi, "集装箱船"),
                ShipEligibilityDecision.eligible(
                        type, "EXPLICIT_ALLOWED", "HIGH"));
    }

    private ShipMetadata metadata(String mmsi, String type) {
        return new ShipMetadata(mmsi, "IMO1234567", "TEST", type,
                type, null, 200.0, 50000L,
                LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private List<TrajectoryPoint> points(String mmsi) {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        return Arrays.asList(
                new TrajectoryPoint(mmsi, start,
                        120.0, 20.0, 2.0, 90.0, "under way"),
                new TrajectoryPoint(mmsi, start.plusMinutes(60),
                        120.01, 20.0, 2.0, 90.0, "under way"));
    }

    private RuleResult acceptedResult() {
        Map<String, Object> features = new LinkedHashMap<String, Object>();
        features.put("durationMinutes", 60L);
        features.put("distanceP10Nm", 0.1);
        features.put("distanceP50Nm", 0.15);
        features.put("distanceP90Nm", 0.2);
        Map<String, Object> exclusion = new LinkedHashMap<String, Object>();
        exclusion.put("portHit", false);
        exclusion.put("anchorageHit", false);
        exclusion.put("berthHit", false);
        exclusion.put("fingerprint",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        features.put("exclusionEvidence", exclusion);
        return RuleResult.accepted(features);
    }
}
