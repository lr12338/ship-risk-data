package com.hifleet.tankeropensea;

import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShipEligibilityPolicyTest {

    private final ShipEligibilityPolicy policy = new ShipEligibilityPolicy();

    @Test
    public void explicitLiquidCargoAndContainerShipsAreEligible() {
        assertEligible("油轮", "成品油轮", StandardShipType.TANKER_OIL);
        assertEligible("化学品船", "chemical tanker", StandardShipType.TANKER_CHEMICAL);
        assertEligible("液化气船", "LPG", StandardShipType.TANKER_GAS);
        assertEligible("tanker", null, StandardShipType.TANKER_UNSPECIFIED);
        assertEligible("集装箱船", "container", StandardShipType.CONTAINER);
    }

    @Test
    public void liquidSubtypeConflictIsEligibleButCrossCategoryConflictIsRejected() {
        assertEligible("油轮", "化学品船", StandardShipType.TANKER_UNSPECIFIED);
        assertRejected("拖轮", "港作拖轮", StandardShipType.TUG);
        assertRejected(null, null, StandardShipType.UNKNOWN);
        assertRejected("油轮", "集装箱船", StandardShipType.CONFLICT);
        assertRejected("集装箱船", "拖轮", StandardShipType.CONFLICT);

        ShipEligibilityDecision failed = policy.lookupFailed("metadata query failed");
        assertFalse(failed.isEligible());
        assertEquals(StandardShipType.UNKNOWN, failed.getStandardShipType());
    }

    private void assertEligible(String type, String minorType, StandardShipType expected) {
        ShipEligibilityDecision decision = policy.evaluate(metadata(type, minorType));
        assertTrue(decision.getDecisionReason(), decision.isEligible());
        assertEquals(expected, decision.getStandardShipType());
    }

    private void assertRejected(String type, String minorType, StandardShipType expected) {
        ShipEligibilityDecision decision = policy.evaluate(metadata(type, minorType));
        assertFalse(decision.isEligible());
        assertEquals(expected, decision.getStandardShipType());
    }

    private ShipMetadata metadata(String type, String minorType) {
        return new ShipMetadata("123456789", "IMO1234567", "TEST",
                type, minorType, null, 250.0, 50000L,
                LocalDateTime.of(2026, 7, 1, 0, 0));
    }
}
