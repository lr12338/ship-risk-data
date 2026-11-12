package com.hifleet.regionbehavior;

import com.alibaba.fastjson.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

public class RegionBehaviorRunServiceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void preflightPassesWithoutCallingReplayOrAnalysis() throws Exception {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        config.setEnabled(true);
        config.setOutputRoot(temporaryFolder.getRoot().getAbsolutePath());
        RegionAisDiscoveryService discovery = mock(RegionAisDiscoveryService.class);
        RegionShipMetadataService metadata = mock(RegionShipMetadataService.class);
        RegionLoiteringRunner loitering = mock(RegionLoiteringRunner.class);
        RegionAlongsideRunner alongside = mock(RegionAlongsideRunner.class);
        RegionBehaviorRunService service = new RegionBehaviorRunService(config, discovery, metadata,
                loitering, alongside, mock(RegionBehaviorReportWriter.class),
                new NoOpRegionEventContextEnricher());

        JSONObject result = service.preflight(rawParams());

        assertEquals("PASS", result.getString("status"));
        assertFalse(result.getBooleanValue("realAisQueryExecuted"));
        assertEquals(0, result.getIntValue("databaseWrites"));
        verifyZeroInteractions(discovery, metadata, loitering, alongside);
    }

    @Test
    public void preflightRejectsUnsafeStoreMode() throws Exception {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        config.setEnabled(true);
        config.setOutputRoot(temporaryFolder.getRoot().getAbsolutePath());
        RegionBehaviorRunService service = new RegionBehaviorRunService(config,
                mock(RegionAisDiscoveryService.class), mock(RegionShipMetadataService.class),
                mock(RegionLoiteringRunner.class), mock(RegionAlongsideRunner.class),
                mock(RegionBehaviorReportWriter.class), new NoOpRegionEventContextEnricher());

        JSONObject result = service.preflight(rawParams().replace("\"dryRun\":true", "\"dryRun\":false"));

        assertEquals("BLOCKED_UNSAFE_STORE_MODE", result.getString("status"));
    }

    @Test
    public void preflightBlocksFormalWriteUntilServerGateEnabled() throws Exception {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        config.setEnabled(true);
        config.setOutputRoot(temporaryFolder.getRoot().getAbsolutePath());
        RegionBehaviorRunService service = new RegionBehaviorRunService(config,
                mock(RegionAisDiscoveryService.class), mock(RegionShipMetadataService.class),
                mock(RegionLoiteringRunner.class), mock(RegionAlongsideRunner.class),
                mock(RegionBehaviorReportWriter.class), new NoOpRegionEventContextEnricher());
        String formal = rawParams().replace("\"dryRun\":true,\"storeCandidates\":false",
                "\"writeMode\":\"FORMAL_EVENT\",\"enableInsert\":true");
        JSONObject result = service.preflight(formal);
        assertEquals("BLOCKED_UNSAFE_STORE_MODE", result.getString("status"));
    }

    private String rawParams() {
        return "{\"mode\":\"region_behavior_v1\",\"regionId\":\"r-1\","
                + "\"regionName\":\"test\","
                + "\"polygonWkt\":\"POLYGON((121 30,121.1 30,121.1 30.1,121 30.1,121 30))\","
                + "\"startTime\":\"2026-06-01 00:00:00\","
                + "\"endTime\":\"2026-06-01 06:00:00\","
                + "\"behaviorTypes\":[\"LOITERING\"],\"batchNo\":\"batch-1\","
                + "\"dryRun\":true,\"storeCandidates\":false}";
    }
}
