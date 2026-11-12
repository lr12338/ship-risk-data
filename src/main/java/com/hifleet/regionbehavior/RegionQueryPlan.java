package com.hifleet.regionbehavior;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RegionQueryPlan {
    private final List<RegionReplaySlice> slices;

    public RegionQueryPlan(List<RegionReplaySlice> slices) {
        this.slices = Collections.unmodifiableList(new ArrayList<RegionReplaySlice>(slices));
    }

    public List<RegionReplaySlice> getSlices() {
        return slices;
    }
}
