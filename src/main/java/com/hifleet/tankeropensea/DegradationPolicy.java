package com.hifleet.tankeropensea;

final class DegradationPolicy {
    private DegradationPolicy() {
    }

    static String reason(PortAnchorBerthExclusionService.Snapshot snapshot,
                         ExclusionDecision... decisions) {
        if (decisions != null) {
            for (ExclusionDecision decision : decisions) {
                if (decision != null && decision.isQuarantined()) {
                    return "GEOMETRY_QUARANTINE_HIT";
                }
            }
        }
        return snapshot.getHealth() == ExclusionHealth.DEGRADED
                ? "EXCLUSION_SNAPSHOT_DEGRADED" : null;
    }
}
