package com.hifleet.tankeropensea;

import com.hifleet.trajectory.TrajectoryPoint;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TankerOpenSeaAlongsideRule {
    public static final String RULE_VERSION = "ALONGSIDE_LIQUID_CONTAINER_V2";

    private final Config config;

    public TankerOpenSeaAlongsideRule(Config config) {
        this.config = config == null ? Config.defaults() : config;
    }

    public RuleResult evaluate(List<TrajectoryPoint> ownPoints,
                               List<TrajectoryPoint> targetPoints,
                               ShipEligibilityDecision ownEligibility,
                               ShipEligibilityDecision targetEligibility,
                               ExclusionDecision exclusionDecision,
                               ReplayCompleteness replayCompleteness) {
        return evaluate(ownPoints, targetPoints, ownEligibility, targetEligibility,
                null, null, exclusionDecision, replayCompleteness);
    }

    public RuleResult evaluate(List<TrajectoryPoint> ownPoints,
                               List<TrajectoryPoint> targetPoints,
                               ShipEligibilityDecision ownEligibility,
                               ShipEligibilityDecision targetEligibility,
                               ShipMetadata ownMetadata,
                               ShipMetadata targetMetadata,
                               ExclusionDecision exclusionDecision,
                               ReplayCompleteness replayCompleteness) {
        if (ownEligibility == null || !ownEligibility.isEligible()) {
            return RuleResult.rejected("OWN_SHIP_NOT_LIQUID_CARGO_OR_CONTAINER");
        }
        if (targetEligibility == null || !targetEligibility.isEligible()) {
            return RuleResult.rejected("TARGET_SHIP_NOT_LIQUID_CARGO_OR_CONTAINER");
        }
        if (exclusionDecision == null || exclusionDecision.isExcluded()) {
            return RuleResult.rejected("EXCLUSION_REJECTED:"
                    + (exclusionDecision == null
                    ? "MISSING" : exclusionDecision.getReason()));
        }
        if (replayCompleteness != ReplayCompleteness.COMPLETE
                && replayCompleteness != ReplayCompleteness.TRUNCATED_RETRY_SPLIT) {
            return RuleResult.rejected("REPLAY_INCOMPLETE:" + replayCompleteness);
        }
        List<TrajectoryPoint> own = valid(ownPoints);
        List<TrajectoryPoint> target = valid(targetPoints);
        if (own.size() < config.minPoints || target.size() < config.minPoints) {
            return RuleResult.rejected("INSUFFICIENT_SYNCHRONIZED_POINTS");
        }
        long durationMinutes = Duration.between(
                own.get(0).getTime(), own.get(own.size() - 1).getTime()).toMinutes();
        if (durationMinutes < config.minDurationMinutes) {
            return RuleResult.rejected("DURATION_TOO_SHORT");
        }

        List<Double> distances = new ArrayList<Double>();
        List<Double> relativeSpeeds = new ArrayList<Double>();
        List<Double> headingDifferences = new ArrayList<Double>();
        List<Double> absoluteAlongOffsets = new ArrayList<Double>();
        List<Double> absoluteCrossOffsets = new ArrayList<Double>();
        List<Double> relativeBearings = new ArrayList<Double>();
        int sideCount = 0;
        int leftCount = 0;
        int rightCount = 0;
        int speedAccepted = 0;

        for (TrajectoryPoint ownPoint : own) {
            TrajectoryPoint targetPoint = nearest(ownPoint, target);
            if (targetPoint == null) continue;
            double distance = GeoMath.distanceNm(ownPoint, targetPoint);
            double bearing = GeoMath.bearingDegree(ownPoint, targetPoint);
            double relativeBearing = GeoMath.signedDifference(bearing, ownPoint.getCourse());
            double relativeRadians = Math.toRadians(relativeBearing);
            double alongOffset = distance * Math.cos(relativeRadians);
            double crossOffset = distance * Math.sin(relativeRadians);
            boolean side = Math.abs(relativeBearing) >= config.minSideBearingDegree
                    && Math.abs(relativeBearing) <= config.maxSideBearingDegree;
            if (side) {
                sideCount++;
                if (relativeBearing < 0.0) leftCount++; else rightCount++;
            }
            if (inSpeedRange(ownPoint) && inSpeedRange(targetPoint)) speedAccepted++;
            distances.add(distance);
            relativeSpeeds.add(Math.abs(ownPoint.getSpeed() - targetPoint.getSpeed()));
            headingDifferences.add(GeoMath.circularDifference(
                    ownPoint.getCourse(), targetPoint.getCourse()));
            absoluteAlongOffsets.add(Math.abs(alongOffset));
            absoluteCrossOffsets.add(Math.abs(crossOffset));
            relativeBearings.add(relativeBearing);
        }
        if (distances.size() < config.minPoints) {
            return RuleResult.rejected("SYNCHRONIZED_COVERAGE_TOO_LOW");
        }

        double synchronizedPointRatio = distances.size() / (double) own.size();
        double speedRatio = speedAccepted / (double) distances.size();
        double sideBySideRatio = sideCount / (double) distances.size();
        double bearingStability = Math.max(leftCount, rightCount)
                / (double) Math.max(1, sideCount);
        double distanceP10 = GeoMath.percentile(distances, 0.10);
        double distanceP50 = GeoMath.percentile(distances, 0.50);
        double distanceP90 = GeoMath.percentile(distances, 0.90);
        double stableTolerance = Math.max(0.03, distanceP50 * 0.25);
        int stableDistances = 0;
        for (Double distance : distances) {
            if (Math.abs(distance - distanceP50) <= stableTolerance) stableDistances++;
        }
        double distanceStabilityRatio = stableDistances / (double) distances.size();
        double relativeSpeedP90 = GeoMath.percentile(relativeSpeeds, 0.90);
        double headingDifferenceP90 = GeoMath.percentile(headingDifferences, 0.90);
        double alongTrackOffsetP90 = GeoMath.percentile(absoluteAlongOffsets, 0.90);
        double crossTrackOffsetP50 = GeoMath.percentile(absoluteCrossOffsets, 0.50);
        double maxDistance = dynamicMaxDistanceNm(ownMetadata, targetMetadata);

        Map<String, Object> features = new LinkedHashMap<String, Object>();
        features.put("durationMinutes", durationMinutes);
        features.put("synchronizedPointRatio", synchronizedPointRatio);
        features.put("speedInRangeRatio", speedRatio);
        features.put("relativeBearingDegree", GeoMath.percentile(relativeBearings, 0.50));
        features.put("relativeBearingSide", rightCount >= leftCount ? "STARBOARD" : "PORT");
        features.put("relativeBearingStability", bearingStability);
        features.put("alongTrackOffsetNm", alongTrackOffsetP90);
        features.put("crossTrackOffsetNm", crossTrackOffsetP50);
        features.put("sideBySideRatio", sideBySideRatio);
        features.put("distanceP10Nm", distanceP10);
        features.put("distanceP50Nm", distanceP50);
        features.put("distanceP90Nm", distanceP90);
        features.put("distanceStabilityRatio", distanceStabilityRatio);
        features.put("relativeSpeedP90", relativeSpeedP90);
        features.put("headingDifferenceP90", headingDifferenceP90);
        features.put("dynamicMaxDistanceNm", maxDistance);
        features.put("exclusionEvidence", exclusionDecision.toFeatureMap());
        features.put("replayCompleteness", replayCompleteness.name());

        if (synchronizedPointRatio < config.minSynchronizedPointRatio
                || speedRatio < config.minSpeedInRangeRatio) {
            return RuleResult.rejected("TRACK_OR_SPEED_COVERAGE_TOO_LOW", features);
        }
        if (distanceP90 > maxDistance) {
            return RuleResult.rejected("DISTANCE_TOO_LARGE", features);
        }
        if (relativeSpeedP90 > config.maxRelativeSpeedP90
                || headingDifferenceP90 > config.maxHeadingDifferenceP90) {
            return RuleResult.rejected("RELATIVE_MOTION_UNSTABLE", features);
        }
        if (sideBySideRatio < config.minSideBySideRatio
                || bearingStability < config.minBearingStability
                || distanceStabilityRatio < config.minDistanceStabilityRatio) {
            return RuleResult.rejected("NOT_STABLE_SIDE_BY_SIDE", features);
        }
        if (alongTrackOffsetP90 > maxDistance * config.maxAlongOffsetFactor) {
            return RuleResult.rejected("FOLLOWING_OR_OVERTAKING_GEOMETRY", features);
        }
        return RuleResult.accepted(features);
    }

    private double dynamicMaxDistanceNm(ShipMetadata own, ShipMetadata target) {
        if (own == null || target == null
                || own.getLengthMeters() == null || target.getLengthMeters() == null
                || own.getLengthMeters() <= 0.0 || target.getLengthMeters() <= 0.0) {
            return config.defaultMaxDistanceNm;
        }
        double combinedLengthNm =
                (own.getLengthMeters() + target.getLengthMeters()) / 1852.0;
        return Math.max(config.minDynamicDistanceNm,
                Math.min(config.maxDynamicDistanceNm,
                        combinedLengthNm * config.dynamicLengthFactor));
    }

    private boolean inSpeedRange(TrajectoryPoint point) {
        return point.getSpeed() >= config.minSpeed && point.getSpeed() <= config.maxSpeed;
    }

    private TrajectoryPoint nearest(TrajectoryPoint own, List<TrajectoryPoint> targets) {
        TrajectoryPoint result = null;
        long bestSeconds = Long.MAX_VALUE;
        for (TrajectoryPoint target : targets) {
            long seconds = Math.abs(Duration.between(own.getTime(), target.getTime()).getSeconds());
            if (seconds < bestSeconds) {
                result = target;
                bestSeconds = seconds;
            }
        }
        return bestSeconds <= config.maxSyncGapMinutes * 60L ? result : null;
    }

    private List<TrajectoryPoint> valid(List<TrajectoryPoint> points) {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        if (points == null) return result;
        for (TrajectoryPoint point : points) {
            if (point != null && point.getTime() != null
                    && point.getLon() >= -180.0 && point.getLon() <= 180.0
                    && point.getLat() >= -90.0 && point.getLat() <= 90.0
                    && point.getSpeed() >= 0.0 && point.getSpeed() <= 80.0
                    && point.getCourse() >= 0.0 && point.getCourse() <= 360.0) {
                result.add(point);
            }
        }
        result.sort((a, b) -> a.getTime().compareTo(b.getTime()));
        return result;
    }

    public static class Config {
        private long minDurationMinutes = 45L;
        private int minPoints = 10;
        private double minSpeed = 1.0;
        private double maxSpeed = 3.0;
        private long maxSyncGapMinutes = 3L;
        private double maxRelativeSpeedP90 = 0.5;
        private double maxHeadingDifferenceP90 = 15.0;
        private double minSynchronizedPointRatio = 0.75;
        private double minSpeedInRangeRatio = 0.75;
        private double minSideBySideRatio = 0.75;
        private double minBearingStability = 0.75;
        private double minDistanceStabilityRatio = 0.75;
        private double minSideBearingDegree = 45.0;
        private double maxSideBearingDegree = 135.0;
        private double defaultMaxDistanceNm = 0.30;
        private double minDynamicDistanceNm = 0.10;
        private double maxDynamicDistanceNm = 0.35;
        private double dynamicLengthFactor = 0.90;
        private double maxAlongOffsetFactor = 0.75;

        public static Config defaults() { return new Config(); }
    }
}
