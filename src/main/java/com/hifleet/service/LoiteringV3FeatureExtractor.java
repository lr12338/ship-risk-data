package com.hifleet.service;

import com.hifleet.trajectory.TrajectoryPoint;
import com.hifleet.trajectory.TrajectoryUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Loitering V3 单船候选轨迹特征计算，不读取场景数据、不做事件判定、不落库。
 */
public class LoiteringV3FeatureExtractor {

    private static final double MIN_MOVEMENT_NM = 0.01;
    private static final double MAX_VALID_SPEED = 80.0;
    private static final double SIGNIFICANT_TURN_DEGREE = 15.0;
    private static final double REVERSAL_DEGREE = 135.0;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    public Features extract(List<TrajectoryPoint> sourcePoints) {
        List<TrajectoryPoint> points = cleanAndSort(sourcePoints);
        if (points.isEmpty()) {
            return Features.empty();
        }

        double centerLon = medianLongitude(points);
        double centerLat = medianLatitude(points);
        List<Double> radii = calculateRadii(points, centerLon, centerLat);
        double radiusNm = percentile(radii, 0.95);
        double maxRadiusNm = radii.isEmpty() ? 0.0 : Collections.max(radii);
        double radialCv = coefficientOfVariation(radii);
        double areaNm2 = calculateConvexHullAreaNm2(points, centerLon, centerLat);

        double pathLengthNm = 0.0;
        long maxGapMinutes = 0L;
        List<Double> derivedCourses = new ArrayList<Double>();
        for (int i = 1; i < points.size(); i++) {
            TrajectoryPoint previous = points.get(i - 1);
            TrajectoryPoint current = points.get(i);
            double distance = TrajectoryUtils.distanceNm(previous, current);
            pathLengthNm += distance;
            maxGapMinutes = Math.max(maxGapMinutes,
                    Math.max(0L, Duration.between(previous.getTime(), current.getTime()).toMinutes()));
            if (distance >= MIN_MOVEMENT_NM) {
                derivedCourses.add(bearingDegree(previous, current));
            }
        }

        double startEndDistanceNm = points.size() < 2
                ? 0.0 : TrajectoryUtils.distanceNm(points.get(0), points.get(points.size() - 1));
        double directionEfficiency = safeRatio(startEndDistanceNm, pathLengthNm);
        double closureScore = 1.0 - Math.min(1.0, directionEfficiency);
        long durationMinutes = points.size() < 2 ? 0L
                : Math.max(0L, Duration.between(points.get(0).getTime(),
                points.get(points.size() - 1).getTime()).toMinutes());

        TurnFeatures turns = calculateTurnFeatures(derivedCourses, durationMinutes);
        double averageSpeed = averageSpeed(points);
        double p90Speed = percentile(speeds(points), 0.90);
        double lowSpeedPointRatio = lowSpeedPointRatio(points);
        double tortuosity = pathLengthNm / Math.max(0.1,
                Math.max(startEndDistanceNm, radiusNm));
        double validDerivedCourseRatio = derivedCourses.size()
                / (double) Math.max(1, points.size() - 1);

        return new Features(durationMinutes, points.size(), averageSpeed, p90Speed, lowSpeedPointRatio,
                centerLon, centerLat, radiusNm, maxRadiusNm, radialCv, areaNm2, pathLengthNm,
                startEndDistanceNm, closureScore, directionEfficiency, turns.directionChangeDegree,
                turns.directionChangePerHour, turns.signedTurnDegree, turns.turnSignChangeCount,
                turns.reversalCount, turns.sameTurnDirectionRatio, tortuosity, maxGapMinutes,
                validDerivedCourseRatio);
    }

    private List<TrajectoryPoint> cleanAndSort(List<TrajectoryPoint> sourcePoints) {
        if (sourcePoints == null || sourcePoints.isEmpty()) {
            return Collections.emptyList();
        }
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        for (TrajectoryPoint point : sourcePoints) {
            if (point != null && point.getTime() != null
                    && point.getLon() >= -180.0 && point.getLon() <= 180.0
                    && point.getLat() >= -90.0 && point.getLat() <= 90.0
                    && point.getSpeed() >= 0.0 && point.getSpeed() <= MAX_VALID_SPEED) {
                points.add(point);
            }
        }
        Collections.sort(points, new Comparator<TrajectoryPoint>() {
            @Override
            public int compare(TrajectoryPoint a, TrajectoryPoint b) {
                return a.getTime().compareTo(b.getTime());
            }
        });
        List<TrajectoryPoint> unique = new ArrayList<TrajectoryPoint>();
        int index = 0;
        while (index < points.size()) {
            TrajectoryPoint first = points.get(index);
            int next = index + 1;
            boolean coordinateConflict = false;
            while (next < points.size() && first.getTime().equals(points.get(next).getTime())) {
                TrajectoryPoint sameTime = points.get(next);
                if (Double.compare(first.getLon(), sameTime.getLon()) != 0
                        || Double.compare(first.getLat(), sameTime.getLat()) != 0) {
                    coordinateConflict = true;
                }
                next++;
            }
            if (!coordinateConflict) {
                unique.add(first);
            }
            index = next;
        }
        return unique;
    }

    private double medianLongitude(List<TrajectoryPoint> points) {
        double reference = points.get(0).getLon();
        List<Double> values = new ArrayList<Double>();
        for (TrajectoryPoint point : points) {
            values.add(reference + normalizeLongitudeDelta(point.getLon() - reference));
        }
        return normalizeLongitude(median(values));
    }

    private double medianLatitude(List<TrajectoryPoint> points) {
        List<Double> values = new ArrayList<Double>();
        for (TrajectoryPoint point : points) {
            values.add(point.getLat());
        }
        return median(values);
    }

    private double median(List<Double> values) {
        Collections.sort(values);
        int middle = values.size() / 2;
        if (values.size() % 2 == 1) {
            return values.get(middle);
        }
        return (values.get(middle - 1) + values.get(middle)) / 2.0;
    }

    private double normalizeLongitude(double longitude) {
        double normalized = longitude;
        while (normalized > 180.0) {
            normalized -= 360.0;
        }
        while (normalized < -180.0) {
            normalized += 360.0;
        }
        return normalized;
    }

    private List<Double> calculateRadii(List<TrajectoryPoint> points, double centerLon, double centerLat) {
        List<Double> radii = new ArrayList<Double>();
        TrajectoryPoint center = new TrajectoryPoint(null, points.get(0).getTime(),
                centerLon, centerLat, 0.0, 0.0, null);
        for (TrajectoryPoint point : points) {
            radii.add(TrajectoryUtils.distanceNm(center, point));
        }
        return radii;
    }

    private double calculateConvexHullAreaNm2(List<TrajectoryPoint> points,
                                               double centerLon, double centerLat) {
        if (points.size() < 3) {
            return 0.0;
        }
        Coordinate[] coordinates = new Coordinate[points.size()];
        double longitudeScale = 60.0 * Math.cos(Math.toRadians(centerLat));
        for (int i = 0; i < points.size(); i++) {
            TrajectoryPoint point = points.get(i);
            double xNm = normalizeLongitudeDelta(point.getLon() - centerLon) * longitudeScale;
            double yNm = (point.getLat() - centerLat) * 60.0;
            coordinates[i] = new Coordinate(xNm, yNm);
        }
        return Math.abs(GEOMETRY_FACTORY.createMultiPointFromCoords(coordinates)
                .convexHull().getArea());
    }

    private double normalizeLongitudeDelta(double delta) {
        if (delta > 180.0) {
            return delta - 360.0;
        }
        if (delta < -180.0) {
            return delta + 360.0;
        }
        return delta;
    }

    private double bearingDegree(TrajectoryPoint from, TrajectoryPoint to) {
        double latitudeRadians = Math.toRadians((from.getLat() + to.getLat()) / 2.0);
        double east = normalizeLongitudeDelta(to.getLon() - from.getLon())
                * Math.cos(latitudeRadians);
        double north = to.getLat() - from.getLat();
        double degree = Math.toDegrees(Math.atan2(east, north));
        return degree < 0.0 ? degree + 360.0 : degree;
    }

    private TurnFeatures calculateTurnFeatures(List<Double> courses, long durationMinutes) {
        if (courses.size() < 2) {
            return TurnFeatures.empty();
        }
        double directionChange = 0.0;
        double signedTurn = 0.0;
        int positiveTurns = 0;
        int negativeTurns = 0;
        int signChanges = 0;
        int reversals = 0;
        int previousSign = 0;
        for (int i = 1; i < courses.size(); i++) {
            double delta = signedCircularDelta(courses.get(i - 1), courses.get(i));
            double absolute = Math.abs(delta);
            directionChange += absolute;
            signedTurn += delta;
            if (absolute >= REVERSAL_DEGREE) {
                reversals++;
            }
            if (absolute < SIGNIFICANT_TURN_DEGREE) {
                continue;
            }
            int sign = delta > 0.0 ? 1 : -1;
            if (sign > 0) {
                positiveTurns++;
            } else {
                negativeTurns++;
            }
            if (previousSign != 0 && previousSign != sign) {
                signChanges++;
            }
            previousSign = sign;
        }
        int significantTurnCount = positiveTurns + negativeTurns;
        double sameDirectionRatio = significantTurnCount == 0 ? 0.0
                : Math.max(positiveTurns, negativeTurns) / (double) significantTurnCount;
        double perHour = durationMinutes <= 0 ? 0.0
                : directionChange / (durationMinutes / 60.0);
        return new TurnFeatures(directionChange, perHour, signedTurn, signChanges,
                reversals, sameDirectionRatio);
    }

    private double signedCircularDelta(double from, double to) {
        double delta = (to - from + 540.0) % 360.0 - 180.0;
        return delta == -180.0 ? 180.0 : delta;
    }

    private List<Double> speeds(List<TrajectoryPoint> points) {
        List<Double> result = new ArrayList<Double>();
        for (TrajectoryPoint point : points) {
            result.add(point.getSpeed());
        }
        return result;
    }

    private double averageSpeed(List<TrajectoryPoint> points) {
        double total = 0.0;
        for (TrajectoryPoint point : points) {
            total += point.getSpeed();
        }
        return total / Math.max(1, points.size());
    }

    private double lowSpeedPointRatio(List<TrajectoryPoint> points) {
        int lowSpeedPoints = 0;
        for (TrajectoryPoint point : points) {
            if (point.getSpeed() >= 1.0 && point.getSpeed() <= 3.0) {
                lowSpeedPoints++;
            }
        }
        return lowSpeedPoints / (double) Math.max(1, points.size());
    }

    private double percentile(List<Double> values, double ratio) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<Double>(values);
        Collections.sort(sorted);
        int index = (int) Math.ceil(ratio * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    private double coefficientOfVariation(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        double mean = 0.0;
        for (Double value : values) {
            mean += value;
        }
        mean /= values.size();
        if (mean <= 0.000001) {
            return 0.0;
        }
        double variance = 0.0;
        for (Double value : values) {
            double delta = value - mean;
            variance += delta * delta;
        }
        variance /= values.size();
        return Math.sqrt(variance) / mean;
    }

    private double safeRatio(double numerator, double denominator) {
        if (denominator <= 0.000001) {
            return 0.0;
        }
        return numerator / denominator;
    }

    private static class TurnFeatures {
        private final double directionChangeDegree;
        private final double directionChangePerHour;
        private final double signedTurnDegree;
        private final int turnSignChangeCount;
        private final int reversalCount;
        private final double sameTurnDirectionRatio;

        private TurnFeatures(double directionChangeDegree, double directionChangePerHour,
                             double signedTurnDegree, int turnSignChangeCount,
                             int reversalCount, double sameTurnDirectionRatio) {
            this.directionChangeDegree = directionChangeDegree;
            this.directionChangePerHour = directionChangePerHour;
            this.signedTurnDegree = signedTurnDegree;
            this.turnSignChangeCount = turnSignChangeCount;
            this.reversalCount = reversalCount;
            this.sameTurnDirectionRatio = sameTurnDirectionRatio;
        }

        private static TurnFeatures empty() {
            return new TurnFeatures(0.0, 0.0, 0.0, 0, 0, 0.0);
        }
    }

    public static class Features {
        private final long durationMinutes;
        private final int pointCount;
        private final double averageSpeed;
        private final double p90Speed;
        private final double lowSpeedPointRatio;
        private final double centerLon;
        private final double centerLat;
        private final double radiusNm;
        private final double maxRadiusNm;
        private final double radialCv;
        private final double trajectoryAreaNm2;
        private final double pathLengthNm;
        private final double startEndDistanceNm;
        private final double closureScore;
        private final double directionEfficiency;
        private final double directionChangeDegree;
        private final double directionChangePerHour;
        private final double signedTurnDegree;
        private final int turnSignChangeCount;
        private final int reversalCount;
        private final double sameTurnDirectionRatio;
        private final double tortuosity;
        private final long maxGapMinutes;
        private final double validDerivedCourseRatio;

        private Features(long durationMinutes, int pointCount, double averageSpeed, double p90Speed,
                         double lowSpeedPointRatio, double centerLon, double centerLat,
                         double radiusNm, double maxRadiusNm, double radialCv,
                         double trajectoryAreaNm2, double pathLengthNm, double startEndDistanceNm,
                         double closureScore, double directionEfficiency,
                         double directionChangeDegree, double directionChangePerHour,
                         double signedTurnDegree, int turnSignChangeCount, int reversalCount,
                         double sameTurnDirectionRatio, double tortuosity, long maxGapMinutes,
                         double validDerivedCourseRatio) {
            this.durationMinutes = durationMinutes;
            this.pointCount = pointCount;
            this.averageSpeed = averageSpeed;
            this.p90Speed = p90Speed;
            this.lowSpeedPointRatio = lowSpeedPointRatio;
            this.centerLon = centerLon;
            this.centerLat = centerLat;
            this.radiusNm = radiusNm;
            this.maxRadiusNm = maxRadiusNm;
            this.radialCv = radialCv;
            this.trajectoryAreaNm2 = trajectoryAreaNm2;
            this.pathLengthNm = pathLengthNm;
            this.startEndDistanceNm = startEndDistanceNm;
            this.closureScore = closureScore;
            this.directionEfficiency = directionEfficiency;
            this.directionChangeDegree = directionChangeDegree;
            this.directionChangePerHour = directionChangePerHour;
            this.signedTurnDegree = signedTurnDegree;
            this.turnSignChangeCount = turnSignChangeCount;
            this.reversalCount = reversalCount;
            this.sameTurnDirectionRatio = sameTurnDirectionRatio;
            this.tortuosity = tortuosity;
            this.maxGapMinutes = maxGapMinutes;
            this.validDerivedCourseRatio = validDerivedCourseRatio;
        }

        private static Features empty() {
            return new Features(0L, 0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    0, 0, 0.0, 0.0, 0L, 0.0);
        }

        public long getDurationMinutes() {
            return durationMinutes;
        }

        public int getPointCount() {
            return pointCount;
        }

        public double getAverageSpeed() {
            return averageSpeed;
        }

        public double getP90Speed() {
            return p90Speed;
        }

        public double getLowSpeedPointRatio() {
            return lowSpeedPointRatio;
        }

        public double getCenterLon() {
            return centerLon;
        }

        public double getCenterLat() {
            return centerLat;
        }

        public double getRadiusNm() {
            return radiusNm;
        }

        public double getMaxRadiusNm() {
            return maxRadiusNm;
        }

        public double getRadialCv() {
            return radialCv;
        }

        public double getTrajectoryAreaNm2() {
            return trajectoryAreaNm2;
        }

        public double getPathLengthNm() {
            return pathLengthNm;
        }

        public double getStartEndDistanceNm() {
            return startEndDistanceNm;
        }

        public double getClosureScore() {
            return closureScore;
        }

        public double getDirectionEfficiency() {
            return directionEfficiency;
        }

        public double getDirectionChangeDegree() {
            return directionChangeDegree;
        }

        public double getDirectionChangePerHour() {
            return directionChangePerHour;
        }

        public double getSignedTurnDegree() {
            return signedTurnDegree;
        }

        public int getTurnSignChangeCount() {
            return turnSignChangeCount;
        }

        public int getReversalCount() {
            return reversalCount;
        }

        public double getSameTurnDirectionRatio() {
            return sameTurnDirectionRatio;
        }

        public double getTortuosity() {
            return tortuosity;
        }

        public long getMaxGapMinutes() {
            return maxGapMinutes;
        }

        public double getValidDerivedCourseRatio() {
            return validDerivedCourseRatio;
        }
    }
}
