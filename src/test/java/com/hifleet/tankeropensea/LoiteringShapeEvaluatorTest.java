package com.hifleet.tankeropensea;

import com.hifleet.regionbehavior.ShadowRulesConfig;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LoiteringShapeEvaluatorTest {
    @Test
    public void closedLoopIsConfirmed() {
        assertEquals(LoiteringShapeDecision.SHAPE_CONFIRMED,
                evaluator().evaluate(loop()).getDecision());
    }

    @Test
    public void straightLowSpeedTransitIsRejected() {
        assertEquals(LoiteringShapeDecision.WOULD_REJECT_STRAIGHT_TRANSIT,
                evaluator().evaluate(straight(2.0)).getDecision());
    }

    @Test
    public void stationaryWaitingIsReviewDecision() {
        assertEquals(LoiteringShapeDecision.WOULD_REJECT_STATIONARY_WAITING,
                evaluator().evaluate(straight(0.1)).getDecision());
    }

    @Test
    public void backAndForthTrackIsConfirmed() {
        assertEquals(LoiteringShapeDecision.SHAPE_CONFIRMED,
                evaluator().evaluate(backAndForth()).getDecision());
    }

    @Test
    public void singleTurnAndSparseTrackFailClosed() {
        assertEquals(LoiteringShapeDecision.WOULD_REJECT_SINGLE_TURN,
                evaluator().evaluate(singleTurn()).getDecision());
        List<TrajectoryPoint> sparse = straight(2.0);
        sparse.set(12, new TrajectoryPoint("A", LocalDateTime.of(2026, 6, 1, 3, 0),
                120.0, 20.012, 2.0, 0.0, "0"));
        assertEquals(LoiteringShapeDecision.DATA_INCOMPLETE,
                evaluator().evaluate(sparse).getDecision());
    }

    private LoiteringShapeEvaluator evaluator() {
        return new LoiteringShapeEvaluator(new ShadowRulesConfig());
    }

    private List<TrajectoryPoint> straight(double speed) {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0);
        for (int i = 0; i <= 24; i++) {
            result.add(new TrajectoryPoint("A", start.plusMinutes(i * 5L),
                    120.0, 20.0 + i * 0.001, speed, 0.0, "0"));
        }
        return result;
    }

    private List<TrajectoryPoint> loop() {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0);
        for (int i = 0; i <= 24; i++) {
            double angle = 2.0 * Math.PI * i / 24.0;
            result.add(new TrajectoryPoint("A", start.plusMinutes(i * 5L),
                    120.0 + Math.cos(angle) * 0.01,
                    20.0 + Math.sin(angle) * 0.01,
                    2.0, (Math.toDegrees(angle) + 90.0) % 360.0, "0"));
        }
        return result;
    }

    private List<TrajectoryPoint> backAndForth() {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0);
        for (int i = 0; i <= 24; i++) {
            int phase = i <= 12 ? i : 24 - i;
            double course = i <= 12 ? 90.0 : 270.0;
            result.add(new TrajectoryPoint("A", start.plusMinutes(i * 5L),
                    120.0 + phase * 0.001, 20.0, 2.0, course, "0"));
        }
        return result;
    }

    private List<TrajectoryPoint> singleTurn() {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0);
        for (int i = 0; i <= 24; i++) {
            boolean east = i <= 12;
            result.add(new TrajectoryPoint("A", start.plusMinutes(i * 5L),
                    120.0 + (east ? i : 12) * 0.001,
                    20.0 + (east ? 0 : i - 12) * 0.001,
                    2.0, east ? 90.0 : 0.0, "0"));
        }
        return result;
    }
}
