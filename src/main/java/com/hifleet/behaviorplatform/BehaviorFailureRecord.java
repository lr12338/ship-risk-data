package com.hifleet.behaviorplatform;

import java.time.Instant;

public final class BehaviorFailureRecord {
    private final String mmsi;
    private final String targetMmsi;
    private final String sourceType;
    private final String stage;
    private final String errorCode;
    private final String errorMessage;
    private final int replayFailures;
    private final int truncatedQueries;
    private final String timeRange;
    private final int attempt;
    private final String timestamp;
    private String sliceId;
    private String cellId;
    private String sliceStart;
    private String sliceEnd;
    private String completeness;
    private String regionFingerprint;

    public BehaviorFailureRecord(String mmsi, String targetMmsi, BehaviorTargetSourceType sourceType,
                                 String stage, String errorCode, String errorMessage,
                                 int replayFailures, int truncatedQueries, String timeRange, int attempt) {
        this.mmsi = mmsi; this.targetMmsi = targetMmsi; this.sourceType = sourceType.name();
        this.stage = stage; this.errorCode = errorCode; this.errorMessage = safe(errorMessage);
        this.replayFailures = replayFailures; this.truncatedQueries = truncatedQueries;
        this.timeRange = timeRange; this.attempt = attempt; this.timestamp = Instant.now().toString();
    }
    private String safe(String value) { return value == null ? "" : value.replaceAll("(?i)(password|token|secret)=[^, ]+", "$1=<REDACTED>"); }
    public String getMmsi() { return mmsi; }
    public String getTargetMmsi() { return targetMmsi; }
    public String getSourceType() { return sourceType; }
    public String getStage() { return stage; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public int getReplayFailures() { return replayFailures; }
    public int getTruncatedQueries() { return truncatedQueries; }
    public String getTimeRange() { return timeRange; }
    public int getAttempt() { return attempt; }
    public String getTimestamp() { return timestamp; }
    public String getSliceId() { return sliceId; } public void setSliceId(String v) { sliceId = v; }
    public String getCellId() { return cellId; } public void setCellId(String v) { cellId = v; }
    public String getSliceStart() { return sliceStart; } public void setSliceStart(String v) { sliceStart = v; }
    public String getSliceEnd() { return sliceEnd; } public void setSliceEnd(String v) { sliceEnd = v; }
    public String getCompleteness() { return completeness; } public void setCompleteness(String v) { completeness = v; }
    public String getRegionFingerprint() { return regionFingerprint; } public void setRegionFingerprint(String v) { regionFingerprint = v; }
}
