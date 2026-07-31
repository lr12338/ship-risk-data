package com.hifleet.tankeropensea;

import com.hifleet.trajectory.TrajectoryPoint;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TankerOpenSeaLoiteringRule {
    public static final String RULE_VERSION = "LOITERING_LIQUID_CONTAINER_V2";

    private final Config config;

    public TankerOpenSeaLoiteringRule(Config config) {
        this.config = config == null ? Config.defaults() : config;
    }

    public RuleResult evaluate(List<TrajectoryPoint> source,
                               ShipEligibilityDecision eligibility,
                               ExclusionDecision exclusionDecision) {
        if (eligibility == null || !eligibility.isEligible()) {
            return RuleResult.rejected("SHIP_NOT_LIQUID_CARGO_OR_CONTAINER");
        }
        if (exclusionDecision == null || exclusionDecision.isExcluded()) {
            return RuleResult.rejected("EXCLUSION_REJECTED:"
                    + (exclusionDecision == null
                    ? "MISSING" : exclusionDecision.getReason()));
        }
        List<TrajectoryPoint> points = valid(source);
        if (points.size() < config.minPoints) {
            return RuleResult.rejected("INSUFFICIENT_POINTS");
        }
        long durationMinutes = Duration.between(
                points.get(0).getTime(), points.get(points.size() - 1).getTime()).toMinutes();
        if (durationMinutes < config.minDurationMinutes) {
            return RuleResult.rejected("DURATION_TOO_SHORT");
        }

        List<Double> speeds = new ArrayList<Double>();
        double actualTravelDistanceNm = 0.0;
        double cumulativeTurnDegree = 0.0;
        int turnCount = 0;
        int headingReversals = 0;
        long maxAisGapMinutes = 0L;
        int[] directionBins = new int[8];
        for (int i = 0; i < points.size(); i++) {
            TrajectoryPoint point = points.get(i);
            speeds.add(point.getSpeed());
            directionBins[Math.min(7, (int) (GeoMath.normalizeDegree(point.getCourse()) / 45.0))]++;
            if (i == 0) continue;
            maxAisGapMinutes = Math.max(maxAisGapMinutes,
                    Duration.between(points.get(i - 1).getTime(),
                            point.getTime()).toMinutes());
            actualTravelDistanceNm += GeoMath.distanceNm(points.get(i - 1), point);
            double turn = GeoMath.circularDifference(
                    points.get(i - 1).getCourse(), point.getCourse());
            cumulativeTurnDegree += turn;
            if (turn >= config.turnThresholdDegree) turnCount++;
            if (turn >= config.reversalThresholdDegree) headingReversals++;
        }
        double startEndDistanceNm = GeoMath.distanceNm(points.get(0), points.get(points.size() - 1));
        double pathToDisplacementRatio = actualTravelDistanceNm
                / Math.max(config.displacementFloorNm, startEndDistanceNm);
        double netDisplacementSpeed = startEndDistanceNm / Math.max(1.0 / 60.0,
                durationMinutes / 60.0);
        double[] center = center(points);
        double radiusNm = radius(points, center[0], center[1]);
        double loopClosureScore = Math.max(0.0, Math.min(1.0,
                1.0 - startEndDistanceNm / Math.max(config.displacementFloorNm, actualTravelDistanceNm)));
        double backtrackRatio = headingReversals == 0 ? 0.0
                : Math.min(1.0, pathToDisplacementRatio / 5.0);
        double directionEntropy = entropy(directionBins, points.size());
        double coverage = coverage(points, durationMinutes);
        int speedInRange = 0;
        for (Double speed : speeds) {
            if (speed >= config.minSpeed && speed <= config.maxSpeed) speedInRange++;
        }
        double speedRatio = speedInRange / (double) speeds.size();
        double averageSpeed = average(speeds);
        double turnCountPerHour = turnCount / Math.max(1.0 / 60.0, durationMinutes / 60.0);

        Map<String, Object> features = new LinkedHashMap<String, Object>();
        features.put("durationMinutes", durationMinutes);
        features.put("averageSpeed", averageSpeed);
        features.put("speedP10", GeoMath.percentile(speeds, 0.10));
        features.put("speedP50", GeoMath.percentile(speeds, 0.50));
        features.put("speedP90", GeoMath.percentile(speeds, 0.90));
        features.put("radiusNm", radiusNm);
        features.put("startEndDistanceNm", startEndDistanceNm);
        features.put("actualTravelDistanceNm", actualTravelDistanceNm);
        features.put("pathToDisplacementRatio", pathToDisplacementRatio);
        features.put("netDisplacementSpeed", netDisplacementSpeed);
        features.put("cumulativeTurnDegree", cumulativeTurnDegree);
        features.put("turnCountPerHour", turnCountPerHour);
        features.put("headingReversalCount", headingReversals);
        features.put("loopClosureScore", loopClosureScore);
        features.put("backtrackRatio", backtrackRatio);
        features.put("directionEntropy", directionEntropy);
        features.put("trackCoverageRatio", coverage);
        features.put("maxAisGapMinutes", maxAisGapMinutes);
        features.put("exclusionEvidence", exclusionDecision.toFeatureMap());

        if (speedRatio < config.minSpeedInRangeRatio || coverage < config.minCoverageRatio) {
            return RuleResult.rejected("SPEED_OR_TRACK_COVERAGE_TOO_LOW", features);
        }
        int evidence = 0;
        if (startEndDistanceNm <= config.maxStartEndDistanceNm) evidence++;
        if (pathToDisplacementRatio >= config.minPathToDisplacementRatio) evidence++;
        if (cumulativeTurnDegree >= config.minCumulativeTurnDegree) evidence++;
        if (headingReversals >= config.minHeadingReversals) evidence++;
        if (loopClosureScore >= config.minLoopClosureScore) evidence++;
        if (backtrackRatio >= config.minBacktrackRatio) evidence++;
        if (netDisplacementSpeed <= averageSpeed * config.maxNetToAverageSpeedRatio) evidence++;
        if (radiusNm <= config.maxRadiusNm) evidence++;
        if (evidence < config.minIndependentEvidence) {
            return RuleResult.rejected("INSUFFICIENT_LOITERING_MORPHOLOGY", features);
        }
        if (pathToDisplacementRatio < config.straightPathRatioCeiling
                && headingReversals == 0
                && cumulativeTurnDegree < config.minCumulativeTurnDegree) {
            return RuleResult.rejected("STRAIGHT_LOW_SPEED_PASSAGE", features);
        }
        return RuleResult.accepted(features);
    }

    private double coverage(List<TrajectoryPoint> points, long durationMinutes) {
        long expected = durationMinutes / config.maxAisGapMinutes + 1L;
        return Math.min(1.0, points.size() / (double) Math.max(1L, expected));
    }

    private double entropy(int[] bins, int count) {
        double result = 0.0;
        for (int bin : bins) {
            if (bin == 0) continue;
            double probability = bin / (double) count;
            result -= probability * Math.log(probability);
        }
        return result / Math.log(bins.length);
    }

    private double[] center(List<TrajectoryPoint> points) {
        double lon = 0.0;
        double lat = 0.0;
        for (TrajectoryPoint point : points) {
            lon += point.getLon();
            lat += point.getLat();
        }
        return new double[]{lon / points.size(), lat / points.size()};
    }

    private double radius(List<TrajectoryPoint> points, double lon, double lat) {
        TrajectoryPoint center = new TrajectoryPoint("", points.get(0).getTime(),
                lon, lat, 0.0, 0.0, "");
        double max = 0.0;
        for (TrajectoryPoint point : points) {
            max = Math.max(max, GeoMath.distanceNm(center, point));
        }
        return max;
    }

    private double average(List<Double> values) {
        double total = 0.0;
        for (Double value : values) total += value;
        return total / Math.max(1, values.size());
    }

    private List<TrajectoryPoint> valid(List<TrajectoryPoint> source) {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        if (source == null) return result;
        for (TrajectoryPoint point : source) {
            if (point == null || point.getTime() == null
                    || point.getLon() < -180.0 || point.getLon() > 180.0
                    || point.getLat() < -90.0 || point.getLat() > 90.0
                    || point.getSpeed() < 0.0 || point.getSpeed() > 80.0
                    || point.getCourse() < 0.0 || point.getCourse() > 360.0) {
                continue;
            }
            result.add(point);
        }
        result.sort((a, b) -> a.getTime().compareTo(b.getTime()));
        for (int i = 1; i < result.size(); i++) {
            if (Duration.between(result.get(i - 1).getTime(),
                    result.get(i).getTime()).toMinutes() > config.maxAisGapMinutes) {
                return new ArrayList<TrajectoryPoint>();
            }
        }
        return result;
    }

    public static class Config {
        private long minDurationMinutes = 120L;
        private int minPoints = 24;
        private double minSpeed = 1.0;
        private double maxSpeed = 3.0;
        private double minSpeedInRangeRatio = 0.75;
        private double minCoverageRatio = 0.75;
        private long maxAisGapMinutes = 30L;
        private double maxRadiusNm = 3.0;
        private double maxStartEndDistanceNm = 0.75;
        private double minPathToDisplacementRatio = 2.5;
        private double minCumulativeTurnDegree = 270.0;
        private int minHeadingReversals = 1;
        private double minLoopClosureScore = 0.70;
        private double minBacktrackRatio = 0.40;
        private double maxNetToAverageSpeedRatio = 0.35;
        private int minIndependentEvidence = 2;
        private double straightPathRatioCeiling = 1.5;
        private double turnThresholdDegree = 45.0;
        private double reversalThresholdDegree = 150.0;
        private double displacementFloorNm = 0.05;

        public static Config defaults() { return new Config(); }
    }
}
