package com.hifleet.trajectory;

import com.hifleet.utils.GeometryUtils;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class TrajectoryUtils {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TrajectoryUtils() {
    }

    public static String formatTime(TrajectoryPoint point) {
        return point.getTime().format(TIME_FORMATTER);
    }

    public static double distanceNm(TrajectoryPoint a, TrajectoryPoint b) {
        return GeometryUtils.getNauticalMileDistance(a.getLon(), a.getLat(), b.getLon(), b.getLat());
    }

    public static long minutesBetween(TrajectoryPoint a, TrajectoryPoint b) {
        return Duration.between(a.getTime(), b.getTime()).toMinutes();
    }

    public static double averageSpeed(List<TrajectoryPoint> points) {
        if (points == null || points.isEmpty()) { return Double.NaN; }
        double total = 0.0;
        int valid = 0;
        for (TrajectoryPoint point : points) {
            if (point != null && point.isSpeedValid()) {
                total += point.getSpeed();
                valid++;
            }
        }
        return valid == 0 ? Double.NaN : total / valid;
    }

    public static double maxDistanceFromStartNm(List<TrajectoryPoint> points) {
        if (points == null || points.size() < 2) {
            return 0.0;
        }
        TrajectoryPoint start = points.get(0);
        double max = 0.0;
        for (TrajectoryPoint point : points) {
            max = Math.max(max, distanceNm(start, point));
        }
        return max;
    }
}
