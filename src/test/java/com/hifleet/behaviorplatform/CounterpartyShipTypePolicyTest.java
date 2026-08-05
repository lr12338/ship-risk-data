package com.hifleet.behaviorplatform;

import com.hifleet.service.ShipTypeClassifier;
import org.junit.Test;

import static org.junit.Assert.*;

public class CounterpartyShipTypePolicyTest {
    @Test
    public void primaryIsFailClosedAndExtendedCounterpartyIsInterpretive() {
        ShipTypePolicyEvaluator evaluator = new ShipTypePolicyEvaluator(new ShipTypeClassifier());
        assertFalse(evaluator.primaryAllowed(null, PrimaryShipTypePolicy.MERCHANT_STRICT));
        CounterpartyTypeEvidence tug = evaluator.counterparty("tug",
                CounterpartyShipTypePolicy.COMMERCIAL_EXTENDED,
                UnknownShipTypeAction.REVIEW_REQUIRED);
        assertTrue(tug.isAllowed());
        assertTrue(tug.isReviewRequired());
        assertTrue(tug.getRiskContextTags().contains("NORMAL_TOWING_POSSIBLE"));
        assertFalse(tug.getRiskContextTags().contains("ILLEGAL_ACTIVITY"));
    }

    @Test
    public void unknownCounterpartyCannotEnterFormalFlow() {
        CounterpartyTypeEvidence value = new ShipTypePolicyEvaluator(new ShipTypeClassifier())
                .counterparty(null, CounterpartyShipTypePolicy.ALL_KNOWN_SHIPS,
                        UnknownShipTypeAction.ALLOW_LOW_CONFIDENCE_SHADOW_ONLY);
        assertTrue(value.isAllowed());
        assertTrue(value.isShadowOnly());
        assertFalse(value.isFormalEligible());
    }

    @Test
    public void extendedPolicyChangesSupplyCandidateCoverage() {
        ShipTypePolicyEvaluator evaluator = new ShipTypePolicyEvaluator(new ShipTypeClassifier());
        assertFalse(evaluator.counterparty("补给服务船", CounterpartyShipTypePolicy.STRICT_SAME_AS_PRIMARY,
                UnknownShipTypeAction.REJECT).isAllowed());
        CounterpartyTypeEvidence extended = evaluator.counterparty("补给服务船",
                CounterpartyShipTypePolicy.COMMERCIAL_EXTENDED, UnknownShipTypeAction.REVIEW_REQUIRED);
        assertTrue(extended.isAllowed());
        assertTrue(extended.isReviewRequired());
        assertNotNull(extended.getReviewReason());
    }
}
