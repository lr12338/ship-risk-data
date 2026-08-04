package com.hifleet.regionbehavior;

import com.hifleet.trajectory.TrajectoryPoint;

public final class SynchronizedShipPairPoint {
    private final TrajectoryPoint ownPoint;
    private final TrajectoryPoint targetPoint;
    private final long synchronizedEpochMillis;
    private final long timeDifferenceMillis;
    private final double midpointLon;
    private final double midpointLat;

    SynchronizedShipPairPoint(TrajectoryPoint ownPoint, TrajectoryPoint targetPoint,
                              long synchronizedEpochMillis, long timeDifferenceMillis,
                              double midpointLon, double midpointLat) {
        this.ownPoint = ownPoint;
        this.targetPoint = targetPoint;
        this.synchronizedEpochMillis = synchronizedEpochMillis;
        this.timeDifferenceMillis = timeDifferenceMillis;
        this.midpointLon = midpointLon;
        this.midpointLat = midpointLat;
    }

    public TrajectoryPoint getOwnPoint() { return ownPoint; }
    public TrajectoryPoint getTargetPoint() { return targetPoint; }
    public long getSynchronizedEpochMillis() { return synchronizedEpochMillis; }
    public long getTimeDifferenceMillis() { return timeDifferenceMillis; }
    public double getMidpointLon() { return midpointLon; }
    public double getMidpointLat() { return midpointLat; }
}
