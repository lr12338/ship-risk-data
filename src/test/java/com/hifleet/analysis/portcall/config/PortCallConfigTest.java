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

        Assert.assertEquals(600L, config.getReorderMaxLatenessSeconds());
        Assert.assertEquals(200, config.getReorderMaxPointsPerVessel());
        Assert.assertTrue(config.getAcceptedDataStates().contains("normal"));
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
        Assert.assertEquals(21600, config.resolveMaxRetainedCheckpoints());
    }
}
