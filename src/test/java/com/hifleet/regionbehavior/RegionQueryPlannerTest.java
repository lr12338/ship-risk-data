package com.hifleet.regionbehavior;

import org.junit.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RegionQueryPlannerTest {

    @Test
    public void createsDeterministicTimeAndSpaceSlices() {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        config.setSpatialCellMaxDegrees(0.25);
        config.setTimeSliceMinutes(30);
        RegionGeometry geometry = RegionGeometry.parse(
                "POLYGON((121 30,121.5 30,121.5 30.5,121 30.5,121 30))", 0.0, config);
        RegionBehaviorJobParams params = params();

        RegionQueryPlan first = new RegionQueryPlanner(config).plan(params, geometry);
        RegionQueryPlan second = new RegionQueryPlanner(config).plan(params, geometry);

        assertEquals(8, first.getSlices().size());
        Set<String> ids = new HashSet<String>();
        for (RegionReplaySlice slice : first.getSlices()) {
            ids.add(slice.getSliceId());
            assertEquals(30L, Duration.between(slice.getStart(), slice.getEnd()).toMinutes());
        }
        assertEquals(8, ids.size());
        assertEquals(first.getSlices().get(0).getSliceId(), second.getSlices().get(0).getSliceId());
    }

    @Test
    public void splitsTruncatedSliceByTimeThenSpace() {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        config.setMinTimeSliceMinutes(5);
        RegionReplaySlice slice = RegionReplaySlice.root("fp", "c", 121, 122, 30, 31,
                RegionBehaviorTimes.parse("2026-06-01 00:00:00"),
                RegionBehaviorTimes.parse("2026-06-01 00:30:00"));
        assertEquals(2, slice.splitTime().size());
        RegionReplaySlice minimum = RegionReplaySlice.root("fp", "c", 121, 122, 30, 31,
                RegionBehaviorTimes.parse("2026-06-01 00:00:00"),
                RegionBehaviorTimes.parse("2026-06-01 00:05:00"));
        assertTrue(!minimum.canSplitTime(config));
        assertEquals(4, minimum.splitSpace().size());
        RegionReplaySlice sixMinutes = RegionReplaySlice.root("fp", "c", 121, 122, 30, 31,
                RegionBehaviorTimes.parse("2026-06-01 00:00:00"),
                RegionBehaviorTimes.parse("2026-06-01 00:06:00"));
        assertTrue(!sixMinutes.canSplitTime(config));
    }

    private RegionBehaviorJobParams params() {
        RegionBehaviorJobParams params = new RegionBehaviorJobParams();
        params.setRegionId("r");
        params.setBatchNo("b");
        params.setStartTime("2026-06-01 00:00:00");
        params.setEndTime("2026-06-01 01:00:00");
        return params;
    }
}
