package com.hifleet.regionbehavior;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public final class RegionReplaySlice {
    private final String regionFingerprint;
    private final String cellId;
    private final double minLon;
    private final double maxLon;
    private final double minLat;
    private final double maxLat;
    private final LocalDateTime start;
    private final LocalDateTime end;
    private final int spatialRefinementDepth;
    private final String sliceId;

    private RegionReplaySlice(String regionFingerprint, String cellId,
                              double minLon, double maxLon, double minLat, double maxLat,
                              LocalDateTime start, LocalDateTime end, int spatialRefinementDepth) {
        this.regionFingerprint = regionFingerprint;
        this.cellId = cellId;
        this.minLon = minLon;
        this.maxLon = maxLon;
        this.minLat = minLat;
        this.maxLat = maxLat;
        this.start = start;
        this.end = end;
        this.spatialRefinementDepth = spatialRefinementDepth;
        this.sliceId = sha256(regionFingerprint + "|" + cellId + "|"
                + RegionBehaviorTimes.format(start) + "|" + RegionBehaviorTimes.format(end));
    }

    public static RegionReplaySlice root(String fingerprint, String cellId,
                                         double minLon, double maxLon, double minLat, double maxLat,
                                         LocalDateTime start, LocalDateTime end) {
        return new RegionReplaySlice(fingerprint, cellId, minLon, maxLon, minLat, maxLat, start, end, 0);
    }

    public boolean canSplitTime(RegionBehaviorConfig config) {
        return Duration.between(start, end).toMinutes()
                >= config.getMinTimeSliceMinutes() * 2L;
    }

    public boolean canSplitSpace(RegionBehaviorConfig config) {
        return spatialRefinementDepth < config.getMaxSpatialRefinementDepth()
                && maxLon > minLon && maxLat > minLat;
    }

    public List<RegionReplaySlice> splitTime() {
        long minutes = Duration.between(start, end).toMinutes();
        LocalDateTime middle = start.plusMinutes(Math.max(1L, minutes / 2L));
        return Arrays.asList(
                child(cellId + "-t0", minLon, maxLon, minLat, maxLat, start, middle, spatialRefinementDepth),
                child(cellId + "-t1", minLon, maxLon, minLat, maxLat, middle, end, spatialRefinementDepth));
    }

    public List<RegionReplaySlice> splitSpace() {
        double middleLon = (minLon + maxLon) / 2.0;
        double middleLat = (minLat + maxLat) / 2.0;
        int depth = spatialRefinementDepth + 1;
        return Arrays.asList(
                child(cellId + "-s0", minLon, middleLon, minLat, middleLat, start, end, depth),
                child(cellId + "-s1", middleLon, maxLon, minLat, middleLat, start, end, depth),
                child(cellId + "-s2", minLon, middleLon, middleLat, maxLat, start, end, depth),
                child(cellId + "-s3", middleLon, maxLon, middleLat, maxLat, start, end, depth));
    }

    private RegionReplaySlice child(String id, double childMinLon, double childMaxLon,
                                    double childMinLat, double childMaxLat,
                                    LocalDateTime childStart, LocalDateTime childEnd, int depth) {
        return new RegionReplaySlice(regionFingerprint, id, childMinLon, childMaxLon,
                childMinLat, childMaxLat, childStart, childEnd, depth);
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format("%02x", b & 0xff));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public String getSliceId() { return sliceId; }
    public String getCellId() { return cellId; }
    public double getMinLon() { return minLon; }
    public double getMaxLon() { return maxLon; }
    public double getMinLat() { return minLat; }
    public double getMaxLat() { return maxLat; }
    public LocalDateTime getStart() { return start; }
    public LocalDateTime getEnd() { return end; }
    public int getSpatialRefinementDepth() { return spatialRefinementDepth; }
}
