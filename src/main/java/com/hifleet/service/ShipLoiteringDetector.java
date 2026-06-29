package com.hifleet.service;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.trajectory.TrajectoryPoint;
import com.hifleet.trajectory.TrajectorySegment;
import com.hifleet.trajectory.TrajectoryUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 船舶徘徊候选检测：低速、持续时间足够、活动半径较小。
 */
public class ShipLoiteringDetector {

    public List<ShipBehaviorEvent> detect(String mmsi, List<TrajectoryPoint> points, LoiteringRule rule) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        List<ShipBehaviorEvent> events = new ArrayList<ShipBehaviorEvent>();
        List<TrajectoryPoint> current = new ArrayList<TrajectoryPoint>();
        for (TrajectoryPoint point : points) {
            if (point.getSpeed() <= rule.getMaxSpeed()) {
                current.add(point);
            } else {
                collectSegment(mmsi, current, rule, events);
                current.clear();
            }
        }
        collectSegment(mmsi, current, rule, events);
        return events;
    }

    private void collectSegment(String mmsi, List<TrajectoryPoint> points, LoiteringRule rule,
                                List<ShipBehaviorEvent> events) {
        if (points.size() < rule.getMinPoints()) {
            return;
        }
        TrajectorySegment segment = new TrajectorySegment(new ArrayList<TrajectoryPoint>(points));
        if (segment.getDurationMinutes() < rule.getMinDurationMinutes()) {
            return;
        }
        double radiusNm = TrajectoryUtils.maxDistanceFromStartNm(points);
        if (radiusNm > rule.getMaxRadiusNm()) {
            return;
        }
        TrajectoryPoint start = segment.getStartPoint();
        TrajectoryPoint end = segment.getEndPoint();
        ShipBehaviorEvent event = new ShipBehaviorEvent();
        event.setBehaviorType(ShipBehaviorEvent.TYPE_LOITERING);
        event.setMmsi(mmsi);
        event.setStartTime(TrajectoryUtils.formatTime(start));
        event.setEndTime(TrajectoryUtils.formatTime(end));
        event.setDurationMinutes(segment.getDurationMinutes());
        event.setStartLon(start.getLon());
        event.setStartLat(start.getLat());
        event.setEndLon(end.getLon());
        event.setEndLat(end.getLat());
        event.setMaxDistanceNm(radiusNm);
        event.setAvgSpeed(TrajectoryUtils.averageSpeed(points));
        event.setPointCount(points.size());
        event.setRuleVersion(rule.getRuleVersion());
        events.add(event);
    }

    public static class LoiteringRule {
        private final double maxSpeed;
        private final long minDurationMinutes;
        private final double maxRadiusNm;
        private final int minPoints;
        private final String ruleVersion;

        public LoiteringRule(double maxSpeed, long minDurationMinutes, double maxRadiusNm,
                             int minPoints, String ruleVersion) {
            this.maxSpeed = maxSpeed;
            this.minDurationMinutes = minDurationMinutes;
            this.maxRadiusNm = maxRadiusNm;
            this.minPoints = minPoints;
            this.ruleVersion = ruleVersion;
        }

        public double getMaxSpeed() {
            return maxSpeed;
        }

        public long getMinDurationMinutes() {
            return minDurationMinutes;
        }

        public double getMaxRadiusNm() {
            return maxRadiusNm;
        }

        public int getMinPoints() {
            return minPoints;
        }

        public String getRuleVersion() {
            return ruleVersion;
        }
    }
}
