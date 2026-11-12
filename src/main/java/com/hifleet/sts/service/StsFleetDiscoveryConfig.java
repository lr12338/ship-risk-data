package com.hifleet.sts.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StsFleetDiscoveryConfig {
    @Value("${ship.behavior.sts.backfill.discovery.initial-time-slice-hours:12}")
    private int initialTimeSliceHours = 12;
    @Value("${ship.behavior.sts.backfill.discovery.initial-spatial-cell-degrees:1.0}")
    private double initialSpatialCellDegrees = 1.0;
    @Value("${ship.behavior.sts.backfill.discovery.max-initial-slices-per-region:512}")
    private int maxInitialSlicesPerRegion = 512;

    public int getInitialTimeSliceHours() { return initialTimeSliceHours; }
    public double getInitialSpatialCellDegrees() { return initialSpatialCellDegrees; }
    public int getMaxInitialSlicesPerRegion() { return maxInitialSlicesPerRegion; }
    public void setInitialTimeSliceHours(int value) { initialTimeSliceHours = value; }
    public void setInitialSpatialCellDegrees(double value) { initialSpatialCellDegrees = value; }
    public void setMaxInitialSlicesPerRegion(int value) { maxInitialSlicesPerRegion = value; }
}
