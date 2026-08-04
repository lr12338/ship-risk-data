package com.hifleet.regionbehavior;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RegionBehaviorJobParamsTest {

    @Test
    public void parsesAndValidatesDryRunOnlyParams() {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        RegionBehaviorJobParams params = RegionBehaviorJobParams.parse("{"
                + "\"mode\":\"region_behavior_v1\","
                + "\"regionId\":\"r-1\",\"regionName\":\"test\","
                + "\"polygonWkt\":\"POLYGON((121 30,122 30,122 31,121 31,121 30))\","
                + "\"startTime\":\"2026-06-01 00:00:00\","
                + "\"endTime\":\"2026-06-01 06:00:00\","
                + "\"behaviorTypes\":[\"LOITERING\",\"ALONGSIDE\"],"
                + "\"batchNo\":\"batch-1\",\"dryRun\":true,\"storeCandidates\":false}");

        params.validate(config);

        assertEquals(2, params.getBehaviorTypes().size());
        assertEquals(6L, params.runHours());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsStoreMode() {
        RegionBehaviorJobParams params = validParams();
        params.setStoreCandidates(true);
        params.validate(RegionBehaviorConfig.defaults());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsCredentials() {
        RegionBehaviorJobParams.parse("{\"regionId\":\"r\",\"batchNo\":\"b\","
                + "\"password\":\"secret\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTooLongRun() {
        RegionBehaviorJobParams params = validParams();
        params.setEndTime("2026-06-03 00:00:00");
        params.validate(RegionBehaviorConfig.defaults());
    }

    @Test
    public void defaultsAreConservativeAndBasedOnExistingReplayLimits() {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        assertEquals(24, config.getMaxRunHours());
        assertEquals(50000, config.getMaxReplayPointsPerSlice());
        assertTrue(config.getMaxCandidateVessels() <= 500);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parameterOverridesCannotBypassConfiguredContextLimit() {
        RegionBehaviorJobParams params = RegionBehaviorJobParams.parse("{"
                + "\"mode\":\"region_behavior_v1\",\"regionId\":\"r\",\"regionName\":\"n\","
                + "\"polygonWkt\":\"POLYGON((121 30,122 30,122 31,121 31,121 30))\","
                + "\"startTime\":\"2026-06-01 00:00:00\",\"endTime\":\"2026-06-01 01:00:00\","
                + "\"behaviorTypes\":[\"LOITERING\"],\"analysisContextBeforeHours\":1000,"
                + "\"batchNo\":\"b\",\"dryRun\":true,\"storeCandidates\":false}");
        params.validate(RegionBehaviorConfig.defaults());
    }

    @Test
    public void shadowV2RequiresExplicitSafeFlagsAndOilTankerPolicy() {
        ShadowRulesConfig shadow = new ShadowRulesConfig();
        shadow.setEnabled(true);
        RegionBehaviorJobParams params = RegionBehaviorJobParams.parse(shadowParams(false));

        params.validate(RegionBehaviorConfig.defaults(), shadow);

        assertTrue(params.isShadowRulesEnabled());
        assertEquals("OIL_TANKER_ONLY", params.getShipTypePolicy());
        assertEquals(Integer.valueOf(1), params.getParallelism());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shadowV2RejectsDatabaseWrites() {
        ShadowRulesConfig shadow = new ShadowRulesConfig();
        shadow.setEnabled(true);
        RegionBehaviorJobParams.parse(shadowParams(true))
                .validate(RegionBehaviorConfig.defaults(), shadow);
    }

    @Test
    public void oldRegionParamsRemainValidWhenServerHasShadowCapability() {
        ShadowRulesConfig shadow = new ShadowRulesConfig();
        shadow.setEnabled(true);

        validParams().validate(RegionBehaviorConfig.defaults(), shadow);
    }

    private String shadowParams(boolean databaseWriteEnabled) {
        return "{\"mode\":\"region_behavior_v1\",\"regionId\":\"r\",\"regionName\":\"n\","
                + "\"polygonWkt\":\"POLYGON((121 30,122 30,122 31,121 31,121 30))\","
                + "\"startTime\":\"2026-06-01 00:00:00\",\"endTime\":\"2026-06-01 01:00:00\","
                + "\"behaviorTypes\":[\"LOITERING\"],\"batchNo\":\"b\","
                + "\"dryRun\":true,\"storeCandidates\":false,\"shadowRulesEnabled\":true,"
                + "\"enforceEnabled\":false,\"databaseWriteEnabled\":" + databaseWriteEnabled + ","
                + "\"shipTypePolicy\":\"OIL_TANKER_ONLY\","
                + "\"scenePolicyVersion\":\"MARITIME_SCENE_SHADOW_V2\",\"parallelism\":1}";
    }

    private RegionBehaviorJobParams validParams() {
        RegionBehaviorJobParams params = new RegionBehaviorJobParams();
        params.setMode("region_behavior_v1");
        params.setRegionId("r-1");
        params.setRegionName("test");
        params.setPolygonWkt("POLYGON((121 30,122 30,122 31,121 31,121 30))");
        params.setStartTime("2026-06-01 00:00:00");
        params.setEndTime("2026-06-01 06:00:00");
        params.getBehaviorTypes().add("LOITERING");
        params.setBatchNo("batch-1");
        params.setDryRun(true);
        return params;
    }
}
