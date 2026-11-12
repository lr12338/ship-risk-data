package com.hifleet.job.services;

import com.alibaba.fastjson.JSON;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.service.ShipAlongsideDetector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Exposes the existing production suppression policies without changing their implementation files.
 */
public final class RegionProductionEventFilterAdapter {
    private RegionProductionEventFilterAdapter() {
    }

    public static List<ShipBehaviorTrace> validNonWeakLoitering(List<ShipBehaviorTrace> traces) {
        if (traces == null || traces.isEmpty()) {
            return Collections.emptyList();
        }
        List<ShipBehaviorTrace> copies = new ArrayList<ShipBehaviorTrace>();
        Map<ShipBehaviorTrace, ShipBehaviorTrace> originals =
                new IdentityHashMap<ShipBehaviorTrace, ShipBehaviorTrace>();
        for (ShipBehaviorTrace trace : traces) {
            if (trace == null || trace.getEvent() == null) {
                continue;
            }
            ShipBehaviorEvent eventCopy = JSON.parseObject(JSON.toJSONString(trace.getEvent()),
                    ShipBehaviorEvent.class);
            ShipBehaviorTrace copy = new ShipBehaviorTrace(eventCopy,
                    trace.getOwnPoints(), trace.getTargetPoints());
            copies.add(copy);
            originals.put(copy, trace);
        }
        List<ShipBehaviorTrace> result = new ArrayList<ShipBehaviorTrace>();
        for (ShipBehaviorTrace stored : LoiteringProductionEventFilter
                .validNonWeak(copies).getStoredTraces()) {
            result.add(originals.get(stored));
        }
        return result;
    }

    public static List<ShipBehaviorTrace> validNonWeakAlongside(List<ShipBehaviorTrace> traces) {
        if (traces == null || traces.isEmpty()) {
            return Collections.emptyList();
        }
        List<ShipBehaviorTrace> result = new ArrayList<ShipBehaviorTrace>();
        for (ShipBehaviorTrace trace : traces) {
            ShipBehaviorEvent event = trace == null ? null : trace.getEvent();
            if (event != null
                    && !ShipAlongsideDetector.SUB_TYPE_WEAK.equals(event.getEventSubType())
                    && ShipAlongsideDetector.VALID_RISK_EVENT.equals(event.getExclusionFlag())) {
                result.add(trace);
            }
        }
        return result;
    }
}
