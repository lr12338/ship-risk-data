package com.hifleet.analysis.portcall.flink;

import com.hifleet.analysis.portcall.model.AisPoint;
import org.apache.flink.api.common.functions.FilterFunction;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Optional runtime scope filter for local live validation or regional canaries.
 */
public class AisScopeFilter implements FilterFunction<AisPoint> {

    private static final long serialVersionUID = 1L;

    private final Set<Long> mmsiWhitelist;
    private final Double minLon;
    private final Double maxLon;
    private final Double minLat;
    private final Double maxLat;

    public AisScopeFilter(Set<Long> mmsiWhitelist, Double minLon, Double maxLon, Double minLat, Double maxLat) {
        this.mmsiWhitelist = normalize(mmsiWhitelist);
        this.minLon = minLon;
        this.maxLon = maxLon;
        this.minLat = minLat;
        this.maxLat = maxLat;
    }

    @Override
    public boolean filter(AisPoint point) {
        if (point == null) {
            return false;
        }
        if (!mmsiWhitelist.isEmpty() && (point.getMmsi() == null || !mmsiWhitelist.contains(point.getMmsi()))) {
            return false;
        }
        if (!hasBbox()) {
            return true;
        }
        if (!point.hasValidPosition()) {
            return false;
        }
        return point.getLon() >= minLon
            && point.getLon() <= maxLon
            && point.getLat() >= minLat
            && point.getLat() <= maxLat;
    }

    private boolean hasBbox() {
        return minLon != null && maxLon != null && minLat != null && maxLat != null;
    }

    private static Set<Long> normalize(Set<Long> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<Long>(values);
    }
}
