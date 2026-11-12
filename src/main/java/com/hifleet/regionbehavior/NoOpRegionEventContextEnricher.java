package com.hifleet.regionbehavior;

import org.springframework.stereotype.Component;

@Component
public class NoOpRegionEventContextEnricher implements RegionEventContextEnricher {
    @Override
    public RegionMatchedEvent enrich(RegionMatchedEvent event) {
        return event;
    }
}
