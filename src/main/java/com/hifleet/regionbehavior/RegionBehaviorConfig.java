package com.hifleet.regionbehavior;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RegionBehaviorConfig {

    @Value("${ship.behavior.region.enabled:false}")
    private boolean enabled = false;
    @Value("${ship.behavior.region.dry-run-only:true}")
    private boolean dryRunOnly = true;
    @Value("${ship.behavior.region.write-enabled:false}")
    private boolean writeEnabled = false;
    @Value("${ship.behavior.region.output-root:var/region-behavior-runs}")
    private String outputRoot = "var/region-behavior-runs";
    @Value("${ship.behavior.region.max-polygon-vertices:2000}")
    private int maxPolygonVertices = 2000;
    @Value("${ship.behavior.region.max-region-area-sq-km:2500}")
    private double maxRegionAreaSqKm = 2500.0;
    @Value("${ship.behavior.region.max-run-hours:24}")
    private int maxRunHours = 24;
    @Value("${ship.behavior.region.max-candidate-vessels:500}")
    private int maxCandidateVessels = 500;
    @Value("${ship.behavior.region.max-query-slices:5000}")
    private int maxQuerySlices = 5000;
    @Value("${ship.behavior.region.discovery.time-slice-minutes:30}")
    private int timeSliceMinutes = 30;
    @Value("${ship.behavior.region.discovery.min-time-slice-minutes:5}")
    private int minTimeSliceMinutes = 5;
    @Value("${ship.behavior.region.discovery.max-points-per-slice:50000}")
    private int maxReplayPointsPerSlice = 50000;
    @Value("${ship.behavior.region.discovery.spatial-cell-max-degrees:0.25}")
    private double spatialCellMaxDegrees = 0.25;
    @Value("${ship.behavior.region.discovery.max-spatial-refinement-depth:4}")
    private int maxSpatialRefinementDepth = 4;
    @Value("${ship.behavior.region.discovery.max-parse-error-ratio:0.02}")
    private double maxParseErrorRatio = 0.02;
    @Value("${ship.behavior.region.analysis.context-before-hours:6}")
    private int analysisContextBeforeHours = 6;
    @Value("${ship.behavior.region.analysis.context-after-hours:6}")
    private int analysisContextAfterHours = 6;
    @Value("${ship.behavior.region.analysis.buffer-nm:3}")
    private double regionBufferNm = 3.0;
    @Value("${ship.behavior.region.analysis.trajectory-slice-hours:6}")
    private int trajectorySliceHours = 6;
    @Value("${ship.behavior.region.analysis.max-points-per-vessel:200000}")
    private int maxPointsPerVessel = 200000;
    @Value("${ship.behavior.region.analysis.simplify-zoom:15}")
    private int simplifyZoom = 15;
    @Value("${ship.behavior.region.metadata.in-batch-size:200}")
    private int metadataBatchSize = 200;
    @Value("${ship.behavior.region.match.loitering-min-inside-ratio:0.60}")
    private double loiteringMinInsideRatio = 0.60;
    @Value("${ship.behavior.region.match.alongside-min-inside-ratio:0.60}")
    private double alongsideMinInsideRatio = 0.60;
    @Value("${ship.behavior.region.match.alongside-pair-overlap-ratio:0.80}")
    private double alongsidePairOverlapRatio = 0.80;
    @Value("${ship.behavior.region.match.pair-sync-tolerance-minutes:10}")
    private int pairSyncToleranceMinutes = 10;

    public static RegionBehaviorConfig defaults() {
        return new RegionBehaviorConfig();
    }

    public boolean isEnabled() { return enabled; }
    public boolean isDryRunOnly() { return dryRunOnly; }
    public boolean isWriteEnabled() { return writeEnabled; }
    public String getOutputRoot() { return outputRoot; }
    public int getMaxPolygonVertices() { return maxPolygonVertices; }
    public double getMaxRegionAreaSqKm() { return maxRegionAreaSqKm; }
    public int getMaxRunHours() { return maxRunHours; }
    public int getMaxCandidateVessels() { return maxCandidateVessels; }
    public int getMaxQuerySlices() { return maxQuerySlices; }
    public int getTimeSliceMinutes() { return timeSliceMinutes; }
    public int getMinTimeSliceMinutes() { return minTimeSliceMinutes; }
    public int getMaxReplayPointsPerSlice() { return maxReplayPointsPerSlice; }
    public double getSpatialCellMaxDegrees() { return spatialCellMaxDegrees; }
    public int getMaxSpatialRefinementDepth() { return maxSpatialRefinementDepth; }
    public double getMaxParseErrorRatio() { return maxParseErrorRatio; }
    public int getAnalysisContextBeforeHours() { return analysisContextBeforeHours; }
    public int getAnalysisContextAfterHours() { return analysisContextAfterHours; }
    public double getRegionBufferNm() { return regionBufferNm; }
    public int getTrajectorySliceHours() { return trajectorySliceHours; }
    public int getMaxPointsPerVessel() { return maxPointsPerVessel; }
    public int getSimplifyZoom() { return simplifyZoom; }
    public int getMetadataBatchSize() { return metadataBatchSize; }
    public double getLoiteringMinInsideRatio() { return loiteringMinInsideRatio; }
    public double getAlongsideMinInsideRatio() { return alongsideMinInsideRatio; }
    public double getAlongsidePairOverlapRatio() { return alongsidePairOverlapRatio; }
    public int getPairSyncToleranceMinutes() { return pairSyncToleranceMinutes; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setWriteEnabled(boolean value) { this.writeEnabled = value; }
    public void setOutputRoot(String outputRoot) { this.outputRoot = outputRoot; }
    public void setMaxRegionAreaSqKm(double value) { this.maxRegionAreaSqKm = value; }
    public void setMaxRunHours(int value) { this.maxRunHours = value; }
    public void setMaxCandidateVessels(int value) { this.maxCandidateVessels = value; }
    public void setMaxQuerySlices(int value) { this.maxQuerySlices = value; }
    public void setTimeSliceMinutes(int value) { this.timeSliceMinutes = value; }
    public void setMinTimeSliceMinutes(int value) { this.minTimeSliceMinutes = value; }
    public void setSpatialCellMaxDegrees(double value) { this.spatialCellMaxDegrees = value; }
    public void setMaxSpatialRefinementDepth(int value) { this.maxSpatialRefinementDepth = value; }
    public void setMaxParseErrorRatio(double value) { this.maxParseErrorRatio = value; }
    public void setLoiteringMinInsideRatio(double value) { this.loiteringMinInsideRatio = value; }
    public void setAlongsideMinInsideRatio(double value) { this.alongsideMinInsideRatio = value; }
    public void setAlongsidePairOverlapRatio(double value) { this.alongsidePairOverlapRatio = value; }
    public void setMetadataBatchSize(int value) { this.metadataBatchSize = value; }
}
