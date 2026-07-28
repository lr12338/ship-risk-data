package com.hifleet.service;

import com.hifleet.domain.TargetShip;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TargetShipSelectorTest {

    @Test
    public void selectionIsDeterministicAndAssignsSequence() {
        List<TargetShip> candidates = Arrays.asList(
                new TargetShip("413000003", null, "C", "货船", null),
                new TargetShip("413000001", null, "A", "货船", null),
                new TargetShip("413000002", null, "B", "货船", null));

        List<TargetShip> first = TargetShipSelector.select(
                "merchant-50k-2026h1-v1", candidates, 2);
        List<TargetShip> second = TargetShipSelector.select(
                "merchant-50k-2026h1-v1", candidates, 2);

        assertEquals(first.get(0).getMmsi(), second.get(0).getMmsi());
        assertEquals(first.get(1).getMmsi(), second.get(1).getMmsi());
        assertEquals(1, first.get(0).getSeqNo());
        assertEquals(2, first.get(1).getSeqNo());
    }
}
