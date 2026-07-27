package com.hifleet.service;

import com.hifleet.trajectory.TrajectoryLoaderService;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LoiteringV3HBaseTrajectorySourceTest {

    @Test
    public void adapterUsesUnsimplifiedSlicedTrajectoryLoad() {
        CapturingLoader loader = new CapturingLoader();
        LoiteringV3HBaseTrajectorySource source =
                new LoiteringV3HBaseTrajectorySource(loader, 6, 100000);
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 12, 0);
        LocalDateTime end = LocalDateTime.of(2026, 7, 2, 12, 0);

        List<TrajectoryPoint> result = source.load("413193000", start, end);

        assertEquals(1, result.size());
        assertEquals("413193000", loader.mmsi);
        assertEquals("2026-07-01 12:00:00", loader.startTime);
        assertEquals("2026-07-02 12:00:00", loader.endTime);
        assertEquals(0, loader.simplifyZoom);
        assertEquals(6, loader.sliceHours);
        assertEquals(100000, loader.maxPoints);
    }

    private static class CapturingLoader extends TrajectoryLoaderService {
        private String mmsi;
        private String startTime;
        private String endTime;
        private int simplifyZoom;
        private int sliceHours;
        private int maxPoints;

        @Override
        public List<TrajectoryPoint> loadSafely(String mmsi, String startTime, String endTime,
                                                int simplifyZoom, int sliceHours,
                                                int maxPointsPerWindow) {
            this.mmsi = mmsi;
            this.startTime = startTime;
            this.endTime = endTime;
            this.simplifyZoom = simplifyZoom;
            this.sliceHours = sliceHours;
            this.maxPoints = maxPointsPerWindow;
            return Collections.singletonList(new TrajectoryPoint(mmsi,
                    LocalDateTime.parse(startTime.replace(" ", "T")),
                    121.0, 31.0, 2.0, 90.0, null));
        }
    }
}
