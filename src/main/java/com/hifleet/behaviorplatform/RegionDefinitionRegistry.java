package com.hifleet.behaviorplatform;

import com.hifleet.regionbehavior.RegionBehaviorConfig;
import com.hifleet.regionbehavior.RegionGeometry;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RegionDefinitionRegistry {
    private final Map<String, RegionDefinition> values = new LinkedHashMap<String, RegionDefinition>();
    private final RegionBehaviorConfig config;
    RegionDefinitionRegistry(RegionBehaviorConfig config) { this.config = config; }
    void register(RegionDefinition value) {
        if (values.put(value.getRegionId(), value) != null)
            throw new IllegalArgumentException("duplicate regionId: " + value.getRegionId());
    }
    public RegionDefinition require(String id) {
        RegionDefinition value = values.get(id);
        if (value == null) throw new IllegalArgumentException("unknown regionId: " + id);
        return value;
    }
    public RegionDefinition bbox(double[] value, int maxCandidates) {
        String id = "bbox-" + value[0] + "-" + value[1] + "-" + value[2] + "-" + value[3];
        String wkt = "POLYGON((" + value[0] + " " + value[1] + "," + value[2] + " " + value[1]
                + "," + value[2] + " " + value[3] + "," + value[0] + " " + value[3]
                + "," + value[0] + " " + value[1] + "))";
        RegionGeometry geometry = RegionGeometry.parse(wkt, 0.0, config);
        geometry.assertWithinAreaLimit(config);
        return new RegionDefinition(id, 1, id, wkt, 0.0, config.getTimeSliceMinutes(),
                Math.min(maxCandidates, config.getMaxCandidateVessels()), geometry);
    }
}
