package com.hifleet.regionbehavior;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShipTypePolicyTest {
    @Test
    public void oilPolicyIsNarrowAndUnknownFailsClosed() {
        assertTrue(ShipTypePolicy.OIL_TANKER_ONLY.accepts("Crude Oil Tanker"));
        assertFalse(ShipTypePolicy.OIL_TANKER_ONLY.accepts("Chemical Tanker"));
        assertFalse(ShipTypePolicy.OIL_TANKER_ONLY.accepts("Container Ship"));
        assertFalse(ShipTypePolicy.OIL_TANKER_ONLY.accepts(null));
    }

    @Test
    public void broaderPoliciesAreExplicit() {
        assertTrue(ShipTypePolicy.LIQUID_CARGO_ONLY.accepts("LNG Carrier"));
        assertTrue(ShipTypePolicy.LIQUID_AND_CONTAINER.accepts("Container Ship"));
        assertTrue(ShipTypePolicy.ALL_MERCHANT.accepts("Bulk Carrier"));
    }
}
