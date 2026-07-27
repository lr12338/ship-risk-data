package com.hifleet.domain;

/**
 * 搭靠分析每日运行统计，用于数据质量和风险分布监控。
 */
public class AlongsideDailyStats {

    private final int scannedMerchantShips;
    private final int candidatePairs;
    private final int confirmedEvents;
    private final int l1Count;
    private final int l2Count;
    private final int l3Count;
    private final int l4Count;

    public AlongsideDailyStats(int scannedMerchantShips, int candidatePairs, int confirmedEvents,
                               int l1Count, int l2Count, int l3Count, int l4Count) {
        this.scannedMerchantShips = scannedMerchantShips;
        this.candidatePairs = candidatePairs;
        this.confirmedEvents = confirmedEvents;
        this.l1Count = l1Count;
        this.l2Count = l2Count;
        this.l3Count = l3Count;
        this.l4Count = l4Count;
    }

    public int getScannedMerchantShips() {
        return scannedMerchantShips;
    }

    public int getCandidatePairs() {
        return candidatePairs;
    }

    public int getConfirmedEvents() {
        return confirmedEvents;
    }

    public int getL1Count() {
        return l1Count;
    }

    public int getL2Count() {
        return l2Count;
    }

    public int getL3Count() {
        return l3Count;
    }

    public int getL4Count() {
        return l4Count;
    }
}
