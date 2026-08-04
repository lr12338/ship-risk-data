package com.hifleet.tankeropensea;

import com.hifleet.trajectory.TrajectoryPoint;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class TankerOpenSeaTrajectory {
    private TankerOpenSeaTrajectory() {
    }

    static List<TrajectoryPoint> clean(List<TrajectoryPoint> source) {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        if (source == null) return result;
        for (TrajectoryPoint point : source) {
            if (point != null && point.getTime() != null
                    && point.getLon() >= -180.0 && point.getLon() <= 180.0
                    && point.getLat() >= -90.0 && point.getLat() <= 90.0
                    && point.getSpeed() >= 0.0 && point.getSpeed() <= 80.0) {
                result.add(point);
            }
        }
        Collections.sort(result, Comparator.comparing(TrajectoryPoint::getTime));
        return result;
    }

    static List<List<TrajectoryPoint>> lowSpeedEpisodes(
            List<TrajectoryPoint> source, double minSpeed, double maxSpeed,
            long minDurationMinutes, long maxGapMinutes) {
        List<List<TrajectoryPoint>> result = new ArrayList<List<TrajectoryPoint>>();
        List<TrajectoryPoint> current = new ArrayList<TrajectoryPoint>();
        for (TrajectoryPoint point : clean(source)) {
            boolean gap = !current.isEmpty()
                    && Duration.between(current.get(current.size() - 1).getTime(),
                    point.getTime()).toMinutes() > maxGapMinutes;
            if (gap || point.getSpeed() < minSpeed || point.getSpeed() > maxSpeed) {
                collect(current, minDurationMinutes, result);
                current = new ArrayList<TrajectoryPoint>();
            }
            if (point.getSpeed() >= minSpeed && point.getSpeed() <= maxSpeed) {
                current.add(point);
            }
        }
        collect(current, minDurationMinutes, result);
        return result;
    }

    static List<TrajectoryPoint> between(List<TrajectoryPoint> points,
                                         LocalDateTime start, LocalDateTime end) {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        for (TrajectoryPoint point : clean(points)) {
            if (!point.getTime().isBefore(start) && point.getTime().isBefore(end)) {
                result.add(point);
            }
        }
        return result;
    }

    private static void collect(List<TrajectoryPoint> current, long minDurationMinutes,
                                List<List<TrajectoryPoint>> target) {
        if (current.size() < 2) return;
        long duration = Duration.between(current.get(0).getTime(),
                current.get(current.size() - 1).getTime()).toMinutes();
        if (duration >= minDurationMinutes) {
            target.add(new ArrayList<TrajectoryPoint>(current));
        }
    }
}
