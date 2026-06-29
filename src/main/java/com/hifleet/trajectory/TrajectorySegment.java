package com.hifleet.trajectory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public class TrajectorySegment {

    private final List<TrajectoryPoint> points;

    public TrajectorySegment(List<TrajectoryPoint> points) {
        this.points = points;
    }

    public List<TrajectoryPoint> getPoints() {
        return points;
    }

    public TrajectoryPoint getStartPoint() {
        return points.get(0);
    }

    public TrajectoryPoint getEndPoint() {
        return points.get(points.size() - 1);
    }

    public LocalDateTime getStartTime() {
        return getStartPoint().getTime();
    }

    public LocalDateTime getEndTime() {
        return getEndPoint().getTime();
    }

    public long getDurationMinutes() {
        return Duration.between(getStartTime(), getEndTime()).toMinutes();
    }
}
