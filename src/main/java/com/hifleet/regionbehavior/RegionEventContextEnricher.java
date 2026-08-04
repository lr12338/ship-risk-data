package com.hifleet.regionbehavior;

public interface RegionEventContextEnricher {
    RegionMatchedEvent enrich(RegionMatchedEvent event);
}
