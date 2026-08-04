package com.hifleet.tankeropensea;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LoiteringShapeEvidence {
    private final Map<String, Object> values;
    private final List<String> shapeEvidence;

    LoiteringShapeEvidence(Map<String, Object> values, List<String> shapeEvidence) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(values));
        this.shapeEvidence = Collections.unmodifiableList(new ArrayList<String>(shapeEvidence));
    }

    public Map<String, Object> getValues() { return values; }
    public List<String> getShapeEvidence() { return shapeEvidence; }
    public int getShapeEvidenceCount() { return shapeEvidence.size(); }
    public double getDurationMinutes() { return number("durationMinutes"); }
    public double getStationaryTimeRatio() { return number("stationaryTimeRatio"); }
    public double getSlowSpeedTimeRatio() { return number("slowSpeedTimeRatio"); }
    public double getManeuverSpeedTimeRatio() { return number("maneuverSpeedTimeRatio"); }
    public double getTrackCoverageRatio() { return number("trackCoverageRatio"); }
    public double getMaxAisGapMinutes() { return number("maxAisGapMinutes"); }
    public double getStraightTransitScore() { return number("straightTransitScore"); }

    private double number(String key) {
        Object value = values.get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }
}
