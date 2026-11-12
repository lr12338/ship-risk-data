package com.hifleet.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** In-memory candidate funnel diagnostics. Target counters use query/window/MMSI track evaluations. */
public class CandidateFunnelStats {

    public enum RejectionReason {
        TOO_FEW_POINTS,
        NO_LOW_SPEED_POINT,
        NO_TIME_MATCH,
        DISTANCE,
        MATCHED_POINTS,
        DURATION
    }

    private static final int SAMPLE_LIMIT = 3;

    private int replayQueries;
    private int replaySuccessfulQueries;
    private int replayFailedQueries;
    private int replayTruncatedQueries;
    private int replayRawPoints;
    private int replayValidPoints;
    private int replayInvalidPoints;
    private int replayPointsOutsideRequestedTime;
    private int targetTracksGrouped;
    private int invalidTargets;
    private int selfMmsiExcluded;
    private int targetTracksEvaluated;
    private int targetTracksWith1Point;
    private int targetTracksWith2Points;
    private int targetTracksWith3PlusPoints;
    private int cleanPointsGeMin;
    private int hasLowSpeedPoint;
    private int hasTimeAlignedPair;
    private int hasDistanceEligiblePair;
    private int matchedPointsGeMin;
    private int matchedDurationGeMin;
    private int rejectTooFewPoints;
    private int rejectNoLowSpeedPoint;
    private int rejectNoTimeMatch;
    private int rejectDistance;
    private int rejectMatchedPoints;
    private int rejectDuration;
    private int acceptedTrackEvaluations;
    private int candidateAccepted;
    private int identityPolicyRejected;
    private int identityPolicyAccepted;
    private final Map<RejectionReason, List<String>> samples =
            new EnumMap<RejectionReason, List<String>>(RejectionReason.class);

    public void recordReplayQuery() { replayQueries++; }
    public void addReplaySuccessfulQueries(int count) { replaySuccessfulQueries += Math.max(0, count); }
    public void recordReplayFailedQuery() { replayFailedQueries++; }
    public void recordReplayTruncatedQuery() { replayTruncatedQueries++; }

    public void addReplayPoints(int raw, int valid, int invalid, int outsideRequestedTime) {
        replayRawPoints += Math.max(0, raw);
        replayValidPoints += Math.max(0, valid);
        replayInvalidPoints += Math.max(0, invalid);
        replayPointsOutsideRequestedTime += Math.max(0, outsideRequestedTime);
    }

    public void recordTargetTrackGrouped() { targetTracksGrouped++; }
    public void recordInvalidTarget() { invalidTargets++; }
    public void recordSelfMmsiExcluded() { selfMmsiExcluded++; }

    public void recordTargetTrackEvaluated(int cleanPoints) {
        targetTracksEvaluated++;
        if (cleanPoints == 1) targetTracksWith1Point++;
        else if (cleanPoints == 2) targetTracksWith2Points++;
        else if (cleanPoints >= 3) targetTracksWith3PlusPoints++;
    }

    public void recordCleanPointsGeMin() { cleanPointsGeMin++; }
    public void recordHasLowSpeedPoint() { hasLowSpeedPoint++; }
    public void recordHasTimeAlignedPair() { hasTimeAlignedPair++; }
    public void recordHasDistanceEligiblePair() { hasDistanceEligiblePair++; }
    public void recordMatchedPointsGeMin() { matchedPointsGeMin++; }
    public void recordMatchedDurationGeMin() { matchedDurationGeMin++; }

    public void recordRejection(RejectionReason reason, String sample) {
        if (reason == RejectionReason.TOO_FEW_POINTS) rejectTooFewPoints++;
        else if (reason == RejectionReason.NO_LOW_SPEED_POINT) rejectNoLowSpeedPoint++;
        else if (reason == RejectionReason.NO_TIME_MATCH) rejectNoTimeMatch++;
        else if (reason == RejectionReason.DISTANCE) rejectDistance++;
        else if (reason == RejectionReason.MATCHED_POINTS) rejectMatchedPoints++;
        else if (reason == RejectionReason.DURATION) rejectDuration++;
        addSample(reason, sample);
    }

    public void recordAcceptedTrackEvaluation() { acceptedTrackEvaluations++; }
    public void addCandidateAccepted(int count) { candidateAccepted += Math.max(0, count); }
    public void recordIdentityPolicyRejected() { identityPolicyRejected++; }
    public void recordIdentityPolicyAccepted() { identityPolicyAccepted++; }

    public void add(CandidateFunnelStats other) {
        if (other == null) return;
        replayQueries += other.replayQueries;
        replaySuccessfulQueries += other.replaySuccessfulQueries;
        replayFailedQueries += other.replayFailedQueries;
        replayTruncatedQueries += other.replayTruncatedQueries;
        replayRawPoints += other.replayRawPoints;
        replayValidPoints += other.replayValidPoints;
        replayInvalidPoints += other.replayInvalidPoints;
        replayPointsOutsideRequestedTime += other.replayPointsOutsideRequestedTime;
        targetTracksGrouped += other.targetTracksGrouped;
        invalidTargets += other.invalidTargets;
        selfMmsiExcluded += other.selfMmsiExcluded;
        targetTracksEvaluated += other.targetTracksEvaluated;
        targetTracksWith1Point += other.targetTracksWith1Point;
        targetTracksWith2Points += other.targetTracksWith2Points;
        targetTracksWith3PlusPoints += other.targetTracksWith3PlusPoints;
        cleanPointsGeMin += other.cleanPointsGeMin;
        hasLowSpeedPoint += other.hasLowSpeedPoint;
        hasTimeAlignedPair += other.hasTimeAlignedPair;
        hasDistanceEligiblePair += other.hasDistanceEligiblePair;
        matchedPointsGeMin += other.matchedPointsGeMin;
        matchedDurationGeMin += other.matchedDurationGeMin;
        rejectTooFewPoints += other.rejectTooFewPoints;
        rejectNoLowSpeedPoint += other.rejectNoLowSpeedPoint;
        rejectNoTimeMatch += other.rejectNoTimeMatch;
        rejectDistance += other.rejectDistance;
        rejectMatchedPoints += other.rejectMatchedPoints;
        rejectDuration += other.rejectDuration;
        acceptedTrackEvaluations += other.acceptedTrackEvaluations;
        candidateAccepted += other.candidateAccepted;
        identityPolicyRejected += other.identityPolicyRejected;
        identityPolicyAccepted += other.identityPolicyAccepted;
        for (RejectionReason reason : RejectionReason.values()) {
            for (String sample : other.getSamples(reason)) addSample(reason, sample);
        }
    }

    private void addSample(RejectionReason reason, String sample) {
        if (sample == null) return;
        List<String> values = samples.get(reason);
        if (values == null) {
            values = new ArrayList<String>();
            samples.put(reason, values);
        }
        if (values.size() < SAMPLE_LIMIT) values.add(sample);
    }

    public List<String> getSamples(RejectionReason reason) {
        List<String> values = samples.get(reason);
        return values == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(values);
    }

    public int getTerminalOutcomeCount() {
        return rejectTooFewPoints + rejectNoLowSpeedPoint + rejectNoTimeMatch + rejectDistance
                + rejectMatchedPoints + rejectDuration + acceptedTrackEvaluations;
    }

    public boolean isTerminalInvariantValid() {
        return targetTracksEvaluated == getTerminalOutcomeCount();
    }

    public boolean isGroupingInvariantValid() {
        return targetTracksGrouped == invalidTargets + selfMmsiExcluded + targetTracksEvaluated;
    }

    public int getReplayQueries() { return replayQueries; }
    public int getReplaySuccessfulQueries() { return replaySuccessfulQueries; }
    public int getReplayFailedQueries() { return replayFailedQueries; }
    public int getReplayTruncatedQueries() { return replayTruncatedQueries; }
    public int getReplayRawPoints() { return replayRawPoints; }
    public int getReplayValidPoints() { return replayValidPoints; }
    public int getReplayInvalidPoints() { return replayInvalidPoints; }
    public int getReplayPointsOutsideRequestedTime() { return replayPointsOutsideRequestedTime; }
    public int getTargetTracksGrouped() { return targetTracksGrouped; }
    public int getInvalidTargets() { return invalidTargets; }
    public int getSelfMmsiExcluded() { return selfMmsiExcluded; }
    public int getTargetTracksEvaluated() { return targetTracksEvaluated; }
    public int getTargetTracksWith1Point() { return targetTracksWith1Point; }
    public int getTargetTracksWith2Points() { return targetTracksWith2Points; }
    public int getTargetTracksWith3PlusPoints() { return targetTracksWith3PlusPoints; }
    public int getCleanPointsGeMin() { return cleanPointsGeMin; }
    public int getHasLowSpeedPoint() { return hasLowSpeedPoint; }
    public int getHasTimeAlignedPair() { return hasTimeAlignedPair; }
    public int getHasDistanceEligiblePair() { return hasDistanceEligiblePair; }
    public int getMatchedPointsGeMin() { return matchedPointsGeMin; }
    public int getMatchedDurationGeMin() { return matchedDurationGeMin; }
    public int getRejectTooFewPoints() { return rejectTooFewPoints; }
    public int getRejectNoLowSpeedPoint() { return rejectNoLowSpeedPoint; }
    public int getRejectNoTimeMatch() { return rejectNoTimeMatch; }
    public int getRejectDistance() { return rejectDistance; }
    public int getRejectMatchedPoints() { return rejectMatchedPoints; }
    public int getRejectDuration() { return rejectDuration; }
    public int getAcceptedTrackEvaluations() { return acceptedTrackEvaluations; }
    public int getCandidateAccepted() { return candidateAccepted; }
    public int getIdentityPolicyRejected() { return identityPolicyRejected; }
    public int getIdentityPolicyAccepted() { return identityPolicyAccepted; }
}
