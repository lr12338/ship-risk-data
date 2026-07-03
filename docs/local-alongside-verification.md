# 本地验证船舶搭靠事件服务

## 快速规则验证

```bash
cd /Users/raymondlu/LocalProject/船舶风险评估数据服务/flink服务参考
bash scripts/verify-alongside-local.sh quick
```

成功标准：

```text
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

这一步只验证本地规则、JSON 和打包，不需要 Kafka/Flink。

## 本地 Kafka + Flink 冒烟验证

前提：

- 本机可执行 Kafka CLI。Homebrew Kafka 命令通常是 `kafka-topics`、`kafka-console-producer`、`kafka-console-consumer`。
- 本地 Flink 使用 1.16.x。当前验证过的路径是 `/tmp/flink-1.16/flink-1.16.0/bin/flink`。
- Flink 1.16 建议用 Java 8 启动；Kafka CLI 可用 Java 21。
- Kafka broker 默认是 `localhost:9092`，也可以覆盖为测试 Kafka。

```bash
bash scripts/verify-alongside-local.sh e2e
bash scripts/verify-alongside-local.sh e2e-disabled
```

脚本会：

- 创建 `AIS_ALONGSIDE_TEST`
- 创建 `RELIABLE_ALONGSIDE_EVENTS_TEST`
- 创建 `RELIABLE_STOP_POINTS_TEST`
- 打包 jar
- 以 `-Dalongside.enabled=true` 启动独立 Flink 验证作业
- 向 `AIS_ALONGSIDE_TEST` 投喂 `src/test/resources/alongside/local-alongside-sample.jsonl`
- 从 `RELIABLE_ALONGSIDE_EVENTS_TEST` 消费输出并校验关键字段
- `e2e-disabled` 会以 `-Dalongside.enabled=false` 启动作业并断言搭靠 topic 无输出

输出验证文件：

```text
target/alongside-e2e-output.jsonl
```

期望字段：

```text
"pair_id":"413193000-413433610"
"event_type":"ALONGSIDE_CANDIDATE"
"event_sub_type":"ALONGSIDE_CONFIRMED_CANDIDATE"
"source_mode":"REALTIME_AUTO_BUCKET"
"rule_version":"ALONGSIDE_V2_INSURANCE"
```

如 Kafka 不在本地，可覆盖 broker：

```bash
KAFKA_BOOTSTRAP=192.168.99.92:9092 bash scripts/verify-alongside-local.sh e2e
```

当前本地已验证命令：

```bash
cd /Users/raymondlu/LocalProject/船舶风险评估数据服务/flink服务参考
KAFKA_BOOTSTRAP=192.168.99.92:9092 bash scripts/verify-alongside-local.sh e2e \
  2>&1 | tee target/alongside-e2e-run.log
```

成功时 `target/alongside-e2e-output.jsonl` 至少包含弱候选和正式候选两条事件；正式候选的 `event_sub_type` 为 `ALONGSIDE_CONFIRMED_CANDIDATE`。

验证开关关闭：

```bash
bash scripts/verify-alongside-local.sh e2e-disabled
```

成功时不会从 `RELIABLE_ALONGSIDE_EVENTS_TEST` 消费到任何搭靠事件。

Flink debug 输出在本地 TaskManager stdout：

```text
/tmp/flink-1.16/flink-1.16.0/log/flink-raymondlu-taskexecutor-0-RaymonddeMacBook-Air.local.out
```

可看到 `debug-clean-ais`、`debug-pair-evidence` 和搭靠事件 JSON。

注意：本地脚本默认使用 `flink.state.backend=hashmap`，避免 Mac ARM 上 Flink 1.16 RocksDB native 包架构不匹配。

## 真实 Kafka 实时消费验证

用于验证项目能消费真实 `LIVE_RECENTSHIPS_DATA` 并进入实时搭靠候选计算链路。脚本默认：

- 从真实输入 topic `LIVE_RECENTSHIPS_DATA` 消费。
- 输出到测试 topic `RELIABLE_ALONGSIDE_EVENTS_LIVE_TEST` 和 `RELIABLE_STOP_POINTS_LIVE_TEST`。
- 使用独立 consumer group。
- 使用 `kafka.starting-offsets=latest`，避免从头消费历史大流量。
- 到达 `LIVE_SECONDS` 后自动取消 Flink job。

全量实时流短时验证：

```bash
KAFKA_BOOTSTRAP=192.168.99.92:9092 \
LIVE_SECONDS=90 \
bash scripts/verify-alongside-local.sh live \
  2>&1 | tee target/alongside-live-run.log
```

本机已验证结果：

```text
Live validation observed clean_ais=22953, pair_evidence=13564, alongside_events=0
Live validation log: target/alongside-live-taskmanager.out
```

说明：

- `clean_ais > 0` 证明真实 Kafka 消息已被解析、过滤并进入 keyed preprocess。
- `pair_evidence > 0` 证明搭靠空间桶召回和 pair 证据计算已执行。
- `alongside_events=0` 在 90 秒短窗口内是可接受结果；正式搭靠事件需要至少 30 分钟、6 个证据点。

数据量太大时，可以加区域 bbox 过滤。上海附近示例：

```bash
KAFKA_BOOTSTRAP=192.168.99.92:9092 \
LIVE_SECONDS=60 \
SCOPE_MIN_LON=120.5 \
SCOPE_MAX_LON=122.5 \
SCOPE_MIN_LAT=30.5 \
SCOPE_MAX_LAT=32.2 \
bash scripts/verify-alongside-local.sh live \
  2>&1 | tee target/alongside-live-shanghai-run.log
```

本机已验证结果：

```text
Live validation observed clean_ais=98, pair_evidence=12, alongside_events=0
```

3 分钟稳定性验证命令：

```bash
KAFKA_BOOTSTRAP=192.168.99.92:9092 \
LIVE_SECONDS=180 \
SCOPE_MIN_LON=120.5 \
SCOPE_MAX_LON=122.5 \
SCOPE_MIN_LAT=30.5 \
SCOPE_MAX_LAT=32.2 \
bash scripts/verify-alongside-local.sh live \
  2>&1 | tee target/alongside-live-shanghai-180s-run.log
```

本机已验证结果：

```text
Live validation observed clean_ais=10094, pair_evidence=9889, alongside_events=0
```

对应 Flink job `8f420072c5097fe3eff40435ed3da008` 在本地 3 分钟窗口内连续完成 checkpoint 16 到 36，随后由脚本正常取消；`flink list` 确认无残留任务。

如需用真实 Kafka 最新流快速验证“可以实际产出搭靠事件”，使用本地低阈值验证模式：

```bash
KAFKA_BOOTSTRAP=192.168.99.92:9092 \
LIVE_SECONDS=180 \
SCOPE_MIN_LON=120.5 \
SCOPE_MAX_LON=122.5 \
SCOPE_MIN_LAT=30.5 \
SCOPE_MAX_LAT=32.2 \
bash scripts/verify-alongside-local.sh live-event \
  2>&1 | tee target/alongside-live-event-run.log
```

`live-event` 只用于本地端到端冒烟验证，默认覆盖为：

```text
alongside.candidate.min-duration-minutes=1
alongside.candidate.min-points=2
alongside.confirm.min-duration-minutes=2
alongside.confirm.min-points=3
```

成功标准：

```text
Live validation observed clean_ais>0, pair_evidence>0, alongside_events>0
Kafka alongside event output: target/alongside-live-event-kafka-output.jsonl
```

这一步证明真实 `LIVE_RECENTSHIPS_DATA` 最新流已经完成：

```text
Kafka 消费 -> AIS 解析 -> CleanAisPoint -> 空间桶召回 -> pair evidence -> AlongsideEvent -> 独立 Kafka topic
```

注意：`live-event` 会使用 `RELIABLE_ALONGSIDE_EVENTS_LIVE_EVENT_TEST_<timestamp>` 和 `RELIABLE_STOP_POINTS_LIVE_EVENT_TEST_<timestamp>`，不会写生产 topic；生产口径仍以默认 20/30 分钟阈值为准。

也可以用 MMSI 白名单限制范围：

```bash
SCOPE_MMSI_WHITELIST=413193000,413433610 \
KAFKA_BOOTSTRAP=192.168.99.92:9092 \
LIVE_SECONDS=120 \
bash scripts/verify-alongside-local.sh live
```

## 手工投喂样本

样本文件：

```text
src/test/resources/alongside/local-alongside-sample.jsonl
```

手工投喂：

```bash
kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic AIS_ALONGSIDE_TEST \
  < src/test/resources/alongside/local-alongside-sample.jsonl
```

手工观察输出：

```bash
kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic RELIABLE_ALONGSIDE_EVENTS_TEST \
  --from-beginning \
  --timeout-ms 20000
```

## 历史回放验证

如果已有 JSONL AIS 归档：

```bash
REPLAY_INPUT_DIR=data/ais-history \
REPLAY_START_TIME="2026-06-29 00:00:00" \
REPLAY_END_TIME="2026-06-29 02:00:00" \
bash scripts/verify-alongside-local.sh replay
```

建议先回放 `413433610 / 413193000` 一类离线已知样本窗口，再对比 `RELIABLE_ALONGSIDE_EVENTS_TEST` 中的 pair、时间段和事件等级。

## 安全边界

- 验证默认使用 `_TEST` topic。
- 搭靠结果只写 `RELIABLE_ALONGSIDE_EVENTS_TEST`。
- 停船点验证 topic 为 `RELIABLE_STOP_POINTS_TEST`，不会写生产 `RELIABLE_STOP_POINTS`。
- 不加 `-Dalongside.enabled=true` 时，搭靠分支不启动。
