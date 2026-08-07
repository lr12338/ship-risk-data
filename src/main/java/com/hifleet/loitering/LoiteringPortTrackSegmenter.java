package com.hifleet.loitering;

import com.hifleet.trajectory.TrajectoryPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class LoiteringPortTrackSegmenter {
    private final LoiteringPortSceneService sceneService;
    private final LoiteringPortSceneProperties properties;
    private final ThreadLocal<Stats> stats = new ThreadLocal<Stats>();
    @Autowired public LoiteringPortTrackSegmenter(LoiteringPortSceneService sceneService,
                                                   LoiteringPortSceneProperties properties) {
        this.sceneService = sceneService; this.properties = properties;
    }
    public List<List<TrajectoryPoint>> splitEligible(List<TrajectoryPoint> points) {
        LoiteringPortSceneSnapshot snapshot = sceneService.requireSnapshot();
        if (points == null || points.isEmpty()) { stats.set(new Stats()); return Collections.emptyList(); }
        List<TrajectoryPoint> sorted = new ArrayList<TrajectoryPoint>(points);
        Collections.sort(sorted, Comparator.comparing(TrajectoryPoint::getTime, Comparator.nullsLast(Comparator.naturalOrder())));
        List<List<TrajectoryPoint>> result = new ArrayList<List<TrajectoryPoint>>(); List<TrajectoryPoint> current = new ArrayList<TrajectoryPoint>(); Stats value = new Stats();
        for (TrajectoryPoint point : sorted) {
            LoiteringPortSceneSnapshot.Match match = point == null ? null : snapshot.match(point.getLon(), point.getLat(), properties.getMaxDistanceNm());
            boolean below = point == null || !point.isSpeedValid() || point.getSpeed() < properties.getMinSpeed();
            boolean eligible = !below && match != null && match.isEligible();
            if (!eligible) {
                if (!current.isEmpty()) result.add(current); current = new ArrayList<TrajectoryPoint>();
                if (below) value.belowMinSpeedSplitCount++; else if (match != null && match.isOperational()) value.portOperationalRejectedCount++;
                else if (match != null && match.isAnchor()) value.anchorRejectedCount++; else value.tooFarFromPortRejectedCount++;
            } else current.add(point);
        }
        if (!current.isEmpty()) result.add(current); stats.set(value); return result;
    }
    public Stats getLastStats() { Stats value=stats.get(); return value == null ? new Stats() : value; }
    public static final class Stats {
        private int belowMinSpeedSplitCount, portOperationalRejectedCount, anchorRejectedCount, tooFarFromPortRejectedCount;
        public int getBelowMinSpeedSplitCount() { return belowMinSpeedSplitCount; }
        public int getPortOperationalRejectedCount() { return portOperationalRejectedCount; }
        public int getAnchorRejectedCount() { return anchorRejectedCount; }
        public int getTooFarFromPortRejectedCount() { return tooFarFromPortRejectedCount; }
    }
}
