package com.hifleet.worldsea;

import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class WorldSeaTrackSegmenterTest {
    @Test
    public void outsidePointIsHardBoundary() {
        WorldSeaTrackSegmenter segmenter = new WorldSeaTrackSegmenter(WorldSeaTestFixtures.service());
        List<List<TrajectoryPoint>> segments = segmenter.splitInside(Arrays.asList(
                point(1, 1, 0), point(2, 2, 1), point(50, 50, 2), point(3, 3, 3), point(4, 4, 4)));
        assertEquals(2, segments.size());
        assertEquals(2, segments.get(0).size());
        assertEquals(2, segments.get(1).size());
    }

    private TrajectoryPoint point(double lon, double lat, int minute) {
        return new TrajectoryPoint("1", LocalDateTime.of(2026, 1, 1, 0, minute), lon, lat, 1, 1, null);
    }
}
