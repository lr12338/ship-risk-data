package com.hifleet.regionbehavior;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ShipBehaviorEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class RegionMatchedEvent {
    private String batchNo;
    private String regionId;
    private String regionName;
    private String regionFingerprint;
    private String behaviorType;
    private String mmsi;
    private String targetMmsi;
    private String pairMmsi1;
    private String pairMmsi2;
    private String startTime;
    private String endTime;
    private String ruleVersion;
    private String eventSubType;
    private String eventKey;
    private boolean eventCenterInside;
    private boolean eventStartInside;
    private boolean eventEndInside;
    private double ownPointInsideRatio;
    private int ownPointInsideCount;
    private double targetPointInsideRatio;
    private int targetPointInsideCount;
    private double pairMidpointInsideRatio;
    private int pairMidpointInsideCount;
    private long insideDurationMinutes;
    private long synchronizedInsideMinutes;
    private RegionMatchStatus regionMatchStatus;
    private RegionBehaviorStatus queryCompleteness = RegionBehaviorStatus.COMPLETE;
    private JSONObject sourceEvent;

    static RegionMatchedEvent from(ShipBehaviorEvent event) {
        RegionMatchedEvent value = new RegionMatchedEvent();
        value.behaviorType = event.getBehaviorType();
        value.mmsi = event.getMmsi();
        value.targetMmsi = event.getTargetMmsi();
        value.startTime = event.getStartTime();
        value.endTime = event.getEndTime();
        value.ruleVersion = event.getRuleVersion();
        value.eventSubType = event.getEventSubType();
        value.sourceEvent = (JSONObject) JSON.toJSON(event);
        if (event.getTargetMmsi() != null) {
            value.pairMmsi1 = event.getMmsi().compareTo(event.getTargetMmsi()) <= 0
                    ? event.getMmsi() : event.getTargetMmsi();
            value.pairMmsi2 = event.getMmsi().compareTo(event.getTargetMmsi()) <= 0
                    ? event.getTargetMmsi() : event.getMmsi();
        }
        return value;
    }

    public String getBatchNo() { return batchNo; }
    public String getRegionId() { return regionId; }
    public String getRegionName() { return regionName; }
    public String getRegionFingerprint() { return regionFingerprint; }
    public String getBehaviorType() { return behaviorType; }
    public String getMmsi() { return mmsi; }
    public String getTargetMmsi() { return targetMmsi; }
    public String getPairMmsi1() { return pairMmsi1; }
    public String getPairMmsi2() { return pairMmsi2; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public String getRuleVersion() { return ruleVersion; }
    public String getEventSubType() { return eventSubType; }
    public String getEventKey() { return eventKey; }
    public boolean isEventCenterInside() { return eventCenterInside; }
    public boolean isEventStartInside() { return eventStartInside; }
    public boolean isEventEndInside() { return eventEndInside; }
    public double getOwnPointInsideRatio() { return ownPointInsideRatio; }
    public int getOwnPointInsideCount() { return ownPointInsideCount; }
    public double getTargetPointInsideRatio() { return targetPointInsideRatio; }
    public int getTargetPointInsideCount() { return targetPointInsideCount; }
    public double getPairMidpointInsideRatio() { return pairMidpointInsideRatio; }
    public int getPairMidpointInsideCount() { return pairMidpointInsideCount; }
    public long getInsideDurationMinutes() { return insideDurationMinutes; }
    public long getSynchronizedInsideMinutes() { return synchronizedInsideMinutes; }
    public RegionMatchStatus getRegionMatchStatus() { return regionMatchStatus; }
    public RegionBehaviorStatus getQueryCompleteness() { return queryCompleteness; }
    public JSONObject getSourceEvent() { return sourceEvent; }

    void setIdentity(String batchNo, String regionId, String regionName, String fingerprint) {
        this.batchNo = batchNo;
        this.regionId = regionId;
        this.regionName = regionName;
        this.regionFingerprint = fingerprint;
        this.eventKey = sha256(fingerprint + "|" + behaviorType + "|"
                + (pairMmsi1 == null ? mmsi : pairMmsi1 + "|" + pairMmsi2) + "|"
                + normalizeTime(startTime) + "|" + normalizeTime(endTime) + "|" + ruleVersion);
    }
    void setEventCenterInside(boolean value) { this.eventCenterInside = value; }
    void setEventStartInside(boolean value) { this.eventStartInside = value; }
    void setEventEndInside(boolean value) { this.eventEndInside = value; }
    void setOwnPointInsideRatio(double value) { this.ownPointInsideRatio = value; }
    void setOwnPointInsideCount(int value) { this.ownPointInsideCount = value; }
    void setTargetPointInsideRatio(double value) { this.targetPointInsideRatio = value; }
    void setTargetPointInsideCount(int value) { this.targetPointInsideCount = value; }
    void setPairMidpointInsideRatio(double value) { this.pairMidpointInsideRatio = value; }
    void setPairMidpointInsideCount(int value) { this.pairMidpointInsideCount = value; }
    void setInsideDurationMinutes(long value) { this.insideDurationMinutes = value; }
    void setSynchronizedInsideMinutes(long value) { this.synchronizedInsideMinutes = value; }
    void setRegionMatchStatus(RegionMatchStatus value) { this.regionMatchStatus = value; }
    public void setQueryCompleteness(RegionBehaviorStatus value) { this.queryCompleteness = value; }

    private String normalizeTime(String value) {
        return value == null ? "" : RegionBehaviorTimes.format(RegionBehaviorTimes.parse(value));
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format("%02x", b & 0xff));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
