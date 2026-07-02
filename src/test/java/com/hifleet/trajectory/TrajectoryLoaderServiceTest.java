package com.hifleet.trajectory;

import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TrajectoryLoaderServiceTest {

    @Test
    public void loadSafelySplitsWindowByHours() {
        StubTrajectoryLoaderService loader = new StubTrajectoryLoaderService();

        List<TrajectoryPoint> points = loader.loadSafely("414790000",
                "2026-01-01 00:00:00", "2026-01-02 00:00:00", 15, 6, 100);

        assertEquals(4, loader.calls.size());
        assertEquals("2026-01-01 00:00:00~2026-01-01 06:00:00", loader.calls.get(0));
        assertEquals("2026-01-01 18:00:00~2026-01-02 00:00:00", loader.calls.get(3));
        assertEquals(4, points.size());
    }

    @Test
    public void loadSafelyRejectsTooManyPoints() {
        StubTrajectoryLoaderService loader = new StubTrajectoryLoaderService();
        boolean failed = false;

        try {
            loader.loadSafely("414790000",
                    "2026-01-01 00:00:00", "2026-01-02 00:00:00", 15, 6, 2);
        } catch (IllegalStateException ex) {
            failed = true;
            assertTrue(ex.getMessage().contains("trajectory points exceed limit"));
        }

        assertTrue(failed);
    }

    private static class StubTrajectoryLoaderService extends TrajectoryLoaderService {
        private final List<String> calls = new ArrayList<String>();

        @Override
        public List<TrajectoryPoint> load(String mmsi, String startTime, String endTime, int simplifyZoom) {
            calls.add(startTime + "~" + endTime);
            List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
            points.add(new TrajectoryPoint(mmsi, LocalDateTime.parse(startTime.replace(" ", "T")),
                    120.0 + calls.size(), 30.0, 0.5, 90.0, "ANCHORED"));
            return points;
        }
    }
}
