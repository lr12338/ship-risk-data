package com.hifleet.behaviorplatform;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class BehaviorRunOrchestratorTest {
    @Test
    public void sameCandidateProducesControlShadowAndFilteredResultWithOneSceneRead() {
        ShipBehaviorEvent event = new ShipBehaviorEvent();
        event.setBehaviorType("LOITERING"); event.setMmsi("413000001");
        event.setStartTime("2026-01-01 00:00:00"); event.setEndTime("2026-01-01 04:00:00");
        event.setEventSubType("SLOW_LOITERING"); event.setConfidenceLevel("HIGH");
        ShipBehaviorTrace trace = new ShipBehaviorTrace(event, Collections.emptyList(), Collections.emptyList());
        final AtomicInteger sceneReads = new AtomicInteger();
        BehaviorTraceSceneEvaluator evaluator = value -> {
            sceneReads.incrementAndGet();
            return SceneEvaluation.rejectInland(1.0);
        };
        RuleSetDefinition rules = new RuleSetDefinition("loitering-v1", 1, false,
                Arrays.asList(new RuleDefinition("duration", "1", 240, "min")));
        rules.validateAndHash();
        BehaviorOrchestrationResult result = new BehaviorRunOrchestrator().evaluate(
                BehaviorType.LOITERING, SceneMode.FILTER, rules, Arrays.asList(trace), evaluator);
        assertEquals(1, sceneReads.get());
        assertEquals(1, result.getComparison().getControlCandidateCount());
        assertEquals(1, result.getComparison().getRejectedByInlandCount());
        assertEquals("REJECTED", result.getAuditResults().get(0).getFilteredDisposition());
    }
}
