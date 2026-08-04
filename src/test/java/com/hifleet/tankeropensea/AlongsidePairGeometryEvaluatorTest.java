package com.hifleet.tankeropensea;

import com.hifleet.regionbehavior.ShadowRulesConfig;
import com.hifleet.regionbehavior.TrajectoryPairSynchronizer;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AlongsidePairGeometryEvaluatorTest {
    @Test
    public void stablePortAndStarboardTracksAreConfirmed() {
        assertEquals(AlongsideGeometryDecision.SIDE_BY_SIDE_CONFIRMED,
                evaluate(0.002, 0.0).getDecision());
        assertEquals(AlongsideGeometryDecision.SIDE_BY_SIDE_CONFIRMED,
                evaluate(-0.002, 0.0).getDecision());
    }

    @Test
    public void followingAndCrossingAreSeparated() {
        assertEquals(AlongsideGeometryDecision.WOULD_REJECT_FOLLOWING,
                evaluate(0.0, 0.002).getDecision());
        assertEquals(AlongsideGeometryDecision.WOULD_REJECT_CROSSING,
                evaluateWithTargetCourse(0.002, 0.0, 90.0).getDecision());
    }

    @Test
    public void overtakingRelativeSpeedHeadingAndDistanceInstabilityAreSeparated() {
        assertEquals(AlongsideGeometryDecision.WOULD_REJECT_OVERTAKING,
                evaluate(0.0, -0.002).getDecision());
        assertEquals(AlongsideGeometryDecision.WOULD_REJECT_RELATIVE_SPEED,
                evaluateCustom(0.002, 0.0, 0.0, 2.8, false).getDecision());
        assertEquals(AlongsideGeometryDecision.WOULD_REJECT_HEADING_DIFFERENCE,
                evaluateCustom(0.002, 0.0, 20.0, 2.0, false).getDecision());
        assertEquals(AlongsideGeometryDecision.WOULD_REJECT_UNSTABLE_DISTANCE,
                evaluateCustom(0.002, 0.0, 0.0, 2.0, true).getDecision());
    }

    @Test
    public void insufficientSynchronizedPointsFailClosed() {
        List<TrajectoryPoint> own = track("A", 0.0, 0.0, 0.0);
        List<TrajectoryPoint> target = track("B", 0.002, 0.0, 0.0).subList(0, 3);
        TrajectoryPairSynchronizer.Result synchronizedTrack =
                new TrajectoryPairSynchronizer(3).synchronize(own, target);

        assertEquals(AlongsideGeometryDecision.INSUFFICIENT_SYNCHRONIZED_POINTS,
                new AlongsidePairGeometryEvaluator(new ShadowRulesConfig())
                        .evaluate(synchronizedTrack, 200.0, 200.0).getDecision());
    }

    @Test
    public void stationaryPairIsDiagnosticOnly() {
        assertEquals(AlongsideGeometryDecision.STATIONARY_ALONGSIDE_CANDIDATE,
                evaluateCustom(0.002, 0.0, 0.0, 0.1, false, 0.1).getDecision());
    }

    private AlongsidePairGeometryEvaluator.Evaluation evaluate(double targetLonOffset,
                                                                 double targetLatOffset) {
        return evaluateWithTargetCourse(targetLonOffset, targetLatOffset, 0.0);
    }

    private AlongsidePairGeometryEvaluator.Evaluation evaluateWithTargetCourse(
            double targetLonOffset, double targetLatOffset, double targetCourse) {
        return evaluateCustom(targetLonOffset, targetLatOffset, targetCourse, 2.0, false);
    }

    private AlongsidePairGeometryEvaluator.Evaluation evaluateCustom(
            double targetLonOffset, double targetLatOffset, double targetCourse,
            double targetSpeed, boolean unstableDistance) {
        return evaluateCustom(targetLonOffset, targetLatOffset, targetCourse,
                targetSpeed, unstableDistance, 2.0);
    }

    private AlongsidePairGeometryEvaluator.Evaluation evaluateCustom(
            double targetLonOffset, double targetLatOffset, double targetCourse,
            double targetSpeed, boolean unstableDistance, double ownSpeed) {
        List<TrajectoryPoint> target = track("B", targetLonOffset, targetLatOffset,
                targetCourse, targetSpeed, unstableDistance);
        TrajectoryPairSynchronizer.Result synchronizedTrack = new TrajectoryPairSynchronizer(3)
                .synchronize(track("A", 0.0, 0.0, 0.0, ownSpeed, false),
                        target);
        return new AlongsidePairGeometryEvaluator(new ShadowRulesConfig())
                .evaluate(synchronizedTrack, 200.0, 200.0);
    }

    private List<TrajectoryPoint> track(String mmsi, double lonOffset,
                                        double latOffset, double course) {
        return track(mmsi, lonOffset, latOffset, course, 2.0, false);
    }

    private List<TrajectoryPoint> track(String mmsi, double lonOffset,
                                        double latOffset, double course,
                                        double speed, boolean unstableDistance) {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0);
        for (int i = 0; i < 10; i++) {
            result.add(new TrajectoryPoint(mmsi, start.plusMinutes(i * 5L),
                    120.0 + lonOffset * (unstableDistance ? (i % 2 == 0 ? 0.5 : 2.0) : 1.0),
                    20.0 + latOffset + i * 0.001, speed, course, "0"));
        }
        return result;
    }
}
