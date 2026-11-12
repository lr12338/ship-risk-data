package com.hifleet.domain;

import com.hifleet.trajectory.TrajectoryPoint;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * 行为事件与命中轨迹点快照。
 */
public class ShipBehaviorTrace {

    private final ShipBehaviorEvent event;
    private final List<TrajectoryPoint> ownPoints;
    private final List<TrajectoryPoint> targetPoints;
    private final List<AlongsideEvidencePair> alongsideEvidencePairs;
    private final int alongsideSourceOwnPointCount;
    private final int alongsideSourceTargetPointCount;
    private final long alongsideMaxTimeGapSeconds;
    private final String alongsidePairEvidenceSource;

    public ShipBehaviorTrace(ShipBehaviorEvent event, List<TrajectoryPoint> ownPoints, List<TrajectoryPoint> targetPoints) {
        this(event, ownPoints, targetPoints, Collections.<AlongsideEvidencePair>emptyList(),
                0, 0, 0L, null);
    }

    public ShipBehaviorTrace(ShipBehaviorEvent event, List<TrajectoryPoint> ownPoints,
                             List<TrajectoryPoint> targetPoints,
                             List<AlongsideEvidencePair> alongsideEvidencePairs,
                             int alongsideSourceOwnPointCount,
                             int alongsideSourceTargetPointCount,
                             long alongsideMaxTimeGapSeconds,
                             String alongsidePairEvidenceSource) {
        this.event = event;
        this.ownPoints = ownPoints;
        this.targetPoints = targetPoints;
        this.alongsideEvidencePairs = Collections.unmodifiableList(
                new ArrayList<AlongsideEvidencePair>(alongsideEvidencePairs == null
                        ? Collections.<AlongsideEvidencePair>emptyList() : alongsideEvidencePairs));
        this.alongsideSourceOwnPointCount = alongsideSourceOwnPointCount;
        this.alongsideSourceTargetPointCount = alongsideSourceTargetPointCount;
        this.alongsideMaxTimeGapSeconds = alongsideMaxTimeGapSeconds;
        this.alongsidePairEvidenceSource = alongsidePairEvidenceSource;
    }

    public ShipBehaviorEvent getEvent() {
        return event;
    }

    public List<TrajectoryPoint> getOwnPoints() {
        return ownPoints;
    }

    public List<TrajectoryPoint> getTargetPoints() {
        return targetPoints;
    }

    public List<AlongsideEvidencePair> getAlongsideEvidencePairs() { return alongsideEvidencePairs; }
    public int getAlongsideSourceOwnPointCount() { return alongsideSourceOwnPointCount; }
    public int getAlongsideSourceTargetPointCount() { return alongsideSourceTargetPointCount; }
    public long getAlongsideMaxTimeGapSeconds() { return alongsideMaxTimeGapSeconds; }
    public String getAlongsidePairEvidenceSource() { return alongsidePairEvidenceSource; }
}
