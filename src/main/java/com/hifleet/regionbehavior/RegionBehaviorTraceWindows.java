package com.hifleet.regionbehavior;

import com.hifleet.domain.ShipBehaviorTrace;

import java.time.LocalDateTime;

final class RegionBehaviorTraceWindows {
    private RegionBehaviorTraceWindows() {
    }

    static boolean overlaps(ShipBehaviorTrace trace, LocalDateTime start, LocalDateTime end) {
        if (trace == null || trace.getEvent() == null
                || trace.getEvent().getStartTime() == null || trace.getEvent().getEndTime() == null) {
            return false;
        }
        LocalDateTime eventStart = RegionBehaviorTimes.parse(trace.getEvent().getStartTime());
        LocalDateTime eventEnd = RegionBehaviorTimes.parse(trace.getEvent().getEndTime());
        return eventStart.isBefore(end) && eventEnd.isAfter(start);
    }
}
