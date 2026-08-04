package com.hifleet.tankeropensea;

import com.hifleet.regionbehavior.ShadowRulesConfig;
import com.hifleet.trajectory.TrajectoryPoint;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LoiteringShapeEvaluator {
    private final ShadowRulesConfig config;

    public LoiteringShapeEvaluator(ShadowRulesConfig config) {
        this.config = config;
    }

    public Evaluation evaluate(List<TrajectoryPoint> source) {
        List<TrajectoryPoint> points = validSorted(source);
        RuleResult strict = new TankerOpenSeaLoiteringRule(TankerOpenSeaLoiteringRule.Config.defaults())
                .evaluate(points, ShipEligibilityDecision.eligible(StandardShipType.TANKER_OIL,
                                "SHADOW_INPUT_ELIGIBLE", "SHADOW"),
                        ExclusionDecision.accepted("SHADOW", Collections.<String, Integer>emptyMap()));
        Map<String, Object> values = new LinkedHashMap<String, Object>(strict.getFeatures());
        TimeStats time = timeStats(points, config.getSceneGapCapMinutes());
        values.put("durationMinutes", time.eventSpanMinutes);
        values.put("stationaryTimeRatio", time.stationaryRatio);
        values.put("slowSpeedTimeRatio", time.slowRatio);
        values.put("maneuverSpeedTimeRatio", time.maneuverRatio);
        values.put("trackCoverageRatio", time.coverageRatio);
        values.put("maxAisGapMinutes", time.maxGapMinutes);
        values.put("netDisplacementNm", number(values, "startEndDistanceNm"));
        values.put("activityRadiusNm", number(values, "radiusNm"));
        values.put("straightTransitScore", straightScore(values));
        values.put("strictRuleAccepted", strict.isAccepted());
        values.put("strictRuleReason", strict.getReason());
        List<String> evidenceNames = evidence(values);
        LoiteringShapeEvidence shapeEvidence = new LoiteringShapeEvidence(values, evidenceNames);
        return new Evaluation(decide(shapeEvidence, values), shapeEvidence);
    }

    private LoiteringShapeDecision decide(LoiteringShapeEvidence evidence, Map<String, Object> values) {
        if (evidence.getDurationMinutes() < config.getLoiteringMinDurationMinutes()
                || evidence.getTrackCoverageRatio() < config.getLoiteringMinTrackCoverage()
                || evidence.getMaxAisGapMinutes() > 30.0) return LoiteringShapeDecision.DATA_INCOMPLETE;
        if (number(values, "speedP50") <= 0.5 || evidence.getStationaryTimeRatio() >= 0.75) {
            return LoiteringShapeDecision.WOULD_REJECT_STATIONARY_WAITING;
        }
        double cumulativeTurn = number(values, "cumulativeTurnDegree");
        if (cumulativeTurn >= 45.0 && cumulativeTurn < 270.0
                && number(values, "headingReversalCount") < 1.0) {
            return LoiteringShapeDecision.WOULD_REJECT_SINGLE_TURN;
        }
        if (evidence.getStraightTransitScore() >= 0.75) {
            return LoiteringShapeDecision.WOULD_REJECT_STRAIGHT_TRANSIT;
        }
        boolean speedBranch = evidence.getSlowSpeedTimeRatio() >= 0.75
                || evidence.getManeuverSpeedTimeRatio() >= 0.75;
        if (speedBranch && evidence.getShapeEvidenceCount() >= config.getLoiteringMinShapeEvidenceCount()) {
            return LoiteringShapeDecision.SHAPE_CONFIRMED;
        }
        return LoiteringShapeDecision.INSUFFICIENT_SHAPE_EVIDENCE;
    }

    private List<String> evidence(Map<String, Object> values) {
        List<String> result = new ArrayList<String>();
        if (number(values, "startEndDistanceNm") <= 0.75) result.add("START_END_CLOSE");
        if (number(values, "pathToDisplacementRatio") >= 2.5) result.add("HIGH_PATH_DISPLACEMENT_RATIO");
        if (number(values, "netDisplacementSpeed") <= Math.max(0.2,
                number(values, "averageSpeed") * 0.35)) result.add("LOW_NET_DISPLACEMENT_SPEED");
        if (number(values, "cumulativeTurnDegree") >= 270.0) result.add("HIGH_CUMULATIVE_TURN");
        if (number(values, "headingReversalCount") >= 1.0) result.add("MULTIPLE_HEADING_REVERSALS");
        if (number(values, "loopClosureScore") >= 0.70) result.add("LOOP_CLOSURE");
        if (number(values, "backtrackRatio") >= 0.40) result.add("HIGH_BACKTRACK_RATIO");
        if (number(values, "radiusNm") > 0.0 && number(values, "radiusNm") <= 3.0) {
            result.add("BOUNDED_ACTIVITY_RADIUS");
        }
        return result;
    }

    private double straightScore(Map<String, Object> values) {
        double pathRatio = number(values, "pathToDisplacementRatio");
        double turn = number(values, "cumulativeTurnDegree");
        double reversals = number(values, "headingReversalCount");
        if (pathRatio > 0.0 && pathRatio < 1.5 && turn < 270.0 && reversals == 0.0) return 1.0;
        return 0.0;
    }

    private TimeStats timeStats(List<TrajectoryPoint> points, long gapCapMinutes) {
        if (points.size() < 2) return new TimeStats(0, 0, 0, 0, 0, 0);
        double span = Duration.between(points.get(0).getTime(),
                points.get(points.size() - 1).getTime()).toMillis() / 60_000.0;
        double observed = 0.0;
        double stationary = 0.0;
        double slow = 0.0;
        double maneuver = 0.0;
        double maxGap = 0.0;
        for (int i = 0; i + 1 < points.size(); i++) {
            double gap = Math.max(0.0, Duration.between(points.get(i).getTime(),
                    points.get(i + 1).getTime()).toMillis() / 60_000.0);
            maxGap = Math.max(maxGap, gap);
            double weight = Math.min(gap, gapCapMinutes);
            observed += weight;
            double speed = points.get(i).getSpeed();
            if (speed <= 0.5) stationary += weight;
            if (speed >= 0.5 && speed <= 3.0) slow += weight;
            if (speed >= 0.5 && speed <= 5.0) maneuver += weight;
        }
        double denominator = observed <= 0.0 ? 1.0 : observed;
        return new TimeStats(span, span <= 0.0 ? 0.0 : observed / span,
                stationary / denominator, slow / denominator, maneuver / denominator, maxGap);
    }

    private double number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    private List<TrajectoryPoint> validSorted(List<TrajectoryPoint> source) {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        if (source != null) {
            for (TrajectoryPoint point : source) {
                if (point != null && point.getTime() != null
                        && Double.isFinite(point.getLon()) && Double.isFinite(point.getLat())
                        && point.getLon() >= -180.0 && point.getLon() <= 180.0
                        && point.getLat() >= -90.0 && point.getLat() <= 90.0
                        && Double.isFinite(point.getSpeed()) && point.getSpeed() >= 0.0
                        && point.getSpeed() <= 80.0 && Double.isFinite(point.getCourse())
                        && point.getCourse() >= 0.0 && point.getCourse() <= 360.0) result.add(point);
            }
        }
        Collections.sort(result, new Comparator<TrajectoryPoint>() {
            @Override
            public int compare(TrajectoryPoint left, TrajectoryPoint right) {
                return left.getTime().compareTo(right.getTime());
            }
        });
        return result;
    }

    public static final class Evaluation {
        private final LoiteringShapeDecision decision;
        private final LoiteringShapeEvidence evidence;

        Evaluation(LoiteringShapeDecision decision, LoiteringShapeEvidence evidence) {
            this.decision = decision;
            this.evidence = evidence;
        }

        public LoiteringShapeDecision getDecision() { return decision; }
        public LoiteringShapeEvidence getEvidence() { return evidence; }
    }

    private static final class TimeStats {
        private final double eventSpanMinutes;
        private final double coverageRatio;
        private final double stationaryRatio;
        private final double slowRatio;
        private final double maneuverRatio;
        private final double maxGapMinutes;

        private TimeStats(double eventSpanMinutes, double coverageRatio, double stationaryRatio,
                          double slowRatio, double maneuverRatio, double maxGapMinutes) {
            this.eventSpanMinutes = eventSpanMinutes;
            this.coverageRatio = coverageRatio;
            this.stationaryRatio = stationaryRatio;
            this.slowRatio = slowRatio;
            this.maneuverRatio = maneuverRatio;
            this.maxGapMinutes = maxGapMinutes;
        }
    }
}
