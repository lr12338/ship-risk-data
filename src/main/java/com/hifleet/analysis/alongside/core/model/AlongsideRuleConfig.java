package com.hifleet.analysis.alongside.core.model;

import java.io.Serializable;

public class AlongsideRuleConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private double candidateMaxDistanceNm;
    private double candidateMaxSpeedKnots;
    private long candidateMaxTimeGapMinutes;
    private int candidateMinPoints;
    private long candidateMinDurationMinutes;
    private double confirmMaxDistanceNm;
    private double confirmMaxSpeedKnots;
    private double confirmMaxRelativeSpeedKnots;
    private long confirmMaxTimeGapMinutes;
    private long confirmHighConfidenceTimeGapMinutes;
    private long confirmMinDurationMinutes;
    private int confirmMinPoints;
    private boolean headingEnabled;
    private double headingParallelThresholdDegrees;
    private String noSceneDataMaxLevel;
    private boolean autoL4Enabled;
    private String ruleVersion;

    public double getCandidateMaxDistanceNm() {
        return candidateMaxDistanceNm;
    }

    public void setCandidateMaxDistanceNm(double candidateMaxDistanceNm) {
        this.candidateMaxDistanceNm = candidateMaxDistanceNm;
    }

    public double getCandidateMaxSpeedKnots() {
        return candidateMaxSpeedKnots;
    }

    public void setCandidateMaxSpeedKnots(double candidateMaxSpeedKnots) {
        this.candidateMaxSpeedKnots = candidateMaxSpeedKnots;
    }

    public long getCandidateMaxTimeGapMinutes() {
        return candidateMaxTimeGapMinutes;
    }

    public void setCandidateMaxTimeGapMinutes(long candidateMaxTimeGapMinutes) {
        this.candidateMaxTimeGapMinutes = candidateMaxTimeGapMinutes;
    }

    public int getCandidateMinPoints() {
        return candidateMinPoints;
    }

    public void setCandidateMinPoints(int candidateMinPoints) {
        this.candidateMinPoints = candidateMinPoints;
    }

    public long getCandidateMinDurationMinutes() {
        return candidateMinDurationMinutes;
    }

    public void setCandidateMinDurationMinutes(long candidateMinDurationMinutes) {
        this.candidateMinDurationMinutes = candidateMinDurationMinutes;
    }

    public double getConfirmMaxDistanceNm() {
        return confirmMaxDistanceNm;
    }

    public void setConfirmMaxDistanceNm(double confirmMaxDistanceNm) {
        this.confirmMaxDistanceNm = confirmMaxDistanceNm;
    }

    public double getConfirmMaxSpeedKnots() {
        return confirmMaxSpeedKnots;
    }

    public void setConfirmMaxSpeedKnots(double confirmMaxSpeedKnots) {
        this.confirmMaxSpeedKnots = confirmMaxSpeedKnots;
    }

    public double getConfirmMaxRelativeSpeedKnots() {
        return confirmMaxRelativeSpeedKnots;
    }

    public void setConfirmMaxRelativeSpeedKnots(double confirmMaxRelativeSpeedKnots) {
        this.confirmMaxRelativeSpeedKnots = confirmMaxRelativeSpeedKnots;
    }

    public long getConfirmMaxTimeGapMinutes() {
        return confirmMaxTimeGapMinutes;
    }

    public void setConfirmMaxTimeGapMinutes(long confirmMaxTimeGapMinutes) {
        this.confirmMaxTimeGapMinutes = confirmMaxTimeGapMinutes;
    }

    public long getConfirmHighConfidenceTimeGapMinutes() {
        return confirmHighConfidenceTimeGapMinutes;
    }

    public void setConfirmHighConfidenceTimeGapMinutes(long confirmHighConfidenceTimeGapMinutes) {
        this.confirmHighConfidenceTimeGapMinutes = confirmHighConfidenceTimeGapMinutes;
    }

    public long getConfirmMinDurationMinutes() {
        return confirmMinDurationMinutes;
    }

    public void setConfirmMinDurationMinutes(long confirmMinDurationMinutes) {
        this.confirmMinDurationMinutes = confirmMinDurationMinutes;
    }

    public int getConfirmMinPoints() {
        return confirmMinPoints;
    }

    public void setConfirmMinPoints(int confirmMinPoints) {
        this.confirmMinPoints = confirmMinPoints;
    }

    public boolean isHeadingEnabled() {
        return headingEnabled;
    }

    public void setHeadingEnabled(boolean headingEnabled) {
        this.headingEnabled = headingEnabled;
    }

    public double getHeadingParallelThresholdDegrees() {
        return headingParallelThresholdDegrees;
    }

    public void setHeadingParallelThresholdDegrees(double headingParallelThresholdDegrees) {
        this.headingParallelThresholdDegrees = headingParallelThresholdDegrees;
    }

    public String getNoSceneDataMaxLevel() {
        return noSceneDataMaxLevel;
    }

    public void setNoSceneDataMaxLevel(String noSceneDataMaxLevel) {
        this.noSceneDataMaxLevel = noSceneDataMaxLevel;
    }

    public boolean isAutoL4Enabled() {
        return autoL4Enabled;
    }

    public void setAutoL4Enabled(boolean autoL4Enabled) {
        this.autoL4Enabled = autoL4Enabled;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }
}
