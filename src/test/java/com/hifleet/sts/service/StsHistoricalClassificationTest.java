package com.hifleet.sts.service;

import com.alibaba.fastjson.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class StsHistoricalClassificationTest {
    @Test public void formalTankerEvidenceSupportsCandidateWithoutClaimingTransfer() {
        for (String type : new String[]{"Oil tanker", "Petroleum tanker", "Chemical tanker", "液货船", "石油化学品船"}) {
            JSONObject evidence = classify(type, null, "OFFSHORE", false, false);
            assertEquals(type, "STS_CANDIDATE", evidence.getString("stsClassification"));
            assertEquals("OBSERVED_ALONGSIDE", evidence.getString("physicalEventType"));
            assertEquals("FORMAL", evidence.getString("physicalSupportLevel"));
            assertTrue(evidence.getBooleanValue("reviewQueueEligible"));
        }
    }

    @Test public void formalServiceNeverPromotesEvenWithTankerMetadata() {
        for (String type : new String[]{"Tug", "Pilot", "Service vessel", "Work boat", "Bunker tanker", "Supply vessel", "拖轮", "工程及服务船"}) {
            JSONObject evidence = classify(type, "原油船", "OFFSHORE", false, false);
            assertEquals(type, "PHYSICAL_ONLY", evidence.getString("stsClassification"));
            assertEquals("NOT_PROMOTED", evidence.getString("stsPromotionStatus"));
            assertFalse(evidence.getBooleanValue("reviewQueueEligible"));
            assertTrue(evidence.getJSONArray("contextEvidence").contains("SERVICE_INTERACTION"));
        }
    }

    @Test public void formalUnknownBargeAndGenericMerchantRequireReview() {
        for (String type : new String[]{null, "UNKNOWN", "Barge", "Lighter", "Bulk carrier", "Cargo", "Container", "LARGE_MERCHANT"}) {
            assertEquals("STS_REVIEW_REQUIRED", classify(type, null, "OFFSHORE", false, false).getString("stsClassification"));
        }
        assertEquals("STS_REVIEW_REQUIRED", classify("Container", "原油船", "OFFSHORE", false, false).getString("stsClassification"));
    }

    @Test public void knownPortStationarySuppressesWhileUnknownStaysUnknown() {
        assertEquals("PHYSICAL_ONLY", classify("Oil tanker", null, "PORT_OPERATIONAL", true, false).getString("stsClassification"));
        assertEquals("PHYSICAL_ONLY", classify("Oil tanker", null, "BERTH", true, false).getString("stsClassification"));
        JSONObject unknown = classify("Oil tanker", null, "UNKNOWN", false, false);
        assertEquals("UNKNOWN", unknown.getString("sceneType"));
        assertFalse(unknown.containsKey("nearPort"));
        assertFalse(unknown.containsKey("offshore"));
        assertEquals("STS_CANDIDATE", classify("Oil tanker", null, "ANCHORAGE", false, false).getString("stsClassification"));
    }

    @Test public void weakPhysicalEvidenceNeverPromotesForAnyRole() {
        for (String type : new String[]{"Oil tanker", "Tug", "UNKNOWN", "Container", "Barge"}) {
            JSONObject evidence = classify(type, null, "OFFSHORE", false, true);
            assertEquals(type, "PHYSICAL_ONLY", evidence.getString("stsClassification"));
            assertEquals("WEAK", evidence.getString("physicalSupportLevel"));
            assertEquals("NOT_PROMOTED", evidence.getString("stsPromotionStatus"));
            assertFalse(evidence.getBooleanValue("reviewQueueEligible"));
        }
    }

    private JSONObject classify(String type, String minotype, String scene,
                                boolean stationary, boolean weak) {
        JSONObject e = new JSONObject();
        e.put("targetShipTypeRaw", type);
        e.put("targetShipMinTypeRaw", minotype);
        e.put("sceneType", scene);
        e.put("sceneDataAvailable", !"UNKNOWN".equals(scene));
        e.put("behaviorMode", stationary ? "STATIONARY_SIDE_BY_SIDE_REVIEW" : "UNDERWAY_PARALLEL_COMPANIONSHIP");
        e.put("weakCandidate", weak);
        StsHistoricalClassification.classify(e);
        return e;
    }
}
