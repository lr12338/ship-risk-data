package com.hifleet.service;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.trajectory.TrajectoryPoint;
import com.hifleet.trajectory.TrajectorySegment;
import com.hifleet.trajectory.TrajectoryUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 船舶搭靠候选检测：两船在时间邻近、空间邻近且低速时形成连续片段。
 */
public class ShipAlongsideDetector {

    public List<ShipBehaviorEvent> detect(String mmsi, List<TrajectoryPoint> ownPoints,
                                          String targetMmsi, List<TrajectoryPoint> targetPoints,
                                          AlongsideRule rule) {
        if (ownPoints == null || ownPoints.isEmpty() || targetPoints == null || targetPoints.isEmpty()) {
            return Collections.emptyList();
        }
        List<MatchedPoint> current = new ArrayList<MatchedPoint>();
        List<ShipBehaviorEvent> events = new ArrayList<ShipBehaviorEvent>();
        int targetIndex = 0;
        for (TrajectoryPoint ownPoint : ownPoints) {
            MatchResult match = findNearest(ownPoint, targetPoints, targetIndex, rule.getMaxTimeGapMinutes());
            if (match != null) {
                targetIndex = match.getIndex();
            }
            if (isAlongsideCandidate(ownPoint, match, rule)) {
                current.add(new MatchedPoint(ownPoint, match.getDistanceNm()));
            } else {
                collectSegment(mmsi, targetMmsi, current, rule, events);
                current.clear();
            }
        }
        collectSegment(mmsi, targetMmsi, current, rule, events);
        return events;
    }

    private boolean isAlongsideCandidate(TrajectoryPoint ownPoint, MatchResult match, AlongsideRule rule) {
        return match != null
                && ownPoint.getSpeed() <= rule.getMaxSpeed()
                && match.getPoint().getSpeed() <= rule.getMaxSpeed()
                && match.getDistanceNm() <= rule.getMaxDistanceNm();
    }

    private MatchResult findNearest(TrajectoryPoint ownPoint, List<TrajectoryPoint> targetPoints,
                                    int startIndex, long maxTimeGapMinutes) {
        MatchResult best = null;
        for (int i = Math.max(0, startIndex); i < targetPoints.size(); i++) {
            TrajectoryPoint targetPoint = targetPoints.get(i);
            long gap = Math.abs(TrajectoryUtils.minutesBetween(ownPoint, targetPoint));
            if (best != null && targetPoint.getTime().isAfter(ownPoint.getTime()) && gap > maxTimeGapMinutes) {
                break;
            }
            if (gap > maxTimeGapMinutes) {
                continue;
            }
            double distanceNm = TrajectoryUtils.distanceNm(ownPoint, targetPoint);
            if (best == null || distanceNm < best.getDistanceNm()) {
                best = new MatchResult(i, targetPoint, distanceNm);
            }
        }
        return best;
    }

    private void collectSegment(String mmsi, String targetMmsi, List<MatchedPoint> matches,
                                AlongsideRule rule, List<ShipBehaviorEvent> events) {
        if (matches.size() < rule.getMinPoints()) {
            return;
        }
        List<TrajectoryPoint> ownPoints = new ArrayList<TrajectoryPoint>();
        double minDistance = Double.MAX_VALUE;
        double maxDistance = 0.0;
        for (MatchedPoint match : matches) {
            ownPoints.add(match.getOwnPoint());
            minDistance = Math.min(minDistance, match.getDistanceNm());
            maxDistance = Math.max(maxDistance, match.getDistanceNm());
        }
        TrajectorySegment segment = new TrajectorySegment(ownPoints);
        if (segment.getDurationMinutes() < rule.getMinDurationMinutes()) {
            return;
        }
        TrajectoryPoint start = segment.getStartPoint();
        TrajectoryPoint end = segment.getEndPoint();
        ShipBehaviorEvent event = new ShipBehaviorEvent();
        event.setBehaviorType(ShipBehaviorEvent.TYPE_ALONGSIDE);
        event.setMmsi(mmsi);
        event.setTargetMmsi(targetMmsi);
        event.setStartTime(TrajectoryUtils.formatTime(start));
        event.setEndTime(TrajectoryUtils.formatTime(end));
        event.setDurationMinutes(segment.getDurationMinutes());
        event.setStartLon(start.getLon());
        event.setStartLat(start.getLat());
        event.setEndLon(end.getLon());
        event.setEndLat(end.getLat());
        event.setMinDistanceNm(minDistance);
        event.setMaxDistanceNm(maxDistance);
        event.setAvgSpeed(TrajectoryUtils.averageSpeed(ownPoints));
        event.setPointCount(matches.size());
        event.setRuleVersion(rule.getRuleVersion());
        events.add(event);
    }

    public static class AlongsideRule {
        private final double maxDistanceNm;
        private final double maxSpeed;
        private final long maxTimeGapMinutes;
        private final long minDurationMinutes;
        private final int minPoints;
        private final String ruleVersion;

        public AlongsideRule(double maxDistanceNm, double maxSpeed, long maxTimeGapMinutes,
                             long minDurationMinutes, int minPoints, String ruleVersion) {
            this.maxDistanceNm = maxDistanceNm;
            this.maxSpeed = maxSpeed;
            this.maxTimeGapMinutes = maxTimeGapMinutes;
            this.minDurationMinutes = minDurationMinutes;
            this.minPoints = minPoints;
            this.ruleVersion = ruleVersion;
        }

        public double getMaxDistanceNm() {
            return maxDistanceNm;
        }

        public double getMaxSpeed() {
            return maxSpeed;
        }

        public long getMaxTimeGapMinutes() {
            return maxTimeGapMinutes;
        }

        public long getMinDurationMinutes() {
            return minDurationMinutes;
        }

        public int getMinPoints() {
            return minPoints;
        }

        public String getRuleVersion() {
            return ruleVersion;
        }
    }

    private static class MatchResult {
        private final int index;
        private final TrajectoryPoint point;
        private final double distanceNm;

        private MatchResult(int index, TrajectoryPoint point, double distanceNm) {
            this.index = index;
            this.point = point;
            this.distanceNm = distanceNm;
        }

        private int getIndex() {
            return index;
        }

        private TrajectoryPoint getPoint() {
            return point;
        }

        private double getDistanceNm() {
            return distanceNm;
        }
    }

    private static class MatchedPoint {
        private final TrajectoryPoint ownPoint;
        private final double distanceNm;

        private MatchedPoint(TrajectoryPoint ownPoint, double distanceNm) {
            this.ownPoint = ownPoint;
            this.distanceNm = distanceNm;
        }

        private TrajectoryPoint getOwnPoint() {
            return ownPoint;
        }

        private double getDistanceNm() {
            return distanceNm;
        }
    }
}
