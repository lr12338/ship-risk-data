package com.hifleet.domain;

/**
 * 船舶行为事件轨迹快照明细。
 */
public class ShipBehaviorEventDetail {

    public static final String ROLE_OWN = "OWN";
    public static final String ROLE_TARGET = "TARGET";

    private String behaviorType;
    private String mmsi;
    private String targetMmsi;
    private String pointRole;
    private String eventStartTime;
    private String eventEndTime;
    private String pointTime;
    private Integer pointIndex;
    private Double lon;
    private Double lat;
    private Double speed;
    private Double course;
    private String navStatus;
    private String batchNo;
    private String ruleVersion;

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

    public String getPointRole() {
        return pointRole;
    }

    public void setPointRole(String pointRole) {
        this.pointRole = pointRole;
    }

    public String getEventStartTime() {
        return eventStartTime;
    }

    public void setEventStartTime(String eventStartTime) {
        this.eventStartTime = eventStartTime;
    }

    public String getEventEndTime() {
        return eventEndTime;
    }

    public void setEventEndTime(String eventEndTime) {
        this.eventEndTime = eventEndTime;
    }

    public String getPointTime() {
        return pointTime;
    }

    public void setPointTime(String pointTime) {
        this.pointTime = pointTime;
    }

    public Integer getPointIndex() {
        return pointIndex;
    }

    public void setPointIndex(Integer pointIndex) {
        this.pointIndex = pointIndex;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getSpeed() {
        return speed;
    }

    public void setSpeed(Double speed) {
        this.speed = speed;
    }

    public Double getCourse() {
        return course;
    }

    public void setCourse(Double course) {
        this.course = course;
    }

    public String getNavStatus() {
        return navStatus;
    }

    public void setNavStatus(String navStatus) {
        this.navStatus = navStatus;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }
}
