package com.hifleet.behaviorplatform;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class BehaviorSceneEvaluatorTest {
    @Test
    public void offDoesNotReadSceneAndShadowKeepsControlAcceptance() {
        final AtomicInteger calls = new AtomicInteger();
        SceneEvidenceProvider provider = key -> {
            calls.incrementAndGet();
            return SceneEvaluation.rejectInland(1.0);
        };
        List<BehaviorCandidate> candidates = Arrays.asList(new BehaviorCandidate("a", true));
        BehaviorComparison off = new BehaviorSceneEvaluator(provider).evaluate(candidates, SceneMode.OFF);
        assertEquals(0, calls.get());
        assertEquals(1, off.getControlCandidateCount());
        BehaviorComparison shadow = new BehaviorSceneEvaluator(provider).evaluate(candidates, SceneMode.SHADOW);
        assertEquals(1, calls.get());
        assertEquals(1, shadow.getRetainedCount());
        assertEquals(0, shadow.getRejectedByInlandCount());
    }

    @Test
    public void filterRejectsInlandButRoutesUnknownAndConflictToReview() {
        SceneEvidenceProvider provider = key -> "inland".equals(key)
                ? SceneEvaluation.rejectInland(1.0)
                : "conflict".equals(key) ? SceneEvaluation.conflict(1.0)
                : SceneEvaluation.unknown(0.2);
        BehaviorComparison result = new BehaviorSceneEvaluator(provider).evaluate(Arrays.asList(
                new BehaviorCandidate("inland", true), new BehaviorCandidate("conflict", true),
                new BehaviorCandidate("unknown", true)), SceneMode.FILTER);
        assertEquals(1, result.getRejectedByInlandCount());
        assertEquals(2, result.getReviewCount());
        assertEquals(0, result.getRetainedCount());
        assertEquals(1, result.getUnknownCount());
        assertEquals(1, result.getConflictCount());
    }
}
