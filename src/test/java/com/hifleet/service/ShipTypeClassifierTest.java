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
}
