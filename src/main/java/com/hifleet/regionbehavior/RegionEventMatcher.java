package com.hifleet.regionbehavior;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.trajectory.TrajectoryPoint;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RegionEventMatcher {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private final RegionGeometry region;
    private final RegionBehaviorConfig config;
    private final String batchNo;
    private final String regionId;
    private final String regionName;
    private final boolean requireTimeRatios;
    private final long timeGapCapMinutes;
    private final int pairSyncToleranceMinutes;
    private int lastDuplicateCount;

    public RegionEventMatcher(RegionGeometry region, RegionBehaviorConfig config,
                              String batchNo, String regionId, String regionName) {
        this(region, config, batchNo, regionId, regionName, null);
    }

    public RegionEventMatcher(RegionGeometry region, RegionBehaviorConfig config,
                              String batchNo, String regionId, String regionName,
                              ShadowRulesConfig shadowConfig) {
        this.region = region;
        this.config = config;
        this.batchNo = batchNo;
        this.regionId = regionId;
        this.regionName = regionName;
        this.requireTimeRatios = shadowConfig != null && shadowConfig.isEnabled();
        this.timeGapCapMinutes = shadowConfig == null ? 10L : shadowConfig.getSceneGapCapMinutes();
        this.pairSyncToleranceMinutes = shadowConfig == null
                ? config.getPairSyncToleranceMinutes() : (int) shadowConfig.getAlongsideMaxSyncGapMinutes();
    }

    public RegionMatchedEvent matchLoitering(ShipBehaviorTrace trace) {
        RegionMatchedEvent matched = base(trace);
        List<TrajectoryPoint> own = safe(trace.getOwnPoints());
        double ratio = insideRatio(own);
        TimeStats time = timeStats(own);
        boolean centerInside = centerInside(trace.getEvent(), own);
        matched.setEventCenterInside(centerInside);
        matched.setEventStartInside(endpointInside(own, true));
        matched.setEventEndInside(endpointInside(own, false));
        matched.setOwnPointInsideRatio(ratio);
        matched.setOwnInsideTimeRatio(time.insideRatio);
        matched.setOwnPointInsideCount(insideCount(own));
        matched.setInsideDurationMinutes(insideDuration(own));
        boolean strict = centerInside && ratio >= config.getLoiteringMinInsideRatio()
                && (!requireTimeRatios || time.insideRatio >= config.getLoiteringMinInsideRatio());
        matched.setRegionMatchStatus(strict ? RegionMatchStatus.STRICT_CORE
                : (centerInside || ratio > 0.0 || intersects(own)
                ? RegionMatchStatus.REGION_BOUNDARY_PARTIAL
                : RegionMatchStatus.OUTSIDE_CORE));
        return matched;
    }

    public RegionMatchedEvent matchAlongside(ShipBehaviorTrace trace) {
        RegionMatchedEvent matched = base(trace);
        List<TrajectoryPoint> own = safe(trace.getOwnPoints());
        List<TrajectoryPoint> target = safe(trace.getTargetPoints());
        double ownRatio = insideRatio(own);
        double targetRatio = insideRatio(target);
        TimeStats ownTime = timeStats(own);
        TimeStats targetTime = timeStats(target);
        PairStats pair = pairStats(own, target);
        boolean centerInside = centerInside(trace.getEvent(), own);
        matched.setEventCenterInside(centerInside);
        matched.setEventStartInside(endpointInside(own, true));
        matched.setEventEndInside(endpointInside(own, false));
        matched.setOwnPointInsideRatio(ownRatio);
        matched.setOwnInsideTimeRatio(ownTime.insideRatio);
        matched.setOwnPointInsideCount(insideCount(own));
        matched.setTargetPointInsideRatio(targetRatio);
        matched.setTargetInsideTimeRatio(targetTime.insideRatio);
        matched.setTargetPointInsideCount(insideCount(target));
        matched.setPairMidpointInsideRatio(pair.insideRatio);
        matched.setPairMidpointInsideTimeRatio(pair.insideTimeRatio);
        matched.setPairMidpointInsideCount(pair.insideCount);
        matched.setInsideDurationMinutes(insideDuration(own));
        matched.setSynchronizedInsideMinutes(pair.insideMinutes);
        double threshold = config.getAlongsideMinInsideRatio();
        boolean strict = centerInside && ownRatio >= threshold && targetRatio >= threshold
                && pair.insideRatio >= threshold
                && (!requireTimeRatios || ownTime.insideRatio >= threshold
                && targetTime.insideRatio >= threshold && pair.insideTimeRatio >= threshold);
        boolean intersects = centerInside || ownRatio > 0.0 || targetRatio > 0.0 || pair.insideRatio > 0.0
                || intersects(own) || intersects(target) || intersectsCoordinates(pair.midpoints);
        matched.setRegionMatchStatus(strict ? RegionMatchStatus.STRICT_CORE
                : (intersects ? RegionMatchStatus.REGION_BOUNDARY_PARTIAL : RegionMatchStatus.OUTSIDE_CORE));
        return matched;
    }

    public List<RegionMatchedEvent> deduplicateAlongside(List<RegionMatchedEvent> events) {
        lastDuplicateCount = 0;
        List<RegionMatchedEvent> sorted = new ArrayList<RegionMatchedEvent>(events);
        Collections.sort(sorted, new Comparator<RegionMatchedEvent>() {
            @Override
            public int compare(RegionMatchedEvent left, RegionMatchedEvent right) {
                return left.getStartTime().compareTo(right.getStartTime());
            }
        });
        List<RegionMatchedEvent> kept = new ArrayList<RegionMatchedEvent>();
        for (RegionMatchedEvent candidate : sorted) {
            boolean duplicate = false;
            for (RegionMatchedEvent existing : kept) {
                if (samePair(existing, candidate) && sameRule(existing, candidate)
                        && overlapRatio(existing, candidate) >= config.getAlongsidePairOverlapRatio()) {
                    duplicate = true;
                    lastDuplicateCount++;
                    break;
                }
            }
            if (!duplicate) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    private RegionMatchedEvent base(ShipBehaviorTrace trace) {
        if (trace == null || trace.getEvent() == null) {
            throw new IllegalArgumentException("trace event is required");
        }
        RegionMatchedEvent matched = RegionMatchedEvent.from(trace.getEvent());
        matched.setIdentity(batchNo, regionId, regionName, region.getFingerprint());
        return matched;
    }

    private boolean centerInside(ShipBehaviorEvent event, List<TrajectoryPoint> own) {
        Double lon = event.getCenterLon();
        Double lat = event.getCenterLat();
        if (lon == null || lat == null) {
            if (own.isEmpty()) {
                return false;
            }
            double sumLon = 0.0;
            double sumLat = 0.0;
            for (TrajectoryPoint point : own) {
                sumLon += point.getLon();
                sumLat += point.getLat();
            }
            lon = sumLon / own.size();
            lat = sumLat / own.size();
        }
        return region.containsCore(new Coordinate(lon, lat));
    }

    private double insideRatio(List<TrajectoryPoint> points) {
        if (points.isEmpty()) {
            return 0.0;
        }
        int inside = 0;
        for (TrajectoryPoint point : points) {
            if (region.containsCore(new Coordinate(point.getLon(), point.getLat()))) {
                inside++;
            }
        }
        return inside / (double) points.size();
    }

    private int insideCount(List<TrajectoryPoint> points) {
        int inside = 0;
        for (TrajectoryPoint point : points) {
            if (region.containsCore(new Coordinate(point.getLon(), point.getLat()))) {
                inside++;
            }
        }
        return inside;
    }

    private boolean intersects(List<TrajectoryPoint> points) {
        List<Coordinate> coordinates = new ArrayList<Coordinate>();
        for (TrajectoryPoint point : points) {
            coordinates.add(new Coordinate(point.getLon(), point.getLat()));
        }
        return intersectsCoordinates(coordinates);
    }

    private boolean intersectsCoordinates(List<Coordinate> coordinates) {
        if (coordinates.isEmpty()) {
            return false;
        }
        Geometry geometry = coordinates.size() == 1
                ? GEOMETRY_FACTORY.createPoint(coordinates.get(0))
                : GEOMETRY_FACTORY.createLineString(coordinates.toArray(new Coordinate[coordinates.size()]));
        return region.intersectsCore(geometry);
    }

    private boolean endpointInside(List<TrajectoryPoint> points, boolean first) {
        if (points.isEmpty()) {
            return false;
        }
        TrajectoryPoint point = first ? points.get(0) : points.get(points.size() - 1);
        return region.containsCore(new Coordinate(point.getLon(), point.getLat()));
    }

    private long insideDuration(List<TrajectoryPoint> points) {
        long minutes = 0L;
        for (int i = 1; i < points.size(); i++) {
            TrajectoryPoint previous = points.get(i - 1);
            TrajectoryPoint current = points.get(i);
            if (region.containsCore(new Coordinate(previous.getLon(), previous.getLat()))
                    && region.containsCore(new Coordinate(current.getLon(), current.getLat()))) {
                minutes += Math.max(0L, Duration.between(previous.getTime(), current.getTime()).toMinutes());
            }
        }
        return minutes;
    }

    private TimeStats timeStats(List<TrajectoryPoint> points) {
        double observed = 0.0;
        double inside = 0.0;
        for (int i = 0; i + 1 < points.size(); i++) {
            long gap = Math.max(0L, Duration.between(points.get(i).getTime(),
                    points.get(i + 1).getTime()).toMinutes());
            double weight = Math.min(gap, timeGapCapMinutes);
            observed += weight;
            if (region.containsCore(new Coordinate(points.get(i).getLon(), points.get(i).getLat()))) {
                inside += weight;
            }
        }
        return new TimeStats(observed <= 0.0 ? 0.0 : inside / observed);
    }

    private PairStats pairStats(List<TrajectoryPoint> own, List<TrajectoryPoint> target) {
        TrajectoryPairSynchronizer.Result synchronizedTrack =
                new TrajectoryPairSynchronizer(pairSyncToleranceMinutes).synchronize(own, target);
        int inside = 0;
        long insideMinutes = 0L;
        double observedMinutes = 0.0;
        double weightedInsideMinutes = 0.0;
        List<Coordinate> midpoints = new ArrayList<Coordinate>();
        List<SynchronizedShipPairPoint> pairs = synchronizedTrack.getPairs();
        for (int i = 0; i < pairs.size(); i++) {
            SynchronizedShipPairPoint pair = pairs.get(i);
            Coordinate midpoint = new Coordinate(pair.getMidpointLon(), pair.getMidpointLat());
            boolean midpointInside = region.containsCore(midpoint);
            midpoints.add(midpoint);
            if (midpointInside) {
                inside++;
            }
            if (i + 1 < pairs.size()) {
                long gap = Math.max(0L, (pairs.get(i + 1).getSynchronizedEpochMillis()
                        - pair.getSynchronizedEpochMillis()) / 60_000L);
                double weight = Math.min(gap, timeGapCapMinutes);
                observedMinutes += weight;
                if (midpointInside) weightedInsideMinutes += weight;
                if (midpointInside && region.containsCore(new Coordinate(
                        pairs.get(i + 1).getMidpointLon(), pairs.get(i + 1).getMidpointLat()))) {
                    insideMinutes += gap;
                }
            }
        }
        return new PairStats(pairs.isEmpty() ? 0.0 : inside / (double) pairs.size(),
                observedMinutes <= 0.0 ? 0.0 : weightedInsideMinutes / observedMinutes,
                inside, insideMinutes, midpoints);
    }

    private boolean samePair(RegionMatchedEvent left, RegionMatchedEvent right) {
        return equals(left.getPairMmsi1(), right.getPairMmsi1())
                && equals(left.getPairMmsi2(), right.getPairMmsi2());
    }

    private boolean sameRule(RegionMatchedEvent left, RegionMatchedEvent right) {
        return equals(left.getRuleVersion(), right.getRuleVersion());
    }

    private boolean equals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private double overlapRatio(RegionMatchedEvent left, RegionMatchedEvent right) {
        LocalDateTime leftStart = RegionBehaviorTimes.parse(left.getStartTime());
        LocalDateTime leftEnd = RegionBehaviorTimes.parse(left.getEndTime());
        LocalDateTime rightStart = RegionBehaviorTimes.parse(right.getStartTime());
        LocalDateTime rightEnd = RegionBehaviorTimes.parse(right.getEndTime());
        LocalDateTime start = leftStart.isAfter(rightStart) ? leftStart : rightStart;
        LocalDateTime end = leftEnd.isBefore(rightEnd) ? leftEnd : rightEnd;
        long overlap = Math.max(0L, Duration.between(start, end).toMinutes());
        long shortest = Math.min(Duration.between(leftStart, leftEnd).toMinutes(),
                Duration.between(rightStart, rightEnd).toMinutes());
        return shortest <= 0L ? 0.0 : overlap / (double) shortest;
    }

    private List<TrajectoryPoint> safe(List<TrajectoryPoint> points) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        List<TrajectoryPoint> sorted = new ArrayList<TrajectoryPoint>(points);
        Collections.sort(sorted, new Comparator<TrajectoryPoint>() {
            @Override
            public int compare(TrajectoryPoint left, TrajectoryPoint right) {
                return left.getTime().compareTo(right.getTime());
            }
        });
        return sorted;
    }

    public int getLastDuplicateCount() { return lastDuplicateCount; }

    private static final class PairStats {
        private final double insideRatio;
        private final double insideTimeRatio;
        private final int insideCount;
        private final long insideMinutes;
        private final List<Coordinate> midpoints;

        private PairStats(double insideRatio, double insideTimeRatio,
                          int insideCount, long insideMinutes,
                          List<Coordinate> midpoints) {
            this.insideRatio = insideRatio;
            this.insideTimeRatio = insideTimeRatio;
            this.insideCount = insideCount;
            this.insideMinutes = insideMinutes;
            this.midpoints = midpoints;
        }
    }

    private static final class TimeStats {
        private final double insideRatio;

        private TimeStats(double insideRatio) { this.insideRatio = insideRatio; }
    }
}
