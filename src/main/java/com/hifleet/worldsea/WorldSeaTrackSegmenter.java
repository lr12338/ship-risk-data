package com.hifleet.worldsea;

import com.hifleet.trajectory.TrajectoryPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class WorldSeaTrackSegmenter {
    private final WorldSeaMaskService maskService;
    @Autowired public WorldSeaTrackSegmenter(WorldSeaMaskService maskService) { this.maskService = maskService; }

    public List<List<TrajectoryPoint>> splitInside(List<TrajectoryPoint> points) {
        maskService.requireSnapshot();
        if (points == null || points.isEmpty()) return Collections.emptyList();
        List<TrajectoryPoint> sorted = new ArrayList<TrajectoryPoint>(points);
        Collections.sort(sorted, Comparator.comparing(TrajectoryPoint::getTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<List<TrajectoryPoint>> result = new ArrayList<List<TrajectoryPoint>>();
        List<TrajectoryPoint> current = new ArrayList<TrajectoryPoint>();
        for (TrajectoryPoint point : sorted) {
            boolean inside = point != null && maskService.match(point.getLon(), point.getLat()) != null;
            if (!inside) {
                if (!current.isEmpty()) result.add(current);
                current = new ArrayList<TrajectoryPoint>();
            } else {
                current.add(point);
            }
        }
        if (!current.isEmpty()) result.add(current);
        return result;
    }
}
