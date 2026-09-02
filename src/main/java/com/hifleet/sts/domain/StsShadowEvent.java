package com.hifleet.sts.domain;

/**
 * STS Phase 1.2 — Shadow Event (EXPERIMENTAL).
 *
 * Region-gated baseline alongside candidate, persisted to a standalone
 * shadow table for manual review. NOT a production behavior event.
 */
public class StsShadowEvent {

    private String batchNo;
    private String eventKey;

    private String algorithmVersion;

    private String regionId;
    private String regionVersion;

    private String ownImo;
    private String ownMmsi;

    private String targetImo;
    private String targetMmsi;

    private String identityMode;

    private String startTime;
    private String endTime;
    private Integer durationMinutes;

    private Double centerLon;
    private Double centerLat;

    private String behaviorType;
    private String baselineRule;

    private Double minDistanceNm;
    private Double avgDistanceNm;

    private Double ownAvgSpeedKn;
    private Double targetAvgSpeedKn;

    private Double behaviorScore;

    private String targetRole;

    private String reviewStatus;

    private String scoreDetailJson;

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String v) { this.batchNo = v; }

    public String getEventKey() { return eventKey; }
    public void setEventKey(String v) { this.eventKey = v; }

    public String getAlgorithmVersion() { return algorithmVersion; }
    public void setAlgorithmVersion(String v) { this.algorithmVersion = v; }

    public String getRegionId() { return regionId; }
    public void setRegionId(String v) { this.regionId = v; }

    public String getRegionVersion() { return regionVersion; }
    public void setRegionVersion(String v) { this.regionVersion = v; }

    public String getOwnImo() { return ownImo; }
    public void setOwnImo(String v) { this.ownImo = v; }

    public String getOwnMmsi() { return ownMmsi; }
    public void setOwnMmsi(String v) { this.ownMmsi = v; }

    public String getTargetImo() { return targetImo; }
    public void setTargetImo(String v) { this.targetImo = v; }

    public String getTargetMmsi() { return targetMmsi; }
    public void setTargetMmsi(String v) { this.targetMmsi = v; }

    public String getIdentityMode() { return identityMode; }
    public void setIdentityMode(String v) { this.identityMode = v; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String v) { this.startTime = v; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String v) { this.endTime = v; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer v) { this.durationMinutes = v; }

    public Double getCenterLon() { return centerLon; }
    public void setCenterLon(Double v) { this.centerLon = v; }

    public Double getCenterLat() { return centerLat; }
    public void setCenterLat(Double v) { this.centerLat = v; }

    public String getBehaviorType() { return behaviorType; }
    public void setBehaviorType(String v) { this.behaviorType = v; }

    public String getBaselineRule() { return baselineRule; }
    public void setBaselineRule(String v) { this.baselineRule = v; }

    public Double getMinDistanceNm() { return minDistanceNm; }
    public void setMinDistanceNm(Double v) { this.minDistanceNm = v; }

    public Double getAvgDistanceNm() { return avgDistanceNm; }
    public void setAvgDistanceNm(Double v) { this.avgDistanceNm = v; }

    public Double getOwnAvgSpeedKn() { return ownAvgSpeedKn; }
    public void setOwnAvgSpeedKn(Double v) { this.ownAvgSpeedKn = v; }

    public Double getTargetAvgSpeedKn() { return targetAvgSpeedKn; }
    public void setTargetAvgSpeedKn(Double v) { this.targetAvgSpeedKn = v; }

    public Double getBehaviorScore() { return behaviorScore; }
    public void setBehaviorScore(Double v) { this.behaviorScore = v; }

    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String v) { this.targetRole = v; }

    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String v) { this.reviewStatus = v; }

    public String getScoreDetailJson() { return scoreDetailJson; }
    public void setScoreDetailJson(String v) { this.scoreDetailJson = v; }
}
