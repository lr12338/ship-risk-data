package com.hifleet.tankeropensea;

import com.hifleet.trajectory.TrajectoryPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class GeoMath {
    private static final double EARTH_RADIUS_NM = 3440.065;

    private GeoMath() {
    }

    static double distanceNm(TrajectoryPoint a, TrajectoryPoint b) {
        double lat1 = Math.toRadians(a.getLat());
        double lat2 = Math.toRadians(b.getLat());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.getLon() - a.getLon());
        double h = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        return EARTH_RADIUS_NM * 2.0 * Math.atan2(Math.sqrt(h), Math.sqrt(1.0 - h));
    }

    static double bearingDegree(TrajectoryPoint from, TrajectoryPoint to) {
        double lat1 = Math.toRadians(from.getLat());
        double lat2 = Math.toRadians(to.getLat());
        double dLon = Math.toRadians(to.getLon() - from.getLon());
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        return normalizeDegree(Math.toDegrees(Math.atan2(y, x)));
    }

    static double circularDifference(double a, double b) {
        double difference = Math.abs(normalizeDegree(a) - normalizeDegree(b));
        return difference > 180.0 ? 360.0 - difference : difference;
    }

    static double signedDifference(double bearing, double course) {
        double value = normalizeDegree(bearing) - normalizeDegree(course);
        if (value > 180.0) value -= 360.0;
        if (value < -180.0) value += 360.0;
        return value;
    }

    static double normalizeDegree(double value) {
        double result = value % 360.0;
        return result < 0.0 ? result + 360.0 : result;
    }

    static double percentile(List<Double> source, double percentile) {
        if (source == null || source.isEmpty()) return 0.0;
        List<Double> values = new ArrayList<Double>(source);
        Collections.sort(values);
        double index = Math.max(0.0, Math.min(1.0, percentile)) * (values.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) return values.get(lower);
        double fraction = index - lower;
        return values.get(lower) * (1.0 - fraction) + values.get(upper) * fraction;
    }
}
