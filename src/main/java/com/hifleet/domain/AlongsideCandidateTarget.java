package com.hifleet.domain;

import com.hifleet.trajectory.TrajectoryPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * AUTO 模式候选目标船，候选来源可以是区域回放或后续空间索引。
 */
public class AlongsideCandidateTarget {

    private String ownMmsi;
    private String targetMmsi;
    private String windowStart;
    private String windowEnd;
    private int matchedPointCount;
    private double minDistanceNm;
    private double maxDistanceNm;
    private double avgDistanceNm;
    private List<TrajectoryPoint> ownWindowPoints = new ArrayList<TrajectoryPoint>();
    private List<TrajectoryPoint> targetPoints = new ArrayList<TrajectoryPoint>();
    private ReplayQueryResult replayQueryResult;

    public String getOwnMmsi() {
        return ownMmsi;
    }

    public void setOwnMmsi(String ownMmsi) {
        this.ownMmsi = ownMmsi;
    }

    public String getTargetMmsi() {
        return targetMmsi;
    }

    public void setTargetMmsi(String targetMmsi) {
        this.targetMmsi = targetMmsi;
    }

    public String getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(String windowStart) {
        this.windowStart = windowStart;
    }

    public String getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(String windowEnd) {
        this.windowEnd = windowEnd;
    }

    public int getMatchedPointCount() {
        return matchedPointCount;
    }

    public void setMatchedPointCount(int matchedPointCount) {
        this.matchedPointCount = matchedPointCount;
    }

    public double getMinDistanceNm() {
        return minDistanceNm;
    }

    public void setMinDistanceNm(double minDistanceNm) {
        this.minDistanceNm = minDistanceNm;
    }

    public double getMaxDistanceNm() {
        return maxDistanceNm;
    }

    public void setMaxDistanceNm(double maxDistanceNm) {
        this.maxDistanceNm = maxDistanceNm;
    }

    public double getAvgDistanceNm() {
        return avgDistanceNm;
    }

    public void setAvgDistanceNm(double avgDistanceNm) {
        this.avgDistanceNm = avgDistanceNm;
    }

    public List<TrajectoryPoint> getOwnWindowPoints() {
        return ownWindowPoints;
    }

    public void setOwnWindowPoints(List<TrajectoryPoint> ownWindowPoints) {
        this.ownWindowPoints = ownWindowPoints;
    }

    public List<TrajectoryPoint> getTargetPoints() {
        return targetPoints;
    }

    public void setTargetPoints(List<TrajectoryPoint> targetPoints) {
        this.targetPoints = targetPoints;
    }

    public ReplayQueryResult getReplayQueryResult() {
        return replayQueryResult;
    }

    public void setReplayQueryResult(ReplayQueryResult replayQueryResult) {
        this.replayQueryResult = replayQueryResult;
    }

    @Override
    public String toString() {
        return "AlongsideCandidateTarget{"
                + "ownMmsi='" + ownMmsi + '\''
                + ", targetMmsi='" + targetMmsi + '\''
                + ", windowStart='" + windowStart + '\''
                + ", windowEnd='" + windowEnd + '\''
                + ", matchedPointCount=" + matchedPointCount
                + ", minDistanceNm=" + minDistanceNm
                + ", maxDistanceNm=" + maxDistanceNm
                + ", avgDistanceNm=" + avgDistanceNm
                + '}';
    }
}
