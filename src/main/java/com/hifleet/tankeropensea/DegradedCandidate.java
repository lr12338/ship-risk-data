package com.hifleet.tankeropensea;

import com.hifleet.domain.ShipBehaviorTrace;

public class DegradedCandidate {
    private final ShipBehaviorTrace trace;
    private final String reason;

    public DegradedCandidate(ShipBehaviorTrace trace, String reason) {
        this.trace = trace;
        this.reason = reason;
    }

    public ShipBehaviorTrace getTrace() { return trace; }
    public String getReason() { return reason; }
}
