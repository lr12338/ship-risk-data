package com.hifleet.behaviorplatform;

public final class BehaviorRunManifest {
    private String runId;
    private String batchNo;
    private String behaviorType;
    private String profileId;
    private String profileHash;
    private String resolvedProfileSnapshot;
    private String ruleSetId;
    private int ruleSetVersion;
    private String ruleSetHash;
    private String behaviorRuleSetHash;
    private String sceneRuleSetHash;
    private String resolvedRulesSnapshot;
    private String sceneMode;
    private String sceneSnapshotVersion;
    private String sceneFingerprint;
    private String runtimePolicyVersion;
    private String snapshotPolicyVersion;
    private String writeModeRequested;
    private String writeModeEffective;
    private String gitCommit;
    private String jarSha256;
    private String listBatchNo;
    private String sourceType;
    private String sourceFingerprint;
    private String sourceSnapshot;
    private String parameterHash;
    private int startSeq;
    private int pageSize;
    private int parallelism;
    private boolean resume;
    private int inlineShipCount;
    private int listEnabledCount;
    private String regionId;
    private int regionVersion;
    private String regionFingerprint;
    private double regionAreaSqKm;
    private String regionBbox;
    private int querySliceCount;
    private int candidateVesselCount;
    private String queryCompleteness;
    private int skippedShips;
    private int retriedShips;
    private java.util.Map<String, Integer> failureCountByStage = new java.util.LinkedHashMap<String, Integer>();
    private String startTime;
    private String endTime;
    private int shipLimit;
    private int processedShips;
    private int successfulShips;
    private int failedShips;
    private int candidateCount;
    private int acceptedCount;
    private int reviewCount;
    private int databaseWriteCount;
    private int formalWriteCount;
    private String startedAt;
    private String finishedAt;
    private long durationMillis;

    public String getRunId() { return runId; } public void setRunId(String v) { runId = v; }
    public String getBatchNo() { return batchNo; } public void setBatchNo(String v) { batchNo = v; }
    public String getBehaviorType() { return behaviorType; } public void setBehaviorType(String v) { behaviorType = v; }
    public String getProfileId() { return profileId; } public void setProfileId(String v) { profileId = v; }
    public String getProfileHash() { return profileHash; } public void setProfileHash(String v) { profileHash = v; }
    public String getResolvedProfileSnapshot() { return resolvedProfileSnapshot; } public void setResolvedProfileSnapshot(String v) { resolvedProfileSnapshot = v; }
    public String getRuleSetId() { return ruleSetId; } public void setRuleSetId(String v) { ruleSetId = v; }
    public int getRuleSetVersion() { return ruleSetVersion; } public void setRuleSetVersion(int v) { ruleSetVersion = v; }
    public String getRuleSetHash() { return ruleSetHash; } public void setRuleSetHash(String v) { ruleSetHash = v; }
    public String getBehaviorRuleSetHash() { return behaviorRuleSetHash; } public void setBehaviorRuleSetHash(String v) { behaviorRuleSetHash = v; }
    public String getSceneRuleSetHash() { return sceneRuleSetHash; } public void setSceneRuleSetHash(String v) { sceneRuleSetHash = v; }
    public String getResolvedRulesSnapshot() { return resolvedRulesSnapshot; } public void setResolvedRulesSnapshot(String v) { resolvedRulesSnapshot = v; }
    public String getSceneMode() { return sceneMode; } public void setSceneMode(String v) { sceneMode = v; }
    public String getSceneSnapshotVersion() { return sceneSnapshotVersion; } public void setSceneSnapshotVersion(String v) { sceneSnapshotVersion = v; }
    public String getSceneFingerprint() { return sceneFingerprint; } public void setSceneFingerprint(String v) { sceneFingerprint = v; }
    public String getRuntimePolicyVersion() { return runtimePolicyVersion; } public void setRuntimePolicyVersion(String v) { runtimePolicyVersion = v; }
    public String getSnapshotPolicyVersion() { return snapshotPolicyVersion; } public void setSnapshotPolicyVersion(String v) { snapshotPolicyVersion = v; }
    public String getWriteModeRequested() { return writeModeRequested; } public void setWriteModeRequested(String v) { writeModeRequested = v; }
    public String getWriteModeEffective() { return writeModeEffective; } public void setWriteModeEffective(String v) { writeModeEffective = v; }
    public String getGitCommit() { return gitCommit; } public void setGitCommit(String v) { gitCommit = v; }
    public String getJarSha256() { return jarSha256; } public void setJarSha256(String v) { jarSha256 = v; }
    public String getListBatchNo() { return listBatchNo; } public void setListBatchNo(String v) { listBatchNo = v; }
    public String getSourceType() { return sourceType; } public void setSourceType(String v) { sourceType = v; }
    public String getSourceFingerprint() { return sourceFingerprint; } public void setSourceFingerprint(String v) { sourceFingerprint = v; }
    public String getSourceSnapshot() { return sourceSnapshot; } public void setSourceSnapshot(String v) { sourceSnapshot = v; }
    public String getParameterHash() { return parameterHash; } public void setParameterHash(String v) { parameterHash = v; }
    public int getStartSeq() { return startSeq; } public void setStartSeq(int v) { startSeq = v; }
    public int getPageSize() { return pageSize; } public void setPageSize(int v) { pageSize = v; }
    public int getParallelism() { return parallelism; } public void setParallelism(int v) { parallelism = v; }
    public boolean isResume() { return resume; } public void setResume(boolean v) { resume = v; }
    public int getInlineShipCount() { return inlineShipCount; } public void setInlineShipCount(int v) { inlineShipCount = v; }
    public int getListEnabledCount() { return listEnabledCount; } public void setListEnabledCount(int v) { listEnabledCount = v; }
    public String getRegionId() { return regionId; } public void setRegionId(String v) { regionId = v; }
    public int getRegionVersion() { return regionVersion; } public void setRegionVersion(int v) { regionVersion = v; }
    public String getRegionFingerprint() { return regionFingerprint; } public void setRegionFingerprint(String v) { regionFingerprint = v; }
    public double getRegionAreaSqKm() { return regionAreaSqKm; } public void setRegionAreaSqKm(double v) { regionAreaSqKm = v; }
    public String getRegionBbox() { return regionBbox; } public void setRegionBbox(String v) { regionBbox = v; }
    public int getQuerySliceCount() { return querySliceCount; } public void setQuerySliceCount(int v) { querySliceCount = v; }
    public int getCandidateVesselCount() { return candidateVesselCount; } public void setCandidateVesselCount(int v) { candidateVesselCount = v; }
    public String getQueryCompleteness() { return queryCompleteness; } public void setQueryCompleteness(String v) { queryCompleteness = v; }
    public int getSkippedShips() { return skippedShips; } public void setSkippedShips(int v) { skippedShips = v; }
    public int getRetriedShips() { return retriedShips; } public void setRetriedShips(int v) { retriedShips = v; }
    public java.util.Map<String, Integer> getFailureCountByStage() { return failureCountByStage; }
    public void setFailureCountByStage(java.util.Map<String, Integer> v) { failureCountByStage = v; }
    public String getStartTime() { return startTime; } public void setStartTime(String v) { startTime = v; }
    public String getEndTime() { return endTime; } public void setEndTime(String v) { endTime = v; }
    public int getShipLimit() { return shipLimit; } public void setShipLimit(int v) { shipLimit = v; }
    public int getProcessedShips() { return processedShips; } public void setProcessedShips(int v) { processedShips = v; }
    public int getSuccessfulShips() { return successfulShips; } public void setSuccessfulShips(int v) { successfulShips = v; }
    public int getFailedShips() { return failedShips; } public void setFailedShips(int v) { failedShips = v; }
    public int getCandidateCount() { return candidateCount; } public void setCandidateCount(int v) { candidateCount = v; }
    public int getAcceptedCount() { return acceptedCount; } public void setAcceptedCount(int v) { acceptedCount = v; }
    public int getReviewCount() { return reviewCount; } public void setReviewCount(int v) { reviewCount = v; }
    public int getDatabaseWriteCount() { return databaseWriteCount; } public void setDatabaseWriteCount(int v) { databaseWriteCount = v; }
    public int getFormalWriteCount() { return formalWriteCount; } public void setFormalWriteCount(int v) { formalWriteCount = v; }
    public String getStartedAt() { return startedAt; } public void setStartedAt(String v) { startedAt = v; }
    public String getFinishedAt() { return finishedAt; } public void setFinishedAt(String v) { finishedAt = v; }
    public long getDurationMillis() { return durationMillis; } public void setDurationMillis(long v) { durationMillis = v; }
}
