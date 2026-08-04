package com.hifleet.regionbehavior;

import com.hifleet.maritime.MaritimeSceneShadowAuditParams;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class DeploymentJobTemplatesTest {
    private static final Path JOBS = Paths.get(
            "deploy/region-scene-integration-app-deploy/jobs");

    @Test
    public void deploymentJobsUseTheActualParameterModelsAndUniqueBatches() throws Exception {
        String scene = read("scene-preflight.json").replace("<SCENE_FINGERPRINT>",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        MaritimeSceneShadowAuditParams sceneParams = MaritimeSceneShadowAuditParams.parse(scene);
        assertEquals(100, sceneParams.getShipLimit());

        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        config.setMaxCandidateVessels(100);
        Set<String> batches = new HashSet<String>();
        validateRegion("region-preflight-2h.json", "2026-06-01 02:00:00", config, batches);
        validateRegion("region-loitering-2h.json", "2026-06-01 02:00:00", config, batches);
        validateRegion("region-alongside-6h.json", "2026-06-01 06:00:00", config, batches);
        validateRegion("region-combined-24h.json", "2026-06-02 00:00:00", config, batches);
        assertEquals(4, batches.size());
    }

    private void validateRegion(String file, String endTime, RegionBehaviorConfig config,
                                Set<String> batches) throws Exception {
        String json = read(file)
                .replace("<POLYGON_WKT>",
                        "POLYGON((121 30,121.1 30,121.1 30.1,121 30.1,121 30))")
                .replace("<START_TIME>", "2026-06-01 00:00:00")
                .replace("<END_TIME>", endTime)
                .replace("<BATCH_SUFFIX>", "test");
        RegionBehaviorJobParams params = RegionBehaviorJobParams.parse(json);
        params.validate(config);
        batches.add(params.getBatchNo());
    }

    private String read(String file) throws Exception {
        return new String(Files.readAllBytes(JOBS.resolve(file)), StandardCharsets.UTF_8);
    }
}
