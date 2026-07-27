package com.hifleet.service;

import com.hifleet.service.LoiteringV3EventReconciler.Candidate;
import com.hifleet.service.LoiteringV3EventReconciler.ExistingEvent;
import com.hifleet.service.LoiteringV3EventReconciler.Reconciliation;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class LoiteringV3EventReconcilerTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 7, 1, 0, 0);
    private final LoiteringV3EventReconciler reconciler = new LoiteringV3EventReconciler();

    @Test
    public void overlappingCandidateReusesKeyAndExpandsBoundary() {
        ExistingEvent existing = existing("event-a", 0, 240, 121.0, 31.0);
        Candidate candidate = candidate(120, 360, 121.005, 31.0);

        Reconciliation result = reconciler.reconcile(candidate,
                Collections.singletonList(existing));

        assertEquals(LoiteringV3EventReconciler.ACTION_MERGE, result.getAction());
        assertEquals("event-a", result.getEventKey());
        assertEquals(START, result.getStartTime());
        assertEquals(START.plusMinutes(360), result.getEndTime());
    }

    @Test
    public void smallOverlapBelowHalfDoesNotMerge() {
        ExistingEvent existing = existing("event-a", 0, 240, 121.0, 31.0);
        Candidate candidate = candidate(225, 345, 121.0, 31.0);

        Reconciliation result = reconciler.reconcile(candidate,
                Collections.singletonList(existing));

        assertEquals(LoiteringV3EventReconciler.ACTION_CREATE, result.getAction());
        assertNotEquals("event-a", result.getEventKey());
    }

    @Test
    public void nonOverlappingEventsWithinThirtyMinutesMerge() {
        ExistingEvent existing = existing("event-a", 0, 120, 121.0, 31.0);
        Candidate candidate = candidate(150, 270, 121.0, 31.0);

        Reconciliation result = reconciler.reconcile(candidate,
                Collections.singletonList(existing));

        assertEquals(LoiteringV3EventReconciler.ACTION_MERGE, result.getAction());
        assertEquals("event-a", result.getEventKey());
    }

    @Test
    public void centerDistanceOverOneNauticalMileDoesNotMerge() {
        ExistingEvent existing = existing("event-a", 0, 240, 121.0, 31.0);
        Candidate candidate = candidate(120, 360, 121.05, 31.0);

        Reconciliation result = reconciler.reconcile(candidate,
                Collections.singletonList(existing));

        assertEquals(LoiteringV3EventReconciler.ACTION_CREATE, result.getAction());
    }

    @Test
    public void invalidExistingCenterDoesNotMerge() {
        ExistingEvent existing = existing("event-a", 0, 240, Double.NaN, 31.0);

        Reconciliation result = reconciler.reconcile(candidate(120, 360, 121.0, 31.0),
                Collections.singletonList(existing));

        assertEquals(LoiteringV3EventReconciler.ACTION_CREATE, result.getAction());
    }

    @Test
    public void differentRuleVersionDoesNotMerge() {
        ExistingEvent existing = new ExistingEvent("event-a", "413193000", "LOITERING_V2",
                START, START.plusMinutes(240), 121.0, 31.0);

        Reconciliation result = reconciler.reconcile(candidate(120, 360, 121.0, 31.0),
                Collections.singletonList(existing));

        assertEquals(LoiteringV3EventReconciler.ACTION_CREATE, result.getAction());
    }

    @Test
    public void createKeyIsStableWhenOnlyCandidateEndChanges() {
        Reconciliation first = reconciler.reconcile(candidate(0, 120, 121.0, 31.0),
                Collections.<ExistingEvent>emptyList());
        Reconciliation extended = reconciler.reconcile(candidate(0, 240, 121.0, 31.0),
                Collections.<ExistingEvent>emptyList());

        assertEquals(LoiteringV3EventReconciler.ACTION_CREATE, first.getAction());
        assertEquals(first.getEventKey(), extended.getEventKey());
    }

    @Test
    public void bestTemporalMatchWinsIndependentOfInputOrder() {
        ExistingEvent weak = existing("event-weak", 0, 240, 121.0, 31.0);
        ExistingEvent strong = existing("event-strong", 100, 300, 121.0, 31.0);
        Candidate candidate = candidate(120, 300, 121.0, 31.0);

        Reconciliation result = reconciler.reconcile(candidate, Arrays.asList(weak, strong));

        assertEquals("event-strong", result.getEventKey());
    }

    private Candidate candidate(int startMinute, int endMinute, double lon, double lat) {
        return new Candidate("413193000", "LOITERING_V3_SHADOW",
                START.plusMinutes(startMinute), START.plusMinutes(endMinute), lon, lat);
    }

    private ExistingEvent existing(String key, int startMinute, int endMinute,
                                   double lon, double lat) {
        return new ExistingEvent(key, "413193000", "LOITERING_V3_SHADOW",
                START.plusMinutes(startMinute), START.plusMinutes(endMinute), lon, lat);
    }
}
