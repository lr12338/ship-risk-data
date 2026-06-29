package com.hifleet.analysis.portcall.preprocess;

import com.hifleet.analysis.portcall.model.AisPoint;

import java.time.Duration;

/**
 * Jump-point pre-check based on implied speed between consecutive points.
 */
public class JumpPointFilter {

    private final double maxImpliedSpeedKnots;

    public JumpPointFilter(double maxImpliedSpeedKnots) {
        this.maxImpliedSpeedKnots = maxImpliedSpeedKnots;
    }

    /**
     * Checks if current point is a jump against previous point.
     *
     * @param previous previous accepted point
     * @param current current candidate point
     * @return true when implied speed exceeds threshold
     */
    public boolean isJumpPoint(AisPoint previous, AisPoint current) {
        if (previous == null || current == null) {
            return false;
        }
        if (!previous.hasValidPosition() || !current.hasValidPosition()) {
            return false;
        }
        if (previous.getUpdateTime() == null || current.getUpdateTime() == null) {
            return false;
        }

        long seconds = Duration.between(previous.getUpdateTime(), current.getUpdateTime()).getSeconds();
        if (seconds <= 0) {
            return false;
        }

        double distanceNm = distanceMeters(previous.getLat(), previous.getLon(), current.getLat(), current.getLon()) / 1852.0;
        double hours = seconds / 3600.0;
        double impliedKnots = distanceNm / hours;
        return impliedKnots > maxImpliedSpeedKnots;
    }

    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final double radius = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return radius * c;
    }
}