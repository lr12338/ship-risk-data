package com.hifleet.sts.service;

import com.hifleet.regionbehavior.RegionGeometry;
import com.hifleet.regionbehavior.RegionQueryPlan;
import com.hifleet.regionbehavior.RegionReplaySlice;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class StsFleetDiscoveryPlanner {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private final StsFleetDiscoveryConfig config;

    @Autowired
    public StsFleetDiscoveryPlanner(StsFleetDiscoveryConfig config) {
        this.config = config;
    }

    public Plan plan(RegionGeometry region, LocalDateTime from, LocalDateTime to) {
        validate(from, to);
        double cellDegrees = config.getInitialSpatialCellDegrees();
        int timeHours = config.getInitialTimeSliceHours();
        List<Cell> cells = cells(region, cellDegrees);
        int timeBuckets = timeBuckets(from, to, timeHours);
        Envelope envelope = region.getAnalysisEnvelope();

        while ((long) cells.size() * timeBuckets > config.getMaxInitialSlicesPerRegion()
                && cellDegrees < Math.max(envelope.getWidth(), envelope.getHeight())) {
            cellDegrees *= 2.0;
            cells = cells(region, cellDegrees);
        }
        while ((long) cells.size() * timeBuckets > config.getMaxInitialSlicesPerRegion()) {
            timeHours *= 2;
            timeBuckets = timeBuckets(from, to, timeHours);
        }

        List<RegionReplaySlice> slices = new ArrayList<RegionReplaySlice>();
        for (Cell cell : cells) {
            LocalDateTime start = from;
            while (start.isBefore(to)) {
                LocalDateTime end = start.plusHours(timeHours);
                if (end.isAfter(to)) end = to;
                slices.add(RegionReplaySlice.root(region.getFingerprint(), cell.id,
                        cell.minLon, cell.maxLon, cell.minLat, cell.maxLat, start, end));
                start = end;
            }
        }
        if (slices.size() > config.getMaxInitialSlicesPerRegion()) {
            throw new IllegalStateException("STS discovery plan exceeds maxInitialSlicesPerRegion="
                    + config.getMaxInitialSlicesPerRegion());
        }
        return new Plan(new RegionQueryPlan(slices), cells.size(), timeBuckets,
                cellDegrees, timeHours);
    }

    private List<Cell> cells(RegionGeometry region, double cellDegrees) {
        Envelope envelope = region.getAnalysisEnvelope();
        Geometry analysis = region.getAnalysisRegion();
        int columns = Math.max(1, (int) Math.ceil(envelope.getWidth() / cellDegrees));
        int rows = Math.max(1, (int) Math.ceil(envelope.getHeight() / cellDegrees));
        List<Cell> result = new ArrayList<Cell>();
        for (int row = 0; row < rows; row++) {
            double minLat = envelope.getMinY() + row * cellDegrees;
            double maxLat = Math.min(envelope.getMaxY(), minLat + cellDegrees);
            for (int column = 0; column < columns; column++) {
                double minLon = envelope.getMinX() + column * cellDegrees;
                double maxLon = Math.min(envelope.getMaxX(), minLon + cellDegrees);
                Envelope candidate = new Envelope(minLon, maxLon, minLat, maxLat);
                if (analysis.intersects(GEOMETRY_FACTORY.toGeometry(candidate))) {
                    result.add(new Cell("r" + row + "c" + column,
                            minLon, maxLon, minLat, maxLat));
                }
            }
        }
        return result;
    }

    private int timeBuckets(LocalDateTime from, LocalDateTime to, int hours) {
        long minutes = Duration.between(from, to).toMinutes();
        long bucketMinutes = hours * 60L;
        return (int) ((minutes + bucketMinutes - 1L) / bucketMinutes);
    }

    private void validate(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || !from.isBefore(to)
                || config.getInitialTimeSliceHours() <= 0
                || config.getInitialSpatialCellDegrees() <= 0.0
                || config.getMaxInitialSlicesPerRegion() <= 0) {
            throw new IllegalArgumentException("invalid STS fleet discovery plan configuration");
        }
    }

    public static final class Plan {
        private final RegionQueryPlan queryPlan;
        private final int spatialCells;
        private final int timeBuckets;
        private final double effectiveSpatialCellDegrees;
        private final int effectiveTimeSliceHours;

        Plan(RegionQueryPlan queryPlan, int spatialCells, int timeBuckets,
             double effectiveSpatialCellDegrees, int effectiveTimeSliceHours) {
            this.queryPlan = queryPlan;
            this.spatialCells = spatialCells;
            this.timeBuckets = timeBuckets;
            this.effectiveSpatialCellDegrees = effectiveSpatialCellDegrees;
            this.effectiveTimeSliceHours = effectiveTimeSliceHours;
        }

        public RegionQueryPlan getQueryPlan() { return queryPlan; }
        public int getSpatialCells() { return spatialCells; }
        public int getTimeBuckets() { return timeBuckets; }
        public int getInitialSlices() { return queryPlan.getSlices().size(); }
        public double getEffectiveSpatialCellDegrees() { return effectiveSpatialCellDegrees; }
        public int getEffectiveTimeSliceHours() { return effectiveTimeSliceHours; }
    }

    private static final class Cell {
        private final String id;
        private final double minLon;
        private final double maxLon;
        private final double minLat;
        private final double maxLat;

        private Cell(String id, double minLon, double maxLon, double minLat, double maxLat) {
            this.id = id;
            this.minLon = minLon;
            this.maxLon = maxLon;
            this.minLat = minLat;
            this.maxLat = maxLat;
        }
    }
}
