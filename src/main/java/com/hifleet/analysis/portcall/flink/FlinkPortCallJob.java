package com.hifleet.analysis.portcall.flink;

import com.hifleet.analysis.alongside.core.model.AlongsideEvent;
import com.hifleet.analysis.alongside.core.model.PairProximityEvidence;
import com.hifleet.analysis.alongside.flink.AlongsideAisPointFilter;
import com.hifleet.analysis.alongside.flink.bucket.SpatialBucketedAisPoint;
import com.hifleet.analysis.portcall.config.AlongsideConfig;
import com.hifleet.analysis.portcall.config.PortCallConfig;
import com.hifleet.analysis.portcall.model.AisPoint;
import com.hifleet.analysis.portcall.model.CleanAisPoint;
import com.hifleet.analysis.portcall.model.StopPointEvent;
import org.apache.flink.api.common.functions.MapFunction;
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
     * @param args Flink Web UI Program Arguments
     * @throws Exception when Flink job startup fails
     */
    public static void main(String[] args) throws Exception {
        PortCallConfig config = PortCallConfig.defaultConfig(args);
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
            .setStartingOffsets(resolveStartingOffsets(config.getKafkaStartingOffsets()))
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();
        //System.out.println(config.getKafkaBootstrapServers());
        WatermarkStrategy<AisPoint> watermarkStrategy = WatermarkStrategy
            .<AisPoint>forBoundedOutOfOrderness(Duration.ofSeconds(config.getReorderMaxLatenessSeconds()))
            // Idle Kafka partitions otherwise keep the global watermark at MIN_WATERMARK forever.
            .withIdleness(Duration.ofMinutes(1))
            .withTimestampAssigner(new AisPointTimestampAssigner());

        DataStream<CleanAisPoint> cleanAisPoints = env.fromSource(source, WatermarkStrategy.noWatermarks(), "ais-kafka-source")
            .flatMap(new FlinkAisParserFlatMap())
            .name("parse-ais-payload")
            .filter(new ValidAisPointFilter(config.getAcceptedDataStates()))
            .name("filter-valid-ais")
            .filter(new AisScopeFilter(
                config.getScopeMmsiWhitelist(),
                config.getScopeMinLon(),
                config.getScopeMaxLon(),
                config.getScopeMinLat(),
                config.getScopeMaxLat()
            ))
            .name("filter-ais-scope")
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
            .name("keyed-ais-preprocess");

        if (config.isFlinkDebugPrint()) {
            cleanAisPoints
                .map(new CleanAisDebugMapFunction())
                .name("debug-clean-ais")
                .print();
        }

        if (!config.isStopPointEnabled()
            && (config.getAlongsideConfig() == null || !config.getAlongsideConfig().isEnabled())) {
            throw new IllegalArgumentException("At least one output branch must be enabled: stop-point.enabled or alongside.enabled");
        }
        addStopPointBranch(cleanAisPoints, config);
        addAlongsideBranch(cleanAisPoints, config);

        env.execute(resolveJobName(config));
    }

    private static String resolveJobName(PortCallConfig config) {
        if (config.isStopPointEnabled()) {
            return "reliable-stop-point-flink";
        }
        return "reliable-alongside-flink";
    }

    private static void addStopPointBranch(DataStream<CleanAisPoint> cleanAisPoints, PortCallConfig config) {
        if (!config.isStopPointEnabled()) {
            return;
        }

        DataStream<StopPointEvent> stopPoints = cleanAisPoints
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
    }

    private static void addAlongsideBranch(DataStream<CleanAisPoint> cleanAisPoints, PortCallConfig config) {
        AlongsideConfig alongsideConfig = config.getAlongsideConfig();
        if (alongsideConfig == null || !alongsideConfig.isEnabled()) {
            return;
        }

        DataStream<SpatialBucketedAisPoint> alongsideBuckets = cleanAisPoints
            .filter(new AlongsideAisPointFilter(alongsideConfig))
            .name("alongside-pre-filter")
            .flatMap(new com.hifleet.analysis.alongside.flink.bucket.SpatialBucketAssigner(
                alongsideConfig.getBucketSizeDegrees(),
                alongsideConfig.getCandidateMaxSpeedKnots()
            ))
            .name("alongside-low-speed-spatial-buckets");

        DataStream<PairProximityEvidence> pairEvidence = alongsideBuckets
            .keyBy(new com.hifleet.analysis.alongside.flink.bucket.SpatialBucketKeySelector())
            .process(new com.hifleet.analysis.alongside.flink.SpatialCandidateProcessFunction(alongsideConfig))
            .name("alongside-spatial-candidate-recall");

        if (config.isFlinkDebugPrint()) {
            pairEvidence
                .map(new PairEvidenceDebugMapFunction())
                .name("debug-alongside-pair-evidence")
                .print();
        }

        DataStream<AlongsideEvent> alongsideEvents = pairEvidence
            .keyBy(new com.hifleet.analysis.alongside.flink.PairEvidenceKeySelector())
            .process(new com.hifleet.analysis.alongside.flink.AlongsidePairDetectorProcessFunction(alongsideConfig))
            .name("alongside-pair-detection");

        KafkaSink<AlongsideEvent> alongsideSink = KafkaSink.<AlongsideEvent>builder()
            .setBootstrapServers(config.getKafkaBootstrapServers())
            .setRecordSerializer(new com.hifleet.analysis.alongside.output.AlongsideKafkaSerializationSchema(alongsideConfig.getOutputTopic()))
            .build();

        alongsideEvents
            .sinkTo(alongsideSink)
            .name("kafka-alongside-event-sink");

        if (alongsideConfig.getDbSinkConfig() != null && alongsideConfig.getDbSinkConfig().isEnabled()) {
            alongsideEvents
                .addSink(new com.hifleet.analysis.alongside.output.db.AlongsideEventJdbcSink(alongsideConfig.getDbSinkConfig()))
                .name("jdbc-alongside-event-sink");
        }

        if (config.isFlinkDebugPrint()) {
            alongsideEvents
                .map(new AlongsideJsonMapFunction())
                .name("debug-alongside-event-json")
                .print();
        }
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

    private static OffsetsInitializer resolveStartingOffsets(String startingOffsets) {
        if ("earliest".equalsIgnoreCase(startingOffsets)) {
            return OffsetsInitializer.earliest();
        }
        if ("latest".equalsIgnoreCase(startingOffsets)) {
            return OffsetsInitializer.latest();
        }
        return OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST);
    }

    private static void applyIfConfigured(Configuration configuration, org.apache.flink.configuration.ConfigOption<String> option, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        configuration.set(option, value.trim());
    }

    private static final class CleanAisDebugMapFunction implements MapFunction<CleanAisPoint, String> {

        private static final long serialVersionUID = 1L;

        @Override
        public String map(CleanAisPoint point) {
            return "[debug-clean-ais] mmsi=" + point.getMmsi()
                + ", time=" + point.getUpdateTime()
                + ", lon=" + point.getLon()
                + ", lat=" + point.getLat()
                + ", speed=" + point.getSpeed()
                + ", usable=" + point.isUsable();
        }
    }

    private static final class PairEvidenceDebugMapFunction implements MapFunction<PairProximityEvidence, String> {

        private static final long serialVersionUID = 1L;

        @Override
        public String map(PairProximityEvidence evidence) {
            return "[debug-pair-evidence] pair_id=" + evidence.getPairId()
                + ", evidence_time=" + evidence.getEvidenceTime()
                + ", point_a_time=" + evidence.getPointATime()
                + ", point_b_time=" + evidence.getPointBTime()
                + ", distance_nm=" + evidence.getDistanceNm()
                + ", time_gap_seconds=" + evidence.getTimeGapSeconds();
        }
    }
}
