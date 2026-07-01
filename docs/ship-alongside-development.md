# 船舶搭靠模块开发文档

## 1. 模块目标

船舶搭靠模块用于识别“两条船在时间邻近、空间邻近、双方低速且具备连续轨迹证据时形成的搭靠候选事件”。

当前版本为 `ALONGSIDE_V2_INSURANCE`。它不是最终保险评分全口径，而是在原有指定双船候选识别链路上，补齐保险风控需要的基础能力：

- 保留指定 `mmsi + targetMmsi` 的双船复核模式
- 支持不传 `targetMmsi` 的 AUTO 单船入口
- 增加轨迹清洗
- 增加相对速度
- 增加航向并靠判断
- 增加事件合并
- 增加事件置信度
- 增加正常场景轻量降权
- 增加单事件风险评分
- 增加 L0-L4 风险等级
- 增加无外部场景数据时的等级封顶
- 增加 `score_detail_json` 证据落库
- 增加 AUTO 模式低速窗口提取和年度指标日志
- 增加批量 AUTO 运行、时间切片和断点续跑能力

需要特别注意：当前模块仍然只应被理解为“搭靠候选事件识别 + 轻量保险化评分”。在未接入港口、锚地、STS 热点、制裁区、船舶画像、PSC、事故等外部证据前，不应把输出结果直接作为高风险最终结论。

## 2. 当前能力边界

### 2.1 已完成能力

- XXL-Job 入口：`shipAlongsideJobHandler`
- PAIR 模式：指定主船 `mmsi` 和目标船 `targetMmsi` 的双船分析
- AUTO 模式入口：不传 `targetMmsi` 或 `mode=AUTO` 时进入单船流程
- 主船和目标船轨迹读取：复用 `TrajectoryLoaderService`
- 检测器：`ShipAlongsideDetector.detectWithTrace`
- 主船低速窗口提取：用于 AUTO 候选搜索前置收敛
- AUTO 候选召回：基于 `getRecentAis` 区域回放接口按低速窗口 bbox 召回附近船舶
- 批量 AUTO 入口：`shipAlongsideBatchJobHandler`
- 批量时间切片：支持 `DAY/WEEK/MONTH/CUSTOM_DAYS`
- 批量断点续跑：`resume=true` 时跳过已完成的 `batchNo + mmsi + 时间窗`
- 批量状态表：`dataanalysis.ship_behavior_alongside_batch_status`
- replay AIS 解析：将分号分隔 AIS 字符串解析为 `TrajectoryPoint`
- 候选预过滤：排除主船、点数不足、高速、时间/距离不匹配、持续不足的目标船
- 时间邻近匹配：在时间窗内优先选择时间最近的目标船点，再用距离做次级比较
- 低速确认：双方 SOG 不超过确认阈值
- 相对速度：当前使用双方 SOG 差值作为轻量相对速度
- 航向并靠：支持同向或反向并靠判断
- 事件合并：同一对船相邻事件可按时间间隔和空间中心距离合并
- 正常场景轻量降权：`nav_status` 命中锚泊、系泊、泊位时降权
- 单事件风险评分：写入 `event_score`
- 风险等级：写入 `severity_level=L0-L4`
- 置信度：写入 `confidence_level=HIGH/MEDIUM/LOW`
- 排除/降权原因：写入 `exclusion_flag`、`exclusion_reason`
- 证据 JSON：写入 `score_detail_json`
- 事件摘要落库：`dataanalysis.ship_behavior_event`
- 事件轨迹快照落库：`dataanalysis.ship_behavior_event_detail`
- 点角色追溯：`point_role=OWN/TARGET`
- 单元测试覆盖 detector 关键误判控制场景

### 2.2 尚未完成能力

- AUTO 模式已支持区域回放候选召回，但尚未做并发查询和截断后自动拆分重查
- 尚未接入港口、锚地、泊位、STS 热点、制裁区、战区、海盗区等 geofence
- 尚未接入目标船船型、船旗、PSC、事故、制裁、影子船队等对象画像
- 尚未接入报备 STS、补给、救助、拖轮、引航等正常作业记录
- 尚未接入徘徊、异常停泊、AIS 中断等事件链联动查询
- 尚未新增年度评分表落库
- 尚未形成最终核保/定价评分口径
- 批量状态表只记录执行进度和窗口统计，不等同于年度搭靠评分表

因此，当前输出可以用于算法调试、样本标注、案件复核和低风险提示，不应单独用于一票否决或 L4 严重异常结论。

## 3. 任务入口与参数

XXL-Job Handler：

- `shipAlongsideJobHandler`
- `shipAlongsideBatchJobHandler`

入口代码：

- [ShipBehaviorJob.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/xxljob/ShipBehaviorJob.java:33)

核心服务：

- [ShipBehaviorAnalysisService.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/job/services/ShipBehaviorAnalysisService.java:335)

## 4. PAIR 模式

当参数包含 `targetMmsi` 且未显式指定 `mode=AUTO` 时，进入 PAIR 模式。

示例：

```json
{
  "mmsi": "413433610",
  "targetMmsi": "413193000",
  "startTime": "2026-06-28 18:00:00",
  "endTime": "2026-06-30 09:40:00",
  "batchNo": "alongside-test-20260629-01",
  "mode": "PAIR"
}
```

用途：

- 已知两船关系复核
- 理赔案件调查
- 算法规则调试
- 样本标注
- 回归测试

链路：

```text
shipAlongsideJobHandler
-> ShipBehaviorAnalysisService.analysisAlongside
-> analysisAlongsidePair
-> TrajectoryLoaderService.load(主船)
-> TrajectoryLoaderService.load(目标船)
-> ShipAlongsideDetector.detectWithTrace
-> ShipBehaviorEventRepository.saveEvents
-> ShipBehaviorEventDetailRepository.saveDetails
```

## 5. AUTO 模式

当参数不包含 `targetMmsi`，或显式指定 `mode=AUTO` 时，进入 AUTO 模式。

示例：

```json
{
  "mmsi": "413433610",
  "startTime": "2026-06-28 18:00:00",
  "endTime": "2026-06-30 09:40:00",
  "batchNo": "alongside-auto-test-20260630-01",
  "mode": "AUTO"
}
```

当前 AUTO 已实现：

1. 读取主船 AIS 轨迹
2. 清洗和排序主船轨迹
3. 提取主船低速窗口
4. 按低速窗口构造 bbox replay 查询区域
5. 调用 `https://gm.hifleet.com/spatialais/getRecentAis`
6. 解析 replay 返回的 AIS 点
7. 对候选目标船做预过滤
8. 将候选目标船轨迹送入 `ShipAlongsideDetector.detectWithTrace`
9. 输出 replay 查询、候选统计和时间窗年度指标日志
10. 写入事件摘要和事件明细

当前 AUTO 尚未实现：

1. replay 查询并发控制，目前第一版以串行为主
2. `devSize=50000` 截断后的自动拆分重查
3. replay 查询窗口合并/拆分的高级策略
4. 跨 replay 子窗口的复杂候选事件去重
5. 写年度评分表

当前 AUTO 候选来源：

```text
ShipAlongsideReplayCandidateProvider
-> SpatialAisReplayClient
-> getRecentAis
-> ReplayAisPointParser
-> AlongsideCandidateTarget
-> ShipAlongsideDetector.detectWithTrace
```

当前实现采用 bbox 查询，不做全量两两匹配：

```text
低速窗口 minLon/maxLon/minLat/maxLat
加 bbox-buffer-nm
作为 getRecentAis 查询区域
返回区域内所有船舶 AIS 点
再用候选过滤规则收敛 targetMmsi
```

候选过滤规则：

- 排除 `targetMmsi == ownMmsi`
- 目标船有效点数 >= `candidate.min-points`
- 目标船至少一个点 `sog <= candidate.max-speed`
- 与主船低速窗口点存在 `timeGap <= candidate.max-time-gap-minutes`
- 与主船低速窗口点存在 `distance <= candidate.max-distance-nm`
- 候选匹配持续时间 >= `candidate.min-duration-minutes`

## 5.1 批量 AUTO 模式

批量搭靠用于船队和多时间窗自动运行，固定复用单船 AUTO 链路，不支持 `targetMmsi` 批量 PAIR。已知两船复核仍使用 `shipAlongsideJobHandler`。

链路：

```text
shipAlongsideBatchJobHandler
-> ShipBehaviorAnalysisService.analysisAlongsideBatch
-> mmsiList / mmsi / ais41.recentships
-> DAY/WEEK/MONTH/CUSTOM_DAYS 时间切片
-> analysisAlongsideOneWindow(AUTO)
-> getRecentAis 候选召回
-> ShipAlongsideDetector
-> ship_behavior_event / ship_behavior_event_detail
-> ship_behavior_alongside_batch_status
```

示例参数：

```json
{
  "mmsiList": ["414060000", "356470000"],
  "startTime": "2025-01-01 00:00:00",
  "endTime": "2025-02-01 23:59:59",
  "sliceMode": "WEEK",
  "batchNo": "alongside-batch-smoke-202501-v1",
  "resume": true,
  "parallelism": 1,
  "mode": "AUTO"
}
```

船池规则：

- `mmsiList` 存在：按列表执行
- 不传 `mmsiList` 但传 `mmsi`：按单船和多个时间切片执行
- 两者都不传：复用 `ais41.recentships` 作为船池

时间切片：

- `DAY`：按天切片
- `WEEK`：按周切片
- `MONTH`：按月切片
- `CUSTOM_DAYS`：按 `sliceDays` 切片

断点续跑：

- `resume=true` 时查询 `ship_behavior_alongside_batch_status`
- 已存在 `DONE` 的 `batchNo + mmsi + window_start_time + window_end_time` 会跳过
- `FAILED` 不跳过，可用同一 `batchNo` 重跑

状态表字段：

```text
batch_no
mmsi
window_start_time
window_end_time
status
point_count
low_speed_window_count
candidate_target_count
replay_query_count
replay_failure_count
event_count
error_message
created_at
updated_at
```

唯一键：

```text
(batch_no, mmsi, window_start_time, window_end_time)
```

## 6. V2 检测规则

检测器位置：

- [ShipAlongsideDetector.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/service/ShipAlongsideDetector.java:20)

### 6.1 轨迹清洗

检测前会执行：

- 过滤空点
- 过滤时间为空
- 过滤经纬度越界
- 过滤速度小于 0 或大于 80kn 的异常点
- 按时间排序
- 去除完全重复的 `time + lon + lat`

### 6.2 时间匹配

对主船每个点，在目标船轨迹中查找时间差不超过阈值的点：

- `timeGap <= confirm.max-time-gap-minutes`
- 匹配点选择策略：优先时间最近，再用距离最近作为次级比较

这个策略比 V1 的“距离最近优先”更适合 AIS 异步采样，避免两船低速静止时误选较旧目标点，导致时间证据被低估。

### 6.3 距离规则

当前确认距离：

- `ship.behavior.alongside.confirm.max-distance-nm=0.15`

兼容旧配置：

- `ship.behavior.alongside.max-distance-nm=0.1`

如 `confirm.max-distance-nm` 未配置或小于等于 0，才回退到旧配置。

后续接入船长 LOA 后，可升级为动态阈值：

```text
confirmDistanceNm = max(0.1, min(0.25, ((loaA + loaB) / 2 + 100m) / 1852))
```

### 6.4 速度规则

当前规则：

- 双方 SOG <= `confirm.max-speed`
- 当前轻量相对速度 = `abs(sogA - sogB)`
- 相对速度 <= `confirm.max-relative-speed`

后续可用 COG + SOG 计算向量相对速度，替代当前 SOG 差值。

### 6.5 航向并靠

当前使用 COG 计算并靠特征：

```text
headingDiff = abs(courseA - courseB)
headingDiff = min(headingDiff, 360 - headingDiff)
parallelDiff = min(headingDiff, abs(headingDiff - 180))
parallelDiff <= 30° 认为同向或反向并靠成立
```

如果双方速度均低于 `0.5kn`，COG 可能不稳定，当前不硬过滤。若 COG 缺失或异常，也不硬过滤，但会影响置信度证据。

### 6.6 持续时长与点数

当前分两层：

- 弱候选：`duration >= 20min` 且 `pointCount >= 3`
- 正式候选：`duration >= 30min` 且 `pointCount >= 6`

弱候选可以落库，但：

- `event_sub_type=WEAK_ALONGSIDE_CANDIDATE`
- `event_score` 封顶为 20
- `severity_level` 固定为 L1
- 不应进入年度正式扣分

正式候选：

- `event_sub_type=ALONGSIDE_CONFIRMED_CANDIDATE`
- 可进入年度 ARI 计算

### 6.7 事件合并

同一 PAIR 检测过程中，相邻片段满足以下条件时合并：

- 事件间隔 <= `merge.max-gap-minutes`
- 事件中心点距离 <= `merge.max-center-distance-nm`

当前合并只在同一次双船检测的结果内进行。AUTO 真正启用后，需要在 service 层增加跨候选目标的去重合并。

## 7. 正常场景排除与降权

当前没有港口、锚地、报备 STS、拖轮/引航作业、救助作业等外部数据，因此不做强排除，只做轻量降权。

当前规则：

```text
如果主船或目标船 nav_status 包含 ANCHOR / MOORED / BERTH
或 nav_status 为 1 / 5
则：
  exclusion_flag = DOWNGRADED_NORMAL_LIKELY
  exclusion_reason = NAV_STATUS_ANCHORED_OR_MOORED
  event_score 乘以 0.5
```

如果未命中正常场景：

```text
exclusion_flag = VALID_RISK_EVENT
exclusion_reason = NO_SCENE_DATA
```

注意：`VALID_RISK_EVENT + NO_SCENE_DATA` 的含义不是“已确认异常”，而是“没有外部场景数据可排除，需要保守看待”。

## 8. 单事件风险评分

当前单事件评分模型：

```text
event_score = baseEvidenceScore
            + sceneRiskScore
            + counterpartyRiskScore
            + behaviorRiskScore
            + durationRiskScore
            + recurrenceRiskScore
```

当前已实现：

- `baseEvidenceScore`：距离、持续时间、点数、时间差、相对速度、航向并靠
- `behaviorRiskScore`：搭靠片段内 AIS gap
- `durationRiskScore`：长时间搭靠增强
- `sceneRiskScore=0`：等待港口、锚地、STS、制裁区等外部数据
- `counterpartyRiskScore=0`：等待目标船画像
- `recurrenceRiskScore=0`：等待年度/跨批次重复搭靠统计

评分证据写入 `score_detail_json`，包括：

- `baseEvidenceScore`
- `sceneRiskScore`
- `counterpartyRiskScore`
- `behaviorRiskScore`
- `durationRiskScore`
- `recurrenceRiskScore`
- `normalSceneAdjustment`
- `rawScore`
- `eventScore`
- `rawSeverityLevel`
- `severityLevel`
- `severityCapReason`
- `exclusionFlag`
- `exclusionReason`
- `sceneDataAvailable`
- `weakCandidate`
- `avgDistanceNm`
- `avgRelativeSpeed`
- `maxTimeGapSeconds`
- `timeGapHighRatio`
- `headingParallelRatio`
- `trackCoverageRatio`
- `candidateSource`
- `replayQueryCount`
- `replayFailures`
- `replayTruncatedQueries`
- `replayReturnedVessels`
- `replayRawPoints`
- `replayValidPoints`
- `replayParseErrors`
- `replayElapsedMs`

## 9. 风险等级与封顶

等级映射：

```text
0：L0
1-25：L1
26-50：L2
51-75：L3
76-100：L4
```

当前封顶规则：

如果同时满足：

- `exclusion_reason=NO_SCENE_DATA`
- `scene-data.available=false`
- `counterparty-risk.available=false`
- 无 AIS gap
- `confidence_level != HIGH`

则：

```text
severity_level 最高不超过 ship.behavior.alongside.severity-cap.no-scene-data.max-level
```

默认封顶为：

```properties
ship.behavior.alongside.severity-cap.no-scene-data.max-level=L2
ship.behavior.alongside.auto-l4-enabled=false
```

因此，在缺少场景数据和对象风险画像时，当前版本不会直接输出 L4。

## 10. 年度搭靠风险指标

AUTO 模式当前已在内存中计算时间窗聚合指标并输出日志，尚未落年度评分表。

当前计算字段：

- `eventCount`
- `validEventCount`
- `highRiskEventCount`
- `severeEventCount`
- `uniqueTargetCount`
- `totalDurationMinutes`
- `maxDurationMinutes`
- `aisGapRelatedCount`
- `maxEventScore`
- `ARI`
- `subScore10`
- `weightedScore`
- `manualReview`

ARI 权重：

```text
L0 = 0
L1 = 1
L2 = 2
L3 = 4
L4 = 8
```

增强：

```text
伴随 AIS gap：事件权重 * 1.2
正常降权事件：不进入 ARI
弱候选：不进入 ARI
```

10 分制映射：

```text
ARI = 0：10 分
ARI 1-3：8 分
ARI 4-8：6 分
ARI 9-15：3 分
ARI > 15 或 L4 >= 1：0 分
```

当前默认权重：

```properties
ship.behavior.alongside.score.weight=0.20
```

后续应新增 `ShipAlongsideAnnualScoreService` 和 `ship_behavior_alongside_score_yearly` 表，把时间窗聚合结果落库。

## 11. 落库设计

### 11.1 事件摘要表

继续复用：

- `dataanalysis.ship_behavior_event`

当前搭靠 V2 会写入：

- `behavior_type=ALONGSIDE`
- `mmsi`
- `target_mmsi`
- `start_time`
- `end_time`
- `duration_minutes`
- `start_lon`
- `start_lat`
- `end_lon`
- `end_lat`
- `min_distance_nm`
- `max_distance_nm`
- `avg_speed`
- `point_count`
- `rule_version=ALONGSIDE_V2_INSURANCE`
- `batch_no`
- `event_sub_type`
- `severity_level`
- `event_score`
- `confidence_level`
- `exclusion_flag`
- `exclusion_reason`
- `ais_gap_count`
- `max_ais_gap_minutes`
- `track_coverage_ratio`
- `center_lon`
- `center_lat`
- `radius_nm`
- `score_detail_json`

当前未新增摘要表字段。`avgDistanceNm`、`avgRelativeSpeed`、`headingParallelRatio` 等先写入 `score_detail_json`。

### 11.2 事件明细表

继续复用：

- `dataanalysis.ship_behavior_event_detail`

搭靠场景下：

- `point_role=OWN` 表示主船点
- `point_role=TARGET` 表示目标船点

明细表只保存事件轨迹快照，不作为 AIS 原始轨迹长期归档。

### 11.3 后续建议扩字段

事件摘要表可考虑新增：

- `avg_distance_nm`
- `avg_relative_speed`
- `heading_parallel_ratio`
- `target_ship_type`
- `target_flag`
- `target_risk_level`
- `area_type`
- `distance_to_coast_km`
- `night_ratio`
- `related_loitering_event_id`
- `insurance_score_version`

事件明细表可考虑新增：

- `paired_point_time`
- `time_gap_seconds`
- `distance_nm`
- `relative_speed`
- `own_heading`
- `target_heading`
- `heading_diff`
- `match_confidence`

年度表建议新增：

```sql
ship_behavior_alongside_score_yearly
```

## 12. 配置项

配置位置：

- [application.properties](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/resources/application.properties:183)

当前默认配置：

```properties
ship.behavior.alongside.rule-version=ALONGSIDE_V2_INSURANCE
ship.behavior.alongside.mode.auto.enabled=true
ship.behavior.alongside.batch.parallelism=1
ship.behavior.alongside.batch.progress-interval=50
ship.behavior.alongside.auto.enabled=true
ship.behavior.alongside.auto.candidate-provider=SPATIAL_AIS_REPLAY

ship.behavior.alongside.max-distance-nm=0.1
ship.behavior.alongside.max-speed=2.0
ship.behavior.alongside.max-time-gap-minutes=10
ship.behavior.alongside.min-duration-minutes=20
ship.behavior.alongside.min-points=3

ship.behavior.alongside.replay.enabled=true
ship.behavior.alongside.replay.url=https://gm.hifleet.com/spatialais/getRecentAis
ship.behavior.alongside.replay.dev-size=50000
ship.behavior.alongside.replay.connect-timeout-ms=3000
ship.behavior.alongside.replay.read-timeout-ms=8000
ship.behavior.alongside.replay.retry-times=1
ship.behavior.alongside.replay.retry-sleep-ms=500
ship.behavior.alongside.replay.concurrent-requests=2
ship.behavior.alongside.replay.window-minutes=30
ship.behavior.alongside.replay.min-window-minutes=10
ship.behavior.alongside.replay.max-window-minutes=60
ship.behavior.alongside.replay.split-on-truncated=true
ship.behavior.alongside.replay.large-result-threshold=30000
ship.behavior.alongside.replay.query-mode=BBOX
ship.behavior.alongside.replay.default-radius-nm=0.5
ship.behavior.alongside.replay.max-radius-nm=3.0
ship.behavior.alongside.replay.bbox-buffer-nm=0.5

ship.behavior.alongside.candidate.max-distance-nm=0.5
ship.behavior.alongside.candidate.max-speed=4.0
ship.behavior.alongside.candidate.max-time-gap-minutes=10
ship.behavior.alongside.candidate.time-bucket-minutes=5
ship.behavior.alongside.candidate.min-points=3
ship.behavior.alongside.candidate.min-duration-minutes=20
ship.behavior.alongside.candidate.split-gap-minutes=30

ship.behavior.alongside.confirm.max-distance-nm=0.15
ship.behavior.alongside.confirm.max-speed=2.0
ship.behavior.alongside.confirm.max-relative-speed=1.0
ship.behavior.alongside.confirm.max-time-gap-minutes=10
ship.behavior.alongside.confirm.high-confidence-time-gap-minutes=5
ship.behavior.alongside.confirm.min-duration-minutes=30
ship.behavior.alongside.confirm.min-points=6

ship.behavior.alongside.heading.enabled=true
ship.behavior.alongside.heading.parallel-threshold-degree=30

ship.behavior.alongside.merge.enabled=true
ship.behavior.alongside.merge.max-gap-minutes=30
ship.behavior.alongside.merge.max-center-distance-nm=1.0

ship.behavior.alongside.score.weight=0.20
ship.behavior.alongside.severity-cap.no-scene-data.max-level=L2
ship.behavior.alongside.scene-data.available=false
ship.behavior.alongside.counterparty-risk.available=false
ship.behavior.alongside.auto-l4-enabled=false
```

## 13. 日志说明

### 13.1 PAIR 模式

启动日志：

```text
ALONGSIDE_V2 start mode=PAIR mmsi=xxx targetMmsi=yyy startTime=... endTime=... batchNo=...
```

事件日志：

```text
ALONGSIDE_V2 event[1] type=ALONGSIDE severity=L2 confidence=HIGH start=... end=... duration=... minDistanceNm=... avgDistanceNm=... maxDistanceNm=... avgRelativeSpeed=... avgSpeed=... pointCount=... exclusion=... score=... targetMmsi=... batchNo=...
```

汇总日志：

```text
ALONGSIDE_V2 summary mode=PAIR mmsi=xxx targetMmsi=yyy ownPoints=... targetPoints=... rawEvents=... mergedEvents=... savedEvents=... batchNo=...
```

### 13.2 AUTO 模式

启动日志：

```text
ALONGSIDE_V2 start mode=AUTO mmsi=xxx startTime=... endTime=... batchNo=...
```

低速窗口：

```text
ALONGSIDE_V2 low_speed_summary mmsi=xxx points=... cleanPoints=... lowSpeedWindows=... batchNo=...
```

区域回放查询：

```text
ALONGSIDE_V2 replay_query[1] start=... end=... minlon=... maxlon=... minlat=... maxlat=... returnedVessels=... rawPoints=... validPoints=... parseErrors=... elapsedMs=... truncated=false success=true batchNo=...
```

候选统计：

```text
ALONGSIDE_V2 candidate_summary mmsi=xxx replayQueries=... replayFailures=... truncatedQueries=... returnedVessels=... rawPoints=... validPoints=... parseErrors=... prefilteredTargets=... candidateTargets=... candidatePairs=... batchNo=...
```

年度指标：

```text
ALONGSIDE_V2 annual_score mmsi=xxx eventCount=... validEventCount=... highRiskEventCount=... severeEventCount=... ARI=... subScore10=... weightedScore=... manualReview=... batchNo=...
```

汇总日志：

```text
ALONGSIDE_V2 summary mode=AUTO mmsi=xxx points=... lowSpeedWindows=... candidateTargets=... rawEvents=... mergedEvents=... savedEvents=... failed=... replayQueries=... replayElapsedMs=... batchNo=...
```

### 13.3 批量 AUTO 模式

启动日志：

```text
ALONGSIDE_BATCH start batchNo=... ships=... windows=... totalTasks=... pendingTasks=... skipped=... sliceMode=... parallelism=... resume=...
```

单窗口日志：

```text
ALONGSIDE_BATCH ship_window mmsi=... startTime=... endTime=... status=DONE points=... lowSpeedWindows=... candidateTargets=... replayQueries=... replayFailures=... events=... batchNo=...
```

进度日志：

```text
ALONGSIDE_BATCH progress processed=... failed=... totalTasks=... events=... elapsedMs=...
```

汇总日志：

```text
ALONGSIDE_BATCH summary batchNo=... ships=... windows=... totalTasks=... processed=... skipped=... failed=... events=... doneStatus=... failedStatus=... elapsedMs=...
```

## 14. 验证方式

### 14.1 PAIR 回归参数

```json
{
  "mmsi": "413433610",
  "targetMmsi": "413193000",
  "startTime": "2026-06-28 18:00:00",
  "endTime": "2026-06-30 09:40:00",
  "batchNo": "alongside-test-20260629-01"
}
```

验收：

- 任务成功
- 能读取主船轨迹
- 能读取目标船轨迹
- 输出 `ALONGSIDE_V2 start`
- 输出 `ALONGSIDE_V2 event`
- 输出 `ALONGSIDE_V2 summary mode=PAIR`
- 写入 `ship_behavior_event`
- 写入 `ship_behavior_event_detail`
- 明细 `point_role=OWN/TARGET` 正确

### 14.2 AUTO 入口参数

```json
{
  "mmsi": "413433610",
  "startTime": "2026-06-28 18:00:00",
  "endTime": "2026-06-30 09:40:00",
  "batchNo": "alongside-auto-test-20260630-01",
  "mode": "AUTO"
}
```

当前验收：

- 不传 `targetMmsi` 不报错
- 能读取主船轨迹
- 能提取低速窗口
- 能输出 `replay_query`
- 能调用 `getRecentAis`
- 能解析返回的 MMSI 分组和 AIS 点
- 输出 `candidate_summary`
- 如果存在候选，能调用 detector 并落库
- 输出 `annual_score`
- 输出 `summary mode=AUTO`

注意：如果 replay 接口返回空结果或窗口内没有满足预过滤规则的目标船，`candidateTargets=0` 仍是正常结果；但它不再是固定值。

### 14.3 批量 AUTO 入口参数

```json
{
  "mmsiList": ["414060000", "356470000"],
  "startTime": "2025-01-01 00:00:00",
  "endTime": "2025-02-01 23:59:59",
  "sliceMode": "WEEK",
  "batchNo": "alongside-batch-smoke-202501-v1",
  "resume": true,
  "parallelism": 1,
  "mode": "AUTO"
}
```

当前验收：

- XXL-Job 使用 `shipAlongsideBatchJobHandler`
- 能按 `mmsiList` 或 `mmsi` 构造船舶列表
- 不传船舶时能复用 `ais41.recentships`
- 能按 `DAY/WEEK/MONTH/CUSTOM_DAYS` 生成时间切片
- `resume=true` 时跳过状态为 `DONE` 的窗口
- 每个窗口复用单船 AUTO 候选召回和 detector
- 写入 `ship_behavior_event` 和 `ship_behavior_event_detail`
- 写入或更新 `ship_behavior_alongside_batch_status`
- 输出 `ALONGSIDE_BATCH start / ship_window / progress / summary`

### 14.4 单元测试

测试类：

- [ShipAlongsideDetectorTest.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/test/java/com/hifleet/service/ShipAlongsideDetectorTest.java:1)

覆盖：

- V2 事件评分和无场景数据封顶
- 锚泊/系泊场景降权
- 时间差过大拒绝
- 短暂会遇不成正式搭靠
- 相邻事件合并
- AUTO 低速窗口提取
- replay AIS 字符串解析容错
- replay 候选目标船预过滤

由于 `pom.xml` 中 surefire 配置了 `<skip>true</skip>`，普通 `mvn test` 会跳过测试。当前可用以下命令直接执行新增测试：

```bash
mvn -DskipTests compile test-compile
mvn -q dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt
java -cp "target/classes:target/test-classes:$(cat target/test-classpath.txt)" org.junit.runner.JUnitCore com.hifleet.service.ShipAlongsideDetectorTest com.hifleet.service.ReplayAisPointParserTest com.hifleet.service.ShipAlongsideReplayCandidateProviderTest
```

期望输出：

```text
OK (9 tests)
```

## 15. 推荐查询 SQL

事件摘要：

```sql
SELECT
  id,
  behavior_type,
  mmsi,
  target_mmsi,
  start_time,
  end_time,
  duration_minutes,
  event_sub_type,
  severity_level,
  event_score,
  confidence_level,
  exclusion_flag,
  exclusion_reason,
  ais_gap_count,
  max_ais_gap_minutes,
  track_coverage_ratio,
  score_detail_json,
  batch_no
FROM dataanalysis.ship_behavior_event
WHERE batch_no = 'alongside-test-20260629-01'
  AND behavior_type = 'ALONGSIDE'
ORDER BY start_time;
```

事件明细：

```sql
SELECT
  behavior_type,
  mmsi,
  target_mmsi,
  point_role,
  event_start_time,
  event_end_time,
  point_time,
  point_index,
  lon,
  lat,
  speed,
  course,
  nav_status,
  batch_no,
  rule_version
FROM dataanalysis.ship_behavior_event_detail
WHERE batch_no = 'alongside-test-20260629-01'
  AND behavior_type = 'ALONGSIDE'
ORDER BY event_start_time, point_role, point_index;
```

清理测试批次：

```sql
DELETE FROM dataanalysis.ship_behavior_event_detail
WHERE batch_no = 'alongside-test-20260629-01';

DELETE FROM dataanalysis.ship_behavior_event
WHERE batch_no = 'alongside-test-20260629-01';
```

## 16. 后续扩展计划

### P0：V2 事件识别继续打磨

当前已完成基础版本。后续可补：

- 明细表保存成对点的 `time_gap_seconds`
- 明细表保存 `distance_nm`
- 明细表保存 `relative_speed`
- 明细表保存 `heading_diff`
- 船长 LOA 动态距离阈值
- COG + SOG 向量相对速度

### P1：AUTO 候选目标船搜索

建议新增：

- `ShipAlongsideCandidateSearchService`
- `AlongsideCandidateTarget`
- 历史 AIS 时间桶 + 空间邻近查询 Repository

推荐流程：

```text
主船低速窗口
-> 按 5/10 分钟时间桶抽样
-> 半径 0.3-0.5nm 空间搜索
-> 目标船 SOG <= 4kn
-> 排除同 MMSI / 同 IMO / 身份漂移
-> 聚合成 candidate target
-> 复用 PAIR detector
```

不要直接对全量船舶做两两匹配。

### P2：年度评分落库

建议新增：

- `ShipAlongsideAnnualScoreService`
- `ShipAlongsideAnnualScoreRepository`
- `ship_behavior_alongside_score_yearly`

字段建议：

- `mmsi`
- `stat_start_time`
- `stat_end_time`
- `event_count`
- `valid_event_count`
- `high_risk_event_count`
- `severe_event_count`
- `unique_target_count`
- `risky_target_count`
- `total_duration_minutes`
- `max_duration_minutes`
- `ais_gap_related_count`
- `loitering_related_count`
- `night_event_ratio`
- `max_event_score`
- `annual_alongside_risk_index`
- `sub_score_10`
- `weighted_score`
- `manual_review_required`
- `score_detail_json`

### P3：外部数据增强

建议逐步接入：

- 港口泊位
- 官方锚地
- STS 热点
- 制裁敏感区
- 战区 / 海盗区
- 目标船船型
- 目标船船旗
- PSC 滞留
- 事故记录
- 制裁记录
- 影子船队
- 管理公司评级
- 报备 STS / 补给 / 救助作业

接入后可开放更高等级：

- 港区正规作业：`EXCLUDED_NORMAL`
- 官方锚地：`DOWNGRADED_NORMAL_LIKELY`
- 合法 STS 区且有报备：降权
- 高风险 STS 热点：`sceneRiskScore` 增强
- 制裁敏感区 + 高风险对象 + AIS gap：允许升 L3/L4

### P4：事件链联动

建议关联：

- 搭靠前 6 小时徘徊
- 搭靠前后 AIS gap
- 搭靠期间异常停泊
- 搭靠后异常改航

联动原则：

- 搭靠作为主事件
- 徘徊、停泊、AIS gap 作为辅助证据
- 年度评分避免重复线性扣分

## 17. 禁止事项

- 不要把所有近距离低速事件都判为异常
- 不要把当前候选事件直接作为最终保险评分
- 不要在 detector 中直接写库
- 不要让 detector 依赖 HBase 原始对象
- 不要直接做全量两两船舶匹配
- 不要删除 PAIR 模式
- 不要破坏徘徊模块已有链路
- 不要把低置信事件升级为高风险
- 不要在没有外部证据时输出 L4
- 不要把明细表当作长期 AIS 原始轨迹归档表
