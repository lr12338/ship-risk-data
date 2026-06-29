package com.hifleet.analysis.portcall.flink;

import com.hifleet.analysis.portcall.model.StopPointEvent;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.nio.charset.StandardCharsets;

/**
 * Serializes stop-state events with MMSI as Kafka key so one vessel remains ordered by partition.
 */
public class StopPointKafkaSerializationSchema implements KafkaRecordSerializationSchema<StopPointEvent> {

    private static final long serialVersionUID = 1L;

    private final String topic;

    public StopPointKafkaSerializationSchema(String topic) {
        this.topic = topic;
    }

    @Override
    public ProducerRecord<byte[], byte[]> serialize(StopPointEvent event, KafkaSinkContext context, Long timestamp) {
        byte[] key = event.getMmsi() == null
            ? null
            : String.valueOf(event.getMmsi()).getBytes(StandardCharsets.UTF_8);
        byte[] value = StopPointJsonMapFunction.toJson(event).getBytes(StandardCharsets.UTF_8);
        return new ProducerRecord<byte[], byte[]>(topic, null, timestamp, key, value);
    }
}
