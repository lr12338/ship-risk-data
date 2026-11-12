package com.hifleet.sts.domain;

import java.util.Date;

public class StsBackfillBatchRow {
    private String batchNo;
    private String runnerVersion;
    private String configFingerprint;
    private String targetListBatchNo;
    private String targetListFingerprint;
    private int expectedTargetCount;
    private int actualTargetCount;
    private int shipOffset;
    private int shipCount;
    private int selectedShipCount;
    private String regionSetCode;
    private String regionSetFingerprint;
    private int resolvedRegionCount;
    private Date fromTime;
    private Date toTime;
    private int sliceDays;
    private String status;
    private int totalTasks;
    private int completedTasks;
    private int failedTasks;
    private int totalCandidateShips;
    private int totalProcessedShips;
    private int totalBaselineEvents;
    private int totalShadowInserted;
    private int totalDuplicateSkipped;
    private String lastError;
    private Date createdAt;
    private Date startedAt;
    private Date updatedAt;
    private Date finishedAt;

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String v) { this.batchNo = v; }

    public String getRunnerVersion() { return runnerVersion; }
    public void setRunnerVersion(String v) { this.runnerVersion = v; }

    public String getConfigFingerprint() { return configFingerprint; }
    public void setConfigFingerprint(String v) { this.configFingerprint = v; }

    public String getTargetListBatchNo() { return targetListBatchNo; }
    public void setTargetListBatchNo(String v) { this.targetListBatchNo = v; }

    public String getTargetListFingerprint() { return targetListFingerprint; }
    public void setTargetListFingerprint(String v) { this.targetListFingerprint = v; }

    public int getExpectedTargetCount() { return expectedTargetCount; }
    public void setExpectedTargetCount(int v) { this.expectedTargetCount = v; }

    public int getActualTargetCount() { return actualTargetCount; }
    public void setActualTargetCount(int v) { this.actualTargetCount = v; }

    public int getShipOffset() { return shipOffset; }
    public void setShipOffset(int v) { this.shipOffset = v; }

    public int getShipCount() { return shipCount; }
    public void setShipCount(int v) { this.shipCount = v; }

    public int getSelectedShipCount() { return selectedShipCount; }
    public void setSelectedShipCount(int v) { this.selectedShipCount = v; }

    public String getRegionSetCode() { return regionSetCode; }
    public void setRegionSetCode(String v) { this.regionSetCode = v; }

    public String getRegionSetFingerprint() { return regionSetFingerprint; }
    public void setRegionSetFingerprint(String v) { this.regionSetFingerprint = v; }

    public int getResolvedRegionCount() { return resolvedRegionCount; }
    public void setResolvedRegionCount(int v) { this.resolvedRegionCount = v; }

    public Date getFromTime() { return fromTime; }
    public void setFromTime(Date v) { this.fromTime = v; }

    public Date getToTime() { return toTime; }
    public void setToTime(Date v) { this.toTime = v; }

    public int getSliceDays() { return sliceDays; }
    public void setSliceDays(int v) { this.sliceDays = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public int getTotalTasks() { return totalTasks; }
    public void setTotalTasks(int v) { this.totalTasks = v; }

    public int getCompletedTasks() { return completedTasks; }
    public void setCompletedTasks(int v) { this.completedTasks = v; }

    public int getFailedTasks() { return failedTasks; }
    public void setFailedTasks(int v) { this.failedTasks = v; }

    public int getTotalCandidateShips() { return totalCandidateShips; }
    public void setTotalCandidateShips(int v) { this.totalCandidateShips = v; }

    public int getTotalProcessedShips() { return totalProcessedShips; }
    public void setTotalProcessedShips(int v) { this.totalProcessedShips = v; }

    public int getTotalBaselineEvents() { return totalBaselineEvents; }
    public void setTotalBaselineEvents(int v) { this.totalBaselineEvents = v; }

    public int getTotalShadowInserted() { return totalShadowInserted; }
    public void setTotalShadowInserted(int v) { this.totalShadowInserted = v; }

    public int getTotalDuplicateSkipped() { return totalDuplicateSkipped; }
    public void setTotalDuplicateSkipped(int v) { this.totalDuplicateSkipped = v; }

    public String getLastError() { return lastError; }
    public void setLastError(String v) { this.lastError = v; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date v) { this.createdAt = v; }

    public Date getStartedAt() { return startedAt; }
    public void setStartedAt(Date v) { this.startedAt = v; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date v) { this.updatedAt = v; }

    public Date getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Date v) { this.finishedAt = v; }
}
