package com.hifleet.tankeropensea;

public class TankerOpenSeaRangeResult {
    private final String behavior;
    private final String startTime;
    private final String endTime;
    private int segmentsCompleted;
    private int segmentsFailed;
    private String failedBatchNo;

    public TankerOpenSeaRangeResult(String behavior,
                                    String startTime,
                                    String endTime) {
        this.behavior = behavior;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    void segmentCompleted() {
        segmentsCompleted++;
    }

    void segmentFailed(String batchNo) {
        segmentsFailed++;
        failedBatchNo = batchNo;
    }

    public boolean isSuccess() {
        return segmentsFailed == 0;
    }

    public int getSegmentsCompleted() {
        return segmentsCompleted;
    }

    public int getSegmentsFailed() {
        return segmentsFailed;
    }

    public String summary() {
        return "LIQUID_CONTAINER " + behavior + " range summary"
                + " startTime=" + startTime
                + " endTime=" + endTime
                + " segmentsCompleted=" + segmentsCompleted
                + " segmentsFailed=" + segmentsFailed
                + (failedBatchNo == null ? "" : " failedBatchNo=" + failedBatchNo);
    }
}
