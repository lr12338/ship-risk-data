# 船舶搭靠实时识别实现说明

## 1. 目标和边界

本次目标是在不改变停船点主链路语义的前提下，将实时搭靠识别拆成可独立演进、可灰度上线的 Flink 分支。

保持不变的停船点链路：

```text
Kafka LIVE_RECENTSHIPS_DATA
-> AisMessageParser.parseMany()
-> ValidAisPointFilter
-> watermark by updatetime
-> keyBy(mmsi)
-> FlinkKeyedPreprocessFunction
-> CleanAisPoint
-> StopPointDetectorProcessFunction
-> Kafka RELIABLE_STOP_POINTS
```

搭靠链路仅从 `CleanAisPoint` 后分叉，默认 `alongside.enabled=false`，开启后只输出到 `RELIABLE_ALONGSIDE_EVENTS`。

## 2. 分层结构

core 层：`com.hifleet.analysis.alongside.core`

- `model`: `AlongsideEvent`, `PairEvidenceWindow`, `PairProximityEvidence`, `AlongsideRuleConfig`, `VesselProfile`, `VesselClass`, `VesselType`, `PairPriority`
- `rule`: `AlongsideRuleEngine`, `AlongsideRuleEngineImpl`
- `classifier`: `VesselTypeClassifier`, `VesselTypeClassifierImpl`, `AlongsidePairPriorityClassifier`, `AlongsidePairPriorityClassifierImpl`
- `score`: `AlongsideRiskScorer`, `AlongsideRiskScorerImpl`, `AlongsideSceneAdjustmentService`, `AlongsideSceneAdjustmentServiceImpl`

flink adapter 层：`com.hifleet.analysis.alongside.flink`

- `AlongsideAisPointFilter`
- `SpatialCandidateProcessFunction`
- `AlongsidePairDetectorProcessFunction`
- `PairEvidenceKeySelector`
- `bucket/SpatialBucketAssigner`
- `bucket/SpatialBucketKeySelector`
- `bucket/SpatialBucketedAisPoint`
- `state/BucketVesselStateStore`
- `state/LatestPointBucketStateStore`

output adapter 层：`com.hifleet.analysis.alongside.output`

- `AlongsideEventSinkMapper`
- `AlongsideEventMapper`
- `AlongsideKafkaSerializationSchema`

`portcall.flink.AlongsideJsonMapFunction` 只作为 Flink debug `MapFunction` 委托 output mapper，不承载业务 JSON 规则。

## 3. 实时链路

```text
CleanAisPoint
-> AlongsideAisPointFilter
-> SpatialBucketAssigner
-> keyBy(bucketId)
-> SpatialCandidateProcessFunction
-> keyBy(pairId)
-> AlongsidePairDetectorProcessFunction
-> AlongsideKafkaSerializationSchema
-> Kafka RELIABLE_ALONGSIDE_EVENTS
```

pairId 固定为 `minMmsi-maxMmsi`，避免 A-B / B-A 对称双写。

## 4. 规则表

| 阶段 | 规则 |
| --- | --- |
| 候选外圈 | 双方 SOG `<= 4 kn`，timeGap `<= 10 min`，distance `<= 0.5 nm`，非同一 MMSI |
| 弱候选 | duration `>= 20 min`，pointCount `>= 3`，`event_sub_type=WEAK_ALONGSIDE_CANDIDATE` |
| 正式候选 | distance `<= 0.15 nm`，相对速度 `<= 1 kn`，parallelDiff `<= 30`，duration `>= 30 min`，pointCount `>= 6` |
| UPDATE | 正式候选已输出后，持续满足规则且超过 `alongside.output.update-interval-minutes` |
| END | 已输出过弱/正式候选的 pair 出现 evidence 断档并切分状态 |
| nav_status 降权 | 任一船 nav status 为 `1/5` 时，`DOWNGRADED_NORMAL_LIKELY`，分数乘 `0.5` |
| 无外部证据封顶 | 无港口、锚地、STS、画像等外部证据时最高 `L2` |

## 5. 输出模型

新增 `com.hifleet.analysis.alongside.core.model.AlongsideEvent`，不复用 `StopPointEvent`。

Kafka：

- topic: `RELIABLE_ALONGSIDE_EVENTS`
- key: `pair_id`
- value: `AlongsideEventMapper` 输出 JSON

关键字段包括 `event_type`, `event_sub_type`, `source_mode`, `rule_version`, `pair_id`, `mmsi_a`, `mmsi_b`, `start_time`, `end_time`, `duration_minutes`, `point_count`, `min_distance_nm`, `max_distance_nm`, `avg_distance_nm`, `avg_relative_speed`, `heading_parallel_ratio`, `pair_priority`, `ship_class_a`, `ship_class_b`, `severity_level`, `event_score`, `confidence_level`, `exclusion_flag`, `exclusion_reason`, `score_detail_json`。

## 6. 配置项

配置集中在 `AlongsideConfig` 和 `application.properties`，核心阈值不硬编码：

- `alongside.enabled`
- `alongside.output.topic`
- `alongside.rule-version`
- `alongside.pre-filter.enabled`
- `alongside.pre-filter.max-speed`
- `alongside.pre-filter.exclude-invalid-mmsi`
- `alongside.ship-type-filter.enabled`
- `alongside.ship-type.high-priority`
- `alongside.ship-type.normal-service`
- `alongside.ship-type.low-priority`
- `alongside.candidate.max-distance-nm`
- `alongside.candidate.max-speed`
- `alongside.candidate.max-time-gap-minutes`
- `alongside.candidate.min-duration-minutes`
- `alongside.candidate.min-points`
- `alongside.confirm.max-distance-nm`
- `alongside.confirm.max-speed`
- `alongside.confirm.max-relative-speed`
- `alongside.confirm.min-duration-minutes`
- `alongside.confirm.min-points`
- `alongside.heading.enabled`
- `alongside.heading.parallel-threshold-degree`
- `alongside.scene.nav-status-downgrade.enabled`
- `alongside.scene.no-scene-data.max-level`
- `alongside.scene.auto-l4-enabled`
- `alongside.state.bucket-point-ttl-minutes`
- `alongside.state.pair-ttl-minutes`
- `alongside.state.ended-pair-ttl-hours`
- `alongside.output.update-interval-minutes`
- `alongside.metrics.enabled`

## 7. 诊断和指标

Flink metrics counters：

- `rejectByInvalidAis`
- `rejectBySpeed`
- `rejectByShipType`
- `rejectByNavStatus`
- `rejectByDistance`
- `rejectByTimeGap`
- `rejectBySameVessel`
- `rejectByPairPriority`
- `candidateEvidenceCount`
- `weakCandidateCount`
- `confirmedCandidateCount`
- `downgradedNormalLikelyCount`
- `emittedEventCount`

日志统一使用 `ALONGSIDE_REALTIME` 前缀，覆盖 `prefilter_summary`, `bucket_summary`, `pair_evidence`, `candidate_summary`, `event`, `state_summary`。

## 8. 验证结果

已执行：

```bash
mvn test -q
bash scripts/verify-alongside-local.sh quick
```

结果：

```text
Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

覆盖点：

- Rule engine 正式候选、弱候选、距离超阈值、时间差超阈值、相对速度超阈值、航向不并靠。
- pairId A-B / B-A 规范化。
- 船型分类：AIS ship type 油轮、船名油轮、拖轮、未知船。
- pair 优先级：油轮-油轮 HIGH，商船-拖轮 DOWNGRADED，同 MMSI EXCLUDE。
- nav_status=1/5 降权。
- pair adapter UPDATE / END 状态。
- Alongside JSON 不包含停船点字段。
- 原停船点 JSON 回归测试继续通过。

本机未执行 Kafka/Flink `e2e`：当前 `localhost:9092` 无 Kafka broker 监听。`quick` 已覆盖单测和打包，真实 Kafka/Flink E2E 仍需在具备 broker 的环境运行 `bash scripts/verify-alongside-local.sh e2e`。

脚本已提供 `bash scripts/verify-alongside-local.sh e2e-disabled`，用于在 Kafka/Flink 环境中验证 `alongside.enabled=false` 时搭靠 topic 无输出。

## 9. 停船点无影响证明

- `StopPointDetectorProcessFunction` 无修改。
- `StopPointEvent` 无修改。
- `StopPointJsonMapFunction` 无修改。
- `StopPointKafkaSerializationSchema` 无修改。
- 搭靠事件模型为 `alongside.core.model.AlongsideEvent`。
- 搭靠 Kafka 序列化使用独立 `AlongsideKafkaSerializationSchema` 和独立 topic。
- `alongside.enabled=false` 时不创建搭靠分支。

## 10. 灰度建议

推荐同一个 jar 独立 Flink job 灰度：

```bash
flink run \
  -c com.hifleet.analysis.portcall.flink.FlinkPortCallJob \
  -Dkafka.group.id=alongside-group \
  -Dkafka.client.id=alongside-consumer-1 \
  -Dkafka.topic=LIVE_RECENTSHIPS_DATA \
  -Dalongside.output.topic=RELIABLE_ALONGSIDE_EVENTS \
  -Dalongside.enabled=true \
  target/ai-flink-detect-ship-stop-0.1.0-SNAPSHOT.jar
```

观察 checkpoint、RocksDB state、Kafka lag、事件量、误报率。回滚时 cancel 独立 alongside job，不影响原停船点生产 job。

## 11. 当前业务边界

- 尚未接入港口、锚地、STS、制裁区、船舶画像、保险评分服务。
- 无外部证据时 severity cap 到 `L2`。
- 当前输出为实时候选事件，不作为最终保险评分结论。
- 实时桶状态仍是短期点状态，后续可替换为滑动窗口状态。

## 12. 下一步模块化建议

1. 将 `com.hifleet.analysis.alongside.core` 抽出独立 Maven module。
2. 将 `CleanAisPoint` 输入抽为 core 自有轻量 DTO 或接口，进一步消除对 portcall model 的编译依赖。
3. 将 geofence、船舶画像、保险评分服务接入 `AlongsideContext`。
4. 用 replay topic 补齐真实 Kafka/Flink E2E 和误报率评估。
