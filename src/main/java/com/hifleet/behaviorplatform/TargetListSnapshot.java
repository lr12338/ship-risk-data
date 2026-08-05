package com.hifleet.behaviorplatform;

public final class TargetListSnapshot {
    private final int totalCount;
    private final int enabledCount;
    private final Integer minSeq;
    private final Integer maxSeq;
    private final int distinctSeq;
    private final int distinctMmsi;
    public TargetListSnapshot(int totalCount, int enabledCount, Integer minSeq, Integer maxSeq,
                              int distinctSeq, int distinctMmsi) {
        this.totalCount = totalCount; this.enabledCount = enabledCount; this.minSeq = minSeq;
        this.maxSeq = maxSeq; this.distinctSeq = distinctSeq; this.distinctMmsi = distinctMmsi;
    }
    public int getTotalCount() { return totalCount; }
    public int getEnabledCount() { return enabledCount; }
    public Integer getMinSeq() { return minSeq; }
    public Integer getMaxSeq() { return maxSeq; }
    public int getDistinctSeq() { return distinctSeq; }
    public int getDistinctMmsi() { return distinctMmsi; }
}
