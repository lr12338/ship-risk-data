package com.hifleet.analysis.alongside.core.model;

import java.io.Serializable;
import java.time.Instant;

public class PairProximityEvidence implements Serializable {

    private static final long serialVersionUID = 1L;

    private String pairId;
    private Long mmsiA;
    private Long mmsiB;
    private Instant evidenceTime;
    private Instant pointATime;
    private Instant pointBTime;
    private Double lonA;
    private Double latA;
    private Double lonB;
    private Double latB;
    private Double speedA;
    private Double speedB;
    private Double courseA;
    private Double courseB;
    private Integer navigationStatusA;
    private Integer navigationStatusB;
    private Double distanceNm;
    private Long timeGapSeconds;
    private Double relativeSpeedKnots;
    private Double headingParallelDiff;
    private Integer nearbyStationaryVesselCount;
    private VesselProfile vesselA;
    private VesselProfile vesselB;
    private PairPriority pairPriority;

    public static String pairId(long mmsi1, long mmsi2) {
        long a = Math.min(mmsi1, mmsi2);
        long b = Math.max(mmsi1, mmsi2);
        return a + "-" + b;
    }

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

    public Instant getEvidenceTime() {
        return evidenceTime;
    }

    public void setEvidenceTime(Instant evidenceTime) {
        this.evidenceTime = evidenceTime;
    }

    public Instant getPointATime() {
        return pointATime;
    }

    public void setPointATime(Instant pointATime) {
        this.pointATime = pointATime;
    }

    public Instant getPointBTime() {
        return pointBTime;
    }

    public void setPointBTime(Instant pointBTime) {
        this.pointBTime = pointBTime;
    }

    public Double getLonA() {
        return lonA;
    }

    public void setLonA(Double lonA) {
        this.lonA = lonA;
    }

    public Double getLatA() {
        return latA;
    }

    public void setLatA(Double latA) {
        this.latA = latA;
    }

    public Double getLonB() {
        return lonB;
    }

    public void setLonB(Double lonB) {
        this.lonB = lonB;
    }

    public Double getLatB() {
        return latB;
    }

    public void setLatB(Double latB) {
        this.latB = latB;
    }

    public Double getSpeedA() {
        return speedA;
    }

    public void setSpeedA(Double speedA) {
        this.speedA = speedA;
    }

    public Double getSpeedB() {
        return speedB;
    }

    public void setSpeedB(Double speedB) {
        this.speedB = speedB;
    }

    public Double getCourseA() {
        return courseA;
    }

    public void setCourseA(Double courseA) {
        this.courseA = courseA;
    }

    public Double getCourseB() {
        return courseB;
    }

    public void setCourseB(Double courseB) {
        this.courseB = courseB;
    }

    public Integer getNavigationStatusA() {
        return navigationStatusA;
    }

    public void setNavigationStatusA(Integer navigationStatusA) {
        this.navigationStatusA = navigationStatusA;
    }

    public Integer getNavigationStatusB() {
        return navigationStatusB;
    }

    public void setNavigationStatusB(Integer navigationStatusB) {
        this.navigationStatusB = navigationStatusB;
    }

    public Double getDistanceNm() {
        return distanceNm;
    }

    public void setDistanceNm(Double distanceNm) {
        this.distanceNm = distanceNm;
    }

    public Long getTimeGapSeconds() {
        return timeGapSeconds;
    }

    public void setTimeGapSeconds(Long timeGapSeconds) {
        this.timeGapSeconds = timeGapSeconds;
    }

    public Double getRelativeSpeedKnots() {
        return relativeSpeedKnots;
    }

    public void setRelativeSpeedKnots(Double relativeSpeedKnots) {
        this.relativeSpeedKnots = relativeSpeedKnots;
    }

    public Double getHeadingParallelDiff() {
        return headingParallelDiff;
    }

    public void setHeadingParallelDiff(Double headingParallelDiff) {
        this.headingParallelDiff = headingParallelDiff;
    }

    public Integer getNearbyStationaryVesselCount() {
        return nearbyStationaryVesselCount;
    }

    public void setNearbyStationaryVesselCount(Integer nearbyStationaryVesselCount) {
        this.nearbyStationaryVesselCount = nearbyStationaryVesselCount;
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
