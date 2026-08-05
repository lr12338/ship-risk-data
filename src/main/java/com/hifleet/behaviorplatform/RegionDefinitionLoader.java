package com.hifleet.behaviorplatform;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.regionbehavior.RegionBehaviorConfig;
import com.hifleet.regionbehavior.RegionGeometry;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RegionDefinitionLoader {
    public RegionDefinitionRegistry load(Path directory, RegionBehaviorConfig config) {
        RegionDefinitionRegistry result = new RegionDefinitionRegistry(config);
        if (directory == null || !Files.exists(directory)) return result;
        if (!Files.isDirectory(directory)) throw new IllegalStateException("region definition path is not a directory");
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) {
                if (!file.getFileName().toString().matches("[A-Za-z0-9._-]+\\.json"))
                    throw new IllegalArgumentException("unsafe region definition filename");
                JSONObject json = JSON.parseObject(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
                String id = required(json.getString("regionId"), "regionId");
                if (!id.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("unsafe regionId");
                int version = json.getIntValue("version");
                String wkt = required(json.getString("polygonWkt"), "polygonWkt");
                double buffer = json.getDoubleValue("regionBufferNm");
                int slice = json.getIntValue("discoveryTimeSliceMinutes");
                int max = json.getIntValue("maxCandidateVessels");
                if (version <= 0 || buffer < 0 || slice <= 0 || max <= 0
                        || max > config.getMaxCandidateVessels())
                    throw new IllegalArgumentException("invalid region definition limits: " + id);
                RegionGeometry geometry = RegionGeometry.parse(wkt, buffer, config);
                geometry.assertWithinAreaLimit(config);
                result.register(new RegionDefinition(id, version,
                        required(json.getString("regionName"), "regionName"), wkt,
                        buffer, slice, max, geometry));
            }
        } catch (IOException ex) { throw new IllegalStateException("cannot load region definitions", ex); }
        return result;
    }
    private String required(String value, String name) {
        value = StringUtils.trimToNull(value);
        if (value == null) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
