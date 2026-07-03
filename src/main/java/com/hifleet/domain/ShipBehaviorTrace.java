package com.hifleet.domain;

import com.hifleet.trajectory.TrajectoryPoint;

import java.util.List;

/**
 * 行为事件与命中轨迹点快照。
 */
public class ShipBehaviorTrace {

    private final ShipBehaviorEvent event;
    private final List<TrajectoryPoint> ownPoints;
    private final List<TrajectoryPoint> targetPoints;

    public ShipBehaviorTrace(ShipBehaviorEvent event, List<TrajectoryPoint> ownPoints, List<TrajectoryPoint> targetPoints) {
        this.event = event;
        this.ownPoints = ownPoints;
        this.targetPoints = targetPoints;
    }

    public ShipBehaviorEvent getEvent() {
        return event;
    }

    public List<TrajectoryPoint> getOwnPoints() {
        return ownPoints;
    }

    public List<TrajectoryPoint> getTargetPoints() {
        return targetPoints;
    }
}
