package com.hifleet.tankeropensea;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AlongsideGeometryEvidence {
    private final Map<String, Object> values;

    AlongsideGeometryEvidence(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(values));
    }

    public Map<String, Object> getValues() { return values; }
    public int getSynchronizedPointCount() { return integer("synchronizedPointCount"); }
    public double getSynchronizedCoverageRatio() { return number("synchronizedCoverageRatio"); }
    public double getSideBySideRatio() { return number("sideBySideRatio"); }
    public double getRelativeBearingStability() { return number("relativeBearingStability"); }
    public double getDistanceStabilityRatio() { return number("distanceStabilityRatio"); }
    public double getRelativeSpeedP90Kn() { return number("relativeSpeedP90Kn"); }
    public double getHeadingDifferenceP90Deg() { return number("headingDifferenceP90Deg"); }
    public double getFollowingRatio() { return number("followingRatio"); }
    public double getOvertakingRatio() { return number("overtakingRatio"); }
    public double getCrossingRatio() { return number("crossingRatio"); }
    public double getDurationMinutes() { return number("durationMinutes"); }
    public double getSpeedInRangeTimeRatio() { return number("speedInRangeTimeRatio"); }
    public double getStationaryTimeRatio() { return number("stationaryTimeRatio"); }

    private double number(String key) {
        Object value = values.get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    private int integer(String key) { return (int) Math.round(number(key)); }
}
