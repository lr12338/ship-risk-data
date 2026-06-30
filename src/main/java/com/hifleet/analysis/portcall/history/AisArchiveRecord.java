package com.hifleet.analysis.portcall.history;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.analysis.portcall.consumer.AisMessageParser;
import com.hifleet.analysis.portcall.model.AisPoint;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.time.Instant;
import java.util.List;

/**
 * JSONL envelope that stores a raw Kafka AIS record with replay metadata.
 */
public class AisArchiveRecord {

    private static final AisMessageParser PARSER = new AisMessageParser();

    private String topic;
    private int partition;
    private long offset;
    private Instant kafkaTimestamp;
    private String key;
    private String value;
    private Long mmsi;
    private Instant updateTime;
    private Instant serverReceiveTime;
    private Instant archiveTime;

    public static AisArchiveRecord fromConsumerRecord(ConsumerRecord<String, String> record) {
        AisArchiveRecord archiveRecord = new AisArchiveRecord();
        archiveRecord.topic = record.topic();
        archiveRecord.partition = record.partition();
        archiveRecord.offset = record.offset();
        archiveRecord.kafkaTimestamp = Instant.ofEpochMilli(record.timestamp());
        archiveRecord.key = record.key();
        archiveRecord.value = record.value();
        archiveRecord.archiveTime = Instant.now();
        archiveRecord.fillParsedFields(record.value());
        return archiveRecord;
    }

    public static AisArchiveRecord fromJsonLine(String line) {
        JSONObject object = JSON.parseObject(line);
        AisArchiveRecord record = new AisArchiveRecord();
        record.topic = object.getString("topic");
        Integer partitionValue = object.getInteger("partition");
        record.partition = partitionValue == null ? -1 : partitionValue;
        Long offsetValue = object.getLong("offset");
        record.offset = offsetValue == null ? -1L : offsetValue;
        record.kafkaTimestamp = parseInstant(object.getString("kafka_timestamp"));
        record.key = object.getString("key");
        record.value = object.getString("value");
        record.mmsi = object.getLong("mmsi");
        record.updateTime = parseInstant(object.getString("updatetime"));
        record.serverReceiveTime = parseInstant(object.getString("server_receive_time"));
        record.archiveTime = parseInstant(object.getString("archive_time"));
        if (record.updateTime == null || record.mmsi == null) {
            record.fillParsedFields(record.value);
        }
        return record;
    }

    public String toJsonLine() {
        JSONObject object = new JSONObject(true);
        object.put("topic", topic);
        object.put("partition", partition);
        object.put("offset", offset);
        object.put("kafka_timestamp", format(kafkaTimestamp));
        object.put("key", key);
        object.put("value", value);
        object.put("mmsi", mmsi);
        object.put("updatetime", format(updateTime));
        object.put("server_receive_time", format(serverReceiveTime));
        object.put("archive_time", format(archiveTime));
        return object.toJSONString();
    }

    private void fillParsedFields(String payload) {
        List<AisPoint> points = PARSER.parseMany(payload);
        if (points.isEmpty()) {
            return;
        }
        AisPoint point = points.get(0);
        this.mmsi = point.getMmsi();
        this.updateTime = point.getUpdateTime();
        this.serverReceiveTime = point.getServerReceiveTime();
    }

    private static String format(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static Instant parseInstant(String value) {
        try {
            return HistoryToolConfig.parseInstant(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public Instant resolvePartitionTime(String partitionTimeField) {
        if ("kafka_timestamp".equalsIgnoreCase(partitionTimeField)) {
            return kafkaTimestamp;
        }
        if ("archive_time".equalsIgnoreCase(partitionTimeField)) {
            return archiveTime;
        }
        if (updateTime != null) {
            return updateTime;
        }
        if (kafkaTimestamp != null) {
            return kafkaTimestamp;
        }
        return archiveTime == null ? Instant.now() : archiveTime;
    }

    public String resolveReplayKey(String keyMode) {
        if ("null".equalsIgnoreCase(keyMode)) {
            return null;
        }
        if ("mmsi".equalsIgnoreCase(keyMode)) {
            return mmsi == null ? key : String.valueOf(mmsi);
        }
        return key;
    }

    public String getValue() {
        return value;
    }

    public Long getMmsi() {
        return mmsi;
    }

    public Instant getUpdateTime() {
        return updateTime;
    }
}
