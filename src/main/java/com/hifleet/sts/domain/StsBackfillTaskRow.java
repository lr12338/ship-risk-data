package com.hifleet.sts.domain;

import java.util.Date;

public class StsBackfillTaskRow {
    private Long id;
    private String batchNo;
    private int sliceSeq;
    private Date sliceFrom;
    private Date sliceTo;
    private String status;
    private int retryCount;
    private int candidateShipCount;
    private int processedShipCount;
    private int trajectoryLoadedShipCount;
    private int activityWindows;
    private int regionActivityWindows;
    private int replayQueries;
    private int replayFailures;
    private int replayTruncated;
    private int baselineEvents;
    private int regionAcceptedEvents;
    private int shadowInserted;
    private int shadowDuplicateSkipped;
    private int selfPairEvents;
    private String lastError;
    private Date startedAt;
    private Date updatedAt;
    private Date finishedAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String v) { this.batchNo = v; }

    public int getSliceSeq() { return sliceSeq; }
    public void setSliceSeq(int v) { this.sliceSeq = v; }

    public Date getSliceFrom() { return sliceFrom; }
    public void setSliceFrom(Date v) { this.sliceFrom = v; }

    public Date getSliceTo() { return sliceTo; }
    public void setSliceTo(Date v) { this.sliceTo = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int v) { this.retryCount = v; }

    public int getCandidateShipCount() { return candidateShipCount; }
    public void setCandidateShipCount(int v) { this.candidateShipCount = v; }

    public int getProcessedShipCount() { return processedShipCount; }
    public void setProcessedShipCount(int v) { this.processedShipCount = v; }

    public int getTrajectoryLoadedShipCount() { return trajectoryLoadedShipCount; }
    public void setTrajectoryLoadedShipCount(int v) { this.trajectoryLoadedShipCount = v; }

    public int getActivityWindows() { return activityWindows; }
    public void setActivityWindows(int v) { this.activityWindows = v; }

    public int getRegionActivityWindows() { return regionActivityWindows; }
    public void setRegionActivityWindows(int v) { this.regionActivityWindows = v; }

    public int getReplayQueries() { return replayQueries; }
    public void setReplayQueries(int v) { this.replayQueries = v; }

    public int getReplayFailures() { return replayFailures; }
    public void setReplayFailures(int v) { this.replayFailures = v; }

    public int getReplayTruncated() { return replayTruncated; }
    public void setReplayTruncated(int v) { this.replayTruncated = v; }

    public int getBaselineEvents() { return baselineEvents; }
    public void setBaselineEvents(int v) { this.baselineEvents = v; }

    public int getRegionAcceptedEvents() { return regionAcceptedEvents; }
    public void setRegionAcceptedEvents(int v) { this.regionAcceptedEvents = v; }

    public int getShadowInserted() { return shadowInserted; }
    public void setShadowInserted(int v) { this.shadowInserted = v; }

    public int getShadowDuplicateSkipped() { return shadowDuplicateSkipped; }
    public void setShadowDuplicateSkipped(int v) { this.shadowDuplicateSkipped = v; }

    public int getSelfPairEvents() { return selfPairEvents; }
    public void setSelfPairEvents(int v) { this.selfPairEvents = v; }

    public String getLastError() { return lastError; }
    public void setLastError(String v) { this.lastError = v; }

    public Date getStartedAt() { return startedAt; }
    public void setStartedAt(Date v) { this.startedAt = v; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date v) { this.updatedAt = v; }

    public Date getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Date v) { this.finishedAt = v; }
}
