package com.hifleet.behaviorplatform;

import com.hifleet.trajectory.TrajectoryPoint;
import java.util.Collections;
import java.util.List;

public final class BehaviorDetectorRequest {
    private final String mmsi;
    private final String targetMmsi;
    private final List<TrajectoryPoint> ownPoints;
    private final List<TrajectoryPoint> targetPoints;
    public BehaviorDetectorRequest(String mmsi, String targetMmsi, List<TrajectoryPoint> ownPoints,
                                   List<TrajectoryPoint> targetPoints) {
        this.mmsi = mmsi;
        this.targetMmsi = targetMmsi;
        this.ownPoints = ownPoints == null ? Collections.<TrajectoryPoint>emptyList() : ownPoints;
        this.targetPoints = targetPoints == null ? Collections.<TrajectoryPoint>emptyList() : targetPoints;
    }
    public String getMmsi() { return mmsi; }
    public String getTargetMmsi() { return targetMmsi; }
    public List<TrajectoryPoint> getOwnPoints() { return ownPoints; }
    public List<TrajectoryPoint> getTargetPoints() { return targetPoints; }
}
