package com.hifleet.regionbehavior;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CombinedShadowDecisionEngineTest {
    private final CombinedShadowDecisionEngine engine = new CombinedShadowDecisionEngine();
    private final OperationalAreaContext outside =
            new OperationalAreaContext(false, false, false, false, true);

    @Test
    public void partialAndBoundaryHaveHighestPriority() {
        assertEquals("DATA_PARTIAL_FAIL_CLOSED", engine.decide(RegionMatchStatus.STRICT_CORE,
                RegionBehaviorStatus.PARTIAL, "OPEN_WATER_CONFIRMED", "SHAPE_CONFIRMED", outside)
                .getDecision());
        assertEquals("REVIEW_REGION_BOUNDARY", engine.decide(RegionMatchStatus.REGION_BOUNDARY_PARTIAL,
                RegionBehaviorStatus.COMPLETE, "OPEN_WATER_CONFIRMED", "SHAPE_CONFIRMED", outside)
                .getDecision());
    }

    @Test
    public void unavailableOperationalDataCannotBeTreatedAsOutside() {
        assertEquals("OPERATIONAL_AREA_REVIEW", engine.decide(RegionMatchStatus.STRICT_CORE,
                RegionBehaviorStatus.COMPLETE, "OPEN_WATER_CONFIRMED", "SIDE_BY_SIDE_CONFIRMED",
                OperationalAreaContext.unavailable()).getDecision());
    }

    @Test
    public void stationaryWaitingRemainsReviewAtCombinedLayer() {
        assertEquals("STATIONARY_WAITING_REVIEW", engine.decide(RegionMatchStatus.STRICT_CORE,
                RegionBehaviorStatus.COMPLETE, "OPEN_WATER_CONFIRMED",
                "WOULD_REJECT_STATIONARY_WAITING", outside).getDecision());
    }
}
