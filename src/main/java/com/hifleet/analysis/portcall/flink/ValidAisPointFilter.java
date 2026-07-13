package com.hifleet.analysis.portcall.flink;

import com.hifleet.analysis.portcall.model.AisPoint;
import org.apache.flink.api.common.functions.FilterFunction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Keeps only AIS points that can participate in event-time keyed processing.
 */
public class ValidAisPointFilter implements FilterFunction<AisPoint> {

    private static final long serialVersionUID = 1L;
    private static final Duration DEFAULT_MAX_FUTURE_SKEW = Duration.ofMinutes(5);

    private final Set<String> acceptedDataStates;
    private final Clock clock;
    private final Duration maxFutureSkew;

    public ValidAisPointFilter() {
        this(Collections.<String>emptySet());
    }

    public ValidAisPointFilter(Set<String> acceptedDataStates) {
        this(acceptedDataStates, Clock.systemUTC(), DEFAULT_MAX_FUTURE_SKEW);
    }

    ValidAisPointFilter(Set<String> acceptedDataStates, Clock clock, Duration maxFutureSkew) {
        this.acceptedDataStates = normalize(acceptedDataStates);
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.maxFutureSkew = maxFutureSkew == null ? DEFAULT_MAX_FUTURE_SKEW : maxFutureSkew;
    }

    @Override
    public boolean filter(AisPoint point) {
        //System.out.println(point.getMmsi()+",filter");
        return point != null
            && point.getMmsi() != null
            && point.getUpdateTime() != null
            && !isFutureTime(point.getUpdateTime())
            && point.hasValidPosition()
            && isAcceptedDataState(point.getDataState());
    }

    private boolean isFutureTime(Instant updateTime) {
        return updateTime.isAfter(Instant.now(clock).plus(maxFutureSkew));
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
