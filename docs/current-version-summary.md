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

Default runtime settings are tuned to keep checkpoint storage bounded:

- The production checkpoint interval is `flink.checkpoint.interval-ms=180000`; the first completed checkpoint normally appears after the job has run for roughly one interval, not immediately at startup.
- RocksDB incremental checkpoints are enabled with `flink.checkpoint.incremental=true`.
- Only one completed checkpoint is retained while the job is running via `flink.checkpoint.max-retained=1`.
- Externalized checkpoints are deleted on normal job cancellation by default with `flink.checkpoint.externalized-cleanup=DELETE_ON_CANCELLATION`; take a savepoint before planned upgrades when a resumable handoff is required.
- Deduplication state uses a smaller per-vessel cache and TTL (`pipeline.dedup.cache-size`, `pipeline.dedup.ttl-hours`), and idle vessel state expires through `pipeline.state.idle-ttl-hours`.

Existing checkpoint directories created by older job runs are not removed by the new job. After deploying this version, keep one known-good checkpoint or savepoint if needed, then delete obsolete `chk-*` directories or old jobId directories from the checkpoint storage.

## Run

```bash
mvn -q -DskipTests package
flink run -c com.hifleet.analysis.portcall.flink.FlinkPortCallJob target/ai-flink-detect-ship-stop-0.1.0-SNAPSHOT.jar
```
