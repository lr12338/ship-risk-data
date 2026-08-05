package com.hifleet.behaviorplatform;

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
}
