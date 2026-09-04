package com.hifleet.sts.service;

import com.hifleet.regionbehavior.RegionBehaviorConfig;
import com.hifleet.regionbehavior.RegionGeometry;
import com.hifleet.regionbehavior.RegionReplaySlice;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StsFleetDiscoveryPlannerTest {

    @Test
    public void coversEveryCellAndTimeRangeWithoutGapOrOverlap() {
        StsFleetDiscoveryPlanner.Plan result = planner(12, 1.0, 512).plan(
                region("POLYGON((100 10,102 10,102 12,100 12,100 10))"),
                time(1, 0), time(2, 0));

        Map<String, List<RegionReplaySlice>> byCell = byCell(result.getQueryPlan().getSlices());
        assertEquals(result.getSpatialCells(), byCell.size());
        for (List<RegionReplaySlice> slices : byCell.values()) {
            Collections.sort(slices, Comparator.comparing(RegionReplaySlice::getStart));
            assertEquals(time(1, 0), slices.get(0).getStart());
            assertEquals(time(2, 0), slices.get(slices.size() - 1).getEnd());
            for (int i = 1; i < slices.size(); i++) {
                assertEquals(slices.get(i - 1).getEnd(), slices.get(i).getStart());
            }
        }
        assertEquals(2, result.getTimeBuckets());
        assertEquals(result.getSpatialCells() * result.getTimeBuckets(),
                result.getInitialSlices());
    }

    @Test
    public void excludesBoundingBoxCellsInsidePolygonHole() {
        StsFleetDiscoveryPlanner.Plan result = planner(24, 1.0, 512).plan(
                region("POLYGON((0 0,3 0,3 3,0 3,0 0),(0.8 0.8,0.8 2.2,2.2 2.2,2.2 0.8,0.8 0.8))"),
                time(1, 0), time(2, 0));

        boolean centerCellPresent = false;
        for (RegionReplaySlice slice : result.getQueryPlan().getSlices()) {
            if (slice.getMinLon() == 1.0 && slice.getMaxLon() == 2.0
                    && slice.getMinLat() == 1.0 && slice.getMaxLat() == 2.0) {
                centerCellPresent = true;
            }
        }
        assertFalse(centerCellPresent);
    }

    @Test
    public void deterministicallyCoarsensLargeSevenDayPlanToBudget() {
        StsFleetDiscoveryPlanner planner = planner(12, 1.0, 512);
        RegionGeometry region = region("POLYGON((0 0,20 0,20 20,0 20,0 0))");

        StsFleetDiscoveryPlanner.Plan first = planner.plan(region, time(1, 0), time(8, 0));
        StsFleetDiscoveryPlanner.Plan second = planner.plan(region, time(1, 0), time(8, 0));

        assertTrue(first.getInitialSlices() <= 512);
        assertEquals(first.getInitialSlices(), second.getInitialSlices());
        assertEquals(first.getEffectiveSpatialCellDegrees(),
                second.getEffectiveSpatialCellDegrees(), 0.0);
        assertTrue(first.getEffectiveSpatialCellDegrees() > 1.0
                || first.getEffectiveTimeSliceHours() > 12);
    }

    private StsFleetDiscoveryPlanner planner(int hours, double degrees, int max) {
        StsFleetDiscoveryConfig config = new StsFleetDiscoveryConfig();
        config.setInitialTimeSliceHours(hours);
        config.setInitialSpatialCellDegrees(degrees);
        config.setMaxInitialSlicesPerRegion(max);
        return new StsFleetDiscoveryPlanner(config);
    }

    private RegionGeometry region(String wkt) {
        return RegionGeometry.parse(wkt, 0, RegionBehaviorConfig.defaults());
    }

    private LocalDateTime time(int day, int hour) {
        return LocalDateTime.of(2026, 1, day, hour, 0);
    }

    private Map<String, List<RegionReplaySlice>> byCell(List<RegionReplaySlice> slices) {
        Map<String, List<RegionReplaySlice>> result =
                new HashMap<String, List<RegionReplaySlice>>();
        for (RegionReplaySlice slice : slices) {
            List<RegionReplaySlice> values = result.get(slice.getCellId());
            if (values == null) {
                values = new ArrayList<RegionReplaySlice>();
                result.put(slice.getCellId(), values);
            }
            values.add(slice);
        }
        return result;
    }
}
