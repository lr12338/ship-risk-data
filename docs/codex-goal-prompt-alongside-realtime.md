# Codex Goal Prompt: 船舶搭靠实时化改造

以下内容可直接复制到新的 Codex 对话中使用。

建议先在 Codex 中打开工作目录：

```text
/Users/raymondlu/LocalProject/船舶风险评估数据服务/flink服务参考
```

然后先创建 goal，再发送下面的完整任务提示词。

## Goal Objective

```text
在 /Users/raymondlu/LocalProject/船舶风险评估数据服务/flink服务参考 项目中，基于现有停船点 Flink 实时作业，参考 /Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data 中已有的船舶搭靠离线实现，新增“船舶搭靠实时识别”方案的落地实现与验证路径，要求先完成本地可验证测试，再给出服务器灰度运行方案，并且不得影响现有停船点服务功能、输出语义和现有 topic 消费链路。
```

## Task Prompt

````text
请在 Codex goal 模式下执行以下任务。

先创建 goal，并以此为唯一目标推进：

objective:
在 /Users/raymondlu/LocalProject/船舶风险评估数据服务/flink服务参考 项目中，基于现有停船点 Flink 实时作业，参考 /Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data 中已有的船舶搭靠离线实现，新增“船舶搭靠实时识别”方案的落地实现与验证路径，要求先完成本地可验证测试，再给出服务器灰度运行方案，并且不得影响现有停船点服务功能、输出语义和现有 topic 消费链路。

工作目录：
/Users/raymondlu/LocalProject/船舶风险评估数据服务/flink服务参考

任务背景：
当前已有一个可运行的 Flink 实时项目，主链路是：
Kafka LIVE_RECENTSHIPS_DATA
-> AisMessageParser
-> ValidAisPointFilter
-> watermark
-> keyBy(mmsi)
-> FlinkKeyedPreprocessFunction
-> CleanAisPoint
-> StopPointDetectorProcessFunction
-> Kafka RELIABLE_STOP_POINTS

现有停船点服务已经可运行，不能因为新增搭靠识别而被破坏。

ship-risk-data 项目路径：
/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data

新 agent 没有读过 ship-risk-data，必须先理解该项目中已经完成的船舶搭靠离线实现。当前 ship-risk-data 的搭靠规则版本为：
ALONGSIDE_V2_INSURANCE

该实现的业务定位：
- 输出“搭靠候选事件识别 + 轻量保险化评分 + 人工复核依据”
- 不是最终保险评分口径
- 不是核保一票否决结论
- 不应把所有近距离低速事件直接判为异常搭靠

ship-risk-data 中搭靠实现的核心入口和链路：
1. XXL-Job 单船入口：
   - ShipBehaviorJob.shipAlongsideJobHandler
2. XXL-Job 批量入口：
   - ShipBehaviorJob.shipAlongsideBatchJobHandler
3. 业务服务：
   - ShipBehaviorAnalysisService.analysisAlongside
   - analysisAlongsidePair
   - analysisAlongsideAuto
   - analysisAlongsideBatch
4. 轨迹读取：
   - TrajectoryLoaderService.load
   - TrajectoryLoaderService.loadSafely
5. AUTO 候选召回：
   - ShipAlongsideDetector.extractLowSpeedWindows
   - ShipAlongsideReplayCandidateProvider.findCandidates
   - SpatialAisReplayClient.query
   - ReplayAisPointParser.parse
6. 双船确认和评分：
   - ShipAlongsideDetector.detectWithTrace
7. 落库：
   - ShipBehaviorEventRepository.saveEvents
   - ShipBehaviorEventDetailRepository.saveDetails
   - dataanalysis.ship_behavior_event
   - dataanalysis.ship_behavior_event_detail

现有搭靠识别有两种模式：
   - PAIR：已知两船，做双船复核
   - AUTO：输入单船，先找低速窗口，再召回候选目标船

PAIR 模式链路：
shipAlongsideJobHandler
-> ShipBehaviorAnalysisService.analysisAlongside
-> analysisAlongsidePair
-> TrajectoryLoaderService.load(主船)
-> TrajectoryLoaderService.load(目标船)
-> ShipAlongsideDetector.detectWithTrace
-> ShipBehaviorEventRepository.saveEvents
-> ShipBehaviorEventDetailRepository.saveDetails

AUTO 模式链路：
shipAlongsideJobHandler
-> analysisAlongsideAuto
-> 读取主船 AIS 轨迹
-> 清洗和排序主船轨迹
-> 提取主船低速窗口
-> 按低速窗口构造 bbox replay 查询
-> 调用 getRecentAis
-> ReplayAisPointParser 解析返回 AIS 点
-> ShipAlongsideReplayCandidateProvider 预过滤候选目标船
-> ShipAlongsideDetector.detectWithTrace
-> 事件合并
-> 评分与等级
-> 落库

当前离线版本依赖：
   - HBase 历史轨迹读取
   - getRecentAis 候选目标船召回
   - XXL-Job 手工触发

这些运行方式不能直接搬到 Flink。Flink 实时链路中禁止继续查 HBase，也禁止继续调 getRecentAis。

可以复用的是规则层，必须重构的是：
   - 实时输入方式
   - 候选召回方式
   - pair 状态管理方式
   - 实时输出方式

需要迁移或对齐的离线规则口径：
- 候选外圈：
  - candidate.max-distance-nm=0.5
  - candidate.max-speed=4.0
  - candidate.max-time-gap-minutes=10
  - candidate.min-points=3
  - candidate.min-duration-minutes=20
- 正式确认：
  - confirm.max-distance-nm=0.15
  - confirm.max-speed=2.0
  - confirm.max-relative-speed=1.0
  - confirm.max-time-gap-minutes=10
  - confirm.high-confidence-time-gap-minutes=5
  - confirm.min-duration-minutes=30
  - confirm.min-points=6
- 航向并靠：
  - headingDiff = abs(courseA - courseB)
  - headingDiff = min(headingDiff, 360 - headingDiff)
  - parallelDiff = min(headingDiff, abs(headingDiff - 180))
  - parallelDiff <= 30
- 事件分层：
  - 弱候选：duration >= 20 分钟 && pointCount >= 3，event_sub_type=WEAK_ALONGSIDE_CANDIDATE
  - 正式候选：duration >= 30 分钟 && pointCount >= 6，event_sub_type=ALONGSIDE_CONFIRMED_CANDIDATE
- 正常场景轻量降权：
  - 主船或目标船 nav_status 命中 ANCHOR / MOORED / BERTH 或状态值 1 / 5
  - exclusion_flag=DOWNGRADED_NORMAL_LIKELY
  - exclusion_reason=NAV_STATUS_ANCHORED_OR_MOORED
  - event_score * 0.5
- 无外部场景和对象画像时：
  - severity-cap.no-scene-data.max-level=L2
  - auto-l4-enabled=false
  - 不输出 L4

Flink 实时化时不要照搬离线运行方式：
- 不要使用 TrajectoryLoaderService 在实时链路回读历史 HBase
- 不要使用 SpatialAisReplayClient / getRecentAis 做实时召回
- 不要把 XXL-Job 的 batchNo / 时间切片模型搬进实时状态机
- 不要把明细表当成 AIS 原始轨迹归档

Kafka 配置如下：
kafka.bootstrap.servers=192.168.99.92:9092
kafka.group.id=alongside-group
kafka.topic=LIVE_RECENTSHIPS_DATA

你必须遵守的关键约束：
1. 不允许破坏现有停船点服务。
2. 不允许修改现有 RELIABLE_STOP_POINTS 的消息语义。
3. 不允许把搭靠结果写进现有停船点 topic。
4. 不允许让搭靠逻辑直接侵入 StopPointDetectorProcessFunction 的核心状态机。
5. 不允许继续在 Flink 实时链路里依赖 HBase 或 getRecentAis。
6. 必须优先复用现有 AisMessageParser、AisPoint、CleanAisPoint、watermark、预处理链路。
7. 必须采用“新增搭靠分支”的实现方式，而不是“替换现有停船点链路”的方式。
8. 先做本地测试与历史回放验证，再给出服务器运行方案。
9. 在真正修改代码前，必须先阅读并理解以下内容：
   - /Users/raymondlu/LocalProject/船舶风险评估数据服务/flink服务参考/src/main/java/com/hifleet/analysis/portcall/flink/FlinkPortCallJob.java
   - /Users/raymondlu/LocalProject/船舶风险评估数据服务/flink服务参考/src/main/java/com/hifleet/analysis/portcall/consumer/AisMessageParser.java
   - /Users/raymondlu/LocalProject/船舶风险评估数据服务/flink服务参考/src/main/java/com/hifleet/analysis/portcall/model/AisPoint.java
   - /Users/raymondlu/LocalProject/船舶风险评估数据服务/flink服务参考/src/main/java/com/hifleet/analysis/portcall/model/CleanAisPoint.java
   - /Users/raymondlu/LocalProject/船舶风险评估数据服务/flink服务参考/src/main/java/com/hifleet/analysis/portcall/flink/StopPointDetectorProcessFunction.java
   - /Users/raymondlu/LocalProject/船舶风险评估数据服务/flink服务参考/src/main/resources/application.properties
   - /Users/raymondlu/LocalProject/船舶风险评估数据服务/flink服务参考/docs/current-version-summary.md
   - /Users/raymondlu/LocalProject/船舶风险评估数据服务/flink服务参考/程序实现报告-2026-06-27.md
   - /Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/docs/船舶搭靠事件计算模块-开发文档.md
   - /Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/docs/ship-alongside-development.md
   - /Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/xxljob/ShipBehaviorJob.java
   - /Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/job/services/ShipBehaviorAnalysisService.java
   - /Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/service/ShipAlongsideDetector.java
   - /Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/service/ShipAlongsideReplayCandidateProvider.java
   - /Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/service/SpatialAisReplayClient.java
   - /Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/service/ReplayAisPointParser.java
   - /Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/resources/application.properties
   - /Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/resources/sql/ship_behavior_event.sql
   - /Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/resources/sql/ship_behavior_event_detail.sql

目标边界：
一期只做实时 AUTO 搭靠发现，不做实时 PAIR。
一期只做分钟级搭靠候选事件识别，不做最终保险评分。
一期只做新 topic 输出，不直接替换现有数据库落库链路。
一期不做年度 ARI。
一期不做港口、STS、制裁区、画像等外部数据联动。

推荐实现方向：
1. 在 CleanAisPoint 之后新增 alongside 分支。
2. 新增一条实时搭靠链路：
   CleanAisPoint
   -> 低速点过滤
   -> 空间桶/邻桶分发
   -> 候选目标船近邻召回
   -> pair 证据流
   -> pair 聚合确认
   -> Kafka 新 topic 输出
3. 保持现有停船点分支不变。
4. 推荐新 topic 名称使用独立 alongside 事件 topic，例如 RELIABLE_ALONGSIDE_EVENTS。
5. 搭靠分支中禁止继续查 HBase、禁止继续调 getRecentAis。

你需要完成的工作分成 4 个阶段：

阶段 1：环境与基线确认
- 阅读现有 Flink 主链路和停船点状态机。
- 确认 LIVE_RECENTSHIPS_DATA 的消息格式与 AisMessageParser 的兼容性。
- 明确当前停船点链路的输入、输出、状态、checkpoint 和 topic 依赖。
- 输出一份“现有链路基线说明”，明确哪些类和 topic 绝不能破坏。

阶段 2：搭靠分支设计与实现
- 设计并实现独立的搭靠实时分支，不破坏停船点主分支。
- 明确中间模型和输出模型，避免复用 StopPointEvent。
- 推荐新增如下模块，除非你确认有更小改动方案且不会增加耦合：
  - AlongsideConfig
  - PairProximityEvidence
  - AlongsideEvent
  - SpatialBucketAssigner
  - SpatialCandidateProcessFunction
  - AlongsidePairDetectorProcessFunction
  - AlongsideJsonMapFunction
  - AlongsideKafkaSerializationSchema
- 保证 pairId 规则固定，避免 A-B / B-A 双写。
- 搭靠规则应尽量对齐现有离线规则：
  - 候选外圈：速度 <= 4 kn，时间差 <= 10 分钟，距离 <= 0.5 海里
  - 正式确认：距离 <= 0.15 海里，双方速度 <= 2 kn，相对速度 <= 1 kn，parallelDiff <= 30°
  - 弱候选：duration >= 20 分钟 && pointCount >= 3
  - 正式候选：duration >= 30 分钟 && pointCount >= 6

阶段 3：本地测试与回归验证
- 先做单元测试和小范围本地流式测试。
- 尽量复用参考工程的 archive/replay 工具做历史样本回放。
- 选已有离线验证样本做对比，例如 413433610 / 413193000 一类样本。
- 必须验证：
  1. 搭靠分支可以正常产出事件
  2. 停船点原有输出保持一致
  3. 新增分支不会导致现有主链路结果变化
  4. 不会双写同一 pair 对称事件
- 如果本地不能完成某项验证，要明确写出原因和替代验证方式。

阶段 4：服务器灰度运行方案
- 不要直接替换现有停船点生产作业。
- 先给出“独立作业灰度运行”的建议方案。
- 输出服务器部署建议时，要明确：
  - 是否建议单独 jar
  - 是否建议单独 consumer group
  - 是否建议单独 output topic
  - 如何避免影响现有 RELIABLE_STOP_POINTS
  - 如何观察新作业稳定性
  - 如何回滚
- 必须说明为什么“先独立灰度，再决定是否并入现有停船点作业”更安全。

最终交付物要求：
1. 代码实现或最小可运行骨架
2. 新增/修改文件说明
3. 本地测试步骤
4. 测试结果摘要
5. 对现有停船点服务“无影响”的验证结论
6. 服务器灰度运行建议
7. 风险与后续建议

验收标准：
1. 能说明现有停船点链路未被破坏。
2. 新增搭靠识别走独立分支和独立 topic。
3. 本地能完成至少一种回放或模拟验证。
4. 停船点原有输出语义不变。
5. 有清晰的服务器灰度运行方案，而不是直接建议替换现有作业。
6. 最终总结中必须清楚写明：
   - 哪些已经完成
   - 哪些还需服务器验证
   - 哪些风险仍未消除

工作方式要求：
- 先探索、再实现，先给基线判断。
- 在动代码前先输出简短实施计划。
- 所有重要假设都要显式写出。
- 遇到会影响现有停船点服务的实现路径时，必须暂停并改走更隔离的方案。
- 除非必要，不要做大规模重构。
- 优先做外科手术式修改。
- 每完成一个阶段，都给出阶段性结论与下一步。
````

## 使用建议

建议在新的 Codex 线程中：

1. 打开目录 `/Users/raymondlu/LocalProject/船舶风险评估数据服务/flink服务参考`
2. 创建 goal，`objective` 直接使用上面的 `Goal Objective`
3. 将 `Task Prompt` 整段作为第一条消息发送

如果希望更稳妥，可再补一句人工说明：

```text
优先保证停船点链路零回归，再追求搭靠功能完整度。
```

## 默认约束

- 默认在 Flink 项目路径工作，不在 `ship-risk-data` 仓库直接实现
- 默认是 goal 模式执行型任务
- 默认先本地测试，再服务器灰度
- 默认新增分支、独立 topic、独立事件模型
- 默认不能影响现有停船点功能
