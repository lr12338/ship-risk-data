package com.hifleet.analysis.portcall.flink;

import com.hifleet.analysis.portcall.config.PortCallConfig;
import com.hifleet.analysis.portcall.model.AisPoint;
import com.hifleet.analysis.portcall.model.StopPointEvent;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
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

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        if (config.getFlinkCheckpointIntervalMs() > 0L) {
            env.enableCheckpointing(config.getFlinkCheckpointIntervalMs());
        }

        KafkaSource<String> source = KafkaSource.<String>builder()
            .setBootstrapServers(config.getKafkaBootstrapServers())
            .setTopics(config.getKafkaTopic())
            .setGroupId(config.getKafkaGroupId())
            .setClientIdPrefix(config.getKafkaClientId())
            .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST))
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();

        WatermarkStrategy<AisPoint> watermarkStrategy = WatermarkStrategy
            .<AisPoint>forBoundedOutOfOrderness(Duration.ofSeconds(config.getReorderMaxLatenessSeconds()))
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
                config.getOutputUnknownTimeoutSeconds()
            ))
            .name("keyed-reliable-stop-point-detection");

        KafkaSink<StopPointEvent> stopPointSink = KafkaSink.<StopPointEvent>builder()
            .setBootstrapServers(config.getKafkaBootstrapServers())
            .setRecordSerializer(new StopPointKafkaSerializationSchema(config.getKafkaStopPointTopic()))
            .build();

        stopPoints
            .sinkTo(stopPointSink)
            .name("kafka-stop-point-sink");

        stopPoints
            .map(new StopPointJsonMapFunction())
            .name("debug-stop-point-json")
            .print();

        env.execute("reliable-stop-point-flink");
    }
}
