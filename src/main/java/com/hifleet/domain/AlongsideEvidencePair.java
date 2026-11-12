package com.hifleet.domain;

import com.hifleet.trajectory.TrajectoryPoint;

/** The exact point pair accepted by the alongside detector. */
public final class AlongsideEvidencePair {
    private final TrajectoryPoint ownPoint;
    private final TrajectoryPoint targetPoint;
    private final int ownPointIndex;
    private final int targetPointIndex;
    private final long timeGapSeconds;
    private final double distanceNm;

    public AlongsideEvidencePair(TrajectoryPoint ownPoint, TrajectoryPoint targetPoint,
                                 int ownPointIndex, int targetPointIndex,
                                 long timeGapSeconds, double distanceNm) {
        this.ownPoint = ownPoint;
        this.targetPoint = targetPoint;
        this.ownPointIndex = ownPointIndex;
        this.targetPointIndex = targetPointIndex;
        this.timeGapSeconds = timeGapSeconds;
        this.distanceNm = distanceNm;
    }

    public TrajectoryPoint getOwnPoint() { return ownPoint; }
    public TrajectoryPoint getTargetPoint() { return targetPoint; }
    public int getOwnPointIndex() { return ownPointIndex; }
    public int getTargetPointIndex() { return targetPointIndex; }
    public long getTimeGapSeconds() { return timeGapSeconds; }
    public double getDistanceNm() { return distanceNm; }

    public String key() { return ownPointIndex + ":" + targetPointIndex; }
}
