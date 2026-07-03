package com.hifleet.domain;

/**
 * 船舶轨迹行为事件候选结果。
 */
public class ShipBehaviorEvent {

    public static final String TYPE_LOITERING = "LOITERING";
    public static final String TYPE_ALONGSIDE = "ALONGSIDE";

    private String behaviorType;
    private String mmsi;
    private String targetMmsi;
    private String startTime;
    private String endTime;
    private Long durationMinutes;
    private Double startLon;
    private Double startLat;
    private Double endLon;
    private Double endLat;
    private Double minDistanceNm;
    private Double maxDistanceNm;
    private Double avgSpeed;
    private Integer pointCount;
    private String ruleVersion;
    private String batchNo;
    private String eventSubType;
    private String severityLevel;
    private Double eventScore;
    private String confidenceLevel;
    private String exclusionFlag;
    private String exclusionReason;
    private Integer aisGapCount;
    private Long maxAisGapMinutes;
    private Double trackCoverageRatio;
    private Double turnCountPerHour;
    private Double cogChangePerHour;
    private Double tortuosity;
    private Double centerLon;
    private Double centerLat;
    private Double radiusNm;
    private String scoreDetailJson;

    public String getBehaviorType() {
        return behaviorType;
    }

    public void setBehaviorType(String behaviorType) {
        this.behaviorType = behaviorType;
    }

    public String getMmsi() {
        return mmsi;
    }

    public void setMmsi(String mmsi) {
        this.mmsi = mmsi;
    }

    public String getTargetMmsi() {
        return targetMmsi;
    }

    public void setTargetMmsi(String targetMmsi) {
        this.targetMmsi = targetMmsi;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public Long getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Long durationMinutes) {
        this.durationMinutes = durationMinutes;
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

    public Double getMinDistanceNm() {
        return minDistanceNm;
    }

    public void setMinDistanceNm(Double minDistanceNm) {
        this.minDistanceNm = minDistanceNm;
    }

    public Double getMaxDistanceNm() {
        return maxDistanceNm;
    }

    public void setMaxDistanceNm(Double maxDistanceNm) {
        this.maxDistanceNm = maxDistanceNm;
    }

    public Double getAvgSpeed() {
        return avgSpeed;
    }

    public void setAvgSpeed(Double avgSpeed) {
        this.avgSpeed = avgSpeed;
    }

    public Integer getPointCount() {
        return pointCount;
    }

    public void setPointCount(Integer pointCount) {
        this.pointCount = pointCount;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getEventSubType() {
        return eventSubType;
    }

    public void setEventSubType(String eventSubType) {
        this.eventSubType = eventSubType;
    }

    public String getSeverityLevel() {
        return severityLevel;
    }

    public void setSeverityLevel(String severityLevel) {
        this.severityLevel = severityLevel;
    }

    public Double getEventScore() {
        return eventScore;
    }

    public void setEventScore(Double eventScore) {
        this.eventScore = eventScore;
    }

    public String getConfidenceLevel() {
        return confidenceLevel;
    }

    public void setConfidenceLevel(String confidenceLevel) {
        this.confidenceLevel = confidenceLevel;
    }

    public String getExclusionFlag() {
        return exclusionFlag;
    }

    public void setExclusionFlag(String exclusionFlag) {
        this.exclusionFlag = exclusionFlag;
    }

    public String getExclusionReason() {
        return exclusionReason;
    }

    public void setExclusionReason(String exclusionReason) {
        this.exclusionReason = exclusionReason;
    }

    public Integer getAisGapCount() {
        return aisGapCount;
    }

    public void setAisGapCount(Integer aisGapCount) {
        this.aisGapCount = aisGapCount;
    }

    public Long getMaxAisGapMinutes() {
        return maxAisGapMinutes;
    }

    public void setMaxAisGapMinutes(Long maxAisGapMinutes) {
        this.maxAisGapMinutes = maxAisGapMinutes;
    }

    public Double getTrackCoverageRatio() {
        return trackCoverageRatio;
    }

    public void setTrackCoverageRatio(Double trackCoverageRatio) {
        this.trackCoverageRatio = trackCoverageRatio;
    }

    public Double getTurnCountPerHour() {
        return turnCountPerHour;
    }

    public void setTurnCountPerHour(Double turnCountPerHour) {
        this.turnCountPerHour = turnCountPerHour;
    }

    public Double getCogChangePerHour() {
        return cogChangePerHour;
    }

    public void setCogChangePerHour(Double cogChangePerHour) {
        this.cogChangePerHour = cogChangePerHour;
    }

    public Double getTortuosity() {
        return tortuosity;
    }

    public void setTortuosity(Double tortuosity) {
        this.tortuosity = tortuosity;
    }

    public Double getCenterLon() {
        return centerLon;
    }

    public void setCenterLon(Double centerLon) {
        this.centerLon = centerLon;
    }

    public Double getCenterLat() {
        return centerLat;
    }

    public void setCenterLat(Double centerLat) {
        this.centerLat = centerLat;
    }

    public Double getRadiusNm() {
        return radiusNm;
    }

    public void setRadiusNm(Double radiusNm) {
        this.radiusNm = radiusNm;
    }

    public String getScoreDetailJson() {
        return scoreDetailJson;
    }

    public void setScoreDetailJson(String scoreDetailJson) {
        this.scoreDetailJson = scoreDetailJson;
    }
}
