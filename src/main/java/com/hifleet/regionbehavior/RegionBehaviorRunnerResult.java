package com.hifleet.regionbehavior;

import com.hifleet.domain.ShipBehaviorTrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RegionBehaviorRunnerResult {
    private final List<ShipBehaviorTrace> traces;
    private final int rawCandidates;
    private final int points;
    private final int replayQueries;
    private final int replayFailures;
    private final int truncatedQueries;
    private final String failure;

    private RegionBehaviorRunnerResult(List<ShipBehaviorTrace> traces, int rawCandidates,
                                       int points, int replayQueries, int replayFailures,
                                       int truncatedQueries, String failure) {
        this.traces = Collections.unmodifiableList(new ArrayList<ShipBehaviorTrace>(traces));
        this.rawCandidates = rawCandidates;
        this.points = points;
        this.replayQueries = replayQueries;
        this.replayFailures = replayFailures;
        this.truncatedQueries = truncatedQueries;
        this.failure = failure;
    }

    public static RegionBehaviorRunnerResult success(List<ShipBehaviorTrace> traces,
                                                      int rawCandidates, int points,
                                                      int replayQueries) {
        return new RegionBehaviorRunnerResult(traces, rawCandidates, points,
                replayQueries, 0, 0, null);
    }

    public static RegionBehaviorRunnerResult alongside(List<ShipBehaviorTrace> traces,
                                                        int rawCandidates, int points,
                                                        int replayQueries, int replayFailures,
                                                        int truncatedQueries, int failures) {
        String failure = failures > 0 || replayFailures > 0 || truncatedQueries > 0
                ? "incomplete alongside analysis failed=" + failures
                + " replayFailures=" + replayFailures + " truncated=" + truncatedQueries : null;
        return new RegionBehaviorRunnerResult(traces, rawCandidates, points, replayQueries,
                replayFailures, truncatedQueries, failure);
    }

    public static RegionBehaviorRunnerResult failed(String failure) {
        return new RegionBehaviorRunnerResult(Collections.<ShipBehaviorTrace>emptyList(),
                0, 0, 0, 0, 0, failure);
    }

    public List<ShipBehaviorTrace> getTraces() { return traces; }
    public int getRawCandidates() { return rawCandidates; }
    public int getPoints() { return points; }
    public int getReplayQueries() { return replayQueries; }
    public int getReplayFailures() { return replayFailures; }
    public int getTruncatedQueries() { return truncatedQueries; }
    public String getFailure() { return failure; }
    public boolean isComplete() { return failure == null; }
}
