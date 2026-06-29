package com.hifleet.analysis.portcall.flink;

import com.hifleet.analysis.portcall.model.AisPoint;
import org.apache.flink.api.common.functions.FilterFunction;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Keeps only AIS points that can participate in event-time keyed processing.
 */
public class ValidAisPointFilter implements FilterFunction<AisPoint> {

    private static final long serialVersionUID = 1L;

    private final Set<String> acceptedDataStates;

    public ValidAisPointFilter() {
        this(Collections.<String>emptySet());
    }

    public ValidAisPointFilter(Set<String> acceptedDataStates) {
        this.acceptedDataStates = normalize(acceptedDataStates);
    }

    @Override
    public boolean filter(AisPoint point) {
        return point != null
            && point.getMmsi() != null
            && point.getUpdateTime() != null
            && point.hasValidPosition()
            && isAcceptedDataState(point.getDataState());
    }

    private boolean isAcceptedDataState(String dataState) {
        if (acceptedDataStates.isEmpty()) {
            return true;
        }
        return dataState != null && acceptedDataStates.contains(dataState.trim().toLowerCase());
    }

    private Set<String> normalize(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new HashSet<String>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                normalized.add(value.trim().toLowerCase());
            }
        }
        return normalized;
    }
}
