package com.hifleet.regionbehavior;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RegionQueryPlanner {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private final RegionBehaviorConfig config;

    public RegionQueryPlanner(RegionBehaviorConfig config) {
        this.config = config;
    }

    public RegionQueryPlan plan(RegionBehaviorJobParams params, RegionGeometry region) {
        if (config.getSpatialCellMaxDegrees() <= 0.0
                || config.getTimeSliceMinutes() <= 0
                || config.getMinTimeSliceMinutes() <= 0
                || config.getMaxQuerySlices() <= 0
                || config.getMaxSpatialRefinementDepth() < 0
                || config.getMaxSpatialRefinementDepth() > 8) {
            throw new IllegalArgumentException("invalid region query planner configuration");
        }
        List<RegionReplaySlice> slices = new ArrayList<RegionReplaySlice>();
        Envelope envelope = region.getAnalysisEnvelope();
        double cellSize = config.getSpatialCellMaxDegrees();
        int columns = Math.max(1, (int) Math.ceil(envelope.getWidth() / cellSize));
        int rows = Math.max(1, (int) Math.ceil(envelope.getHeight() / cellSize));
        LocalDateTime runStart = RegionBehaviorTimes.parse(params.getStartTime());
        LocalDateTime runEnd = RegionBehaviorTimes.parse(params.getEndTime());
        for (int row = 0; row < rows; row++) {
            double minLat = envelope.getMinY() + row * cellSize;
            double maxLat = Math.min(envelope.getMaxY(), minLat + cellSize);
            for (int column = 0; column < columns; column++) {
                double minLon = envelope.getMinX() + column * cellSize;
                double maxLon = Math.min(envelope.getMaxX(), minLon + cellSize);
                Envelope cell = new Envelope(minLon, maxLon, minLat, maxLat);
                if (!region.getAnalysisRegion().intersects(GEOMETRY_FACTORY.toGeometry(cell))) {
                    continue;
                }
                LocalDateTime start = runStart;
                while (start.isBefore(runEnd)) {
                    LocalDateTime end = start.plusMinutes(params.effectiveTimeSliceMinutes(config));
                    if (end.isAfter(runEnd)) {
                        end = runEnd;
                    }
                    String cellId = "r" + row + "c" + column;
                    slices.add(RegionReplaySlice.root(region.getFingerprint(), cellId,
                            minLon, maxLon, minLat, maxLat, start, end));
                    if (slices.size() > config.getMaxQuerySlices()) {
                        throw new IllegalArgumentException("query plan exceeds maxQuerySlices="
                                + config.getMaxQuerySlices());
                    }
                    start = end;
                }
            }
        }
        return new RegionQueryPlan(slices);
    }
}
