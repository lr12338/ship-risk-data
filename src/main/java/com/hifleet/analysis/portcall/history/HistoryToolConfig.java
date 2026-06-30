package com.hifleet.analysis.portcall.history;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Properties;

/**
 * Properties-backed configuration for AIS archive and replay command line tools.
 */
public class HistoryToolConfig {

    private static final DateTimeFormatter SQL_TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Properties props;

    private HistoryToolConfig(Properties props) {
        this.props = props;
    }

    public static HistoryToolConfig load() {
        Properties props = defaults();
        loadClasspathProperties(props, "application.properties");
        String externalFile = System.getProperty("history.config.file");
        if (externalFile != null && !externalFile.trim().isEmpty()) {
            loadFileProperties(props, externalFile.trim());
        }
        applySystemOverrides(props);
        return new HistoryToolConfig(props);
    }

    private static Properties defaults() {
        Properties defaults = new Properties();
        defaults.setProperty("kafka.bootstrap.servers", "localhost:9092");
        defaults.setProperty("kafka.topic", "flink_aisdata_topic");

        defaults.setProperty("archive.source.topic", "");
        defaults.setProperty("archive.group.id", "ais-archive-group");
        defaults.setProperty("archive.client.id", "ais-archive-consumer");
        defaults.setProperty("archive.output.dir", "data/ais-history");
        defaults.setProperty("archive.rollover", "hourly");
        defaults.setProperty("archive.partition-time-field", "updatetime");
        defaults.setProperty("archive.poll.timeout-ms", "1000");
        defaults.setProperty("archive.flush-record-count", "1000");
        defaults.setProperty("archive.max-records", "0");
        defaults.setProperty("archive.auto.offset.reset", "latest");

        defaults.setProperty("replay.input.dir", "data/ais-history");
        defaults.setProperty("replay.target.topic", "AIS_REPLAY_DEFAULT");
        defaults.setProperty("replay.client.id", "ais-replay-producer");
        defaults.setProperty("replay.start-time", "");
        defaults.setProperty("replay.end-time", "");
        defaults.setProperty("replay.key-mode", "archive-key");
        defaults.setProperty("replay.max-records-per-second", "0");
        defaults.setProperty("replay.dry-run", "false");
        return defaults;
    }

    private static void loadClasspathProperties(Properties props, String resourceName) {
        InputStream inputStream = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream(resourceName);
        if (inputStream == null) {
            return;
        }
        try {
            props.load(inputStream);
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

    private static void loadFileProperties(Properties props, String file) {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            props.load(inputStream);
        } catch (IOException ignored) {
            // keep previous values
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                    // no-op
                }
            }
        }
    }

    private static void applySystemOverrides(Properties props) {
        for (String key : props.stringPropertyNames()) {
            String systemValue = System.getProperty(key);
            if (systemValue != null && !systemValue.trim().isEmpty()) {
                props.setProperty(key, systemValue.trim());
            }
        }
    }

    public String getKafkaBootstrapServers() {
        return value("kafka.bootstrap.servers");
    }

    public String getArchiveSourceTopic() {
        String topic = value("archive.source.topic");
        if (topic == null || topic.trim().isEmpty() || "${kafka.topic}".equals(topic.trim())) {
            return value("kafka.topic");
        }
        return topic;
    }

    public String getArchiveGroupId() {
        return value("archive.group.id");
    }

    public String getArchiveClientId() {
        return value("archive.client.id");
    }

    public String getArchiveOutputDir() {
        return value("archive.output.dir");
    }

    public String getArchiveRollover() {
        return value("archive.rollover");
    }

    public String getArchivePartitionTimeField() {
        return value("archive.partition-time-field");
    }

    public long getArchivePollTimeoutMs() {
        return toLong(value("archive.poll.timeout-ms"), 1000L);
    }

    public int getArchiveFlushRecordCount() {
        return toInt(value("archive.flush-record-count"), 1000);
    }

    public long getArchiveMaxRecords() {
        return toLong(value("archive.max-records"), 0L);
    }

    public String getArchiveAutoOffsetReset() {
        return value("archive.auto.offset.reset");
    }

    public String getReplayInputDir() {
        return value("replay.input.dir");
    }

    public String getReplayTargetTopic() {
        return value("replay.target.topic");
    }

    public String getReplayClientId() {
        return value("replay.client.id");
    }

    public Instant getReplayStartTime() {
        return parseInstant(value("replay.start-time"));
    }

    public Instant getReplayEndTime() {
        return parseInstant(value("replay.end-time"));
    }

    public String getReplayKeyMode() {
        return value("replay.key-mode");
    }

    public int getReplayMaxRecordsPerSecond() {
        return toInt(value("replay.max-records-per-second"), 0);
    }

    public boolean isReplayDryRun() {
        return Boolean.parseBoolean(value("replay.dry-run"));
    }

    private String value(String key) {
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

    public static Instant parseInstant(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(trimmed.substring(0, Math.min(19, trimmed.length())), SQL_TIMESTAMP_FORMATTER)
                .toInstant(ZoneOffset.UTC);
        }
    }
}
