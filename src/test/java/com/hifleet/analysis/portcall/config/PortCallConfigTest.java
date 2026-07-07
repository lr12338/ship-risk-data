package com.hifleet.analysis.portcall.config;

import org.junit.Assert;
import org.junit.Test;

public class PortCallConfigTest {

    @Test
    public void shouldLoadFromApplicationProperties() {
        PortCallConfig config = PortCallConfig.defaultConfig();

        Assert.assertEquals("localhost:9092", config.getKafkaBootstrapServers());
        Assert.assertEquals("portcall-group", config.getKafkaGroupId());
        Assert.assertEquals("flink_aisdata_topic", config.getKafkaTopic());
        Assert.assertEquals("RELIABLE_STOP_POINTS", config.getKafkaStopPointTopic());
        Assert.assertEquals("portcall-consumer-1", config.getKafkaClientId());
        Assert.assertEquals("committed", config.getKafkaStartingOffsets());
        Assert.assertTrue(config.isStopPointEnabled());
        Assert.assertFalse(config.getAlongsideConfig().isEnabled());
        Assert.assertEquals("RELIABLE_ALONGSIDE_EVENTS", config.getAlongsideConfig().getOutputTopic());
        Assert.assertEquals("ALONGSIDE_V2_INSURANCE", config.getAlongsideConfig().getRuleVersion());
        Assert.assertEquals(0.15d, config.getAlongsideConfig().getConfirmMaxDistanceNm(), 0.01d);
        Assert.assertFalse(config.getAlongsideConfig().getDbSinkConfig().isEnabled());
        Assert.assertEquals("ship_realtime_alongside_event", config.getAlongsideConfig().getDbSinkConfig().getEventTable());

        Assert.assertEquals(600L, config.getReorderMaxLatenessSeconds());
        Assert.assertEquals(200, config.getReorderMaxPointsPerVessel());
        Assert.assertEquals(6, config.getDedupTtlHours());
        Assert.assertEquals(48, config.getStateIdleTtlHours());
        Assert.assertTrue(config.getAcceptedDataStates().contains("normal"));
        Assert.assertTrue(config.getScopeMmsiWhitelist().isEmpty());
        Assert.assertNull(config.getScopeMinLon());
        Assert.assertNull(config.getScopeMaxLon());
        Assert.assertNull(config.getScopeMinLat());
        Assert.assertNull(config.getScopeMaxLat());
        Assert.assertEquals(1.0d, config.getStopPointMaxSpeedKnots(), 0.01d);
        Assert.assertEquals(2.0d, config.getStopPointEndSpeedKnots(), 0.01d);
        Assert.assertEquals(300L, config.getStopCandidateMinDurationSeconds());
        Assert.assertEquals(3, config.getStopCandidateMinPointCount());
        Assert.assertEquals(500.0d, config.getStopCandidateMaxNetDisplacementMeters(), 0.1d);
        Assert.assertEquals(600L, config.getStopStableMinDurationSeconds());
        Assert.assertEquals(5, config.getStopStableMinPointCount());
        Assert.assertEquals(100.0d, config.getStopStableMaxRadiusMeters(), 0.1d);
        Assert.assertEquals(150.0d, config.getStopPositionOutlierRadiusMeters(), 0.1d);
        Assert.assertEquals(300L, config.getOutputStopUpdateMinIntervalSeconds());
        Assert.assertEquals(60000L, config.getFlinkCheckpointIntervalMs());
        Assert.assertEquals("rocksdb", config.getFlinkStateBackend());
        Assert.assertEquals("file:///tmp/flink-test/checkpoints", config.getFlinkCheckpointDir());
        Assert.assertEquals("file:///tmp/flink-test/savepoints", config.getFlinkSavepointDir());
        Assert.assertEquals("/tmp/flink-test/rocksdb", config.getFlinkRocksDbLocalDir());
        Assert.assertEquals(15, config.getFlinkCheckpointRetentionDays());
        Assert.assertEquals(15, config.getFlinkSavepointRetentionDays());
        Assert.assertEquals(1, config.resolveMaxRetainedCheckpoints());
        Assert.assertTrue(config.isFlinkCheckpointIncremental());
        Assert.assertEquals(60000L, config.getFlinkCheckpointMinPauseMs());
        Assert.assertEquals(300000L, config.getFlinkCheckpointTimeoutMs());
        Assert.assertEquals("DELETE_ON_CANCELLATION", config.getFlinkCheckpointExternalizedCleanup());
    }

    @Test
    public void shouldAllowDisablingStopPointBranchFromArguments() {
        PortCallConfig config = PortCallConfig.defaultConfig(new String[] {
            "--stop-point.enabled=false",
            "--alongside.enabled=true"
        });

        Assert.assertFalse(config.isStopPointEnabled());
        Assert.assertTrue(config.getAlongsideConfig().isEnabled());
    }
}
