# STS 规则与 XXL Job 历史测试计划

> 任务：`STS_RULES_AND_XXLJOB_TEST_PLAN`  
> 核验基线：分支 `codex/sts-phase1-2-persian-gulf-region-shadow-v1`，提交 `4adfdb424b20bbcdc312a2ed4683a7b6a39fc17c`  
> 范围：STS Phase 1 + Phase 1.2 波斯湾 Region Shadow  
> 状态术语：`CURRENT` = 当前 Phase1.2 Handler 实际执行的代码；`PROPOSED_PHASE2` = 建议下一阶段采用的规则；`NOT_CURRENTLY_ENFORCED` = 当前 Phase1.2 执行链尚未实施。

# 1. 执行摘要（Executive Summary）

当前系统是一个**高召回的物理搭靠候选流水线**，不是已确认 STS 分类器。区域 Handler 为 `shipStsHistoryRegionShadowJobHandler`；它只向 `dataanalysis.ship_sts_shadow_event` 写入 `behavior_type=BASELINE_ALONGSIDE_CANDIDATE`、`review_status=PENDING` 的记录，不写入生产表 `ship_behavior_event`。

- `CURRENT_IMPLEMENTATION` / `CURRENT`：已根据当前分支核实，并由现有 Phase1.2 链路实际执行。
- `PROPOSED_STRICT_STS_RULE` / `PROPOSED_PHASE2`：以准确率优先为目标的建议规则，当前尚未生效。
- `NOT_CURRENTLY_ENFORCED`：当前 Phase1.2 尚未执行的规则，或尚未填充的字段。

当前 OWN 船必须来自标准化原油油轮 Seed。发现的 TARGET 不要求属于 Seed，可以是油轮、成品油轮、加油/补给船、驳船/过驳船、拖轮/服务船、未知船型或其他船型。为保证召回率，当前有意采用这种非对称交易对手策略（`CURRENT`）；严格的 TARGET 角色准入仅属于 Phase2 建议。

建议的严格模型将物理接触事实与风险分类分离：先保留 `PHYSICAL_ALONGSIDE`，再分类为 `UNDERWAY_STS`、`STATIONARY_DRIFT_STS`、`NORMAL_SERVICE_CONTACT`、`PORT_OPERATION` 或 `UNCERTAIN_REVIEW`。只有同时满足离岸/场景准入、具备过驳能力的交易对手证据、长时间稳定近距离接触、横向并靠几何，以及排除跟随/追越后，才应输出 `HIGH_RISK_STS_CANDIDATE`（`PROPOSED_PHASE2`）。后续优化以 Precision（准确率）为首要目标。

# 2. 当前 STS Phase1.2 架构（Current STS Phase1.2 Architecture）

```text
原油油轮 Seed（仅 OWN）
        |
        v
IMO/MMSI 身份映射
        |
        v
历史 AIS
        |
        v
STS 活动窗口（OWN 默认 0.0-4.0 kn，>=30 分钟）
        |
        v
区域分析 Gate（核心多边形 + analysisBufferNm）
        |
        v
空间 AIS Replay
        |
        v
附近 TARGET 发现（TARGET 无需属于 Seed）
        |
        v
自身身份排除（先 MMSI 集合，再排除 Seed 可解析的相同 IMO）
        |
        v
ShipAlongsideDetector（ALONGSIDE_V3 默认规则）
        |
        v
核心区域归属（事件中心必须位于核心多边形内）
        |
        v
dataanalysis.ship_sts_shadow_event
```

关键执行事实：

- `CURRENT`：区域分析 Gate 在 Replay 之前执行。当低速活动窗口至少有一个 OWN 点落在带缓冲的分析区域内时，才执行 Replay。
- `CURRENT`：最终区域归属使用检测后的双船中心点（`center_lon`、`center_lat`）判断其是否位于无缓冲的核心多边形内。
- `CURRENT`：自身排除会拒绝属于 OWN Seed 身份的任何 TARGET MMSI；如果可通过 Seed 将 TARGET MMSI 解析到与 OWN 相同的 IMO，也会拒绝。
- `CURRENT`：输出行为是 `BASELINE_ALONGSIDE_CANDIDATE`，不是 `CONFIRMED_STS`。
- `CURRENT`：算法版本为 `STS_PHASE1_2_REGION_SHADOW_V1`；落库的 `baseline_rule` 为 `ALONGSIDE_V3_BASELINE`。
- `CURRENT`：`analysisBufferNm` 省略或 `<=0` 时默认 10 nm；校验允许 0–30 nm，但解析逻辑会把显式 `0` 转成 10 nm。
- `NOT_CURRENTLY_ENFORCED`：Phase1.2 未向 `ShipAlongsideDetector` 加载港口、泊位、锚地或 TARGET 角色上下文，而是以 unknown context 调用检测器。

# 3. 当前已实现计算规则（Current Implemented Calculation Rules）

## 3.1 端到端规则

| 规则 | 当前值 | 阶段 | 含义 | 状态 | 代码来源 |
|---|---:|---|---|---|---|
| Mode | `STS_PHASE1_REGION_SHADOW` | Job 校验 | Phase1.2 唯一允许的模式 | CURRENT | `StsRegionShadowService.validateParams` |
| 显式 IMO 列表 | 必填、非空且必须存在于 Seed | Job 选船 | 禁止隐式全船队运行 | CURRENT | `StsRegionShadowService.selectImos` |
| 时间范围 | 当前按 `Duration.toDays()` 校验 `<=7` 天 | Job 校验 | 更长范围被拒绝；运维应使用最长七天区间 | CURRENT | `StsRegionShadowService`、`StsBaselineService` |
| 并行度 | `1` | Job 校验 | IMO 串行处理 | CURRENT | 两个 STS Service |
| 写入模式 | `SHADOW_ONLY` | Job 校验 | 只写 Shadow 表 | CURRENT | `StsRegionShadowService` |
| 活动最低速度 | 参数默认 `0.0 kn` | 活动窗口 | OWN 召回下限 | CURRENT | `StsJobParams`、`StsBaselineService` |
| 活动最高速度 | 参数/默认 `4.0 kn` | 活动窗口 | OWN 召回上限 | CURRENT | `StsJobParams`、`StsBaselineService` |
| 活动最短持续时间 | `30 分钟` | 活动窗口 | 连续低速窗口最低时长 | CURRENT | `StsBaselineService.DEFAULT_MIN_WINDOW_MINUTES` |
| 活动切分间隔 | 间隔 `>30 分钟` 时切分 | 活动窗口 | 分隔低速窗口 | CURRENT | `StsBaselineService.DEFAULT_SPLIT_GAP_MINUTES` |
| 历史 AIS 加载 | simplify zoom 14、24 小时 loader chunk、上限 10,000 | 轨迹 | 加载 OWN 历史点 | CURRENT | `StsBaselineService.runOneImoWithHooks` |
| 区域分析 Gate | 窗口任一 OWN 点位于缓冲多边形内 | Replay 前 | 跳过区域外活动窗口 | CURRENT | `StsRegionShadowService.acceptWindow` |
| 分析缓冲 | 默认 `10 nm`；校验范围 0–30 nm | 区域 | 扩大 Replay Gate，不改变最终归属 | CURRENT | `StsJobParams`、`RegionGeometry`、`StsRegionShadowService` |
| Replay 查询窗口 | `30 分钟` | Replay | 计划的空间查询切片 | CURRENT | `application.properties`、`ShipAlongsideReplayCandidateProvider` |
| Replay bbox 缓冲 | `1.0 nm` | Replay | 空间查询外扩；必须 >= 候选最大距离 | CURRENT | 同上 |
| Replay 截断 | 区间 >10 分钟时递归拆分；仍截断则拒绝 | Replay | 不完整的叶子查询不产生候选 | CURRENT | `ShipAlongsideReplayCandidateProvider.executeReplay` |
| 候选距离 | `<=0.27 nm` | Replay 预筛 | 宽松的邻船召回 | CURRENT | `application.properties`、Replay Provider |
| 候选 TARGET 速度 | 至少一个有效 TARGET 点在 `0.0–4.0 kn` | Replay 预筛 | 宽松的 TARGET 低速证据 | CURRENT | Replay Provider；配置 `candidate.max-speed=4.0` |
| 候选最低速度属性 | 配置为 `0.0`，但匹配逻辑未读取 | Replay 预筛 | 除有效速度 >=0 外没有实际下限 | NOT_CURRENTLY_ENFORCED | `candidateMinSpeed` 与 `hasLowSpeedPoint` |
| 候选同步时差 | `<=10 分钟` | Replay 预筛 | OWN 点匹配按时间最近、随后向前消费的 TARGET 点 | CURRENT | Replay Provider |
| 候选最低点数 | `3` | Replay 预筛 | 至少三个近距离匹配点 | CURRENT | Replay Provider |
| 候选最短持续时间 | `20 分钟` | Replay 预筛 | 第一个至最后一个匹配 OWN 点的时间跨度 | CURRENT | Replay Provider |
| TARGET 商船过滤 | `NONE`；旧开关已废弃 | TARGET 发现 | 非商船和未知船型仍保留 | CURRENT | `application.properties`、Replay Provider 警告 |
| 自身 MMSI | 精确排除 OWN 身份 MMSI 集合 | 身份 | 防止同一物理身份配对 | CURRENT | `StsBaselineService` |
| 自身 IMO | Seed 能将 TARGET MMSI 映射到 OWN IMO 时拒绝 | 身份 | 第二层同船保护 | CURRENT | `StsBaselineService` |
| 核心区域归属 | 双船中心点被核心区域覆盖 | Detector 后 | 仅核心区域事件落库 | CURRENT | `StsRegionShadowService.onEvent` |
| 持久化幂等 | `INSERT IGNORE`，唯一键 `(batch_no,event_key)` | Shadow 写入 | 相同批次/事件跳过 | CURRENT | `StsShadowEventRepository`、Shadow DDL |

`0.0–4.0 kn` 活动规则只是候选召回条件，不是 STS 最终速度 Gate。Detector 还会应用下文所述的第二层、更窄规则。

# 4. 当前 Alongside V3 基线阈值（Current Alongside V3 Baseline Thresholds）

Phase1.2 直接构造 `ShipAlongsideDetector.AlongsideRule.defaultRule()`。因此当前 STS Detector 实际采用的是下列硬编码默认值，而不是另一套 `ship.behavior.alongside.confirm.*` 配置。

| 规则 | 当前值 | 阶段 | 含义 | 状态 | 代码来源 |
|---|---:|---|---|---|---|
| 正式最大距离 | 每个匹配对 `0.15 nm` | Detector 接受 | 每个被接受的点对都必须在此距离内 | CURRENT | `AlongsideRule.confirmMaxDistanceNm` |
| OWN Detector 速度 | `0.0–2.0 kn` | Detector 接受 | `confirmMinSpeed=0`，因此所有不超过 2 kn 的有效 OWN 速度都走 underway 分支 | CURRENT | `isAlongsideCandidate`、默认规则 |
| TARGET Detector 速度 | 有效且 `>=0`、`<=2.0 kn` | Detector 接受 | TARGET 下限来自点有效性校验 | CURRENT | 同上 |
| `stationaryPair` 分支 | 在 `confirmMinSpeed=0.0` 时逻辑不可达 | Detector 接受 | 表达式要求速度同时 >=0 且 <0 | NOT_CURRENTLY_ENFORCED | `isAlongsideCandidate` |
| 相对速度 | 瞬时绝对差 `<=1.0 kn` | Detector 接受 | 对每个接受的匹配点对执行 | CURRENT | 默认规则 |
| 同步时差 | `<=10 分钟` | Detector 匹配 | 按时间选择最近 TARGET 点 | CURRENT | 默认规则 |
| Heading/course 条件 | 启用；差值 `<=30°` | Detector 接受 | course 缺失时接受；双方速度都 <0.5 kn 时跳过 Heading | CURRENT | `headingAccepted` |
| 原始最低点数 | `3` | 原始片段 | 少于三个匹配点对直接丢弃 | CURRENT | 默认规则 |
| 弱候选最短持续时间 | `20 分钟` | 原始片段 | 更短片段直接丢弃 | CURRENT | 默认规则 |
| 正式候选最短持续时间 | `30 分钟` | 分类 | 不足时降为弱候选，而非拒绝 | CURRENT | `calculateFeature` |
| 正式候选最低点数 | `6` | 分类 | 不足时降为弱候选，而非拒绝 | CURRENT | 默认规则 |
| 平行比例 | 正式候选要求 `>=0.8` | 分类 | 所有匹配点中 course 差 <=30° 的比例；Heading 缺失会降低比例 | CURRENT | `calculateFeature` |
| 距离方差 | `<=0.0025 nm²` 获得较高得分 | 仅评分 | 不会拒绝已被 Detector 接受的片段 | CURRENT | `calculateBaseEvidenceScore` |
| AIS gap | OWN 相邻点间隔 `>30 分钟` | 特征/评分 | 记录 gap，并增加 5 个风险分 | CURRENT | 默认规则、`calculateFeature` |
| 合并间隔 | `<=30 分钟` | 事件合并 | 相邻的同一船对片段可以合并 | CURRENT | 默认规则 |
| 合并中心距离 | `<=1.0 nm` | 事件合并 | 第二个合并条件 | CURRENT | 默认规则 |
| 事件输出 | 弱候选与正式候选都会返回 | Detector 输出 | 弱候选得分上限 20，严重度固定 L1 | CURRENT | `buildTrace`、`calculateScore` |
| 候选预筛得分 | 无 | Replay 预筛 | 候选选择只使用硬条件，没有 score 阈值 | CURRENT | `CandidateMatchStats.isCandidate` |
| 正式候选 score 阈值 | 无 | Detector 输出 | 正式/弱候选由持续时间、点数、平行比例决定，不由事件分数决定 | CURRENT | `calculateFeature` |
| TARGET 角色处理 | Detector context 为 `UNKNOWN`；Shadow 未赋值 `target_role` | STS 集成 | 当前 Phase1.2 不按角色拒绝，也不存储角色 | NOT_CURRENTLY_ENFORCED | 基线 Detector 调用、Region Shadow 映射 |
| 场景处理 | unknown context；无港口/锚地排除 | STS 集成 | `nearPort=false`、`nearAnchor=false`，场景数据不可用 | NOT_CURRENTLY_ENFORCED | `detect(..., rule)` 重载 |
| TARGET 平均速度落库 | 字段存在但未赋值 | Shadow 写入 | `target_avg_speed_kn` 保持 null | NOT_CURRENTLY_ENFORCED | `StsRegionShadowService.onEvent` |

## 4.1 当前评分机制

`CURRENT`：score 是解释和排序输出，不是 STS 确认阈值。

- 基础证据（最高 55 分）：最小距离（<=0.1005 nm 时 `+10`，否则 `+7`）、方差（`+10` 或 `+4`）、平均相对速度（<=0.5 kn 时 `+8`，否则 `+5`）、已知 course 平均差（`+8`）、平行比例（`+8`）、覆盖率/时间同步质量（`+6`，否则 `+3`）和正式候选点数（`+5`）。
- 持续时间风险分：`<30=0`、`30–59=5`、`60–119=8`、`120–359=12`、`>=360=15`。
- 存在 AIS gap 时增加 5 分。当前场景/context 为 unknown，因此场景与商船船对加分均为 0。
- 低置信度将总分乘以 0.6。弱候选最高 20 分，并强制为 L1。
- 严重度区间：L0=0、L1=(0,25]、L2=(25,50]、L3=(50,75]、L4=(75,100]；自动 L4 已关闭。缺少场景/外部证据时，无 gap 事件最高可被限制到 L2。
- 距离方差、平均相对速度、覆盖率、AIS gap 和 score 在点对通过后不会形成更严格的拒绝条件。

# 5. 严格 STS 业务定义（Strict STS Business Definition）

`PROPOSED_PHASE2`：严格 STS 候选是指 Seed 原油油轮在正常港口、泊位、锚地、引航、拖轮协助或常规加油作业之外，与行为和船型均具备液货过驳合理性的交易对手，发生长时间、低速、近距离、稳定横向并靠的互动。

建议的最终分类：

| 分类 | 定义 | 高风险处理 |
|---|---|---|
| `UNDERWAY_STS` | 双方以低速前进，并保持稳定平行的横向并靠几何 | 通过场景/角色 Gate 后可进入高风险候选 |
| `STATIONARY_DRIFT_STS` | 双方接近静止或具有一致漂移向量，同时保持稳定舷侧关系 | 仅在具备更强反锚泊证据时准入 |
| `NORMAL_SERVICE_CONTACT` | 拖轮、引航、服务或常规加油/补给互动 | 保留事实；从高风险 STS 排除 |
| `PORT_OPERATION` | 泊位、港口、正常锚地或紧邻作业区的接触 | 保留事实；从高风险 STS 排除 |
| `UNCERTAIN_REVIEW` | 有物理搭靠证据，但几何、角色、场景或 AIS 证据不完整 | 人工复核；不自动赋高风险标签 |

以下决策树属于 `PROPOSED_PHASE2`；当前 Phase1.2 只实现了 Seed 身份、低速窗口、附近目标发现、自身排除、基线近距离接触检测、区域 Gate 与 Shadow 持久化。

```text
Seed 原油油轮
        |
        v
低速活动
        |
        v
附近船舶
        |
        v
是否同一物理船舶？-- 是 --> 拒绝
        |
       否
        v
是否正常港口/泊位/锚地作业？-- 是 --> PORT_OPERATION
        |
       否
        v
TARGET 是否拖轮/引航/服务船？-- 是 --> NORMAL_SERVICE_CONTACT
        |
       否
        v
稳定近距离接触是否足够久？-- 否 --> 拒绝
        |
       是
        v
是否跟随/追越/交叉？-- 是 --> 拒绝
        |
       否
        v
横向并靠几何是否稳定？-- 否 --> UNCERTAIN_REVIEW
        |
       是
        +--> 低速前进 ----------------> UNDERWAY_STS
        |
        +--> 静止/共同漂移 -----------> STATIONARY_DRIFT_STS
                                              |
                                              v
                                     是否高风险测试区域？
                                              |
                                              v
                                  HIGH_RISK_STS_CANDIDATE
```

# 6. 严格航行中 STS 建议（Strict Underway STS Recommendation）

## STRICT_UNDERWAY_STS_V1_RECOMMENDATION

本建议基于历史分支 `codex/region-scene-integration-v1` 中的 `TankerOpenSeaAlongsideRule`，版本为 `ALONGSIDE_LIQUID_CONTAINER_V2`。这些数值确实是历史代码默认值，但该类不在当前 Phase1.2 执行链中。

| 特征 | CURRENT 基线 | PROPOSED 严格航行中 STS | 状态 |
|---|---|---|---|
| 持续时间 | >=20 分钟保留；>=30 分钟正式候选 | `>=45 分钟` | PROPOSED_PHASE2 |
| 同步点 | 原始 >=3；正式 >=6 | `>=10` 且同步点比例 `>=0.75` | PROPOSED_PHASE2 |
| 最大同步时差 | 10 分钟 | `<=3 分钟` | PROPOSED_PHASE2 |
| OWN/TARGET 速度 | 每个点对 OWN 0–2、TARGET <=2 | 两船在 >=75% 同步点中均处于 `1.0–3.0 kn` | PROPOSED_PHASE2 |
| 相对速度 | 瞬时 <=1.0 kn，平均值参与评分 | 相对速度 P90 `<=0.5 kn` | PROPOSED_PHASE2 |
| Heading/course | 每点 <=30°，缺失时接受 | Heading 差 P90 `<=15°`；航行中评估要求有效 course | PROPOSED_PHASE2 |
| 舷侧方位 | 无 | 绝对相对方位 `45–135°` | PROPOSED_PHASE2 |
| 横向并靠比例 | 无 | `>=0.75` | PROPOSED_PHASE2 |
| 方位侧稳定性 | 无 | 舷侧点保持相同 PORT/STARBOARD 侧的比例 `>=0.75` | PROPOSED_PHASE2 |
| 距离稳定性 | 方差仅影响评分 | 至少 `>=0.75` 点对位于 P50 的 `max(0.03 nm, P50*25%)` 范围内 | PROPOSED_PHASE2 |
| 距离摘要 | min/avg 加 score 明细 | 保留 P10/P50/P90；用动态最大距离约束 P90 | PROPOSED_PHASE2 |
| 动态距离 | 无；固定 0.15 nm | `(ownLength+targetLength)/1852*0.90`，限制在 0.10–0.35 nm；缺失船长时 0.30 nm | PROPOSED_PHASE2 |
| 纵向/横向偏移 | 无 | 沿航向偏移 P90 > `dynamicMaxDistance*0.75` 时拒绝；保留横向偏移 P50 | PROPOSED_PHASE2 |
| 跟随/追越 | 无显式拒绝 | 拒绝纵向/跟随几何 | PROPOSED_PHASE2 |
| Replay 完整性 | 未解决的截断当前已不产生候选 | 明确要求 COMPLETE 或拆分恢复成功 | PROPOSED_PHASE2（部分 CURRENT） |

为保证准确率，上述所有数值条件都应作为 `UNDERWAY_STS` 硬 Gate；score 可以对通过 Gate 的结果排序，但不能补偿失败的几何条件。交叉行为也应通过 Heading/方位不稳定和横向并靠覆盖不足被拒绝。

# 7. 严格静止/漂移 STS 建议（Strict Stationary/Drift STS Recommendation）

`STATIONARY_DRIFT_STS_V1` 属于 `PROPOSED_PHASE2` 和 `NOT_CURRENTLY_ENFORCED`。真实过驳可能发生在 SOG 接近 0，或两船共同漂移时，因此统一采用 1–3 kn 下限会漏掉相关事件。

用于校准的高准确率起始范围：

| 特征 | 建议 Gate | 状态 |
|---|---:|---|
| 持续时间 | >=90 分钟；先以 120 分钟作为准确率优先备选值测试，验证后再决定自动晋级 | PROPOSED_PHASE2 |
| 同步证据 | >=12 个点对，同步比例 >=0.80，同步时差 P90 <=3 分钟且最大 <=5 分钟 | PROPOSED_PHASE2 |
| 船速 | 每船 SOG P90 <=0.7 kn；至少 75% 点对双方都 <=0.5 kn | PROPOSED_PHASE2 |
| 相对速度 | P90 <=0.20 kn | PROPOSED_PHASE2 |
| 距离 | 距离 P90 <=动态最大值，并采用保守绝对上限 0.25 nm | PROPOSED_PHASE2 |
| 距离稳定性 | 使用 `max(0.02 nm, P50*20%)` 区间，稳定比例 >=0.80 | PROPOSED_PHASE2 |
| 舷侧稳定性 | 有可靠 Heading 时 >=0.80 | PROPOSED_PHASE2 |
| 共同漂移 | 可用区间内位移向量方向/速度一致性 >=0.80 | PROPOSED_PHASE2 |
| Heading 与 COG | Heading 有效时使用船首向；速度低于 0.5 kn 时降低 COG 权重 | PROPOSED_PHASE2 |
| 场景 | 位于泊位、港口边界、正常锚地和已知常规加油区之外 | PROPOSED_PHASE2 |
| 角色/证据 | TARGET 具备过驳能力，且有非锚泊/非系泊证据；否则 `UNCERTAIN_REVIEW` | PROPOSED_PHASE2 |

必须具备反锚泊保护。两艘相邻锚泊船不会仅因距离稳定就成为 STS。自动判定 `STATIONARY_DRIFT_STS` 应要求离岸场景准入，并至少满足两个独立正向信号，例如一致且非零的共同漂移、稳定物理舷侧、具备过驳能力的 TARGET 角色、非锚泊航行状态或相互印证的作业证据。缺少可靠 Heading、TARGET 船型和漂移证据时，应进入 `UNCERTAIN_REVIEW`，而不是高风险。

# 8. 误报排除规则（False Positive Exclusion Rules）

核心设计原则（`PROPOSED_PHASE2`）是分别保存物理互动事实与风险解释：

- `PHYSICAL_ALONGSIDE`：几何事实，可包含港口、服务和加油事件。
- `HIGH_RISK_OFFSHORE_STS`：通过场景、TARGET 角色、持续时间、运动和几何 Gate 的严格子集。

## 8.1 港口（Port）

`NOT_CURRENTLY_ENFORCED`：Phase1.2 的 STS 调用没有港口多边形/距离 Gate。

`PROPOSED_PHASE2`：首先依据实际多边形位置，其次依据到港口边界的距离。建议校准分级：距港口边界 0–1 nm = `PORT_OPERATION`，1–3 nm = `PORT_APPROACH_REVIEW`，>=3 nm = `OFFSHORE_STS_ELIGIBLE`。这些只是测试起始分级，不是当前事实或法律边界。不能仅因离港时间短而拒绝：船舶在空间上脱离正常港区/锚区后，如果随后发生长时间、极低速、稳定的横向并靠，反而可能构成更高风险证据。

## 8.2 泊位（Berth）

`PROPOSED_PHASE2`：任何与泊位/码头多边形相交，或位于校准后的泊位安全缓冲内的事件，都归类为 `PORT_OPERATION`。将 `distanceToBerth` 作为可审计特征。包围整个港区的矩形不能代替泊位数据。

## 8.3 锚地（Anchorage）

`PROPOSED_PHASE2`：与已知正常锚地相交或 `distanceToAnchorage` 很小时，归类为 `ANCHORAGE_OPERATION`；除非经过复核的更强证据支持特殊过驳情形。仅有稳定近距离不足以判定 STS。

## 8.4 拖轮 / 引航 / 服务船（Tug / Pilot / Service）

`PROPOSED_PHASE2`：`TUG_OR_SERVICE` 和 `PASSENGER_OR_PILOT` 不得自动进入高风险 STS，应分类为 `NORMAL_SERVICE_CONTACT`。`FISHING_OR_SMALL_CRAFT` 同样排除。物理事件仍保留用于审计。

## 8.5 加油补给（Bunkering）

`PROPOSED_PHASE2`：不要删除 `BUNKER_OR_SUPPLY` 行为事实。应将其归入 `BUNKERING_OR_SERVICE_REVIEW`，而不是 `HIGH_RISK_STS`；除非独立货物/角色证据证明是油轮间原油过驳。正常加油区应作为显式场景排除区。

## 8.6 跟随 / 追越（Following / Overtaking）

`NOT_CURRENTLY_ENFORCED`：当前 Phase1.2 没有相对方位或沿航向偏移拒绝规则。

`PROPOSED_PHASE2`：纵向偏移占主导、相对方位无法基于有效船舶方向稳定保持 45–135°、并靠比例低于 0.75，或沿航向偏移 P90 超过动态最大距离的 0.75 时，拒绝跟随/追越。交叉行为应因 Heading、舷侧或持续时间稳定性不足而失败。

## 8.7 TARGET 角色策略

当前 `TargetVesselRole` 枚举包含 `LARGE_MERCHANT`、`BUNKER_OR_SUPPLY`、`BARGE_OR_LIGHTER`、`TUG_OR_SERVICE`、`FISHING_OR_SMALL_CRAFT`、`PASSENGER_OR_PILOT` 和 `UNKNOWN`。目前不能区分 `OIL_TANKER` 与 `PRODUCT_TANKER`（`NOT_CURRENTLY_ENFORCED`）。

| 严格分类 | 建议决策 | 状态 |
|---|---|---|
| `OIL_TANKER`、`PRODUCT_TANKER`、其他可信大型油品运输船 | 通过所有其他 Gate 后 `HIGH_RISK_STS_ACCEPT` | PROPOSED_PHASE2 |
| 有可信货物/尺寸证据的 `BARGE_OR_LIGHTER` | `HIGH_RISK_STS_ACCEPT`；否则复核 | PROPOSED_PHASE2 |
| `UNKNOWN` | `HIGH_RISK_STS_REVIEW` | PROPOSED_PHASE2 |
| `BUNKER_OR_SUPPLY` | `BUNKERING_OR_SERVICE_REVIEW` | PROPOSED_PHASE2 |
| `TUG_OR_SERVICE`、`PASSENGER_OR_PILOT`、`FISHING_OR_SMALL_CRAFT` | `HIGH_RISK_STS_EXCLUDE`；保留物理事实 | PROPOSED_PHASE2 |
| 普通港口服务船 | `NORMAL_SERVICE_CONTACT` | PROPOSED_PHASE2 |

# 9. 当前规则与严格 STS 对照（Current vs Strict STS Comparison）

| 特征 | Phase1.2 当前实现 | 严格 STS 目标 | 实现状态 |
|---|---|---|---|
| Seed 船型 | OWN 从标准化原油油轮 Seed 选择 | 保持；可进一步校验子类型/尺寸元数据 | CURRENT / 建议增强 |
| 活动速度 | OWN 0.0–4.0 kn 窗口 | 仅用于召回；拆分航行中与静止分支 | CURRENT + PROPOSED_PHASE2 |
| 事件速度 | 接受点对中 OWN 0–2、TARGET 0–2 | 航行中 1–3 kn 覆盖；静止 P90 <=0.7 | CURRENT vs PROPOSED_PHASE2 |
| 持续时间 | >=20 保留；>=30 正式候选 | 航行中 >=45；静止 >=90/120 校准 | CURRENT vs PROPOSED_PHASE2 |
| 距离 | 候选 <=0.27；Detector 固定 <=0.15 | P90 <=基于船长的 0.10–0.35 上限；静止绝对上限 0.25 | CURRENT vs PROPOSED_PHASE2 |
| 相对速度 | 每点对 <=1.0；平均值参与评分 | 航行中 P90 <=0.5；静止 <=0.2 | CURRENT vs PROPOSED_PHASE2 |
| Heading | 每点对 <=30，缺失/极低速时绕过 | 航行中 P90 <=15；静止时 Heading 感知 | CURRENT vs PROPOSED_PHASE2 |
| 横向并靠 | 无相对方位 Gate | 并靠比例 >=0.75 | NOT_CURRENTLY_ENFORCED |
| 方位 | 无 | 绝对相对方位 45–135°，稳定舷侧 >=0.75 | NOT_CURRENTLY_ENFORCED |
| 距离稳定性 | 方差仅参与评分 | 航行中稳定比例 >=0.75，静止 >=0.80 | NOT_CURRENTLY_ENFORCED |
| 动态距离 | 无 | 合并船长阈值，上限约 0.35 nm | NOT_CURRENTLY_ENFORCED |
| 港口距离 | STS context 中无 | 多边形加 0–1/1–3/>=3 nm 校准分级 | NOT_CURRENTLY_ENFORCED |
| 锚地排除 | STS context 中无 | 已知锚地排除/复核 | NOT_CURRENTLY_ENFORCED |
| 泊位排除 | 无 | 泊位多边形/距离排除 | NOT_CURRENTLY_ENFORCED |
| 拖轮排除 | 不过滤 TARGET | 从高风险排除；保留服务事实 | NOT_CURRENTLY_ENFORCED |
| 加油船处理 | 宽召回；角色未落库 | `BUNKERING_OR_SERVICE_REVIEW` | NOT_CURRENTLY_ENFORCED |
| 跟随拒绝 | 无 | 相对方位与沿航向偏移拒绝 | NOT_CURRENTLY_ENFORCED |
| 静止漂移 | 近零速度点可通过普通 Detector；专用 `stationaryPair` 在 min=0 时不可达 | 专用共同漂移规则 | NOT_CURRENTLY_ENFORCED |
| 区域 | Replay 前缓冲 Gate + 核心中心归属 | 保留，再增加海事场景多边形 | CURRENT + PROPOSED_PHASE2 |
| Shadow 持久化 | 独立表、INSERT IGNORE | 校准期间保留 | CURRENT |
| IMO 身份 | OWN MMSI 别名来自 Seed；可解析时排除相同 IMO | 保留并增强外部身份解析 | CURRENT + 建议增强 |
| TARGET IMO | 仅 TARGET MMSI 存在于 Seed 时填充 | 独立船舶主数据查询 | PARTIAL / PROPOSED_PHASE2 |
| AIS 黑屏 | 统计 gap；不由黑屏推断 STS | 仅作复核/风险上下文；不得替代物理证据 | CURRENT 特征 / PROPOSED_PHASE2 策略 |

# 10. 全球 STS 测试风险区域（Global STS Test Risk Regions）

`GLOBAL_STS_TEST_REGION_SET_V1` 是工程测试目录。每个条目均为 `TEST_RISK_REGION`、`TEST_ONLY_BBOX` 和 `NOT_AUTHORITATIVE_MARITIME_BOUNDARY`。它不是法律定义、制裁名单、事实指控或权威 STS 区域。生产 Gate 应使用 Marine Regions 数据或经过审查的内部海事多边形替换矩形。

| regionId | 显示名称 | 业务/测试理由 | 测试覆盖重点 |
|---|---|---|---|
| `PERSIAN_GULF_TEST_V1` | 波斯湾 | 原油出口和油轮密集活动 | Ras Tanura、Ju'aymah、Mina al-Ahmadi、Basra/ABOT、Kharg Island、Ruwais；沙特、科威特、伊拉克、卡塔尔、阿联酋、伊朗 |
| `GULF_OF_OMAN_FUJAIRAH_TEST_V1` | 阿曼湾 / Fujairah | 离岸油轮、加油和进港航路校准 | Fujairah、Khor Fakkan、Sohar 及外海航路 |
| `SINGAPORE_MALACCA_TEST_V1` | 新加坡 / 马六甲海峡 | 混合交通密集，适合误报压力测试 | Singapore、Malacca Strait、Port Klang、Tanjung Pelepas、Johor/Riau 邻近水域 |
| `VENEZUELA_JOSE_TEST_V1` | 委内瑞拉 José | 东部油品出口水域历史测试 | José Terminal、Puerto La Cruz、Paria/Caribbean 航路 |
| `VENEZUELA_PARAGUANA_TEST_V1` | 委内瑞拉 Paraguaná | 炼厂/出口及外海航路测试 | Amuay、Cardón、Paraguaná 航路 |
| `RUSSIA_BALTIC_TEST_V1` | 俄罗斯波罗的海 | 波罗的海油品出口及航路测试 | Primorsk、Ust-Luga、Gulf of Finland 航路 |
| `RUSSIA_BLACK_SEA_TEST_V1` | 俄罗斯黑海 | 黑海出口/航路测试 | Novorossiysk、Tuapse、外海航路 |
| `RUSSIA_FAR_EAST_TEST_V1` | 俄罗斯远东 | 太平洋出口/航路测试 | Kozmino、Nakhodka、日本海航路 |
| `WEST_AFRICA_LOME_TEST_V1` | 西非 Lomé | 离岸过驳模式和服务接触校准 | Togo/Lomé 外海 |
| `GULF_OF_GUINEA_TEST_V1` | 几内亚湾 | 广域离岸码头/油轮水域扫描 | Bonny、Forcados、Qua Iboe、尼日利亚至安哥拉邻近水域 |
| `MEDITERRANEAN_SICILY_MALTA_TEST_V1` | 西西里 / 马耳他 | 油轮通航和离岸过驳测试区域 | Sicily Channel、Malta 航路 |
| `MALAYSIA_INDONESIA_RIAU_TEST_V1` | 马来西亚/印度尼西亚 Riau | 新加坡东部/Nipah 密集互动区域 | Riau、Nipah、新加坡东部航路 |

# 11. 区域 WKT / BBOX 目录（Region WKT / BBOX Catalog）

所有 WKT 均为单行、经度在前的 EPSG:4326 测试矩形。范围有意覆盖外海航路，不仅框住港口本身。

| regionId | regionVersion | 名称 | lonMin | lonMax | latMin | latMax | WKT | 状态 | 备注 |
|---|---|---|---:|---:|---:|---:|---|---|---|
| `PERSIAN_GULF_TEST_V1` | `20260902-v1` | 波斯湾 | 47.5 | 56.5 | 23.5 | 30.5 | `POLYGON((47.5 23.5,56.5 23.5,56.5 30.5,47.5 30.5,47.5 23.5))` | TEST_ONLY_BBOX | NOT_AUTHORITATIVE_MARITIME_BOUNDARY |
| `GULF_OF_OMAN_FUJAIRAH_TEST_V1` | `20260903-v1` | 阿曼湾 / Fujairah | 54.0 | 59.5 | 22.5 | 27.0 | `POLYGON((54.0 22.5,59.5 22.5,59.5 27.0,54.0 27.0,54.0 22.5))` | TEST_ONLY_BBOX | NOT_AUTHORITATIVE_MARITIME_BOUNDARY；覆盖 Fujairah/Sohar 外海航路 |
| `SINGAPORE_MALACCA_TEST_V1` | `20260903-v1` | 新加坡 / 马六甲 | 98.0 | 105.5 | 0.0 | 6.5 | `POLYGON((98.0 0.0,105.5 0.0,105.5 6.5,98.0 6.5,98.0 0.0))` | TEST_ONLY_BBOX | NOT_AUTHORITATIVE_MARITIME_BOUNDARY；覆盖广域海峡和外海航路 |
| `VENEZUELA_JOSE_TEST_V1` | `20260903-v1` | 委内瑞拉 José | -66.5 | -60.0 | 8.0 | 12.5 | `POLYGON((-66.5 8.0,-60.0 8.0,-60.0 12.5,-66.5 12.5,-66.5 8.0))` | TEST_ONLY_BBOX | NOT_AUTHORITATIVE_MARITIME_BOUNDARY；覆盖东部出口水域和航路 |
| `VENEZUELA_PARAGUANA_TEST_V1` | `20260903-v1` | 委内瑞拉 Paraguaná | -72.5 | -68.0 | 10.0 | 13.5 | `POLYGON((-72.5 10.0,-68.0 10.0,-68.0 13.5,-72.5 13.5,-72.5 10.0))` | TEST_ONLY_BBOX | NOT_AUTHORITATIVE_MARITIME_BOUNDARY；覆盖 Amuay/Cardón 航路 |
| `RUSSIA_BALTIC_TEST_V1` | `20260903-v1` | 俄罗斯波罗的海 | 26.0 | 31.5 | 58.5 | 61.5 | `POLYGON((26.0 58.5,31.5 58.5,31.5 61.5,26.0 61.5,26.0 58.5))` | TEST_ONLY_BBOX | NOT_AUTHORITATIVE_MARITIME_BOUNDARY；覆盖芬兰湾航路 |
| `RUSSIA_BLACK_SEA_TEST_V1` | `20260903-v1` | 俄罗斯黑海 | 35.0 | 41.0 | 42.0 | 46.5 | `POLYGON((35.0 42.0,41.0 42.0,41.0 46.5,35.0 46.5,35.0 42.0))` | TEST_ONLY_BBOX | NOT_AUTHORITATIVE_MARITIME_BOUNDARY；覆盖黑海东部/东北部航路 |
| `RUSSIA_FAR_EAST_TEST_V1` | `20260903-v1` | 俄罗斯远东 | 131.0 | 136.5 | 40.0 | 44.5 | `POLYGON((131.0 40.0,136.5 40.0,136.5 44.5,131.0 44.5,131.0 40.0))` | TEST_ONLY_BBOX | NOT_AUTHORITATIVE_MARITIME_BOUNDARY；覆盖 Kozmino/Nakhodka 航路 |
| `WEST_AFRICA_LOME_TEST_V1` | `20260903-v1` | 西非 Lomé | -1.5 | 2.5 | 4.0 | 7.5 | `POLYGON((-1.5 4.0,2.5 4.0,2.5 7.5,-1.5 7.5,-1.5 4.0))` | TEST_ONLY_BBOX | NOT_AUTHORITATIVE_MARITIME_BOUNDARY；覆盖广域 Lomé 外海水域 |
| `GULF_OF_GUINEA_TEST_V1` | `20260903-v1` | 几内亚湾 | -10.0 | 14.0 | -6.0 | 7.5 | `POLYGON((-10.0 -6.0,14.0 -6.0,14.0 7.5,-10.0 7.5,-10.0 -6.0))` | TEST_ONLY_BBOX | NOT_AUTHORITATIVE_MARITIME_BOUNDARY；生产前应进一步拆分 |
| `MEDITERRANEAN_SICILY_MALTA_TEST_V1` | `20260903-v1` | 西西里 / 马耳他 | 11.0 | 17.0 | 34.0 | 39.0 | `POLYGON((11.0 34.0,17.0 34.0,17.0 39.0,11.0 39.0,11.0 34.0))` | TEST_ONLY_BBOX | NOT_AUTHORITATIVE_MARITIME_BOUNDARY；覆盖西西里海峡/马耳他航路 |
| `MALAYSIA_INDONESIA_RIAU_TEST_V1` | `20260903-v1` | 马来西亚 / 印度尼西亚 Riau | 102.0 | 106.0 | 0.0 | 3.0 | `POLYGON((102.0 0.0,106.0 0.0,106.0 3.0,102.0 3.0,102.0 0.0))` | TEST_ONLY_BBOX | NOT_AUTHORITATIVE_MARITIME_BOUNDARY；覆盖 Nipah/Riau/新加坡东部航路 |

# 12. 测试策略（Test Strategy）

| 批次 | 范围 | 目标 | 扩大测试前检查 |
|---|---|---|---|
| A — Smoke | 1 IMO × 1 区域 × 24h | 轨迹、区域 Gate、Replay、Shadow 插入 | 只能使用由轨迹证据确认进入该区域的 IMO；否则标记 `NEED_CONFIRMED_REGION_SAMPLE` |
| B — 小区域测试 | 5 IMO × 1 区域 × 3d | 候选数量、Replay 失败、DB 写入、首次误报复核 | 无自身船对/重复；已掌握 Replay 健康状态 |
| C — 区域校准 | 20–50 IMO × 1 区域 × 7d | 建立第一批人工复核集 | 标注每个候选，按误报类别汇总 Precision |
| D — Seed 船队区域扫描 | 786 Seed IMO，区域 × <=7d × IMO 分块 | 受控历史扫描 | 每 Job 50 或 100 IMO、batchNo 唯一、核对运行输出与 DB |

不要从 786 IMO × 半年开始。应按 24h → 3d → 7d → 区域月逐级推进。当前 Phase1.2 要求显式 `imos`，不得将 786 个 IMO 一次性粘贴进一个 Job。区域 Gate 在 Replay 前运行，因此可以用 Seed 分块高效扫描区域；区域外活动窗口会被跳过。

# 13. XXL Job 参数设计（XXL Job Parameter Design）

Handler：`shipStsHistoryRegionShadowJobHandler`（`CURRENT`）。必需运行契约：

| 参数 | 规则 |
|---|---|
| `mode` | 必须为 `STS_PHASE1_REGION_SHADOW` |
| `seedFile` | 远端路径 `/data/zhengyi/sts/sts-crude-identity-20260902.csv` |
| `imos` | 显式非空数组；不在 Seed 的条目会被跳过，全部缺失时拒绝 |
| `from`、`to` | `yyyy-MM-dd HH:mm:ss`；使用 <=7 天区间 |
| `windowHours` | 固定包含 `24`；当前会解析，但 STS Service 不用它改变处理逻辑 |
| `activityMinSpeedKn`、`activityMaxSpeedKn` | 当前召回行为使用 `0.0`、`4.0` |
| `regionId`、`regionVersion`、`regionWkt` | 必填；WKT 单行 |
| `analysisBufferNm` | 当前测试建议 `10.0`；校验上限 30 |
| `batchNo` | 必填；每个区域/时间/分块执行必须唯一 |
| `parallelism` | 必须为 `1` |
| `writeMode` | 必须为 `SHADOW_ONLY` |

以下示例全部是单行 JSON。日期区间按半开区间意图 `[from,to)` 使用；复核时需确认 Loader 端点语义。本地标准化 Seed 含 786 个不同 IMO，以下使用前 100 个不同 IMO 作为可重复的 **Seed 扫描分块**。属于 Seed 不代表这些船曾进入示例区域。

# 14. Smoke Job 参数（Smoke Job Parameters）

## A. 1 IMO × 24h — 波斯湾

状态：`NEED_CONFIRMED_REGION_SAMPLE`。只有本地或经授权的轨迹证据确认该船进入测试区域后，才可替换占位符；当前参数不能直接执行。

```json
{"mode":"STS_PHASE1_REGION_SHADOW","seedFile":"/data/zhengyi/sts/sts-crude-identity-20260902.csv","imos":["FILL_WITH_CONFIRMED_IMO"],"from":"2026-08-01 00:00:00","to":"2026-08-02 00:00:00","windowHours":24,"activityMinSpeedKn":0.0,"activityMaxSpeedKn":4.0,"regionId":"PERSIAN_GULF_TEST_V1","regionVersion":"20260902-v1","regionWkt":"POLYGON((47.5 23.5,56.5 23.5,56.5 30.5,47.5 30.5,47.5 23.5))","analysisBufferNm":10.0,"batchNo":"sts-pg-smoke-20260801-confirmed-001","parallelism":1,"writeMode":"SHADOW_ONLY"}
```

# 15. 区域批次 Job 参数（Regional Batch Job Parameters）

以下 5/20/100 IMO 参数是 Seed 扫描参数，由 Region Gate 执行地理过滤，不代表这些船一定进入过对应区域。

## B. 5 IMO × 3d — 波斯湾

```json
{"mode":"STS_PHASE1_REGION_SHADOW","seedFile":"/data/zhengyi/sts/sts-crude-identity-20260902.csv","imos":["1014876","1015258","1019539","1019541","1019735"],"from":"2026-08-01 00:00:00","to":"2026-08-04 00:00:00","windowHours":24,"activityMinSpeedKn":0.0,"activityMaxSpeedKn":4.0,"regionId":"PERSIAN_GULF_TEST_V1","regionVersion":"20260902-v1","regionWkt":"POLYGON((47.5 23.5,56.5 23.5,56.5 30.5,47.5 30.5,47.5 23.5))","analysisBufferNm":10.0,"batchNo":"sts-pg-20260801-03d-imo001-005","parallelism":1,"writeMode":"SHADOW_ONLY"}
```

## C. 20 IMO × 7d — 波斯湾

```json
{"mode":"STS_PHASE1_REGION_SHADOW","seedFile":"/data/zhengyi/sts/sts-crude-identity-20260902.csv","imos":["1014876","1015258","1019539","1019541","1019735","1019747","1023425","1023516","1025485","1025497","1025502","1028384","1028528","1028683","1028695","1029883","1029895","1029924","1030088","1030404"],"from":"2026-08-01 00:00:00","to":"2026-08-08 00:00:00","windowHours":24,"activityMinSpeedKn":0.0,"activityMaxSpeedKn":4.0,"regionId":"PERSIAN_GULF_TEST_V1","regionVersion":"20260902-v1","regionWkt":"POLYGON((47.5 23.5,56.5 23.5,56.5 30.5,47.5 30.5,47.5 23.5))","analysisBufferNm":10.0,"batchNo":"sts-pg-20260801-07d-imo001-020","parallelism":1,"writeMode":"SHADOW_ONLY"}
```

D–K 使用的标准前 100 个 Seed 分块：

```text
1014876,1015258,1019539,1019541,1019735,1019747,1023425,1023516,1025485,1025497,1025502,1028384,1028528,1028683,1028695,1029883,1029895,1029924,1030088,1030404,1031032,1031044,1033705,1033717,1035545,1035557,1037048,1037139,1037933,1038884,1038896,1042500,1042512,1042524,1042550,1043657,1043669,1043695,1044534,1044869,1044871,1050959,1052878,1052880,1055155,1055167,1055181,1055387,1057622,1058030,1058092,1059577,1062952,1064376,1070569,1070832,1070844,1071549,1074058,1079199,1081116,1081128,1081130,1085473,1085485,1085497,1086714,1086726,1090777,1090973,1097775,1100716,1113622,1165340,1165376,8112201,8117512,8413502,8508292,9004530,9038828,9038830,9038866,9038880,9038892,9039626,9050577,9056806,9079066,9079107,9081174,9085376,9108154,9116412,9153537,9159828,9171450,9171462,9172038,9183934
```

## D. 100 IMO 分块 × 波斯湾

```json
{"mode":"STS_PHASE1_REGION_SHADOW","seedFile":"/data/zhengyi/sts/sts-crude-identity-20260902.csv","imos":["1014876","1015258","1019539","1019541","1019735","1019747","1023425","1023516","1025485","1025497","1025502","1028384","1028528","1028683","1028695","1029883","1029895","1029924","1030088","1030404","1031032","1031044","1033705","1033717","1035545","1035557","1037048","1037139","1037933","1038884","1038896","1042500","1042512","1042524","1042550","1043657","1043669","1043695","1044534","1044869","1044871","1050959","1052878","1052880","1055155","1055167","1055181","1055387","1057622","1058030","1058092","1059577","1062952","1064376","1070569","1070832","1070844","1071549","1074058","1079199","1081116","1081128","1081130","1085473","1085485","1085497","1086714","1086726","1090777","1090973","1097775","1100716","1113622","1165340","1165376","8112201","8117512","8413502","8508292","9004530","9038828","9038830","9038866","9038880","9038892","9039626","9050577","9056806","9079066","9079107","9081174","9085376","9108154","9116412","9153537","9159828","9171450","9171462","9172038","9183934"],"from":"2026-08-01 00:00:00","to":"2026-08-08 00:00:00","windowHours":24,"activityMinSpeedKn":0.0,"activityMaxSpeedKn":4.0,"regionId":"PERSIAN_GULF_TEST_V1","regionVersion":"20260902-v1","regionWkt":"POLYGON((47.5 23.5,56.5 23.5,56.5 30.5,47.5 30.5,47.5 23.5))","analysisBufferNm":10.0,"batchNo":"pg-202608-001-01-07","parallelism":1,"writeMode":"SHADOW_ONLY"}
```

## E. 100 IMO 分块 × 新加坡/马六甲

```json
{"mode":"STS_PHASE1_REGION_SHADOW","seedFile":"/data/zhengyi/sts/sts-crude-identity-20260902.csv","imos":["1014876","1015258","1019539","1019541","1019735","1019747","1023425","1023516","1025485","1025497","1025502","1028384","1028528","1028683","1028695","1029883","1029895","1029924","1030088","1030404","1031032","1031044","1033705","1033717","1035545","1035557","1037048","1037139","1037933","1038884","1038896","1042500","1042512","1042524","1042550","1043657","1043669","1043695","1044534","1044869","1044871","1050959","1052878","1052880","1055155","1055167","1055181","1055387","1057622","1058030","1058092","1059577","1062952","1064376","1070569","1070832","1070844","1071549","1074058","1079199","1081116","1081128","1081130","1085473","1085485","1085497","1086714","1086726","1090777","1090973","1097775","1100716","1113622","1165340","1165376","8112201","8117512","8413502","8508292","9004530","9038828","9038830","9038866","9038880","9038892","9039626","9050577","9056806","9079066","9079107","9081174","9085376","9108154","9116412","9153537","9159828","9171450","9171462","9172038","9183934"],"from":"2026-08-01 00:00:00","to":"2026-08-08 00:00:00","windowHours":24,"activityMinSpeedKn":0.0,"activityMaxSpeedKn":4.0,"regionId":"SINGAPORE_MALACCA_TEST_V1","regionVersion":"20260903-v1","regionWkt":"POLYGON((98.0 0.0,105.5 0.0,105.5 6.5,98.0 6.5,98.0 0.0))","analysisBufferNm":10.0,"batchNo":"sgml-202608-001-01-07","parallelism":1,"writeMode":"SHADOW_ONLY"}
```

## F. 100 IMO 分块 × 委内瑞拉 José

```json
{"mode":"STS_PHASE1_REGION_SHADOW","seedFile":"/data/zhengyi/sts/sts-crude-identity-20260902.csv","imos":["1014876","1015258","1019539","1019541","1019735","1019747","1023425","1023516","1025485","1025497","1025502","1028384","1028528","1028683","1028695","1029883","1029895","1029924","1030088","1030404","1031032","1031044","1033705","1033717","1035545","1035557","1037048","1037139","1037933","1038884","1038896","1042500","1042512","1042524","1042550","1043657","1043669","1043695","1044534","1044869","1044871","1050959","1052878","1052880","1055155","1055167","1055181","1055387","1057622","1058030","1058092","1059577","1062952","1064376","1070569","1070832","1070844","1071549","1074058","1079199","1081116","1081128","1081130","1085473","1085485","1085497","1086714","1086726","1090777","1090973","1097775","1100716","1113622","1165340","1165376","8112201","8117512","8413502","8508292","9004530","9038828","9038830","9038866","9038880","9038892","9039626","9050577","9056806","9079066","9079107","9081174","9085376","9108154","9116412","9153537","9159828","9171450","9171462","9172038","9183934"],"from":"2026-08-01 00:00:00","to":"2026-08-08 00:00:00","windowHours":24,"activityMinSpeedKn":0.0,"activityMaxSpeedKn":4.0,"regionId":"VENEZUELA_JOSE_TEST_V1","regionVersion":"20260903-v1","regionWkt":"POLYGON((-66.5 8.0,-60.0 8.0,-60.0 12.5,-66.5 12.5,-66.5 8.0))","analysisBufferNm":10.0,"batchNo":"vjose-202608-001-01-07","parallelism":1,"writeMode":"SHADOW_ONLY"}
```

## G. 100 IMO 分块 × 俄罗斯波罗的海

```json
{"mode":"STS_PHASE1_REGION_SHADOW","seedFile":"/data/zhengyi/sts/sts-crude-identity-20260902.csv","imos":["1014876","1015258","1019539","1019541","1019735","1019747","1023425","1023516","1025485","1025497","1025502","1028384","1028528","1028683","1028695","1029883","1029895","1029924","1030088","1030404","1031032","1031044","1033705","1033717","1035545","1035557","1037048","1037139","1037933","1038884","1038896","1042500","1042512","1042524","1042550","1043657","1043669","1043695","1044534","1044869","1044871","1050959","1052878","1052880","1055155","1055167","1055181","1055387","1057622","1058030","1058092","1059577","1062952","1064376","1070569","1070832","1070844","1071549","1074058","1079199","1081116","1081128","1081130","1085473","1085485","1085497","1086714","1086726","1090777","1090973","1097775","1100716","1113622","1165340","1165376","8112201","8117512","8413502","8508292","9004530","9038828","9038830","9038866","9038880","9038892","9039626","9050577","9056806","9079066","9079107","9081174","9085376","9108154","9116412","9153537","9159828","9171450","9171462","9172038","9183934"],"from":"2026-08-01 00:00:00","to":"2026-08-08 00:00:00","windowHours":24,"activityMinSpeedKn":0.0,"activityMaxSpeedKn":4.0,"regionId":"RUSSIA_BALTIC_TEST_V1","regionVersion":"20260903-v1","regionWkt":"POLYGON((26.0 58.5,31.5 58.5,31.5 61.5,26.0 61.5,26.0 58.5))","analysisBufferNm":10.0,"batchNo":"rbal-202608-001-01-07","parallelism":1,"writeMode":"SHADOW_ONLY"}
```

## H. 100 IMO 分块 × 俄罗斯黑海

```json
{"mode":"STS_PHASE1_REGION_SHADOW","seedFile":"/data/zhengyi/sts/sts-crude-identity-20260902.csv","imos":["1014876","1015258","1019539","1019541","1019735","1019747","1023425","1023516","1025485","1025497","1025502","1028384","1028528","1028683","1028695","1029883","1029895","1029924","1030088","1030404","1031032","1031044","1033705","1033717","1035545","1035557","1037048","1037139","1037933","1038884","1038896","1042500","1042512","1042524","1042550","1043657","1043669","1043695","1044534","1044869","1044871","1050959","1052878","1052880","1055155","1055167","1055181","1055387","1057622","1058030","1058092","1059577","1062952","1064376","1070569","1070832","1070844","1071549","1074058","1079199","1081116","1081128","1081130","1085473","1085485","1085497","1086714","1086726","1090777","1090973","1097775","1100716","1113622","1165340","1165376","8112201","8117512","8413502","8508292","9004530","9038828","9038830","9038866","9038880","9038892","9039626","9050577","9056806","9079066","9079107","9081174","9085376","9108154","9116412","9153537","9159828","9171450","9171462","9172038","9183934"],"from":"2026-08-01 00:00:00","to":"2026-08-08 00:00:00","windowHours":24,"activityMinSpeedKn":0.0,"activityMaxSpeedKn":4.0,"regionId":"RUSSIA_BLACK_SEA_TEST_V1","regionVersion":"20260903-v1","regionWkt":"POLYGON((35.0 42.0,41.0 42.0,41.0 46.5,35.0 46.5,35.0 42.0))","analysisBufferNm":10.0,"batchNo":"rbsea-202608-001-01-07","parallelism":1,"writeMode":"SHADOW_ONLY"}
```

## I. 100 IMO 分块 × 俄罗斯远东

```json
{"mode":"STS_PHASE1_REGION_SHADOW","seedFile":"/data/zhengyi/sts/sts-crude-identity-20260902.csv","imos":["1014876","1015258","1019539","1019541","1019735","1019747","1023425","1023516","1025485","1025497","1025502","1028384","1028528","1028683","1028695","1029883","1029895","1029924","1030088","1030404","1031032","1031044","1033705","1033717","1035545","1035557","1037048","1037139","1037933","1038884","1038896","1042500","1042512","1042524","1042550","1043657","1043669","1043695","1044534","1044869","1044871","1050959","1052878","1052880","1055155","1055167","1055181","1055387","1057622","1058030","1058092","1059577","1062952","1064376","1070569","1070832","1070844","1071549","1074058","1079199","1081116","1081128","1081130","1085473","1085485","1085497","1086714","1086726","1090777","1090973","1097775","1100716","1113622","1165340","1165376","8112201","8117512","8413502","8508292","9004530","9038828","9038830","9038866","9038880","9038892","9039626","9050577","9056806","9079066","9079107","9081174","9085376","9108154","9116412","9153537","9159828","9171450","9171462","9172038","9183934"],"from":"2026-08-01 00:00:00","to":"2026-08-08 00:00:00","windowHours":24,"activityMinSpeedKn":0.0,"activityMaxSpeedKn":4.0,"regionId":"RUSSIA_FAR_EAST_TEST_V1","regionVersion":"20260903-v1","regionWkt":"POLYGON((131.0 40.0,136.5 40.0,136.5 44.5,131.0 44.5,131.0 40.0))","analysisBufferNm":10.0,"batchNo":"rfe-202608-001-01-07","parallelism":1,"writeMode":"SHADOW_ONLY"}
```

## J. 100 IMO 分块 × 阿曼湾/Fujairah

```json
{"mode":"STS_PHASE1_REGION_SHADOW","seedFile":"/data/zhengyi/sts/sts-crude-identity-20260902.csv","imos":["1014876","1015258","1019539","1019541","1019735","1019747","1023425","1023516","1025485","1025497","1025502","1028384","1028528","1028683","1028695","1029883","1029895","1029924","1030088","1030404","1031032","1031044","1033705","1033717","1035545","1035557","1037048","1037139","1037933","1038884","1038896","1042500","1042512","1042524","1042550","1043657","1043669","1043695","1044534","1044869","1044871","1050959","1052878","1052880","1055155","1055167","1055181","1055387","1057622","1058030","1058092","1059577","1062952","1064376","1070569","1070832","1070844","1071549","1074058","1079199","1081116","1081128","1081130","1085473","1085485","1085497","1086714","1086726","1090777","1090973","1097775","1100716","1113622","1165340","1165376","8112201","8117512","8413502","8508292","9004530","9038828","9038830","9038866","9038880","9038892","9039626","9050577","9056806","9079066","9079107","9081174","9085376","9108154","9116412","9153537","9159828","9171450","9171462","9172038","9183934"],"from":"2026-08-01 00:00:00","to":"2026-08-08 00:00:00","windowHours":24,"activityMinSpeedKn":0.0,"activityMaxSpeedKn":4.0,"regionId":"GULF_OF_OMAN_FUJAIRAH_TEST_V1","regionVersion":"20260903-v1","regionWkt":"POLYGON((54.0 22.5,59.5 22.5,59.5 27.0,54.0 27.0,54.0 22.5))","analysisBufferNm":10.0,"batchNo":"gof-202608-001-01-07","parallelism":1,"writeMode":"SHADOW_ONLY"}
```

## K. 100 IMO 分块 × 西非/Lomé

```json
{"mode":"STS_PHASE1_REGION_SHADOW","seedFile":"/data/zhengyi/sts/sts-crude-identity-20260902.csv","imos":["1014876","1015258","1019539","1019541","1019735","1019747","1023425","1023516","1025485","1025497","1025502","1028384","1028528","1028683","1028695","1029883","1029895","1029924","1030088","1030404","1031032","1031044","1033705","1033717","1035545","1035557","1037048","1037139","1037933","1038884","1038896","1042500","1042512","1042524","1042550","1043657","1043669","1043695","1044534","1044869","1044871","1050959","1052878","1052880","1055155","1055167","1055181","1055387","1057622","1058030","1058092","1059577","1062952","1064376","1070569","1070832","1070844","1071549","1074058","1079199","1081116","1081128","1081130","1085473","1085485","1085497","1086714","1086726","1090777","1090973","1097775","1100716","1113622","1165340","1165376","8112201","8117512","8413502","8508292","9004530","9038828","9038830","9038866","9038880","9038892","9039626","9050577","9056806","9079066","9079107","9081174","9085376","9108154","9116412","9153537","9159828","9171450","9171462","9172038","9183934"],"from":"2026-08-01 00:00:00","to":"2026-08-08 00:00:00","windowHours":24,"activityMinSpeedKn":0.0,"activityMaxSpeedKn":4.0,"regionId":"WEST_AFRICA_LOME_TEST_V1","regionVersion":"20260903-v1","regionWkt":"POLYGON((-1.5 4.0,2.5 4.0,2.5 7.5,-1.5 7.5,-1.5 4.0))","analysisBufferNm":10.0,"batchNo":"lome-202608-001-01-07","parallelism":1,"writeMode":"SHADOW_ONLY"}
```

# 16. Seed 船队分批策略（Seed Fleet Batch Strategy）

786 个不同 Seed IMO 应拆成 8 个分块：100/100/100/100/100/100/100/86；若 Replay 压力较大，可改用更小的 50 IMO 分块。各区域保持相同 IMO 分块，以便结果可比。批次标识必须编码区域、月份、分块和时间段，例如 `pg-202608-001-01-07`、`pg-202608-001-08-14`、`pg-202608-002-01-07`。

对于一个“区域月”，当前代码要求拆成以下区间，每段均不得超过七天：

| 区间 | `from` | 2026 年 8 月 `to` 示例 |
|---|---|---|
| 01–07 | `2026-08-01 00:00:00` | `2026-08-08 00:00:00` |
| 08–14 | `2026-08-08 00:00:00` | `2026-08-15 00:00:00` |
| 15–21 | `2026-08-15 00:00:00` | `2026-08-22 00:00:00` |
| 22–28 | `2026-08-22 00:00:00` | `2026-08-29 00:00:00` |
| 29–月末 | `2026-08-29 00:00:00` | `2026-09-01 00:00:00` |

每个批次的运维顺序：记录参数 → 执行一次 → 保存 XXL Summary → 运行 DB 核验查询 → 核对 `shadowInserted`/重复数 → 抽样并标注候选 → 完成后再扩大时间或船队范围。不同参数集绝不能复用同一 `batchNo`。

# 17. 数据库 Schema（Database Schema）

`dataanalysis.ship_sts_shadow_event` 是实验性独立 Shadow 表。它不是 `dataanalysis.ship_behavior_event`，没有轨迹点明细表，不会在 Spring 启动时自动创建，并通过 `INSERT IGNORE` 和唯一键 `(batch_no,event_key)` 实现幂等。

| 字段 | 含义 / 当前填充方式 |
|---|---|
| `id` | Shadow 记录自增 ID |
| `batch_no` | XXL 测试批次标识 |
| `event_key` | 对区域、OWN 身份、TARGET MMSI、事件区间和基线规则计算的 SHA-256 |
| `algorithm_version` | `STS_PHASE1_2_REGION_SHADOW_V1` |
| `region_id`、`region_version` | 提交的测试区域标识 |
| `own_imo`、`own_mmsi` | Seed OWN 身份和该窗口使用的 MMSI |
| `target_imo`、`target_mmsi` | 仅 Seed 查询成功时有 TARGET IMO；TARGET MMSI 来自 Replay |
| `identity_mode` | OWN 的 Seed 身份模式 |
| `start_time`、`end_time`、`duration_minutes` | Detector 事件区间和持续时间 |
| `center_lon`、`center_lat` | 用于核心区域归属的双船平均中心 |
| `behavior_type` | `BASELINE_ALONGSIDE_CANDIDATE` |
| `baseline_rule` | `ALONGSIDE_V3_BASELINE` |
| `min_distance_nm`、`avg_distance_nm` | Detector 双船距离摘要 |
| `own_avg_speed_kn` | Detector 计算的 OWN 平均速度 |
| `target_avg_speed_kn` | 字段存在；当前映射留空为 null |
| `behavior_score` | 当前 Alongside 分数；仅辅助排序，不是确认 STS 的概率 |
| `target_role` | 字段存在；当前映射留空为 null |
| `review_status` | 当前插入值 `PENDING` |
| `score_detail_json` | Detector 证据/评分明细 JSON |
| `created_at` | DB 插入时间 |

# 18. Alongside / STS Schema 映射（Alongside / STS Schema Mapping）

## ALONGSIDE_VS_STS_SCHEMA_MAPPING

| 原 Alongside 字段 | STS Shadow 字段 | 含义相同？ | 转换方式 | 是否缺失？ |
|---|---|---|---|---|
| `id` | `id` | 仅表内有效 | 不具备跨表身份等价性 | 否 |
| `behavior_type` | `behavior_type` | 否 | 将 Shadow 行别名为 `ALONGSIDE`；另行保留原 Shadow 类型 | 否 |
| `mmsi` | `own_mmsi` | 是 | 别名 | 否 |
| `target_mmsi` | `target_mmsi` | 是 | 直接映射 | 否 |
| `start_time`、`end_time` | 同名 | 是 | 直接映射 | 否 |
| `duration_minutes` | 同名 | 是 | 必要时转换 INT/BIGINT | 否 |
| `start_lon`、`start_lat` | 无 | 否 | 无法从中心点可靠推导 | 是 |
| `end_lon`、`end_lat` | 无 | 否 | 无法从中心点可靠推导 | 是 |
| `center_lon`、`center_lat` | 同名 | 是 | 直接映射 | 否 |
| `min_distance_nm` | 同名 | 是 | 直接映射 | 否 |
| `max_distance_nm` | 无 | 否 | score JSON 有 `p90DistanceNm`/特征，但含义不等价 | 是 |
| `avg_distance_nm` | 同名 | 是 | 直接映射 | 否 |
| `distance_variance_nm2` | JSON 特征 | 部分 | `JSON_EXTRACT(score_detail_json,'$.distanceVarianceNm2')` | 缺少独立列 |
| `course_difference_degree` | JSON 特征 | 部分 | 提取 `$.courseDifferenceDegree` | 缺少独立列 |
| `speed_difference_knots` | JSON 特征 | 部分 | 提取 `$.avgRelativeSpeed` | 缺少独立列 |
| `parallel_ratio` | JSON 特征 | 部分 | 提取 `$.headingParallelRatio` | 缺少独立列 |
| `near_port`、`near_anchor`、`open_sea` | 无 | 否 | 当前 STS context 不完整，不得虚构 false 值 | 是 |
| `own_ship_type`、`target_ship_type` | 无 | 否 | TARGET 角色列未填充，且角色不等于原始船型 | 是 |
| `avg_speed` | `own_avg_speed_kn` | 是 | 别名 | 否 |
| `point_count` | JSON 特征 | 部分 | 提取 `$.matchedPointCount` | 缺少独立列 |
| `rule_version` | `algorithm_version` + `baseline_rule` | 部分 | 将算法版本设为别名，单独暴露基线规则 | 否 |
| `batch_no` | 同名 | 是 | 直接映射 | 否 |
| `event_sub_type` | Shadow `behavior_type` | 部分 | 别名 `BASELINE_ALONGSIDE_CANDIDATE` | 否 |
| `event_score` | `behavior_score` | 在同一 Detector 评分语义下是 | 别名 | 否 |
| `confidence_level` | JSON 特征 | 部分 | 提取 `$.behaviorConfidence` | 缺少独立列 |
| `exclusion_flag`、`exclusion_reason` | JSON 字段 | 部分 | JSON 提取 | 缺少独立列 |
| `ais_gap_count`、`max_ais_gap_minutes`、`track_coverage_ratio` | JSON 特征 | 部分 | JSON 提取 | 缺少独立列 |
| `score_detail_json` | 同名 | 是 | 直接映射 | 否 |
| `event_source` | 无 | 否 | 兼容视图中使用常量 `STS_REGION_SHADOW` | 是 |
| `region_id` | `region_id` | 是 | 直接映射 | 否 |
| `region_fingerprint` | 无 | 否 | 当前 Shadow 保存 region version，不保存几何指纹 | 是 |
| `event_key` | 同名 | 相似 | Shadow key 使用 STS 专用哈希输入 | 否 |
| `ship_behavior_event_detail` 明细字段 | 无 STS 明细表 | 否 | 可视化复核需要重新查询轨迹 | 是 |

兼容 SELECT（不修改 Schema）：

```sql
SELECT s.id, 'ALONGSIDE' AS behavior_type, s.own_mmsi AS mmsi, s.target_mmsi, s.start_time, s.end_time, CAST(s.duration_minutes AS SIGNED) AS duration_minutes, NULL AS start_lon, NULL AS start_lat, NULL AS end_lon, NULL AS end_lat, s.min_distance_nm, NULL AS max_distance_nm, s.avg_distance_nm, CAST(JSON_UNQUOTE(JSON_EXTRACT(s.score_detail_json,'$.distanceVarianceNm2')) AS DECIMAL(18,8)) AS distance_variance_nm2, CAST(JSON_UNQUOTE(JSON_EXTRACT(s.score_detail_json,'$.courseDifferenceDegree')) AS DECIMAL(18,8)) AS course_difference_degree, CAST(JSON_UNQUOTE(JSON_EXTRACT(s.score_detail_json,'$.avgRelativeSpeed')) AS DECIMAL(18,8)) AS speed_difference_knots, CAST(JSON_UNQUOTE(JSON_EXTRACT(s.score_detail_json,'$.headingParallelRatio')) AS DECIMAL(18,8)) AS parallel_ratio, NULL AS near_port, NULL AS near_anchor, NULL AS open_sea, NULL AS own_ship_type, NULL AS target_ship_type, s.own_avg_speed_kn AS avg_speed, CAST(JSON_UNQUOTE(JSON_EXTRACT(s.score_detail_json,'$.matchedPointCount')) AS UNSIGNED) AS point_count, s.algorithm_version AS rule_version, s.batch_no, s.behavior_type AS event_sub_type, s.behavior_score AS event_score, JSON_UNQUOTE(JSON_EXTRACT(s.score_detail_json,'$.behaviorConfidence')) AS confidence_level, s.center_lon, s.center_lat, s.score_detail_json, 'STS_REGION_SHADOW' AS event_source, s.region_id, NULL AS region_fingerprint, s.event_key, s.own_imo, s.target_imo, s.identity_mode, s.region_version, s.baseline_rule, s.target_avg_speed_kn, s.target_role, s.review_status, s.created_at FROM dataanalysis.ship_sts_shadow_event s;
```

该映射无法重建起止坐标、最大距离、场景标志、原始船型、区域指纹或 `ship_behavior_event_detail` 轨迹快照。本地分析必须将这些列视为缺失，不得把它们当成等价的 0/false。

# 19. 数据库核验 SQL（Database Verification SQL）

以下语句供后续获得授权后执行；生成本文档时没有执行任何数据库操作。

1. 表是否存在

```sql
SHOW TABLES LIKE 'ship_sts_shadow_event';
```

2. 批次数量

```sql
SELECT batch_no, COUNT(*) AS event_count, MIN(created_at) AS first_inserted_at, MAX(created_at) AS last_inserted_at FROM dataanalysis.ship_sts_shadow_event GROUP BY batch_no ORDER BY MAX(created_at) DESC;
```

3. 事件列表

```sql
SELECT id,batch_no,event_key,region_id,own_imo,own_mmsi,target_imo,target_mmsi,start_time,end_time,duration_minutes,center_lon,center_lat,min_distance_nm,avg_distance_nm,own_avg_speed_kn,target_avg_speed_kn,behavior_score,target_role,review_status,created_at FROM dataanalysis.ship_sts_shadow_event WHERE batch_no='FILL_BATCH_NO' ORDER BY start_time,own_imo,target_mmsi;
```

4. 自身 MMSI — 预期 0 行

```sql
SELECT id,batch_no,event_key,own_mmsi,target_mmsi FROM dataanalysis.ship_sts_shadow_event WHERE own_mmsi IS NOT NULL AND target_mmsi IS NOT NULL AND own_mmsi=target_mmsi;
```

5. 自身 IMO — 预期 0 行

```sql
SELECT id,batch_no,event_key,own_imo,target_imo,own_mmsi,target_mmsi FROM dataanalysis.ship_sts_shadow_event WHERE own_imo IS NOT NULL AND target_imo IS NOT NULL AND own_imo=target_imo;
```

6. 重复 event_key — 预期 0 行

```sql
SELECT batch_no,event_key,COUNT(*) AS duplicate_count FROM dataanalysis.ship_sts_shadow_event GROUP BY batch_no,event_key HAVING COUNT(*)>1;
```

7. 区域分布

```sql
SELECT region_id,region_version,COUNT(*) AS candidate_count,COUNT(DISTINCT batch_no) AS batch_count FROM dataanalysis.ship_sts_shadow_event GROUP BY region_id,region_version ORDER BY candidate_count DESC;
```

8. OWN IMO 事件数量排名

```sql
SELECT own_imo,COUNT(*) AS candidate_count,COUNT(DISTINCT target_mmsi) AS distinct_targets,MIN(start_time) AS first_event,MAX(end_time) AS last_event FROM dataanalysis.ship_sts_shadow_event GROUP BY own_imo ORDER BY candidate_count DESC,own_imo LIMIT 100;
```

9. TARGET 角色分布（当前 Phase1.2 预期为 null）

```sql
SELECT COALESCE(target_role,'<NULL_NOT_POPULATED>') AS target_role,COUNT(*) AS candidate_count FROM dataanalysis.ship_sts_shadow_event GROUP BY COALESCE(target_role,'<NULL_NOT_POPULATED>') ORDER BY candidate_count DESC;
```

10. 持续时间分布

```sql
SELECT CASE WHEN duration_minutes<30 THEN '20-29' WHEN duration_minutes<45 THEN '30-44' WHEN duration_minutes<60 THEN '45-59' WHEN duration_minutes<120 THEN '60-119' WHEN duration_minutes<360 THEN '120-359' ELSE '360+' END AS duration_bucket,COUNT(*) AS candidate_count FROM dataanalysis.ship_sts_shadow_event GROUP BY duration_bucket ORDER BY MIN(duration_minutes);
```

11. 距离分布

```sql
SELECT CASE WHEN avg_distance_nm<=0.05 THEN '<=0.05' WHEN avg_distance_nm<=0.10 THEN '0.05-0.10' WHEN avg_distance_nm<=0.15 THEN '0.10-0.15' ELSE '>0.15_OR_NULL' END AS avg_distance_nm_bucket,COUNT(*) AS candidate_count FROM dataanalysis.ship_sts_shadow_event GROUP BY avg_distance_nm_bucket ORDER BY MIN(avg_distance_nm);
```

12. behavior score 分布

```sql
SELECT CASE WHEN behavior_score IS NULL THEN 'NULL' WHEN behavior_score<=20 THEN '00-20' WHEN behavior_score<=40 THEN '21-40' WHEN behavior_score<=60 THEN '41-60' WHEN behavior_score<=80 THEN '61-80' ELSE '81-100' END AS score_bucket,COUNT(*) AS candidate_count FROM dataanalysis.ship_sts_shadow_event GROUP BY score_bucket ORDER BY MIN(behavior_score);
```

13. 各区域候选数量

```sql
SELECT region_id,COUNT(*) AS candidate_count,COUNT(DISTINCT own_imo) AS own_imo_count,COUNT(DISTINCT target_mmsi) AS target_count FROM dataanalysis.ship_sts_shadow_event GROUP BY region_id ORDER BY candidate_count DESC;
```

14. 运行输出 / DB 插入数核对

```sql
SELECT batch_no,COUNT(*) AS db_rows,COUNT(DISTINCT event_key) AS distinct_event_keys,SUM(review_status='PENDING') AS pending_rows,MIN(created_at) AS first_inserted_at,MAX(created_at) AS last_inserted_at FROM dataanalysis.ship_sts_shadow_event WHERE batch_no='FILL_BATCH_NO' GROUP BY batch_no;
```

对于新的唯一批次，将 `db_rows` 与 XXL 运行 Summary 中的 `shadowInserted` 比较。同一 `batchNo` 重跑时，核对 Summary 的 `shadowDuplicateSkipped`；由于使用 `INSERT IGNORE`，第二次运行可能插入 0 行，而 DB 行数保持不变。除非插入失败导致该 IMO 中断，还应满足 `regionAcceptedEvents = shadowInserted + shadowDuplicateSkipped`。

# 20. 候选人工复核 SQL（Candidate Review SQL）

当前 `target_role` 通常为 null，因此角色排序只是面向未来兼容，不能改善当前排序。以下 Top 50 查询依次优先考虑 score、持续时间、距离和角色合理性：

```sql
SELECT id,batch_no,event_key,region_id,region_version,own_imo,own_mmsi,target_imo,target_mmsi,start_time,end_time,duration_minutes,center_lon,center_lat,min_distance_nm,avg_distance_nm,own_avg_speed_kn,target_avg_speed_kn,behavior_score,target_role,review_status,score_detail_json FROM dataanalysis.ship_sts_shadow_event WHERE batch_no='FILL_BATCH_NO' AND review_status='PENDING' ORDER BY behavior_score DESC,duration_minutes DESC,avg_distance_nm ASC,CASE target_role WHEN 'OIL_TANKER' THEN 0 WHEN 'PRODUCT_TANKER' THEN 1 WHEN 'LARGE_MERCHANT' THEN 2 WHEN 'BARGE_OR_LIGHTER' THEN 3 WHEN 'UNKNOWN' THEN 4 WHEN 'BUNKER_OR_SUPPLY' THEN 5 ELSE 6 END ASC,start_time ASC LIMIT 50;
```

轨迹复核时，应针对双方 MMSI 单独查询带缓冲时间段的 AIS 数据，例如事件开始前 30 分钟至结束后 30 分钟。Shadow Schema 没有轨迹点明细表。

# 21. 人工复核标签（Manual Review Labels）

当前字段类型为 `VARCHAR(32)`，Phase1.2 只写入 `PENDING`。以下标签是人工复核约定（`PROPOSED_PHASE2`），不代表 Schema 或代码已经修改：

| 标签 | 含义 |
|---|---|
| `CONFIRMED_STS_LIKE` | 人工观察可信的离岸稳定横向并靠、疑似过驳事件 |
| `NORMAL_BUNKERING` | 加油/补给行为，不是高风险原油 STS |
| `PORT_OPERATION` | 正常港口/码头接触 |
| `ANCHORAGE_OPERATION` | 普通锚地相邻/作业 |
| `TUG_ASSIST` | 拖轮/服务协助 |
| `FOLLOWING_OVERTAKING` | 纵向跟随或追越误报 |
| `CROSSING` | 交叉相遇误报 |
| `BAD_AIS` | 身份跳变、不可能点、时间稀疏/错位或其他 AIS 缺陷 |
| `UNCERTAIN` | 证据不足，既无法接受也无法归入具体误报类别 |
| `FALSE_POSITIVE` | 已确认误报，但不属于更具体的标签 |

应维护外部标注日志，记录复核人、复核时间、证据链接/截图、自由文本原因和规则版本。在更新流程达成一致前，不要通过不可追踪的编辑滥用 `review_status`。

# 22. 准确率优先的校准策略（Accuracy-First Calibration Strategy）

验收理念是 Precision First。100 艘 × 7 天扫描只产生少量候选，如果其中大部分经人工观察都类似离岸稳定横向并靠，就是好结果。反之，如果大量结果由拖轮作业、港内靠泊、正常锚地船舶、加油补给、跟随或交叉构成，说明规则过宽。

建议调优顺序：

1. 港口 / 泊位 / 锚地排除。
2. TARGET 角色排除和复核路由。
3. 持续时间。
4. 距离和动态距离。
5. 相对速度。
6. 横向并靠几何和相对方位。
7. Heading/course 证据。
8. 距离稳定性。

不要一开始只提高 score 阈值：综合分数可能掩盖关键业务 Gate 的失败。每个校准批次都应报告候选数、可人工复核数、标签分布、估算 Precision、Replay 失败/截断、TARGET 船型/IMO 缺失率、Heading 缺失率，以及港口/锚地重叠率。只有掌握误报类别后才能扩大范围。

# 23. 当前局限（Current Limitations）

- `CURRENT`：Phase1.2 是带区域 Gate 的 Alongside V3 基线，不是严格 STS。
- `NOT_CURRENTLY_ENFORCED`：STS Detector 没有接收港口、泊位、锚地、已知 STS 区或加油区 Gate。
- `NOT_CURRENTLY_ENFORCED`：没有相对方位、舷侧稳定性、纵/横向偏移、基于动态船长的距离，也没有显式跟随/追越拒绝。
- `NOT_CURRENTLY_ENFORCED`：Phase1.2 映射未解析/存储 TARGET 角色；`target_role` 和 `target_avg_speed_kn` 保持 null。
- `PARTIAL`：TARGET IMO 只通过原油油轮 Seed 解析，因此大部分非 Seed 交易对手 IMO 可能为 null。
- `CURRENT`：逐点接受缺失 course，但正式候选平行比例仍可能将片段降为弱候选。
- `CURRENT`：弱候选和正式候选都会落库；`behavior_type` 不区分两者，需要通过 score JSON 查看补充证据。
- `CURRENT`：默认 Detector 最低速度为 0，因此 `stationaryPair` 不可达；不过近零速度点仍可通过普通 0–2 kn 分支。
- `CURRENT`：`windowHours` 和配置的 `candidate.min-speed` 会被解析/注入，但不会改变当前相关 Service 逻辑。
- `CURRENT`：时间校验使用整天截断（`Duration.toDays()`）；运维仍必须使用显式 <=7 天区间，不应依赖边界舍入行为。
- `CURRENT`：事件中心位于核心多边形内，不代表整个事件始终位于其中。
- `CURRENT`：Shadow 没有事件级轨迹明细快照，限制了可复现的可视化分析。
- `TEST_ONLY`：目录内所有区域均为广域矩形工程 bbox，不是海事/港口/锚地真值数据。

# 24. 建议的下一阶段开发（Recommended Next Development）

本计划不实施任何开发。如果 Phase2 获得批准，应按以下克制顺序推进，并先写验收测试再编码：

1. 增加经过审计的港口、泊位、锚地和正常加油区多边形/距离，将 `PHYSICAL_ALONGSIDE` 与风险分类分开保存。
2. 解析 TARGET 主数据，扩展 TARGET 角色以区分原油油轮/成品油轮，并明确路由加油船、拖轮、引航船、服务船、渔船和未知船型。
3. 引入 `STRICT_UNDERWAY_STS_V1`，采用历史已核实的分位数、同步、动态距离、舷侧方位、稳定性和跟随拒绝 Gate。
4. 单独引入 `STATIONARY_DRIFT_STS_V1`，使用更强的持续时间、漂移一致性、反锚泊和证据完整性要求。
5. 在晋级生产前，通过版本化 Shadow Schema 或配套明细产物持久化缺失的复核特征（TARGET 速度/角色、P10/P50/P90、相对方位指标、场景距离、Replay 完整性）。
6. 使用标注数据和准确率优先 Gate 逐区域校准。只有 Precision 稳定后，才能将结果从 `BASELINE_ALONGSIDE_CANDIDATE` 晋级为高风险 STS 分类。

生产晋级标准应包含可复现测试：自身身份拒绝、港口/泊位/锚地排除、拖轮/引航/服务路由、加油复核、跟随/交叉拒绝、航行中接受、静止漂移接受、Replay 不完整处理，以及 Shadow 幂等持久化。

# 25. Phase 1.3 — DB 批量参数（PHASE1_3_DB_BATCH_PARAMETERS）

## 25.1 目标

Phase 1.3 将明文 IMO 数组、seedFile 运行时依赖和 regionWkt/regionId/regionVersion/analysisBufferNm 明文配置替换为 **DB Seed Batch + DB Region Set**，使 XXL Job 参数足够短，支持 786 Seed × 多风险区域 × 多时间切片的批量历史计算。

旧的明文参数仍用于 debug 和单区域快速验证。生产批量运行优先使用新参数格式。

## 25.2 新 Handler

**Handler**: `shipStsHistoryRegionSetShadowJobHandler`

- 从 DB 读取 seed identity（`dataanalysis.ship_sts_seed_identity`）
- 从 DB 读取 region set（`dataanalysis.ship_sts_risk_region` + `dataanalysis.ship_sts_region_set_item`）
- 复用 Phase 1.2 核心 pipeline（Activity Window → Replay → Self-Exclusion → ShipAlongsideDetector → Shadow）
- 多区域重叠时执行 **PRIMARY_REGION_ATTRIBUTION_V1**，每事件只写 1 条 Shadow
- 硬编码：`mode = STS_PHASE1_REGION_SET_SHADOW`、`writeMode = SHADOW_ONLY`、`parallelism = 1`
- 算法版本：`STS_PHASE1_3_REGION_SET_SHADOW_V1`

## 25.3 参数格式（单行 JSON）

```json
{"seedBatchNo":"sts-crude-identity-20260902-v1","expectedShipCount":786,"shipOffset":0,"shipLimit":100,"regionSetCode":"GLOBAL_STS_TEST_REGION_SET_V1","from":"2026-08-01 00:00:00","to":"2026-08-08 00:00:00","batchNo":"sts-global-20260801-001"}
```

| 参数 | 必填 | 规则 |
|---|---|---|
| `seedBatchNo` | 是 | 种子批次标识，对应 `ship_sts_seed_identity.seed_batch_no` |
| `expectedShipCount` | 是 | 预期 distinct IMO 数；与 DB 实际计数不一致时 fail closed |
| `shipOffset` | 是 | 分页偏移，>= 0 |
| `shipLimit` | 是 | 每页 IMO 数，1–100，禁止 0=全部 |
| `regionSetCode` | 是 | 区域集合标识，对应 `ship_sts_region_set_item.region_set_code` |
| `from` / `to` | 是 | `yyyy-MM-dd HH:mm:ss`，时长 <= 7 天 |
| `batchNo` | 是 | 运行批次唯一标识 |

## 25.4 生产安全约束

- `expectedShipCount` 与 DB 实际 `COUNT(DISTINCT imo)` 不一致 → **fail closed**，不执行任何计算
- `shipLimit` 必须 1–100，避免错误参数导致一次跑完整 786 船队
- `regionSetCode` 不存在或 resolved regions = 0 → **fail closed**
- 所有区域 status 必须为 `TEST` 或 `APPROVED`，且 enabled = 1
- Shadow 写入仍为 `INSERT IGNORE` 幂等，唯一键 `(batch_no, event_key)`
- **不写入** `ship_behavior_event` 等任何生产表

## 25.5 PRIMARY_REGION_ATTRIBUTION_V1

当事件中心点落在多个区域核心多边形内时，按以下优先级选择 **唯一 primary region**，只写 1 条 Shadow：

1. `region_set_item.priority` 最小者（数值越小优先级越高）
2. 若相同，核心多边形面积更小者
3. 若仍相同，`region_code` 字典序小者

其他匹配区域记录在 `score_detail_json.matchedRegionCodes` 数组中，用于审计。

## 25.6 Shadow Provenance

Phase 1.3 向 `ship_sts_shadow_event` 新增两个 nullable 字段：

| 字段 | 含义 |
|---|---|
| `seed_batch_no` | 产生该事件的 Seed 批次标识 |
| `region_set_code` | 产生该事件的 Region Set 标识 |

旧数据为 NULL。新 Handler 必须填充。

## 25.7 可用 Region Sets

| regionSetCode | 区域数 | 包含区域 |
|---|---:|---|
| `PERSIAN_GULF_STS_TEST_V1` | 1 | PERSIAN_GULF_TEST_V1 |
| `RUSSIA_STS_TEST_V1` | 3 | RUSSIA_BALTIC_TEST_V1, RUSSIA_BLACK_SEA_TEST_V1, RUSSIA_FAR_EAST_TEST_V1 |
| `VENEZUELA_STS_TEST_V1` | 2 | VENEZUELA_JOSE_TEST_V1, VENEZUELA_PARAGUANA_TEST_V1 |
| `GLOBAL_STS_TEST_REGION_SET_V1` | 12 | 全部 12 个测试区域 |

所有区域 status = `TEST`、source_type = `TEST_ONLY_BBOX`，不是权威海事边界。

## 25.8 推荐参数模板

以下全部为单行 JSON，可直接复制到 XXL Job 参数框。

### Smoke（1 船 × 波斯湾 × 1 天）

```json
{"seedBatchNo":"sts-crude-identity-20260902-v1","expectedShipCount":786,"shipOffset":0,"shipLimit":1,"regionSetCode":"PERSIAN_GULF_STS_TEST_V1","from":"2026-08-01 00:00:00","to":"2026-08-02 00:00:00","batchNo":"sts-pg-smoke-20260801-001"}
```

### 100 船 × 波斯湾 × 7 天（第 1 块）

```json
{"seedBatchNo":"sts-crude-identity-20260902-v1","expectedShipCount":786,"shipOffset":0,"shipLimit":100,"regionSetCode":"PERSIAN_GULF_STS_TEST_V1","from":"2026-08-01 00:00:00","to":"2026-08-08 00:00:00","batchNo":"sts-pg-20260801-001"}
```

### 100 船 × 波斯湾 × 7 天（第 2 块）

```json
{"seedBatchNo":"sts-crude-identity-20260902-v1","expectedShipCount":786,"shipOffset":100,"shipLimit":100,"regionSetCode":"PERSIAN_GULF_STS_TEST_V1","from":"2026-08-01 00:00:00","to":"2026-08-08 00:00:00","batchNo":"sts-pg-20260801-002"}
```

### 100 船 × 全球 × 7 天

```json
{"seedBatchNo":"sts-crude-identity-20260902-v1","expectedShipCount":786,"shipOffset":0,"shipLimit":100,"regionSetCode":"GLOBAL_STS_TEST_REGION_SET_V1","from":"2026-08-01 00:00:00","to":"2026-08-08 00:00:00","batchNo":"sts-global-20260801-001"}
```

786 船队建议拆分为 8 块：0/100、100/100、200/100、300/100、400/100、500/100、600/100、700/86。各区域和时间切片使用相同 IMO 分块以便结果可比。

## 25.9 参数缩减对比

| 场景 | 旧参数（明文 IMO + regionWkt） | 新参数（DB Seed + Region Set） | 缩减比例 |
|---|---:|---:|---:|
| 100 IMO × 1 区域 | ~1800 字符 | ~220 字符 | ~88% |
| 786 IMO（完整船队） | 不可行（参数过长） | ~220 字符 | — |
| 12 区域全局扫描 | 需 12 个独立 Job | 1 个 Job（regionSetCode=GLOBAL） | — |

## 25.10 数据库准备 SQL 文件

DDL 和初始化 SQL 均不自动执行，需人工运行：

| 文件 | 用途 |
|---|---|
| `src/main/resources/sql/ship_sts_seed_identity.sql` | Seed Identity 表 DDL |
| `src/main/resources/sql/ship_sts_risk_region.sql` | Risk Region 表 DDL |
| `src/main/resources/sql/ship_sts_region_set_item.sql` | Region Set 表 DDL |
| `src/main/resources/sql/ship_sts_shadow_event_provenance_alter.sql` | Shadow 表新增 provenance 列 ALTER |
| `scripts/sts/generate_seed_import_sql.py` | 从 CSV 生成 Seed import SQL 的脚本 |
| `scripts/sts/init_global_test_region_set.sql` | 12 个测试区域 + 4 个 Region Set 初始化 SQL |
