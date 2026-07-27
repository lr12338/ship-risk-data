# Loitering V3 测试与指标验证报告

## 1. 报告状态

- 日期：2026-07-27
- V3 状态：Analyzer、纯业务内核和 HBase 轨迹适配器已实现；场景/船型 Provider、定时入口与落库尚未实现
- 当前报告性质：V2/搭靠基线验证 + V3 核心验证 + 后续验收方案
- 生产事件质量结论：证据不足，不能宣称达标
- 规则文档：`docs/ship-loitering-v3-design.md`

本报告严格区分：

1. 当前代码已经被测试证明的行为。
2. 设计中要求未来测试证明的行为。
3. 需要真实 AIS、港口场景和人工标签才能证明的事件质量。

## 2. 当前验证基线

### 2.1 已执行测试

通过 JUnit 4 runner 显式执行：

```text
LoiteringV3RuleEngineTest             18
LoiteringV3EpisodeBuilderTest          7
LoiteringV3EventReconcilerTest         8
LoiteringV3QualityServiceTest          5
LoiteringV3AnalyzerServiceTest         2
LoiteringV3HBaseTrajectorySourceTest   1
ShipLoiteringDetectorTest             14
ShipAlongsideDetectorTest             13
ShipAlongsideReplayCandidateProviderTest 1
ShipTypeClassifierTest                 2
TrajectoryLoaderServiceTest            2
ShipBehaviorTableInitializerTest       8
本轮核心与隔离回归合计                81
```

结果：

```text
OK (81 tests)
```

以上证明：

- V3 圆形、环绕、蛇形、往返、等待漂移、直线通行、正常靠泊、正常进泊、
  无停留进泊、场景缺失、商船过滤、低速门槛、重复点、冲突时刻、静止点和
  日期变更线轨迹符合新增测试。
- V3 5 分钟就近重采样、4 点/15 分钟开始、短暂越界保持、超过 15 分钟分段、
  30 分钟 AIS gap 和正常靠泊结束边界符合新增测试。
- V3 重叠率、30 分钟相邻事件、1 海里中心距离、规则版本隔离、稳定事件键、
  最佳匹配和无效历史坐标处理符合新增测试。
- V3 日内唯一事件计数、FINAL 时长统计、区域分布、TOP 风险排序去重和轨迹/
  航速案例数据保留符合新增测试。
- V3 `[T-24h,T]` 编排窗口、标准输出字段和单船失败隔离符合新增测试。
- V3 HBase 轨迹适配器固定走未抽稀、分片、限量读取符合新增测试。
- 当前 V2 典型慢速、机动、gap、置信度、合并和弱候选行为符合现有测试。
- 当前搭靠 detector 的 13 个单元测试通过。
- 当前搭靠回放候选的 1 个单元测试通过。
- 当前船型分类器的 2 个单元测试通过。
- 当前轨迹加载的 2 个单元测试通过。
- 当前行为事件表初始化/迁移的 8 个单元测试通过。

以上不能证明：

- V2 或 V3 对真实事件的准确率、召回率。
- 真实港口/泊位数据映射正确。
- 每小时重复扫描幂等。
- V3 定时服务和数据库性能。

### 2.2 Maven 测试风险

`pom.xml` 的 Surefire 配置写有：

```xml
<skip>true</skip>
```

实测：

```text
mvn -DskipTests=false test
```

仍输出：

```text
Tests are skipped.
```

当前部署打包脚本还使用：

```text
mvn clean package -DskipTests
```

因此当前 Maven 构建成功不能作为测试通过证据。
V3 实现阶段必须把“默认执行单元测试、仅显式打包场景允许跳过”作为 P0 验收项。

### 2.3 当前真实样本证据

已有 V2 文档记录一个服务器样例：

```text
mmsi=413193000
subType=SLOW_LOITERING
duration=533 min
radius=0.173895 nm
avgSpeed=0.2077 kn
pointCount=13
maxGap=114 min
confidence=MEDIUM
exclusion=DOWNGRADED_NORMAL_LIKELY
reason=NAV_STATUS_ANCHORED_OR_MOORED
score=24
severity=L1
```

该样例只能证明一条候选链路能够执行和落库；
它属于正常锚泊/系泊可能性较高的事件，不能证明异常徘徊识别质量。

本次审计没有查询生产数据库，也没有获得正式人工标注集。

## 3. V3 测试策略

按以下顺序执行：

1. 先建立会失败的业务验收 fixture。
2. 实现纯特征提取并通过数学单元测试。
3. 实现规则分类和场景排除。
4. 实现事件状态机和幂等落库。
5. 实现定时服务集成测试。
6. 执行搭靠全量回归。
7. shadow 运行并建立人工标签。
8. 通过质量门槛后才允许切正式版本。

## 4. 单元测试案例

### 4.1 轨迹数学

| ID | 输入 | 预期 |
|---|---|---|
| F01 | 标准圆轨迹 | `closure` 高、首尾距离小、累计转向约 360° |
| F02 | 未闭合 300° 圆弧 | ORBITAL 特征成立，CIRCULAR 不成立 |
| F03 | 直线等间距轨迹 | 方向效率接近 1，闭合率接近 0 |
| F04 | 蛇形轨迹 | 转向符号切换次数和方向变化达标 |
| F05 | 三次往返 | reversal count 为 3 |
| F06 | 单个远端漂移点 | P95 半径稳定，max radius 反映异常点 |
| F07 | 350° 到 10° | 环形方向差为 20° |
| F08 | COG 缺失 | 使用坐标派生方向，不制造 0° 转向 |
| F09 | 少于 3 点 | 面积为 0，不能判 CIRCULAR |
| F10 | 同点静止 | 路径长度为 0，公式不产生 NaN/Infinity |

### 4.2 候选状态机

| ID | 输入 | 预期 |
|---|---|---|
| S01 | 3 个连续低速点 | 候选开始 |
| S02 | 低速不足 15 分钟 | 不开始 |
| S03 | 10 分钟速度越界 | 保持同一 episode |
| S04 | 速度恢复超过 30 分钟 | 事件结束于最后合格点 |
| S05 | AIS gap 31 分钟 | 切成两个 episode |
| S06 | 离开港口 10 分钟后返回 | 保持 |
| S07 | 离开港口 35 分钟 | 结束 |
| S08 | 进入泊位并停留 30 分钟 | 结束并排除为正常靠泊 |

### 4.3 运动模式

| ID | 场景 | 预期 |
|---|---|---|
| P01 | 完整圆形、2 节、港外 | CIRCULAR |
| P02 | 连续同向环绕、未完全闭合 | ORBITAL |
| P03 | 蛇形、方向反复变化 | SERPENTINE |
| P04 | 小范围往返 3 次 | BACK_AND_FORTH |
| P05 | 港外 1.5 节漂移 6 小时 | WAITING_DRIFT |
| P06 | 直线 2 节进港 | STRAIGHT_LOW_SPEED_TRANSIT |
| P07 | 低速但无形态、90 分钟 | REVIEW，不是 FINAL |
| P08 | 圆形但半径 8 海里 | 不满足正式空间门槛 |

### 4.4 场景排除

| ID | 场景 | 预期 |
|---|---|---|
| C01 | 泊位 polygon 内、静止 40 分钟 | NORMAL_BERTHING |
| C02 | 靠泊点 0.1 海里、静止 40 分钟 | NORMAL_BERTHING |
| C03 | 港外等待后 1 小时内正常入泊 | NORMAL_BERTHING_APPROACH |
| C04 | 港界内但未进泊位且蛇形 | 正式徘徊候选 |
| C05 | 港界外 8 海里圆形 | near_port=true，允许分析 |
| C06 | 港界外 12 海里且不近航道 | 不生成候选 |
| C07 | 场景数据加载失败 | REVIEW，不发布 FINAL |
| C08 | 官方锚地 | 按最终业务口径测试排除或降权 |

### 4.5 船型

| ID | 船型 | 预期 |
|---|---|---|
| T01 | 集装箱船 | eligible |
| T02 | 油轮/油船 | eligible |
| T03 | 散货船 | eligible |
| T04 | LNG/LPG/液化气船 | eligible |
| T05 | 普通货船/杂货船 | eligible |
| T06 | 渔船 | filtered |
| T07 | 工程船 | filtered |
| T08 | 拖轮 | filtered |
| T09 | 游艇/小艇 | filtered |
| T10 | 空或未知船型 | skipped + metric |

### 4.6 评分

| ID | 场景 | 预期 |
|---|---|---|
| R01 | 同一轨迹，不同时长 | 更长事件风险分不低于短事件 |
| R02 | 同一模式，不同半径 | 更小范围得分不低于大范围 |
| R03 | LOW confidence | 不发布 FINAL |
| R04 | NORMAL_BERTHING | risk score 为 0 |
| R05 | 分项总和超过 100 | 最终不超过 100 |
| R06 | score detail | 每个分项和 reason code 可解释 |

## 5. 集成测试

### 5.1 数据库

1. 同一 `event_key` 重复保存，只保留一条摘要。
2. OPEN 更新为 FINAL 时主键和 event_key 不变。
3. 明细重复保存不产生重复点。
4. 摘要成功、明细失败时整体回滚。
5. 特征成功、运行状态失败时整体回滚。
6. V2 和搭靠旧 INSERT 不提供 V3 字段时仍成功。
7. NULL 新字段不改变旧事件查询。

### 5.2 小时扫描

1. 连续两小时扫描相同 24 小时窗口，事件数不翻倍。
2. 第二次扫描增加新点，原 OPEN 事件扩展。
3. 跨午夜的同一事件保持相同 event_key。
4. 延迟 20 分钟到达的 AIS 被 watermark 吸收。
5. 延迟超过 watermark 的点触发显式修订而不是新建重复事件。
6. 同一 `run_key` 重复触发只有一个运行记录。

### 5.3 外部数据失败

1. HBase 单船读取失败只标记该船失败。
2. 船型库失败时不退化为分析全部船舶。
3. 港口数据全部失败时不发布 FINAL。
4. 泊位数据缺失时不能执行正常靠泊强排除。
5. 无效 geometry 被记录并跳过。

### 5.4 搭靠隔离

必须验证：

- `ShipAlongsideDetectorTest` 全部通过。
- 搭靠 handler 参数不变。
- 搭靠事件表 INSERT 不要求 V3 字段非空。
- 搭靠事件查询结果不因 event_key 或 V3 索引改变。
- Loitering 场景对象不能传入搭靠 detector。

## 6. 性能和容量测试

记录：

```text
商船数量
每船原始 AIS 点数
清洗后点数
重采样点数
单船耗时 P50/P95/P99
HBase 查询耗时 P50/P95/P99
整批耗时
峰值堆内存
数据库写入条数与耗时
失败和重试数
```

首期门槛建议：

- 小时任务在下一个整点前完成。
- 单船异常数据不能导致整批 OOM。
- HBase 并发从 1～2 起步，压测后再调整。
- 数据库批量写入使用确定的批大小。
- 日志不输出全量轨迹点。

## 7. 每日数据统计

### 7.1 必须输出

```text
analysis_date
rule_version
run_count
analyzed_ship_count
eligible_ship_count
candidate_event_count
final_event_count
review_event_count
excluded_normal_berthing_count
excluded_straight_transit_count
failed_ship_count
scene_data_failure_count
avg_duration_minutes
median_duration_minutes
p90_duration_minutes
duplicate_merge_count
low_confidence_count
```

### 7.2 分布

至少按以下维度统计：

- nearest_port_id / 港口名称
- 船型
- trajectory_pattern
- confidence_level
- severity_level
- event_status
- 时长区间
- 半径区间

区域分布不能只用事件中心的行政区域替代港口上下文。

### 7.3 质量监控告警

建议告警：

- 分析船舶数较 7 日中位数下降 30%。
- 场景数据不可用率超过 1%。
- LOW confidence 比例超过 30%。
- 某港口事件量较 7 日均值增加 3 倍。
- 重复合并率超过 5%。
- 小时任务未在下一个周期前完成。

## 8. 人工案例库

### 8.1 每日抽样

- TOP 20 风险事件。
- 10 个正常靠泊排除事件。
- 10 个直线通行排除事件。
- 10 个规则边界 REVIEW 事件。
- 10 个随机 FINAL 事件。

同一事件只进入一次待审队列；规则版本变化后可以重新进入。

### 8.2 案例内容

```text
event_key
ship_name
mmsi
imo
ship_type
start_time/end_time
duration
nearest_port
trajectory map
speed time series
course/direction-change time series
radius/area/closure/start-end distance
trajectory_pattern
inside_port/inside_berth
confidence
risk_score
reason_codes
rule_version
```

### 8.3 人工标签

```text
TRUE_LOITERING
NORMAL_BERTHING
NORMAL_PORT_WAITING
STRAIGHT_TRANSIT
OTHER_NORMAL_OPERATION
INSUFFICIENT_DATA
UNSURE
```

附加字段：

```text
reviewer
reviewed_at
label_reason
evidence_note
label_version
```

### 8.4 案例产物

第一阶段推荐生成独立 HTML：

- 地图轨迹按时间渐变。
- 标出港界、泊位和靠泊点。
- 同页展示航速和方向变化曲线。
- 展示规则命中与排除原因。

存储层只保存 `artifact_uri` 和结构化案例数据，不把大图片直接放事件主表。

## 9. 事件质量指标

只对有明确人工标签的样本计算。

### 9.1 核心指标

```text
precision =
  TRUE_LOITERING / 所有预测 FINAL

recall =
  被预测为 FINAL 的真实徘徊 / 全部真实徘徊

normal_berthing_false_positive_rate =
  被预测 FINAL 的正常靠泊 / 全部正常靠泊

duplicate_event_rate =
  人工确认重复事件数 / 全部事件数
```

事件边界：

```text
start_boundary_error_minutes
end_boundary_error_minutes
duration_error_minutes
```

### 9.2 分层指标

必须分别计算：

- 船型
- 港口
- 轨迹模式
- 数据置信度
- 事件时长
- 风险分段
- 港界内/外

不能用总体精确率掩盖某港口或某船型的大量误报。

## 10. Shadow 验收门槛

建议首期门槛：

```text
最终事件 precision >= 80%
真实徘徊 recall >= 70%
正常靠泊误报率 <= 5%
重复事件率 < 1%
事件边界误差 P90 <= 30 min
目标商船扫描成功率 >= 99%
港口/泊位场景可用率 >= 99%
小时任务按时完成率 >= 99%
```

升级正式版还要求：

- 每个目标船型都有足够样本。
- 重点港口都有正负样本。
- 所有轨迹模式都有人工确认样本。
- 至少连续 2～4 周达到门槛。
- 业务和算法共同签字确认阈值。

## 11. V2 与 V3 对照报告

shadow 期间同一批 AIS 同时运行 V2 和 V3，输出：

```text
V2 candidate count
V3 candidate count
V3 final count
V3 normal-berthing exclusions
V3 straight-transit exclusions
V2-only events
V3-only events
same-event score difference
same-event boundary difference
```

重点人工复核：

1. V2 命中、V3 排除的正常靠泊。
2. V2 命中、V3 排除的直线低速航行。
3. V2 未命中、V3 命中的圆形或蛇形轨迹。
4. V2/V3 都命中但事件边界差异大的样本。

## 12. 当前完成度审计

| 目标要求 | 当前证据 | 状态 |
|---|---|---|
| 阅读 README 和源码结构 | 仓库只读审计 | 完成 |
| 当前 AIS 到落库链路 | 现状审计与既有开发文档 | 完成 |
| 当前参数、阈值、起止和去重 | 代码逐项核对 | 完成 |
| V3 episode 起止状态机 | V3 设计文档第 6～7 节、7 项状态机测试 | 纯状态机完成，真实 AIS 待验证 |
| V3 运动模式 | V3 设计文档第 8～10 节、18 项 V3 测试 | 纯规则核心完成，真实样本待验证 |
| 跨小时去重与边界合并 | V3 设计文档第 14 节、8 项对账测试 | 纯对账完成，事务 upsert 待实现 |
| 靠泊排除 | V3 设计文档第 9 节、规则引擎测试 | 规则核心完成，数据源待确认 |
| 商船优先 | V3 设计文档第 3、5 节 | 设计完成，映射待确认 |
| 低速原始轨迹来源 | V3 设计文档第 5、16 节、1 项适配器测试 | 未抽稀 HBase 适配完成，真实数据量待压测 |
| 每小时过去 24 小时服务 | V3 设计文档第 14、17 节、2 项 Analyzer 测试 | 编排核心完成，XXL/Provider/Store 未接入 |
| ship_behavior_event 字段 | V3 设计文档第 15 节 | 设计完成，未迁移 |
| 每日数据统计 | 本报告第 7 节、5 项质量服务测试 | 纯聚合核心完成，查询与落库未实现 |
| TOP 风险案例库 | 本报告第 8 节、5 项质量服务测试 | TOP 选择和结构化案例核心完成，HTML/审核队列未生成 |
| 测试案例 | 本报告第 4～6 节 | episode 7、规则 18、对账 8、质量 5、Analyzer 2、轨迹适配 1 项已实现，真实场景/存储集成测试待实现 |
| 指标验证报告 | 本文 | 基线和验收方案完成，V3 实测待实现 |
| 不影响搭靠 | 本报告第 5.4 节与当前 13 个 detector、1 个回放候选测试 | 当前回归通过，后续需持续验证 |

## 13. 结论

当前证据支持：

- V2、搭靠和 V3 纯规则核心可以运行当前相关单元测试。
- Loitering V3 的 Analyzer 编排、候选 episode、特征、规则、事件对账和质量统计已有可执行实现，且保持为独立新类。
- Loitering V3 的类结构、数据库、服务、测试和质量评价已经形成可评审方案。
- V3 不能在未接泊位数据、未实现定时落库、未建立标注集的情况下宣称完成生产优化。

下一阶段的验证终点不是“代码能编译”，而是：

1. 所有 V3 验收测试通过。
2. 搭靠回归不变。
3. 小时服务重复扫描幂等。
4. shadow 实测达到事件质量门槛。
