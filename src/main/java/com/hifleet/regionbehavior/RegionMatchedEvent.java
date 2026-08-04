package com.hifleet.regionbehavior;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ShipBehaviorEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private double ownInsideTimeRatio;
    private int ownPointInsideCount;
    private double targetPointInsideRatio;
    private double targetInsideTimeRatio;
    private int targetPointInsideCount;
    private double pairMidpointInsideRatio;
    private double pairMidpointInsideTimeRatio;
    private int pairMidpointInsideCount;
    private long insideDurationMinutes;
    private long synchronizedInsideMinutes;
    private RegionMatchStatus regionMatchStatus;
    private RegionBehaviorStatus queryCompleteness = RegionBehaviorStatus.COMPLETE;
    private String dominantMaritimeScene;
    private String ownScene;
    private String targetScene;
    private String synchronizedScene;
    private String sceneConfidence;
    private String shadowDecision;
    private String sceneSnapshotVersion;
    private String sceneSnapshotFingerprint;
    private Double coastDistanceMinNm;
    private Double coastDistanceP50Nm;
    private Double coastDistanceMaxNm;
    private Double sceneUnknownRatio;
    private Double sceneConflictRatio;
    private Double sceneOpenWaterTimeRatio;
    private Double sceneInlandTimeRatio;
    private Double sceneUnknownTimeRatio;
    private Double sceneConflictTimeRatio;
    private Double sceneObservedDurationMinutes;
    private Double sceneEventSpanMinutes;
    private Double sceneTemporalCoverageRatio;
    private Double sceneInlandDurationMinutes;
    private Double sceneMaxGapMinutes;
    private Integer sceneLargeGapCount;
    private JSONObject ownSceneSummary;
    private JSONObject targetSceneSummary;
    private JSONObject pairMidpointSceneSummary;
    private JSONObject behaviorEvidence;
    private String baseRuleDecision;
    private String sceneShadowDecision;
    private String behaviorShadowDecision;
    private String combinedShadowDecision;
    private List<String> shadowRejectReasons = new ArrayList<String>();
    private String scenePolicyVersion;
    private String behaviorRuleVersion;
    private String combinedRuleVersion;
    private boolean shadowRulesExecuted;
    private Boolean insidePort;
    private Boolean insideAnchorage;
    private Boolean nearBerth;
    private Boolean insideKnownStsArea;
    private Boolean operationalAreaDataAvailable;
    private Boolean shipTypePolicyPassed;
    private String shipTypePolicyReason;
    private boolean shadowRulesRequested;
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
    public double getOwnInsidePointRatio() { return ownPointInsideRatio; }
    public double getOwnInsideTimeRatio() { return ownInsideTimeRatio; }
    public int getOwnPointInsideCount() { return ownPointInsideCount; }
    public double getTargetPointInsideRatio() { return targetPointInsideRatio; }
    public double getTargetInsidePointRatio() { return targetPointInsideRatio; }
    public double getTargetInsideTimeRatio() { return targetInsideTimeRatio; }
    public int getTargetPointInsideCount() { return targetPointInsideCount; }
    public double getPairMidpointInsideRatio() { return pairMidpointInsideRatio; }
    public double getPairMidpointInsidePointRatio() { return pairMidpointInsideRatio; }
    public double getPairMidpointInsideTimeRatio() { return pairMidpointInsideTimeRatio; }
    public int getPairMidpointInsideCount() { return pairMidpointInsideCount; }
    public long getInsideDurationMinutes() { return insideDurationMinutes; }
    public long getSynchronizedInsideMinutes() { return synchronizedInsideMinutes; }
    public RegionMatchStatus getRegionMatchStatus() { return regionMatchStatus; }
    public RegionBehaviorStatus getQueryCompleteness() { return queryCompleteness; }
    public String getDominantMaritimeScene() { return dominantMaritimeScene; }
    public String getOwnScene() { return ownScene; }
    public String getTargetScene() { return targetScene; }
    public String getSynchronizedScene() { return synchronizedScene; }
    public String getSceneConfidence() { return sceneConfidence; }
    public String getShadowDecision() { return shadowDecision; }
    public String getSceneSnapshotVersion() { return sceneSnapshotVersion; }
    public String getSceneSnapshotFingerprint() { return sceneSnapshotFingerprint; }
    public Double getCoastDistanceMinNm() { return coastDistanceMinNm; }
    public Double getCoastDistanceP50Nm() { return coastDistanceP50Nm; }
    public Double getCoastDistanceMaxNm() { return coastDistanceMaxNm; }
    public Double getSceneUnknownRatio() { return sceneUnknownRatio; }
    public Double getSceneConflictRatio() { return sceneConflictRatio; }
    public Double getSceneOpenWaterTimeRatio() { return sceneOpenWaterTimeRatio; }
    public Double getSceneInlandTimeRatio() { return sceneInlandTimeRatio; }
    public Double getSceneUnknownTimeRatio() { return sceneUnknownTimeRatio; }
    public Double getSceneConflictTimeRatio() { return sceneConflictTimeRatio; }
    public Double getSceneObservedDurationMinutes() { return sceneObservedDurationMinutes; }
    public Double getSceneEventSpanMinutes() { return sceneEventSpanMinutes; }
    public Double getSceneTemporalCoverageRatio() { return sceneTemporalCoverageRatio; }
    public Double getSceneInlandDurationMinutes() { return sceneInlandDurationMinutes; }
    public Double getSceneMaxGapMinutes() { return sceneMaxGapMinutes; }
    public Integer getSceneLargeGapCount() { return sceneLargeGapCount; }
    public JSONObject getOwnSceneSummary() { return ownSceneSummary; }
    public JSONObject getTargetSceneSummary() { return targetSceneSummary; }
    public JSONObject getPairMidpointSceneSummary() { return pairMidpointSceneSummary; }
    public JSONObject getBehaviorEvidence() { return behaviorEvidence; }
    public String getBaseRuleDecision() { return baseRuleDecision; }
    public String getSceneShadowDecision() { return sceneShadowDecision; }
    public String getBehaviorShadowDecision() { return behaviorShadowDecision; }
    public String getCombinedShadowDecision() { return combinedShadowDecision; }
    public List<String> getShadowRejectReasons() { return Collections.unmodifiableList(shadowRejectReasons); }
    public String getScenePolicyVersion() { return scenePolicyVersion; }
    public String getBehaviorRuleVersion() { return behaviorRuleVersion; }
    public String getCombinedRuleVersion() { return combinedRuleVersion; }
    public boolean isShadowRulesExecuted() { return shadowRulesExecuted; }
    public Boolean getInsidePort() { return insidePort; }
    public Boolean getInsideAnchorage() { return insideAnchorage; }
    public Boolean getNearBerth() { return nearBerth; }
    public Boolean getInsideKnownStsArea() { return insideKnownStsArea; }
    public Boolean getOperationalAreaDataAvailable() { return operationalAreaDataAvailable; }
    public Boolean getShipTypePolicyPassed() { return shipTypePolicyPassed; }
    public String getShipTypePolicyReason() { return shipTypePolicyReason; }
    public boolean isShadowRulesRequested() { return shadowRulesRequested; }
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
    void setOwnInsideTimeRatio(double value) { this.ownInsideTimeRatio = value; }
    void setOwnPointInsideCount(int value) { this.ownPointInsideCount = value; }
    void setTargetPointInsideRatio(double value) { this.targetPointInsideRatio = value; }
    void setTargetInsideTimeRatio(double value) { this.targetInsideTimeRatio = value; }
    void setTargetPointInsideCount(int value) { this.targetPointInsideCount = value; }
    void setPairMidpointInsideRatio(double value) { this.pairMidpointInsideRatio = value; }
    void setPairMidpointInsideTimeRatio(double value) { this.pairMidpointInsideTimeRatio = value; }
    void setPairMidpointInsideCount(int value) { this.pairMidpointInsideCount = value; }
    void setInsideDurationMinutes(long value) { this.insideDurationMinutes = value; }
    void setSynchronizedInsideMinutes(long value) { this.synchronizedInsideMinutes = value; }
    void setRegionMatchStatus(RegionMatchStatus value) { this.regionMatchStatus = value; }
    public void setQueryCompleteness(RegionBehaviorStatus value) { this.queryCompleteness = value; }
    void setDominantMaritimeScene(String value) { this.dominantMaritimeScene = value; }
    void setOwnScene(String value) { this.ownScene = value; }
    void setTargetScene(String value) { this.targetScene = value; }
    void setSynchronizedScene(String value) { this.synchronizedScene = value; }
    void setSceneConfidence(String value) { this.sceneConfidence = value; }
    void setShadowDecision(String value) { this.shadowDecision = value; }
    void setSceneSnapshotVersion(String value) { this.sceneSnapshotVersion = value; }
    void setSceneSnapshotFingerprint(String value) { this.sceneSnapshotFingerprint = value; }
    void setCoastDistanceMinNm(Double value) { this.coastDistanceMinNm = value; }
    void setCoastDistanceP50Nm(Double value) { this.coastDistanceP50Nm = value; }
    void setCoastDistanceMaxNm(Double value) { this.coastDistanceMaxNm = value; }
    void setSceneUnknownRatio(Double value) { this.sceneUnknownRatio = value; }
    void setSceneConflictRatio(Double value) { this.sceneConflictRatio = value; }
    void setSceneOpenWaterTimeRatio(Double value) { this.sceneOpenWaterTimeRatio = value; }
    void setSceneInlandTimeRatio(Double value) { this.sceneInlandTimeRatio = value; }
    void setSceneUnknownTimeRatio(Double value) { this.sceneUnknownTimeRatio = value; }
    void setSceneConflictTimeRatio(Double value) { this.sceneConflictTimeRatio = value; }
    void setSceneObservedDurationMinutes(Double value) { this.sceneObservedDurationMinutes = value; }
    void setSceneEventSpanMinutes(Double value) { this.sceneEventSpanMinutes = value; }
    void setSceneTemporalCoverageRatio(Double value) { this.sceneTemporalCoverageRatio = value; }
    void setSceneInlandDurationMinutes(Double value) { this.sceneInlandDurationMinutes = value; }
    void setSceneMaxGapMinutes(Double value) { this.sceneMaxGapMinutes = value; }
    void setSceneLargeGapCount(Integer value) { this.sceneLargeGapCount = value; }
    void setOwnSceneSummary(JSONObject value) { this.ownSceneSummary = value; }
    void setTargetSceneSummary(JSONObject value) { this.targetSceneSummary = value; }
    void setPairMidpointSceneSummary(JSONObject value) { this.pairMidpointSceneSummary = value; }
    void setBehaviorEvidence(JSONObject value) { this.behaviorEvidence = value; }
    void setBaseRuleDecision(String value) { this.baseRuleDecision = value; }
    void setSceneShadowDecision(String value) { this.sceneShadowDecision = value; }
    void setBehaviorShadowDecision(String value) { this.behaviorShadowDecision = value; }
    void setCombinedShadowDecision(String value) { this.combinedShadowDecision = value; }
    void setShadowRejectReasons(List<String> value) {
        this.shadowRejectReasons = value == null ? new ArrayList<String>() : new ArrayList<String>(value);
    }
    void setScenePolicyVersion(String value) { this.scenePolicyVersion = value; }
    void setBehaviorRuleVersion(String value) { this.behaviorRuleVersion = value; }
    void setCombinedRuleVersion(String value) { this.combinedRuleVersion = value; }
    void setShadowRulesExecuted(boolean value) { this.shadowRulesExecuted = value; }
    void setOperationalArea(OperationalAreaContext value) {
        if (value == null) return;
        insidePort = value.isInsidePort();
        insideAnchorage = value.isInsideAnchorage();
        nearBerth = value.isNearBerth();
        insideKnownStsArea = value.isInsideKnownStsArea();
        operationalAreaDataAvailable = value.isOperationalAreaDataAvailable();
    }
    void setShipTypePolicyDecision(boolean passed, String reason) {
        shipTypePolicyPassed = passed;
        shipTypePolicyReason = reason;
    }
    void setShadowRulesRequested(boolean value) { shadowRulesRequested = value; }

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
