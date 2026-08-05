package com.hifleet.behaviorplatform;

import com.hifleet.regionbehavior.RegionBehaviorConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

public class RegionDefinitionLoaderTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void loadsVersionedPolygonWithStableFingerprint() throws Exception {
        String json = "{\"regionId\":\"offshore-v1\",\"version\":1,\"regionName\":\"offshore\","
                + "\"polygonWkt\":\"POLYGON((120 30,120.01 30,120.01 30.01,120 30.01,120 30))\","
                + "\"regionBufferNm\":1,\"discoveryTimeSliceMinutes\":30,\"maxCandidateVessels\":50}";
        Files.write(folder.newFile("offshore-v1.json").toPath(), json.getBytes(StandardCharsets.UTF_8));
        RegionDefinition value = new RegionDefinitionLoader().load(folder.getRoot().toPath(),
                RegionBehaviorConfig.defaults()).require("offshore-v1");
        assertEquals(1, value.getVersion()); assertEquals(64, value.getFingerprint().length());
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateRegionIdIsRejected() throws Exception {
        String json = "{\"regionId\":\"same-v1\",\"version\":1,\"regionName\":\"same\","
                + "\"polygonWkt\":\"POLYGON((120 30,120.01 30,120.01 30.01,120 30.01,120 30))\","
                + "\"regionBufferNm\":0,\"discoveryTimeSliceMinutes\":30,\"maxCandidateVessels\":50}";
        Files.write(folder.newFile("a.json").toPath(), json.getBytes(StandardCharsets.UTF_8));
        Files.write(folder.newFile("b.json").toPath(), json.getBytes(StandardCharsets.UTF_8));
        new RegionDefinitionLoader().load(folder.getRoot().toPath(), RegionBehaviorConfig.defaults());
    }
}
