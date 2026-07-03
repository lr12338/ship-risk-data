package com.hifleet.analysis.alongside.output;

import com.hifleet.analysis.alongside.core.model.AlongsideEvent;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.nio.charset.StandardCharsets;

public class AlongsideKafkaSerializationSchema implements KafkaRecordSerializationSchema<AlongsideEvent> {

    private static final long serialVersionUID = 1L;

    private final String topic;
    private final AlongsideEventSinkMapper mapper = new AlongsideEventMapper();

    public AlongsideKafkaSerializationSchema(String topic) {
        this.topic = topic;
    }

    @Override
    public ProducerRecord<byte[], byte[]> serialize(AlongsideEvent event, KafkaSinkContext context, Long timestamp) {
        byte[] key = event.getPairId() == null ? null : event.getPairId().getBytes(StandardCharsets.UTF_8);
        byte[] value = mapper.toJson(event).getBytes(StandardCharsets.UTF_8);
        return new ProducerRecord<byte[], byte[]>(topic, null, timestamp, key, value);
    }
}
