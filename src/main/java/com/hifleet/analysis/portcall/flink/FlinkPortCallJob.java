package com.hifleet.analysis.portcall.flink;

import com.hifleet.analysis.portcall.config.PortCallConfig;
import com.hifleet.analysis.portcall.model.AisPoint;
import com.hifleet.analysis.portcall.model.StopPointEvent;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.configuration.CheckpointingOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.StateBackendOptions;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.contrib.streaming.state.RocksDBOptions;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;

import java.time.Duration;

/**
 * Flink entry point for reliable stop point output.
 */
public final class FlinkPortCallJob {

    private FlinkPortCallJob() {
    }

    /**
     * Starts the Flink streaming job.
     *
     * @param args unused; configuration is loaded from application.properties and JVM -D overrides
     * @throws Exception when Flink job startup fails
     */
    public static void main(String[] args) throws Exception {
        PortCallConfig config = PortCallConfig.defaultConfig();
        int maxRetainedCheckpoints = config.resolveMaxRetainedCheckpoints();
        System.err.println("[FlinkPortCallJob] checkpoint max-retained=" + maxRetainedCheckpoints
            + ", interval-ms=" + config.getFlinkCheckpointIntervalMs()
            + ", incremental=" + config.isFlinkCheckpointIncremental()
            + ", cleanup=" + config.getFlinkCheckpointExternalizedCleanup()
            + ", dir=" + config.getFlinkCheckpointDir());
        StreamExecutionEnvironment env = createExecutionEnvironment(config);
        KafkaSource<String> source = KafkaSource.<String>builder()
            .setBootstrapServers(config.getKafkaBootstrapServers())
            .setTopics(config.getKafkaTopic())
            .setGroupId(config.getKafkaGroupId())
            .setClientIdPrefix(config.getKafkaClientId())
            .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST))
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();
        //System.out.println(config.getKafkaBootstrapServers());
        WatermarkStrategy<AisPoint> watermarkStrategy = WatermarkStrategy
            .<AisPoint>forBoundedOutOfOrderness(Duration.ofSeconds(config.getReorderMaxLatenessSeconds()))
            // Idle Kafka partitions otherwise keep the global watermark at MIN_WATERMARK forever.
            .withIdleness(Duration.ofMinutes(1))
            .withTimestampAssigner(new AisPointTimestampAssigner());

        DataStream<StopPointEvent> stopPoints = env.fromSource(source, WatermarkStrategy.noWatermarks(), "ais-kafka-source")
            .flatMap(new FlinkAisParserFlatMap())
            .name("parse-ais-payload")
            .filter(new ValidAisPointFilter(config.getAcceptedDataStates()))
            .name("filter-valid-ais")
            .assignTimestampsAndWatermarks(watermarkStrategy)
            .name("assign-ais-event-time")
            .keyBy(new AisPointMmsiKeySelector())
            .process(new FlinkKeyedPreprocessFunction(
                config.getReorderMaxLatenessSeconds(),
                config.getDedupCacheSize(),
                config.getDedupTtlHours(),
                config.getStateIdleTtlHours(),
                config.getReorderMaxPointsPerVessel(),
                config.getJumpPointMaxImpliedSpeedKnots()
            ))
            .name("keyed-ais-preprocess")
            .keyBy(new CleanAisPointMmsiKeySelector())
            .process(new StopPointDetectorProcessFunction(
                config.getStopPointMaxSpeedKnots(),
                config.getStopCandidateMinDurationSeconds(),
                config.getStopCandidateMinPointCount(),
                config.getStopCandidateMaxNetDisplacementMeters(),
                config.getStopStableMinDurationSeconds(),
                config.getStopStableMinPointCount(),
                config.getStopStableMaxSpeedKnots(),
                config.getStopStableMaxRadiusMeters(),
                config.getStopStableCenterDeltaMeters(),
                config.getStopPositionOutlierRadiusMeters(),
                config.getStopPointEndSpeedKnots(),
                config.getResumeMinDurationSeconds(),
                config.getResumeLeaveRadiusMeters(),
                config.getOutputStopUpdateMinIntervalSeconds(),
                config.getOutputStopUpdateMinPositionDeltaMeters(),
                config.getOutputStopUpdateMinConfidenceDelta(),
                config.getOutputUnknownTimeoutSeconds(),
                config.getStateIdleTtlHours()
            ))
            .name("keyed-reliable-stop-point-detection");

        KafkaSink<StopPointEvent> stopPointSink = KafkaSink.<StopPointEvent>builder()
            .setBootstrapServers(config.getKafkaBootstrapServers())
            .setRecordSerializer(new StopPointKafkaSerializationSchema(config.getKafkaStopPointTopic()))
            .build();

        stopPoints
            .sinkTo(stopPointSink)
            .name("kafka-stop-point-sink");

        if (config.isFlinkDebugPrint()) {
            stopPoints
                .map(new StopPointJsonMapFunction())
                .name("debug-stop-point-json")
                .print();
        }

        env.execute("reliable-stop-point-flink");
    }

    private static StreamExecutionEnvironment createExecutionEnvironment(PortCallConfig config) {
        Configuration flinkConfiguration = new Configuration();
        applyIfConfigured(flinkConfiguration, StateBackendOptions.STATE_BACKEND, config.getFlinkStateBackend());
        applyIfConfigured(flinkConfiguration, CheckpointingOptions.CHECKPOINTS_DIRECTORY, config.getFlinkCheckpointDir());
        applyIfConfigured(flinkConfiguration, CheckpointingOptions.SAVEPOINT_DIRECTORY, config.getFlinkSavepointDir());
        applyIfConfigured(flinkConfiguration, RocksDBOptions.LOCAL_DIRECTORIES, config.getFlinkRocksDbLocalDir());
        if (config.getFlinkCheckpointIntervalMs() > 0L) {
            applyCheckpointRetention(flinkConfiguration, config.resolveMaxRetainedCheckpoints());
            flinkConfiguration.setString("state.backend.incremental", String.valueOf(config.isFlinkCheckpointIncremental()));
        }

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(flinkConfiguration);
        // Re-apply after env creation so remote "flink run" context picks up job-level overrides.
        env.configure(flinkConfiguration, FlinkPortCallJob.class.getClassLoader());
        if (config.getFlinkParallelism() > 0) {
            env.setParallelism(config.getFlinkParallelism());
        }
        if (config.getFlinkCheckpointIntervalMs() > 0L) {
            env.enableCheckpointing(config.getFlinkCheckpointIntervalMs());
            env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
            if (config.getFlinkCheckpointMinPauseMs() > 0L) {
                env.getCheckpointConfig().setMinPauseBetweenCheckpoints(config.getFlinkCheckpointMinPauseMs());
            }
            if (config.getFlinkCheckpointTimeoutMs() > 0L) {
                env.getCheckpointConfig().setCheckpointTimeout(config.getFlinkCheckpointTimeoutMs());
            }
            env.configure(flinkConfiguration, FlinkPortCallJob.class.getClassLoader());
            env.getCheckpointConfig().setExternalizedCheckpointCleanup(
                resolveExternalizedCheckpointCleanup(config.getFlinkCheckpointExternalizedCleanup()));
        }
        return env;
    }

    private static void applyCheckpointRetention(Configuration flinkConfiguration, int maxRetained) {
        flinkConfiguration.set(CheckpointingOptions.MAX_RETAINED_CHECKPOINTS, maxRetained);
        // Legacy cluster key still read by JM in Flink 1.16.
        flinkConfiguration.setString("state.checkpoints.num-retained", String.valueOf(maxRetained));
    }

    private static CheckpointConfig.ExternalizedCheckpointCleanup resolveExternalizedCheckpointCleanup(String cleanup) {
        if (cleanup == null || cleanup.trim().isEmpty()) {
            return CheckpointConfig.ExternalizedCheckpointCleanup.DELETE_ON_CANCELLATION;
        }
        try {
            return CheckpointConfig.ExternalizedCheckpointCleanup.valueOf(cleanup.trim());
        } catch (IllegalArgumentException ex) {
            return CheckpointConfig.ExternalizedCheckpointCleanup.DELETE_ON_CANCELLATION;
        }
    }

    private static void applyIfConfigured(Configuration configuration, org.apache.flink.configuration.ConfigOption<String> option, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        configuration.set(option, value.trim());
    }
}
