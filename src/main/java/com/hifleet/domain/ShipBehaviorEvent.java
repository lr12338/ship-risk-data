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
}
