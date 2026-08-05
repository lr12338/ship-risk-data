package com.hifleet.behaviorplatform;

import com.alibaba.fastjson.JSONObject;

public final class BehaviorAuditResult {
    private final String comparisonKey;
    private final String mmsi;
    private final String targetMmsi;
    private final String startTime;
    private final String endTime;
    private final boolean controlAccepted;
    private final String sceneDecision;
    private final String filteredDisposition;
    private final BehaviorRiskEvidence riskEvidence;
    private final JSONObject controlResult;
    private final JSONObject shadowResult;
    private final JSONObject filteredResult;

    public BehaviorAuditResult(String comparisonKey, String mmsi, String targetMmsi,
                               String startTime, String endTime, boolean controlAccepted,
                               String sceneDecision, String filteredDisposition,
                               BehaviorRiskEvidence riskEvidence) {
        this.comparisonKey = comparisonKey;
        this.mmsi = mmsi;
        this.targetMmsi = targetMmsi;
        this.startTime = startTime;
        this.endTime = endTime;
        this.controlAccepted = controlAccepted;
        this.sceneDecision = sceneDecision;
        this.filteredDisposition = filteredDisposition;
        this.riskEvidence = riskEvidence;
        controlResult = new JSONObject(true); controlResult.put("accepted", controlAccepted);
        shadowResult = new JSONObject(true); shadowResult.put("decision", sceneDecision);
        shadowResult.put("candidateRetained", controlAccepted);
        filteredResult = new JSONObject(true); filteredResult.put("disposition", filteredDisposition);
    }
    public String getComparisonKey() { return comparisonKey; }
    public String getMmsi() { return mmsi; }
    public String getTargetMmsi() { return targetMmsi; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public boolean isControlAccepted() { return controlAccepted; }
    public String getSceneDecision() { return sceneDecision; }
    public String getFilteredDisposition() { return filteredDisposition; }
    public BehaviorRiskEvidence getRiskEvidence() { return riskEvidence; }
    public JSONObject getControlResult() { return controlResult; }
    public JSONObject getShadowResult() { return shadowResult; }
    public JSONObject getFilteredResult() { return filteredResult; }
}
