package com.hifleet.regionbehavior;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RegionAisDiscoveryResult {
    private RegionBehaviorStatus status = RegionBehaviorStatus.COMPLETE;
    private int querySlices;
    private int completedSlices;
    private int timeoutSlices;
    private int truncatedSlices;
    private int parseErrorSlices;
    private int failedSlices;
    private int rawVessels;
    private int rawPoints;
    private int validPoints;
    private int uniquePoints;
    private int duplicatePoints;
    private boolean cappedByMaxCandidates;
    private long elapsedMs;
    final Map<String, RegionShipCandidate> candidates =
            new LinkedHashMap<String, RegionShipCandidate>();
    private final List<RegionReplaySliceResult> sliceResults =
            new ArrayList<RegionReplaySliceResult>();

    public boolean isQueryComplete() { return status == RegionBehaviorStatus.COMPLETE; }
    public RegionBehaviorStatus getStatus() { return status; }
    public int getQuerySlices() { return querySlices; }
    public int getCompletedSlices() { return completedSlices; }
    public int getTimeoutSlices() { return timeoutSlices; }
    public int getTruncatedSlices() { return truncatedSlices; }
    public int getParseErrorSlices() { return parseErrorSlices; }
    public int getFailedSlices() { return failedSlices; }
    public int getRawVessels() { return rawVessels; }
    public int getRawPoints() { return rawPoints; }
    public int getValidPoints() { return validPoints; }
    public int getUniqueVessels() { return candidates.size(); }
    public int getUniquePoints() { return uniquePoints; }
    public int getDuplicatePoints() { return duplicatePoints; }
    public boolean isCappedByMaxCandidates() { return cappedByMaxCandidates; }
    public long getElapsedMs() { return elapsedMs; }
    public Map<String, RegionShipCandidate> getCandidates() {
        return Collections.unmodifiableMap(candidates);
    }
    public List<RegionReplaySliceResult> getSliceResults() {
        return Collections.unmodifiableList(sliceResults);
    }

    void queried(int rawVessels, int rawPoints, int validPoints, long elapsedMs) {
        querySlices++;
        this.rawVessels += rawVessels;
        this.rawPoints += rawPoints;
        this.validPoints += validPoints;
        this.elapsedMs += elapsedMs;
    }
    void completed() { completedSlices++; }
    void timeout() { timeoutSlices++; markPartial(); }
    void truncated() { truncatedSlices++; }
    void terminalTruncation() { markPartial(); }
    void parseError() { parseErrorSlices++; markPartial(); }
    void failed() { failedSlices++; markPartial(); }
    void addSliceResult(RegionReplaySliceResult value) { sliceResults.add(value); }
    void countUniquePoint() { uniquePoints++; }
    void countDuplicatePoint() { duplicatePoints++; }
    void candidateLimitExceeded() {
        cappedByMaxCandidates = true;
        status = RegionBehaviorStatus.CANDIDATE_LIMIT_EXCEEDED;
    }
    private void markPartial() {
        if (status != RegionBehaviorStatus.CANDIDATE_LIMIT_EXCEEDED) {
            status = RegionBehaviorStatus.PARTIAL;
        }
    }
}
