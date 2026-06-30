package com.hifleet.analysis.portcall.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Runtime configuration for the stop-point Flink job.
 */
public class PortCallConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private String kafkaBootstrapServers;
    private String kafkaTopic;
    private String kafkaStopPointTopic;
    private String kafkaGroupId;
    private String kafkaClientId;

    private long reorderMaxLatenessSeconds;
    private int reorderMaxPointsPerVessel;
    private int dedupCacheSize;
    private int dedupTtlHours;
    private int stateIdleTtlHours;
    private double jumpPointMaxImpliedSpeedKnots;
    private Set<String> acceptedDataStates = Collections.emptySet();

    private double stopPointMaxSpeedKnots;
    private double stopPointEndSpeedKnots;
    private long stopCandidateMinDurationSeconds;
    private int stopCandidateMinPointCount;
    private double stopCandidateMaxNetDisplacementMeters;
    private long stopStableMinDurationSeconds;
    private int stopStableMinPointCount;
    private double stopStableMaxSpeedKnots;
    private double stopStableMaxRadiusMeters;
    private double stopStableCenterDeltaMeters;
    private double stopPositionOutlierRadiusMeters;
    private long resumeMinDurationSeconds;
    private double resumeLeaveRadiusMeters;
    private long outputStopUpdateMinIntervalSeconds;
    private double outputStopUpdateMinPositionDeltaMeters;
    private double outputStopUpdateMinConfidenceDelta;
    private long outputUnknownTimeoutSeconds;

    private long flinkCheckpointIntervalMs;
    private String flinkStateBackend;
    private String flinkCheckpointDir;
    private String flinkSavepointDir;
    private String flinkRocksDbLocalDir;
    private int flinkParallelism;
    private boolean flinkDebugPrint;
    private int flinkCheckpointRetentionDays;
    private int flinkSavepointRetentionDays;
    private int flinkCheckpointMaxRetained;
    private boolean flinkCheckpointIncremental;
    private long flinkCheckpointMinPauseMs;
    private long flinkCheckpointTimeoutMs;
    private String flinkCheckpointExternalizedCleanup;

    /**
     * Loads application.properties, then applies JVM -D overrides for known keys.
     *
     * @return populated config
     */
    public static PortCallConfig defaultConfig() {
        Properties props = buildDefaults();
        InputStream inputStream = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream("application.properties");
        if (inputStream != null) {
            try {
                Properties fileProps = new Properties();
                fileProps.load(inputStream);
                props.putAll(fileProps);
            } catch (IOException ignored) {
                // keep defaults
            } finally {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                    // no-op
                }
            }
        }
        applySystemOverrides(props);

        PortCallConfig config = new PortCallConfig();
        config.setKafkaBootstrapServers(value(props, "kafka.bootstrap.servers"));
        config.setKafkaTopic(value(props, "kafka.topic"));
        config.setKafkaStopPointTopic(value(props, "kafka.stop-point.topic"));
        config.setKafkaGroupId(value(props, "kafka.group.id"));
        config.setKafkaClientId(value(props, "kafka.client.id"));

        config.setReorderMaxLatenessSeconds(toLong(value(props, "pipeline.reorder.max-lateness-seconds"), 600L));
        config.setReorderMaxPointsPerVessel(toInt(value(props, "pipeline.reorder.max-points-per-vessel"), 200));
        config.setDedupCacheSize(toInt(value(props, "pipeline.dedup.cache-size"), 200000));
        config.setDedupTtlHours(toInt(value(props, "pipeline.dedup.ttl-hours"), 6));
        config.setStateIdleTtlHours(toInt(value(props, "pipeline.state.idle-ttl-hours"), 48));
        config.setJumpPointMaxImpliedSpeedKnots(toDouble(value(props, "pipeline.jump-point.max-implied-speed-knots"), 45.0d));
        config.setAcceptedDataStates(toStringSet(value(props, "pipeline.accepted-data-states")));

        config.setStopPointMaxSpeedKnots(toDouble(value(props, "stop-point.max-speed-knots"), 1.0d));
        config.setStopPointEndSpeedKnots(toDouble(value(props, "stop-point.end-speed-knots"), 2.0d));
        config.setStopCandidateMinDurationSeconds(toLong(value(props, "stop.candidate.min-duration-seconds"), 300L));
        config.setStopCandidateMinPointCount(toInt(value(props, "stop.candidate.min-point-count"), 3));
        config.setStopCandidateMaxNetDisplacementMeters(toDouble(value(props, "stop.candidate.max-net-displacement-meters"), 500.0d));
        config.setStopStableMinDurationSeconds(toLong(value(props, "stop.stable.min-duration-seconds"), 600L));
        config.setStopStableMinPointCount(toInt(value(props, "stop.stable.min-point-count"), 5));
        config.setStopStableMaxSpeedKnots(toDouble(value(props, "stop.stable.max-speed-knots"), 0.5d));
        config.setStopStableMaxRadiusMeters(toDouble(value(props, "stop.stable.max-radius-meters"), 100.0d));
        config.setStopStableCenterDeltaMeters(toDouble(value(props, "stop.stable.center-delta-meters"), 30.0d));
        config.setStopPositionOutlierRadiusMeters(toDouble(value(props, "stop.position.outlier-radius-meters"), 150.0d));
        config.setResumeMinDurationSeconds(toLong(value(props, "resume.min-duration-seconds"), 180L));
        config.setResumeLeaveRadiusMeters(toDouble(value(props, "resume.leave-radius-meters"), 300.0d));
        config.setOutputStopUpdateMinIntervalSeconds(toLong(value(props, "output.stop-update.min-interval-seconds"), 300L));
        config.setOutputStopUpdateMinPositionDeltaMeters(toDouble(value(props, "output.stop-update.min-position-delta-meters"), 30.0d));
        config.setOutputStopUpdateMinConfidenceDelta(toDouble(value(props, "output.stop-update.min-confidence-delta"), 0.05d));
        config.setOutputUnknownTimeoutSeconds(toLong(value(props, "output.unknown.timeout-seconds"), 1800L));

        config.setFlinkCheckpointIntervalMs(toLong(value(props, "flink.checkpoint.interval-ms"), 60000L));
        config.setFlinkStateBackend(value(props, "flink.state.backend"));
        config.setFlinkCheckpointDir(value(props, "flink.checkpoint.dir"));
        config.setFlinkSavepointDir(value(props, "flink.savepoint.dir"));
        config.setFlinkRocksDbLocalDir(value(props, "flink.rocksdb.localdir"));
        config.setFlinkParallelism(toInt(value(props, "flink.parallelism"), 0));
        config.setFlinkDebugPrint(toBoolean(value(props, "flink.debug.print"), false));
        config.setFlinkCheckpointRetentionDays(toInt(value(props, "flink.checkpoint.retention-days"), 15));
        config.setFlinkSavepointRetentionDays(toInt(value(props, "flink.savepoint.retention-days"), 15));
        config.setFlinkCheckpointMaxRetained(toInt(value(props, "flink.checkpoint.max-retained"), 1));
        config.setFlinkCheckpointIncremental(toBoolean(value(props, "flink.checkpoint.incremental"), true));
        config.setFlinkCheckpointMinPauseMs(toLong(value(props, "flink.checkpoint.min-pause-ms"), 0L));
        config.setFlinkCheckpointTimeoutMs(toLong(value(props, "flink.checkpoint.timeout-ms"), 300000L));
        config.setFlinkCheckpointExternalizedCleanup(value(props, "flink.checkpoint.externalized-cleanup"));
        return config;
    }

    private static Properties buildDefaults() {
        Properties defaults = new Properties();
        defaults.setProperty("kafka.bootstrap.servers", "localhost:9092");
        defaults.setProperty("kafka.topic", "flink_aisdata_topic");
        defaults.setProperty("kafka.stop-point.topic", "RELIABLE_STOP_POINTS");
        defaults.setProperty("kafka.group.id", "portcall-group");
        defaults.setProperty("kafka.client.id", "portcall-consumer-1");
        defaults.setProperty("pipeline.reorder.max-lateness-seconds", "600");
        defaults.setProperty("pipeline.reorder.max-points-per-vessel", "200");
        defaults.setProperty("pipeline.dedup.cache-size", "1024");
        defaults.setProperty("pipeline.dedup.ttl-hours", "6");
        defaults.setProperty("pipeline.state.idle-ttl-hours", "48");
        defaults.setProperty("pipeline.jump-point.max-implied-speed-knots", "45.0");
        defaults.setProperty("pipeline.accepted-data-states", "normal");
        defaults.setProperty("stop-point.max-speed-knots", "1.0");
        defaults.setProperty("stop-point.end-speed-knots", "2.0");
        defaults.setProperty("stop.candidate.min-duration-seconds", "300");
        defaults.setProperty("stop.candidate.min-point-count", "3");
        defaults.setProperty("stop.candidate.max-net-displacement-meters", "500.0");
        defaults.setProperty("stop.stable.min-duration-seconds", "600");
        defaults.setProperty("stop.stable.min-point-count", "5");
        defaults.setProperty("stop.stable.max-speed-knots", "0.5");
        defaults.setProperty("stop.stable.max-radius-meters", "100.0");
        defaults.setProperty("stop.stable.center-delta-meters", "30.0");
        defaults.setProperty("stop.position.outlier-radius-meters", "150.0");
        defaults.setProperty("resume.min-duration-seconds", "180");
        defaults.setProperty("resume.leave-radius-meters", "300.0");
        defaults.setProperty("output.stop-update.min-interval-seconds", "300");
        defaults.setProperty("output.stop-update.min-position-delta-meters", "30.0");
        defaults.setProperty("output.stop-update.min-confidence-delta", "0.05");
        defaults.setProperty("output.unknown.timeout-seconds", "1800");
        defaults.setProperty("flink.checkpoint.interval-ms", "60000");
        defaults.setProperty("flink.state.backend", "rocksdb");
        defaults.setProperty("flink.checkpoint.dir", "");
        defaults.setProperty("flink.savepoint.dir", "");
        defaults.setProperty("flink.rocksdb.localdir", "");
        defaults.setProperty("flink.parallelism", "0");
        defaults.setProperty("flink.debug.print", "false");
        defaults.setProperty("flink.checkpoint.retention-days", "15");
        defaults.setProperty("flink.savepoint.retention-days", "15");
        defaults.setProperty("flink.checkpoint.max-retained", "1");
        defaults.setProperty("flink.checkpoint.incremental", "true");
        defaults.setProperty("flink.checkpoint.min-pause-ms", "0");
        defaults.setProperty("flink.checkpoint.timeout-ms", "300000");
        defaults.setProperty("flink.checkpoint.externalized-cleanup", "DELETE_ON_CANCELLATION");
        return defaults;
    }

    private static void applySystemOverrides(Properties props) {
        for (String key : props.stringPropertyNames()) {
            String systemValue = System.getProperty(key);
            if (systemValue != null && !systemValue.trim().isEmpty()) {
                props.setProperty(key, systemValue.trim());
            }
        }
    }

    private static String value(Properties props, String key) {
        return props.getProperty(key);
    }

    private static int toInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static long toLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static double toDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static boolean toBoolean(String value, boolean fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static Set<String> toStringSet(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> values = new HashSet<String>();
        String[] tokens = value.split(",");
        for (String token : tokens) {
            if (token != null && !token.trim().isEmpty()) {
                values.add(token.trim());
            }
        }
        return values;
    }

    public String getKafkaBootstrapServers() {
        return kafkaBootstrapServers;
    }

    public void setKafkaBootstrapServers(String kafkaBootstrapServers) {
        this.kafkaBootstrapServers = kafkaBootstrapServers;
    }

    public String getKafkaTopic() {
        return kafkaTopic;
    }

    public void setKafkaTopic(String kafkaTopic) {
        this.kafkaTopic = kafkaTopic;
    }

    public String getKafkaStopPointTopic() {
        return kafkaStopPointTopic;
    }

    public void setKafkaStopPointTopic(String kafkaStopPointTopic) {
        this.kafkaStopPointTopic = kafkaStopPointTopic;
    }

    public String getKafkaGroupId() {
        return kafkaGroupId;
    }

    public void setKafkaGroupId(String kafkaGroupId) {
        this.kafkaGroupId = kafkaGroupId;
    }

    public String getKafkaClientId() {
        return kafkaClientId;
    }

    public void setKafkaClientId(String kafkaClientId) {
        this.kafkaClientId = kafkaClientId;
    }

    public long getReorderMaxLatenessSeconds() {
        return reorderMaxLatenessSeconds;
    }

    public void setReorderMaxLatenessSeconds(long reorderMaxLatenessSeconds) {
        this.reorderMaxLatenessSeconds = reorderMaxLatenessSeconds;
    }

    public int getReorderMaxPointsPerVessel() {
        return reorderMaxPointsPerVessel;
    }

    public void setReorderMaxPointsPerVessel(int reorderMaxPointsPerVessel) {
        this.reorderMaxPointsPerVessel = reorderMaxPointsPerVessel;
    }

    public int getDedupCacheSize() {
        return dedupCacheSize;
    }

    public void setDedupCacheSize(int dedupCacheSize) {
        this.dedupCacheSize = dedupCacheSize;
    }

    public int getDedupTtlHours() {
        return dedupTtlHours;
    }

    public void setDedupTtlHours(int dedupTtlHours) {
        this.dedupTtlHours = dedupTtlHours;
    }

    public int getStateIdleTtlHours() {
        return stateIdleTtlHours;
    }

    public void setStateIdleTtlHours(int stateIdleTtlHours) {
        this.stateIdleTtlHours = stateIdleTtlHours;
    }

    public double getJumpPointMaxImpliedSpeedKnots() {
        return jumpPointMaxImpliedSpeedKnots;
    }

    public void setJumpPointMaxImpliedSpeedKnots(double jumpPointMaxImpliedSpeedKnots) {
        this.jumpPointMaxImpliedSpeedKnots = jumpPointMaxImpliedSpeedKnots;
    }

    public Set<String> getAcceptedDataStates() {
        return acceptedDataStates;
    }

    public void setAcceptedDataStates(Set<String> acceptedDataStates) {
        this.acceptedDataStates = acceptedDataStates == null ? Collections.<String>emptySet() : acceptedDataStates;
    }

    public double getStopPointMaxSpeedKnots() {
        return stopPointMaxSpeedKnots;
    }

    public void setStopPointMaxSpeedKnots(double stopPointMaxSpeedKnots) {
        this.stopPointMaxSpeedKnots = stopPointMaxSpeedKnots;
    }

    public double getStopPointEndSpeedKnots() {
        return stopPointEndSpeedKnots;
    }

    public void setStopPointEndSpeedKnots(double stopPointEndSpeedKnots) {
        this.stopPointEndSpeedKnots = stopPointEndSpeedKnots;
    }

    public long getStopCandidateMinDurationSeconds() {
        return stopCandidateMinDurationSeconds;
    }

    public void setStopCandidateMinDurationSeconds(long stopCandidateMinDurationSeconds) {
        this.stopCandidateMinDurationSeconds = stopCandidateMinDurationSeconds;
    }

    public int getStopCandidateMinPointCount() {
        return stopCandidateMinPointCount;
    }

    public void setStopCandidateMinPointCount(int stopCandidateMinPointCount) {
        this.stopCandidateMinPointCount = stopCandidateMinPointCount;
    }

    public double getStopCandidateMaxNetDisplacementMeters() {
        return stopCandidateMaxNetDisplacementMeters;
    }

    public void setStopCandidateMaxNetDisplacementMeters(double stopCandidateMaxNetDisplacementMeters) {
        this.stopCandidateMaxNetDisplacementMeters = stopCandidateMaxNetDisplacementMeters;
    }

    public long getStopStableMinDurationSeconds() {
        return stopStableMinDurationSeconds;
    }

    public void setStopStableMinDurationSeconds(long stopStableMinDurationSeconds) {
        this.stopStableMinDurationSeconds = stopStableMinDurationSeconds;
    }

    public int getStopStableMinPointCount() {
        return stopStableMinPointCount;
    }

    public void setStopStableMinPointCount(int stopStableMinPointCount) {
        this.stopStableMinPointCount = stopStableMinPointCount;
    }

    public double getStopStableMaxSpeedKnots() {
        return stopStableMaxSpeedKnots;
    }

    public void setStopStableMaxSpeedKnots(double stopStableMaxSpeedKnots) {
        this.stopStableMaxSpeedKnots = stopStableMaxSpeedKnots;
    }

    public double getStopStableMaxRadiusMeters() {
        return stopStableMaxRadiusMeters;
    }

    public void setStopStableMaxRadiusMeters(double stopStableMaxRadiusMeters) {
        this.stopStableMaxRadiusMeters = stopStableMaxRadiusMeters;
    }

    public double getStopStableCenterDeltaMeters() {
        return stopStableCenterDeltaMeters;
    }

    public void setStopStableCenterDeltaMeters(double stopStableCenterDeltaMeters) {
        this.stopStableCenterDeltaMeters = stopStableCenterDeltaMeters;
    }

    public double getStopPositionOutlierRadiusMeters() {
        return stopPositionOutlierRadiusMeters;
    }

    public void setStopPositionOutlierRadiusMeters(double stopPositionOutlierRadiusMeters) {
        this.stopPositionOutlierRadiusMeters = stopPositionOutlierRadiusMeters;
    }

    public long getResumeMinDurationSeconds() {
        return resumeMinDurationSeconds;
    }

    public void setResumeMinDurationSeconds(long resumeMinDurationSeconds) {
        this.resumeMinDurationSeconds = resumeMinDurationSeconds;
    }

    public double getResumeLeaveRadiusMeters() {
        return resumeLeaveRadiusMeters;
    }

    public void setResumeLeaveRadiusMeters(double resumeLeaveRadiusMeters) {
        this.resumeLeaveRadiusMeters = resumeLeaveRadiusMeters;
    }

    public long getOutputStopUpdateMinIntervalSeconds() {
        return outputStopUpdateMinIntervalSeconds;
    }

    public void setOutputStopUpdateMinIntervalSeconds(long outputStopUpdateMinIntervalSeconds) {
        this.outputStopUpdateMinIntervalSeconds = outputStopUpdateMinIntervalSeconds;
    }

    public double getOutputStopUpdateMinPositionDeltaMeters() {
        return outputStopUpdateMinPositionDeltaMeters;
    }

    public void setOutputStopUpdateMinPositionDeltaMeters(double outputStopUpdateMinPositionDeltaMeters) {
        this.outputStopUpdateMinPositionDeltaMeters = outputStopUpdateMinPositionDeltaMeters;
    }

    public double getOutputStopUpdateMinConfidenceDelta() {
        return outputStopUpdateMinConfidenceDelta;
    }

    public void setOutputStopUpdateMinConfidenceDelta(double outputStopUpdateMinConfidenceDelta) {
        this.outputStopUpdateMinConfidenceDelta = outputStopUpdateMinConfidenceDelta;
    }

    public long getOutputUnknownTimeoutSeconds() {
        return outputUnknownTimeoutSeconds;
    }

    public void setOutputUnknownTimeoutSeconds(long outputUnknownTimeoutSeconds) {
        this.outputUnknownTimeoutSeconds = outputUnknownTimeoutSeconds;
    }

    public long getFlinkCheckpointIntervalMs() {
        return flinkCheckpointIntervalMs;
    }

    public void setFlinkCheckpointIntervalMs(long flinkCheckpointIntervalMs) {
        this.flinkCheckpointIntervalMs = flinkCheckpointIntervalMs;
    }

    public String getFlinkStateBackend() {
        return flinkStateBackend;
    }

    public void setFlinkStateBackend(String flinkStateBackend) {
        this.flinkStateBackend = flinkStateBackend;
    }

    public String getFlinkCheckpointDir() {
        return flinkCheckpointDir;
    }

    public void setFlinkCheckpointDir(String flinkCheckpointDir) {
        this.flinkCheckpointDir = flinkCheckpointDir;
    }

    public String getFlinkSavepointDir() {
        return flinkSavepointDir;
    }

    public void setFlinkSavepointDir(String flinkSavepointDir) {
        this.flinkSavepointDir = flinkSavepointDir;
    }

    public String getFlinkRocksDbLocalDir() {
        return flinkRocksDbLocalDir;
    }

    public void setFlinkRocksDbLocalDir(String flinkRocksDbLocalDir) {
        this.flinkRocksDbLocalDir = flinkRocksDbLocalDir;
    }

    public int getFlinkParallelism() {
        return flinkParallelism;
    }

    public void setFlinkParallelism(int flinkParallelism) {
        this.flinkParallelism = flinkParallelism;
    }

    public boolean isFlinkDebugPrint() {
        return flinkDebugPrint;
    }

    public void setFlinkDebugPrint(boolean flinkDebugPrint) {
        this.flinkDebugPrint = flinkDebugPrint;
    }

    public int getFlinkCheckpointRetentionDays() {
        return flinkCheckpointRetentionDays;
    }

    public void setFlinkCheckpointRetentionDays(int flinkCheckpointRetentionDays) {
        this.flinkCheckpointRetentionDays = flinkCheckpointRetentionDays;
    }

    public int getFlinkSavepointRetentionDays() {
        return flinkSavepointRetentionDays;
    }

    public void setFlinkSavepointRetentionDays(int flinkSavepointRetentionDays) {
        this.flinkSavepointRetentionDays = flinkSavepointRetentionDays;
    }

    public int getFlinkCheckpointMaxRetained() {
        return flinkCheckpointMaxRetained;
    }

    public void setFlinkCheckpointMaxRetained(int flinkCheckpointMaxRetained) {
        this.flinkCheckpointMaxRetained = flinkCheckpointMaxRetained;
    }

    public boolean isFlinkCheckpointIncremental() {
        return flinkCheckpointIncremental;
    }

    public void setFlinkCheckpointIncremental(boolean flinkCheckpointIncremental) {
        this.flinkCheckpointIncremental = flinkCheckpointIncremental;
    }

    public long getFlinkCheckpointMinPauseMs() {
        return flinkCheckpointMinPauseMs;
    }

    public void setFlinkCheckpointMinPauseMs(long flinkCheckpointMinPauseMs) {
        this.flinkCheckpointMinPauseMs = flinkCheckpointMinPauseMs;
    }

    public long getFlinkCheckpointTimeoutMs() {
        return flinkCheckpointTimeoutMs;
    }

    public void setFlinkCheckpointTimeoutMs(long flinkCheckpointTimeoutMs) {
        this.flinkCheckpointTimeoutMs = flinkCheckpointTimeoutMs;
    }

    public String getFlinkCheckpointExternalizedCleanup() {
        return flinkCheckpointExternalizedCleanup;
    }

    public void setFlinkCheckpointExternalizedCleanup(String flinkCheckpointExternalizedCleanup) {
        this.flinkCheckpointExternalizedCleanup = flinkCheckpointExternalizedCleanup;
    }

    /**
     * Resolves how many completed checkpoints Flink keeps while the job is running.
     * When {@code flink.checkpoint.max-retained} is 0, derive from retention days and interval
     * (capped to avoid thousands of multi-GB RocksDB checkpoints).
     */
    public int resolveMaxRetainedCheckpoints() {
        if (flinkCheckpointMaxRetained > 0) {
            return flinkCheckpointMaxRetained;
        }
        if (flinkCheckpointRetentionDays <= 0 || flinkCheckpointIntervalMs <= 0L) {
            return 1;
        }
        long retentionMillis = flinkCheckpointRetentionDays * 24L * 3600L * 1000L;
        long count = retentionMillis / flinkCheckpointIntervalMs;
        if (count < 1L) {
            return 1;
        }
        int derived = count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
        // e.g. 7 days @ 180s => 3360 checkpoints (~12TB); keep explicit max-retained=1 for large state.
        return Math.min(derived, 3);
    }
}
