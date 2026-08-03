package com.hifleet.maritime;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class MaritimeScenePolicy {
    public static final String VERSION = "MARITIME_SCENE_SHADOW_V1";

    private final String mode;
    private final double boundaryToleranceMeters;
    private final double coastSearchRadiusNm;
    private final double highInlandPointRatio;
    private final double minInlandMinutes;
    private final double openWaterPointRatio;
    private final double maxUnknownRatio;

    public MaritimeScenePolicy(
            String mode,
            double boundaryToleranceMeters,
            double coastSearchRadiusNm,
            double highInlandPointRatio,
            double minInlandMinutes,
            double openWaterPointRatio,
            double maxUnknownRatio) {
        this.mode = mode == null ? "OFF" : mode.toUpperCase();
        this.boundaryToleranceMeters = boundaryToleranceMeters;
        this.coastSearchRadiusNm = coastSearchRadiusNm;
        this.highInlandPointRatio = highInlandPointRatio;
        this.minInlandMinutes = minInlandMinutes;
        this.openWaterPointRatio = openWaterPointRatio;
        this.maxUnknownRatio = maxUnknownRatio;
    }

    public static MaritimeScenePolicy defaults(String mode) {
        return new MaritimeScenePolicy(mode, 1000.0, 60.0, 0.90, 60.0, 0.90, 0.05);
    }

    public String getMode() { return mode; }
    public double getBoundaryToleranceMeters() { return boundaryToleranceMeters; }
    public double getCoastSearchRadiusNm() { return coastSearchRadiusNm; }
    public double getHighInlandPointRatio() { return highInlandPointRatio; }
    public double getMinInlandMinutes() { return minInlandMinutes; }
    public double getOpenWaterPointRatio() { return openWaterPointRatio; }
    public double getMaxUnknownRatio() { return maxUnknownRatio; }

    public boolean isShadow() { return "SHADOW".equals(mode); }

    public static Set<String> canonicalTypes(Iterable<MaritimeSceneTileLoader.TileRecord> records) {
        Set<String> result = new LinkedHashSet<String>();
        if (records != null) {
            for (MaritimeSceneTileLoader.TileRecord record : records) {
                result.add(record.getSceneType());
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
