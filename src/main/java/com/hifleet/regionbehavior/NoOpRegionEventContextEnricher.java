package com.hifleet.regionbehavior;

import com.hifleet.domain.ShipBehaviorTrace;
import org.springframework.stereotype.Component;

@Component
public class NoOpRegionEventContextEnricher implements RegionEventContextEnricher {
    @Override
    public RegionMatchedEvent enrich(RegionMatchedEvent event, ShipBehaviorTrace trace) {
        return event;
    }

    @Override
    public String name() {
        return "NO_OP";
    }
}
