package com.hifleet.service;

import com.hifleet.trajectory.TrajectoryLoaderService;
import com.hifleet.trajectory.TrajectoryPoint;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Loitering V3 HBase 轨迹适配器，固定读取未做几何抽稀的轨迹。
 */
public class LoiteringV3HBaseTrajectorySource
        implements LoiteringV3AnalyzerService.TrajectorySource {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TrajectoryLoaderService trajectoryLoaderService;
    private final int sliceHours;
    private final int maxPointsPerWindow;

    public LoiteringV3HBaseTrajectorySource(TrajectoryLoaderService trajectoryLoaderService,
                                           int sliceHours, int maxPointsPerWindow) {
        if (trajectoryLoaderService == null || sliceHours <= 0 || maxPointsPerWindow <= 0) {
            throw new IllegalArgumentException("trajectory source configuration must be positive");
        }
        this.trajectoryLoaderService = trajectoryLoaderService;
        this.sliceHours = sliceHours;
        this.maxPointsPerWindow = maxPointsPerWindow;
    }

    @Override
    public List<TrajectoryPoint> load(String mmsi, LocalDateTime windowStart,
                                      LocalDateTime windowEnd) {
        if (windowStart == null || windowEnd == null || !windowStart.isBefore(windowEnd)) {
            throw new IllegalArgumentException("trajectory window must be valid");
        }
        return trajectoryLoaderService.loadSafely(mmsi,
                windowStart.format(TIME_FORMATTER),
                windowEnd.format(TIME_FORMATTER),
                0, sliceHours, maxPointsPerWindow);
    }
}
