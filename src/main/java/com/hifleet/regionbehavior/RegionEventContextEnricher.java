package com.hifleet.regionbehavior;

import com.hifleet.domain.ShipBehaviorTrace;

public interface RegionEventContextEnricher {
    RegionMatchedEvent enrich(RegionMatchedEvent event, ShipBehaviorTrace trace);
    String name();
}
