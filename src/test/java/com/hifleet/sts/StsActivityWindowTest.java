package com.hifleet.sts;

import com.hifleet.service.ShipAlongsideDetector;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test B — Activity Window
 *
 * Verifies that STS-speed-range activity windows (0.0–4.0 kn)
 * correctly include sub-1-kn speeds that the production
 * Alongside pipeline (1.0 kn min) would filter out.
 *
 * Reuses ShipAlongsideDetector.extractLowSpeedWindows directly
 * with STS parameters — no modification to production code.
 */
public class StsActivityWindowTest {

    private static final double STS_MIN_SPEED = 0.0;
    private static final double STS_MAX_SPEED = 4.0;
    private static final long MIN_DURATION_MIN = 5;
    private static final long SPLIT_GAP_MIN = 10;

    private final ShipAlongsideDetector detector = new ShipAlongsideDetector();

    @Test
    public void subOneKnotSpeedsEnterStsWindow() {
        double[] speeds = {0.1, 0.3, 0.5, 0.9};
        for (double speed : speeds) {
            List<TrajectoryPoint> points = buildPointSeries(speed, 60);
            List<ShipAlongsideDetector.LowSpeedWindow> windows =
                    detector.extractLowSpeedWindows("111", points, STS_MIN_SPEED, STS_MAX_SPEED,
                            MIN_DURATION_MIN, SPLIT_GAP_MIN);
            assertFalse("speed " + speed + " kn should produce an STS activity window",
                    windows.isEmpty());
        }
    }

    @Test
    public void midRangeSpeedsEnterStsWindow() {
        double[] speeds = {1.2, 2.5, 3.8};
        for (double speed : speeds) {
            List<TrajectoryPoint> points = buildPointSeries(speed, 60);
            List<ShipAlongsideDetector.LowSpeedWindow> windows =
                    detector.extractLowSpeedWindows("111", points, STS_MIN_SPEED, STS_MAX_SPEED,
                            MIN_DURATION_MIN, SPLIT_GAP_MIN);
            assertFalse("speed " + speed + " kn should produce an STS activity window",
                    windows.isEmpty());
        }
    }

    @Test
    public void aboveMaxSpeedExcluded() {
        List<TrajectoryPoint> points = buildPointSeries(4.5, 60);
        List<ShipAlongsideDetector.LowSpeedWindow> windows =
                detector.extractLowSpeedWindows("111", points, STS_MIN_SPEED, STS_MAX_SPEED,
                        MIN_DURATION_MIN, SPLIT_GAP_MIN);
        assertTrue("speed 4.5 kn should be excluded from STS activity window (max 4.0)",
                windows.isEmpty());
    }

    @Test
    public void zeroKnotStationaryIncluded() {
        List<TrajectoryPoint> points = buildPointSeries(0.0, 60);
        List<ShipAlongsideDetector.LowSpeedWindow> windows =
                detector.extractLowSpeedWindows("111", points, STS_MIN_SPEED, STS_MAX_SPEED,
                        MIN_DURATION_MIN, SPLIT_GAP_MIN);
        assertFalse("0.0 kn stationary should be included in STS activity window",
                windows.isEmpty());
    }

    @Test
    public void mixedSpeedsFormSingleWindowWhenAllInRange() {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        double[] speeds = {0.1, 0.5, 1.0, 2.0, 3.5, 3.9};
        for (int i = 0; i < speeds.length; i++) {
            points.add(new TrajectoryPoint(
                    "111", base.plusMinutes(i * 5),
                    120.0 + i * 0.01, 30.0 + i * 0.01,
                    speeds[i], 90.0, 90.0, null));
        }
        List<ShipAlongsideDetector.LowSpeedWindow> windows =
                detector.extractLowSpeedWindows("111", points, STS_MIN_SPEED, STS_MAX_SPEED,
                        MIN_DURATION_MIN, SPLIT_GAP_MIN);
        assertFalse("mixed 0.1–3.9 kn should form one continuous STS window",
                windows.isEmpty());
    }

    @Test
    public void aboveMaxSplitsTheWindow() {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        for (int i = 0; i < 20; i++) {
            double speed;
            if (i < 8) speed = 0.5;
            else if (i < 12) speed = 5.0;
            else speed = 0.5;
            points.add(new TrajectoryPoint(
                    "111", base.plusMinutes(i * 2),
                    120.0, 30.0,
                    speed, 90.0, 90.0, null));
        }
        List<ShipAlongsideDetector.LowSpeedWindow> windows =
                detector.extractLowSpeedWindows("111", points, STS_MIN_SPEED, STS_MAX_SPEED,
                        MIN_DURATION_MIN, SPLIT_GAP_MIN);
        assertEquals("above-max segment should split the window into two STS windows",
                2, windows.size());
    }

    private List<TrajectoryPoint> buildPointSeries(double speedKnots, int count) {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        for (int i = 0; i < count; i++) {
            points.add(new TrajectoryPoint(
                    "111", base.plusMinutes(i),
                    120.0 + i * 0.001, 30.0 + i * 0.001,
                    speedKnots, 90.0, 90.0, null));
        }
        return points;
    }
}
