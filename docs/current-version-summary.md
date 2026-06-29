# Current Version Summary

## Runtime

The project now runs as a Flink streaming job that outputs reliable stop points. The application entry point delegates to:

```text
com.hifleet.analysis.portcall.flink.FlinkPortCallJob
```

## Processing Flow

```text
Kafka flink_aisdata_topic
  -> Flink KafkaSource
  -> AisMessageParser.parseMany()
  -> data_state + valid AIS filter
  -> event-time watermark by updatetime
  -> keyBy(mmsi)
  -> FlinkKeyedPreprocessFunction
     -> duplicate filtering
     -> out-of-order buffering
     -> late-point dropping
     -> jump-point check
  -> StopPointDetectorProcessFunction
     -> low-speed stop candidate tracking
     -> stable-window stop confirmation
     -> stable-position mean with outlier filtering
     -> throttled STOP_UPDATE / STOP_END / UNKNOWN output
  -> StopPointKafkaSerializationSchema
  -> Kafka RELIABLE_STOP_POINTS
```

## Outputs

- `kafka.stop-point.topic`, default `RELIABLE_STOP_POINTS`
- Each record is a persistence-ready vessel stop-state JSON event.
- Kafka key is `mmsi`.
- Supported events: `STOP_CANDIDATE_START`, `STOP_START`, `STOP_UPDATE`, `STOP_END`, `SAILING_START`, `UNKNOWN`.

## Recovery

Flink checkpointing is the active recovery mechanism for Kafka offsets, keyed preprocessing state, and stop detector state.

## Run

```bash
mvn -q -DskipTests package
flink run -c com.hifleet.analysis.portcall.flink.FlinkPortCallJob target/ai-flink-detect-ship-stop-0.1.0-SNAPSHOT.jar
```
