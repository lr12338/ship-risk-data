package com.hifleet.regionbehavior;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.maritime.MaritimeSceneShadowAuditParams;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShadowV2DeploymentTemplatesTest {
    private static final Path ROOT = Paths.get("deploy/region-scene-rule-shadow-v2-app-deploy");

    @Test
    public void allRegionJobsUseExplicitShadowSafetyFlags() throws Exception {
        ShadowRulesConfig shadow = new ShadowRulesConfig();
        shadow.setEnabled(true);
        RegionBehaviorConfig region = RegionBehaviorConfig.defaults();
        region.setMaxCandidateVessels(100);
        for (String file : Arrays.asList("region-negative-inland-2h.json",
                "region-negative-port-2h.json", "region-open-water-2h.json",
                "region-alongside-ab-6h.json", "region-combined-24h.json")) {
            String end = file.contains("24h") ? "2026-06-02 00:00:00"
                    : file.contains("6h") ? "2026-06-01 06:00:00" : "2026-06-01 02:00:00";
            String json = read("jobs/" + file)
                    .replace("<POLYGON_WKT>", "POLYGON((121 30,121.1 30,121.1 30.1,121 30.1,121 30))")
                    .replace("<START_TIME>", "2026-06-01 00:00:00")
                    .replace("<END_TIME>", end).replace("<BATCH_SUFFIX>", "test");
            RegionBehaviorJobParams.parse(json).validate(region, shadow);
        }
    }

    @Test
    public void configPatchNeverEnablesEnforceOrDatabaseWrites() throws Exception {
        String patch = read("config/application-properties-shadow-v2.patch.txt");
        assertTrue(patch.contains("shadow-rules.enabled=true"));
        assertTrue(patch.contains("shadow-rules.enforce-enabled=false"));
        assertTrue(patch.contains("shadow-rules.database-write-enabled=false"));
        assertTrue(patch.contains("maritime-scene.policy-version=MARITIME_SCENE_SHADOW_V2"));
        assertTrue(patch.contains("maritime-scene.snapshot-policy-version=MARITIME_SCENE_SHADOW_V1"));
        assertFalse(patch.contains("phase1-global-base-v1-runtime.tar.gz"));
    }

    @Test
    public void preflightHasRequiredSafetyFields() throws Exception {
        JSONObject json = JSON.parseObject(read("jobs/scene-preflight.json"));
        MaritimeSceneShadowAuditParams.parse(json.toJSONString());
        assertTrue(json.getBooleanValue("shadowRulesEnabled"));
        assertFalse(json.getBooleanValue("enforceEnabled"));
        assertFalse(json.getBooleanValue("databaseWriteEnabled"));
        assertEquals(1, json.getIntValue("parallelism"));
        assertEquals(ShadowRulesConfig.SCENE_POLICY_VERSION, json.getString("scenePolicyVersion"));
    }

    private String read(String relative) throws Exception {
        return new String(Files.readAllBytes(ROOT.resolve(relative)), StandardCharsets.UTF_8);
    }
}
