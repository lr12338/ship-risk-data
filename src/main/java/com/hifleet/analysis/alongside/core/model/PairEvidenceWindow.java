package com.hifleet.analysis.alongside.core.model;

import java.io.Serializable;
import java.time.Instant;

public class PairEvidenceWindow implements Serializable {

    private static final long serialVersionUID = 1L;

    private String pairId;
    private Long mmsiA;
    private Long mmsiB;
    private Instant startTime;
    private Instant endTime;
    private Double startLon;
    private Double startLat;
    private Double endLon;
    private Double endLat;
    private int pointCount;
    private double minDistanceNm;
    private double maxDistanceNm;
    private double avgDistanceNm;
    private double avgRelativeSpeedKnots;
    private double maxSpeedKnots;
    private long durationMinutes;
    private long maxTimeGapSeconds;
    private int highTimeGapPointCount;
    private int headingKnownCount;
    private int headingAcceptedCount;
    private int aisGapCount;
    private long maxAisGapMinutes;
    private double trackCoverageRatio;
    private boolean anchoredOrMooredSeen;
    private VesselProfile vesselA;
    private VesselProfile vesselB;
    private PairPriority pairPriority;

    public String getPairId() {
        return pairId;
    }

    public void setPairId(String pairId) {
        this.pairId = pairId;
    }

    public Long getMmsiA() {
        return mmsiA;
    }

    public void setMmsiA(Long mmsiA) {
        this.mmsiA = mmsiA;
    }

    public Long getMmsiB() {
        return mmsiB;
    }

    public void setMmsiB(Long mmsiB) {
        this.mmsiB = mmsiB;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public Double getStartLon() {
        return startLon;
    }

    public void setStartLon(Double startLon) {
        this.startLon = startLon;
    }

    public Double getStartLat() {
        return startLat;
    }

    public void setStartLat(Double startLat) {
        this.startLat = startLat;
    }

    public Double getEndLon() {
        return endLon;
    }

    public void setEndLon(Double endLon) {
        this.endLon = endLon;
    }

    public Double getEndLat() {
        return endLat;
    }

    public void setEndLat(Double endLat) {
        this.endLat = endLat;
    }

    public int getPointCount() {
        return pointCount;
    }

    public void setPointCount(int pointCount) {
        this.pointCount = pointCount;
    }

    public double getMinDistanceNm() {
        return minDistanceNm;
    }

    public void setMinDistanceNm(double minDistanceNm) {
        this.minDistanceNm = minDistanceNm;
    }

    public double getMaxDistanceNm() {
        return maxDistanceNm;
    }

    public void setMaxDistanceNm(double maxDistanceNm) {
        this.maxDistanceNm = maxDistanceNm;
    }

    public double getAvgDistanceNm() {
        return avgDistanceNm;
    }

    public void setAvgDistanceNm(double avgDistanceNm) {
        this.avgDistanceNm = avgDistanceNm;
    }

    public double getAvgRelativeSpeedKnots() {
        return avgRelativeSpeedKnots;
    }

    public void setAvgRelativeSpeedKnots(double avgRelativeSpeedKnots) {
        this.avgRelativeSpeedKnots = avgRelativeSpeedKnots;
    }

    public double getMaxSpeedKnots() {
        return maxSpeedKnots;
    }

    public void setMaxSpeedKnots(double maxSpeedKnots) {
        this.maxSpeedKnots = maxSpeedKnots;
    }

    public long getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(long durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public long getMaxTimeGapSeconds() {
        return maxTimeGapSeconds;
    }

    public void setMaxTimeGapSeconds(long maxTimeGapSeconds) {
        this.maxTimeGapSeconds = maxTimeGapSeconds;
    }

    public int getHighTimeGapPointCount() {
        return highTimeGapPointCount;
    }

    public void setHighTimeGapPointCount(int highTimeGapPointCount) {
        this.highTimeGapPointCount = highTimeGapPointCount;
    }

    public int getHeadingKnownCount() {
        return headingKnownCount;
    }

    public void setHeadingKnownCount(int headingKnownCount) {
        this.headingKnownCount = headingKnownCount;
    }

    public int getHeadingAcceptedCount() {
        return headingAcceptedCount;
    }

    public void setHeadingAcceptedCount(int headingAcceptedCount) {
        this.headingAcceptedCount = headingAcceptedCount;
    }

    public int getAisGapCount() {
        return aisGapCount;
    }

    public void setAisGapCount(int aisGapCount) {
        this.aisGapCount = aisGapCount;
    }

    public long getMaxAisGapMinutes() {
        return maxAisGapMinutes;
    }

    public void setMaxAisGapMinutes(long maxAisGapMinutes) {
        this.maxAisGapMinutes = maxAisGapMinutes;
    }

    public double getTrackCoverageRatio() {
        return trackCoverageRatio;
    }

    public void setTrackCoverageRatio(double trackCoverageRatio) {
        this.trackCoverageRatio = trackCoverageRatio;
    }

    public boolean isAnchoredOrMooredSeen() {
        return anchoredOrMooredSeen;
    }

    public void setAnchoredOrMooredSeen(boolean anchoredOrMooredSeen) {
        this.anchoredOrMooredSeen = anchoredOrMooredSeen;
    }

    public VesselProfile getVesselA() {
        return vesselA;
    }

    public void setVesselA(VesselProfile vesselA) {
        this.vesselA = vesselA;
    }

    public VesselProfile getVesselB() {
        return vesselB;
    }

    public void setVesselB(VesselProfile vesselB) {
        this.vesselB = vesselB;
    }

    public PairPriority getPairPriority() {
        return pairPriority;
    }

    public void setPairPriority(PairPriority pairPriority) {
        this.pairPriority = pairPriority;
    }
}
