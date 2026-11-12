package com.hifleet.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * AUTO 区域回放候选搜索结果。
 */
public class AlongsideReplaySearchResult {

    private final List<AlongsideCandidateTarget> candidates = new ArrayList<AlongsideCandidateTarget>();
    private int replayQueryCount;
    private int successfulReplayQueries;
    private int replayFailures;
    private int truncatedQueries;
    private int returnedVessels;
    private int rawPoints;
    private int validPoints;
    private int parseErrors;
    private int prefilteredTargets;
    private long elapsedMs;
    private String replayFailureReason;
    private boolean replayFailureRetryable;
    private final CandidateFunnelStats candidateFunnel = new CandidateFunnelStats();

    public List<AlongsideCandidateTarget> getCandidates() {
        return candidates;
    }

    public void addCandidate(AlongsideCandidateTarget candidate) {
        candidates.add(candidate);
    }

    public void addReplayResult(ReplayQueryResult result) {
        recordTerminalReplayResult(result);
        if (result != null && result.isSuccess() && !result.isTruncated()) {
            recordSuccessfulReplayQueries(1);
            addReplayData(result);
        }
    }

    public void recordSplitReplayResult(ReplayQueryResult result) {
        replayQueryCount++;
        candidateFunnel.recordReplayQuery();
        if (result != null) {
            elapsedMs += result.getElapsedMs();
        }
    }

    public void recordTerminalReplayResult(ReplayQueryResult result) {
        replayQueryCount++;
        candidateFunnel.recordReplayQuery();
        if (result != null) {
            elapsedMs += result.getElapsedMs();
        }
        if (result == null || !result.isSuccess()) {
            replayFailures++;
            candidateFunnel.recordReplayFailedQuery();
            replayFailureReason = result == null || result.getErrorMessage() == null
                    ? "NULL_REPLAY_RESULT" : result.getErrorMessage();
            replayFailureRetryable = result != null && result.isRetryableFailure();
            return;
        }
        if (result.isTruncated()) {
            truncatedQueries++;
            candidateFunnel.recordReplayTruncatedQuery();
            return;
        }
    }

    public void recordSuccessfulReplayQueries(int count) {
        successfulReplayQueries += Math.max(0, count);
        candidateFunnel.addReplaySuccessfulQueries(count);
    }

    public void addReplayData(ReplayQueryResult result) {
        if (result == null) {
            return;
        }
        returnedVessels += result.getRawVesselCount();
        rawPoints += result.getRawPointCount();
        validPoints += result.getValidPointCount();
        parseErrors += result.getParseErrorCount();
        candidateFunnel.addReplayPoints(result.getRawPointCount(), result.getValidPointCount(),
                result.getInvalidPointCount(), result.getOutsideRequestedTimePointCount());
    }

    public int getReplayQueryCount() {
        return replayQueryCount;
    }

    public int getAttemptedReplayQueryCount() { return replayQueryCount; }
    public int getSuccessfulReplayQueryCount() { return successfulReplayQueries; }
    public int getFailedReplayQueryCount() { return replayFailures; }
    public int getTruncatedReplayQueryCount() { return truncatedQueries; }

    public int getReplayFailures() {
        return replayFailures;
    }

    public int getTruncatedQueries() {
        return truncatedQueries;
    }

    public int getReturnedVessels() {
        return returnedVessels;
    }

    public int getRawPoints() {
        return rawPoints;
    }

    public int getValidPoints() {
        return validPoints;
    }

    public int getParseErrors() {
        return parseErrors;
    }

    public int getPrefilteredTargets() {
        return prefilteredTargets;
    }

    public void incrementPrefilteredTargets() {
        prefilteredTargets++;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public String getReplayFailureReason() { return replayFailureReason; }
    public boolean isReplayFailureRetryable() { return replayFailureRetryable; }
    public CandidateFunnelStats getCandidateFunnel() { return candidateFunnel; }
}
