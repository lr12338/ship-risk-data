package com.hifleet.analysis.portcall.history;

import com.hifleet.analysis.portcall.consumer.AisMessageParser;
import com.hifleet.analysis.portcall.model.AisPoint;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Replays archived AIS JSONL records into a Kafka topic for algorithm comparison.
 */
public final class AisReplayApplication {

    private static final Logger LOG = LoggerFactory.getLogger(AisReplayApplication.class);
    private static final AisMessageParser PARSER = new AisMessageParser();

    private AisReplayApplication() {
    }

    public static void main(String[] args) throws Exception {
        HistoryToolConfig config = HistoryToolConfig.load();
        List<Path> files = listJsonlFiles(Paths.get(config.getReplayInputDir()));
        Properties kafkaProps = new Properties();
        kafkaProps.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());
        kafkaProps.setProperty(ProducerConfig.CLIENT_ID_CONFIG, config.getReplayClientId());
        kafkaProps.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        kafkaProps.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        kafkaProps.setProperty(ProducerConfig.ACKS_CONFIG, "all");

        long scanned = 0L;
        long replayed = 0L;
        KafkaProducer<String, String> producer = config.isReplayDryRun()
            ? null
            : new KafkaProducer<String, String>(kafkaProps);
        try {
            LOG.info("Start AIS replay, inputDir={}, targetTopic={}, dryRun={}",
                config.getReplayInputDir(), config.getReplayTargetTopic(), config.isReplayDryRun());
            for (Path file : files) {
                BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        scanned++;
                        AisArchiveRecord record;
                        try {
                            record = AisArchiveRecord.fromJsonLine(line);
                        } catch (RuntimeException ex) {
                            LOG.warn("Skip invalid archive line, file={}, scanned={}", file, scanned);
                            continue;
                        }
                        if (!inRange(record, config.getReplayStartTime(), config.getReplayEndTime())) {
                            continue;
                        }
                        replayed++;
                        if (!config.isReplayDryRun()) {
                            producer.send(new ProducerRecord<String, String>(
                                config.getReplayTargetTopic(),
                                record.resolveReplayKey(config.getReplayKeyMode()),
                                record.getValue()
                            ));
                        }
                        throttleIfNeeded(config.getReplayMaxRecordsPerSecond(), replayed);
                        if (replayed % 10000L == 0L) {
                            LOG.info("Replay progress, scanned={}, replayed={}", scanned, replayed);
                        }
                    }
                } finally {
                    reader.close();
                }
            }
            if (producer != null) {
                producer.flush();
            }
            LOG.info("AIS replay finished, scanned={}, replayed={}", scanned, replayed);
        } finally {
            if (producer != null) {
                producer.close();
            }
        }
    }

    private static List<Path> listJsonlFiles(Path inputDir) throws IOException {
        final List<Path> files = new ArrayList<Path>();
        Stream<Path> stream = Files.walk(inputDir);
        try {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                .forEach(files::add);
        } finally {
            stream.close();
        }
        files.sort(new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
                return left.toString().compareTo(right.toString());
            }
        });
        return files;
    }

    private static boolean inRange(AisArchiveRecord record, Instant start, Instant end) {
        if (start == null && end == null) {
            return true;
        }
        List<AisPoint> points = PARSER.parseMany(record.getValue());
        if (points.isEmpty()) {
            return timestampInRange(record.getUpdateTime(), start, end);
        }
        for (AisPoint point : points) {
            if (timestampInRange(point.getUpdateTime(), start, end)) {
                return true;
            }
        }
        return false;
    }

    private static boolean timestampInRange(Instant time, Instant start, Instant end) {
        if (time == null) {
            return false;
        }
        if (start != null && time.isBefore(start)) {
            return false;
        }
        return end == null || time.isBefore(end);
    }

    private static void throttleIfNeeded(int maxRecordsPerSecond, long replayed) throws InterruptedException {
        if (maxRecordsPerSecond <= 0) {
            return;
        }
        if (replayed % maxRecordsPerSecond == 0L) {
            Thread.sleep(1000L);
        }
    }
}
