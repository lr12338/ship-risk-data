package com.hifleet.analysis.portcall.history;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumes live AIS Kafka records and archives raw values as JSONL files.
 */
public final class KafkaAisArchiveApplication {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaAisArchiveApplication.class);

    private KafkaAisArchiveApplication() {
    }

    public static void main(String[] args) throws Exception {
        HistoryToolConfig config = HistoryToolConfig.load();
        Properties kafkaProps = new Properties();
        kafkaProps.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());
        kafkaProps.setProperty(ConsumerConfig.GROUP_ID_CONFIG, config.getArchiveGroupId());
        kafkaProps.setProperty(ConsumerConfig.CLIENT_ID_CONFIG, config.getArchiveClientId());
        kafkaProps.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        kafkaProps.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        kafkaProps.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        kafkaProps.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, config.getArchiveAutoOffsetReset());

        final AtomicBoolean running = new AtomicBoolean(true);
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                running.set(false);
            }
        }, "ais-archive-shutdown"));

        KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(kafkaProps);
        JsonLineAisArchiveWriter writer = new JsonLineAisArchiveWriter(
            config.getArchiveOutputDir(),
            config.getArchiveRollover(),
            config.getArchivePartitionTimeField()
        );
        long archivedCount = 0L;
        int pendingFlushCount = 0;
        try {
            consumer.subscribe(Collections.singletonList(config.getArchiveSourceTopic()));
            LOG.info("Start AIS archive, topic={}, outputDir={}", config.getArchiveSourceTopic(), config.getArchiveOutputDir());
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(config.getArchivePollTimeoutMs()));
                for (ConsumerRecord<String, String> record : records) {
                    writer.write(AisArchiveRecord.fromConsumerRecord(record));
                    archivedCount++;
                    pendingFlushCount++;
                    if (pendingFlushCount >= config.getArchiveFlushRecordCount()) {
                        writer.flush();
                        consumer.commitSync();
                        pendingFlushCount = 0;
                        LOG.info("Archived AIS records: {}", archivedCount);
                    }
                    if (config.getArchiveMaxRecords() > 0L && archivedCount >= config.getArchiveMaxRecords()) {
                        running.set(false);
                        break;
                    }
                }
            }
            writer.flush();
            consumer.commitSync();
            LOG.info("AIS archive stopped, totalRecords={}", archivedCount);
        } finally {
            try {
                writer.close();
            } finally {
                consumer.close();
            }
        }
    }
}
