package com.hifleet.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ShipTypeClassifierTest {

    private final ShipTypeClassifier classifier = new ShipTypeClassifier();

    @Test
    public void recognizesPriorityMerchantTypes() {
        assertEquals(Boolean.TRUE, classifier.isMerchant("油轮"));
        assertEquals(Boolean.TRUE, classifier.isMerchant("散货船"));
        assertEquals(Boolean.TRUE, classifier.isMerchant("Container Ship"));
    }

    @Test
    public void rejectsNoiseTypesAndKeepsUnknownUnclassified() {
        assertEquals(Boolean.FALSE, classifier.isMerchant("渔船"));
        assertEquals(Boolean.FALSE, classifier.isMerchant("拖轮"));
        assertNull(classifier.isMerchant("未知类型船舶"));
    }

    @Test
    public void classifiesOwnShipEligibilityMatrix() {
        for (String type : new String[]{"原油轮", "成品油轮", "化学品船", "集装箱船", "散货船", "杂货船"}) {
            assertEquals(type, OwnShipEligibility.ELIGIBLE_MERCHANT, classifier.classifyOwnShip(type));
        }
        assertEquals(OwnShipEligibility.INELIGIBLE, classifier.classifyOwnShip("拖轮"));
        assertEquals(OwnShipEligibility.INELIGIBLE, classifier.classifyOwnShip("渔船"));
        assertEquals(OwnShipEligibility.UNKNOWN, classifier.classifyOwnShip(null));
    }

    @Test
    public void classifiesTargetRoleMatrixWithoutFilteringUnknown() {
        assertRole(TargetVesselRole.LARGE_MERCHANT, "大型油轮");
        assertRole(TargetVesselRole.LARGE_MERCHANT, "集装箱船");
        assertRole(TargetVesselRole.BUNKER_OR_SUPPLY, "补给船");
        assertRole(TargetVesselRole.BUNKER_OR_SUPPLY, "小型加油船");
        assertRole(TargetVesselRole.BARGE_OR_LIGHTER, "驳船");
        assertRole(TargetVesselRole.BARGE_OR_LIGHTER, "油驳");
        assertRole(TargetVesselRole.TUG_OR_SERVICE, "拖轮");
        assertRole(TargetVesselRole.TUG_OR_SERVICE, "工作艇");
        assertRole(TargetVesselRole.FISHING_OR_SMALL_CRAFT, "渔船");
        assertRole(TargetVesselRole.FISHING_OR_SMALL_CRAFT, "小艇");
        assertRole(TargetVesselRole.UNKNOWN, null);
        assertRole(TargetVesselRole.UNKNOWN, "无法识别文本");
    }

    private void assertRole(TargetVesselRole expected, String type) {
        assertEquals(type, expected, classifier.classifyTargetShip(type));
    }
}
