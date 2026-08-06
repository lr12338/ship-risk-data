# 搭靠事件非对称船型识别与保险风险解释 V1

## 1. 背景与边界

本次规则将搭靠定义为：待评估商船与有效目标船之间，由持续近距离、低相对运动和稳定空间关系支持的接触行为。主船类型负责保险评估准入；目标船类型只负责事后解释，不是行为事实的门槛。

> 本系统输出的是保险风险调查信号，不是对走私、制裁规避、非法过驳或其他违法行为的事实认定。

本实现只使用现有 AIS、船型查询、港口和锚地数据。没有新增海岸线、开放海域、STS 或其他外部图层。Region V1 继续保持独立包、默认关闭、只读和 dry-run；徘徊规则未修改。

## 2. 基线调用链与问题

实际调用链为：

`ShipBehaviorAnalysisService` → `TrajectoryLoaderService` → 主船低速窗口 → `ShipAlongsideReplayCandidateProvider` → `SpatialAisReplayClient` → `ShipAlongsideDetector` → `AlongsideSceneService` → 现有事件与 detail/trace 基础设施。

基线审计确认：

- `ShipAlongsideReplayCandidateProvider.collectCandidates` 在旧配置开启时删除已知非商船目标。
- `ShipAlongsideDetector.detectWithTrace` 对 `isKnownNonMerchantPair()` 直接返回空。
- `TrajectoryLoaderService.parseDouble` 把缺失 SOG/COG 转为 `0.0`，且没有 heading 有效性。
- 候选和确认阶段的最近点匹配都可能让多个主船点复用同一目标点。
- `ShipBehaviorAnalysisService.buildAlongsideContext` 使用整个主船窗口的经纬度均值。
- `AlongsideSceneService` 把不在现有港口/锚地内直接标成 `openSea=true`。

## 3. 新模型

### 3.1 主船资格

`ShipTypeClassifier.classifyOwnShip` 返回：

- `ELIGIBLE_MERCHANT`：油轮、散货、集装箱、杂货、化学品、液化气、滚装等明确货运商船。
- `INELIGIBLE`：拖轮、渔船、客船、游艇等明确非评估主船。
- `UNKNOWN`：画像缺失或文本不可识别；正式调用链默认不评估。

### 3.2 目标船角色

`classifyTargetShipWithReason` 输出原始类型、角色、分类置信度和 reasonCodes：

| 角色 | 典型类型 | 风险解释示例 |
|---|---|---|
| `LARGE_MERCHANT` | 油轮、散货、集装箱 | `POSSIBLE_STS_LIKE_CONTACT` |
| `BUNKER_OR_SUPPLY` | 加油、供油、补给 | `POSSIBLE_BUNKERING` / `OFF_PORT_BUNKERING_REVIEW` |
| `BARGE_OR_LIGHTER` | 驳船、油驳、货驳 | `POSSIBLE_LIGHTERING` |
| `TUG_OR_SERVICE` | 拖轮、工作艇、服务船 | `PORT_ASSISTANCE_LIKELY` / `OFF_PORT_SERVICE_CONTACT_REVIEW` |
| `FISHING_OR_SMALL_CRAFT` | 渔船、小艇、游艇 | `SMALL_VESSEL_TRANSFER_REVIEW` |
| `PASSENGER_OR_PILOT` | 客船、引航船 | 港口协助或未分类接触 |
| `UNKNOWN` | 缺失或不可识别 | `UNKNOWN_COUNTERPARTY_CONTACT` |

未知角色不会删除行为、自动升高风险或把行为置信度归零。

### 3.3 行为、类型与解释分层

现有正式表保持不变，兼容字段继续填充。扩展评估进入 `scoreDetailJson`：

- 行为事实：匹配点数、覆盖率、距离最小/平均/中位/P90/最大/方差、时间差中位/P90/最大、相对速度、有效航向比例、AIS gap、事件配对中点位置。
- `behaviorConfidence`：只由轨迹证据决定，沿用 `HIGH/MEDIUM/LOW`；证据不足不形成事件。
- `targetTypeConfidence`：目标角色字典命中为 `HIGH`，缺失或不可识别为 `LOW`。
- `interpretationConfidence`：结合行为质量与目标角色可靠性。
- `decisionStatus`：大型商船高质量移动伴随可为 `CONFIRMED`；补给、驳船、拖轮、小型船、未知目标及近静止模式为 `REVIEW`。
- `riskInterpretation`、`reasonCodes`、`ruleVersion`、`codeCommitSha`。

事件 ID、批次号、幂等键和现有 detail trace 配方未变。画像从 UNKNOWN 更新后可对相同行为重新解释，不改变行为事实键。

## 4. 检测与数据质量

- 候选目标速度范围默认 `0–4 kn`；正式移动伴随仍以主船 `1–3 kn`、目标 `0–3 kn`、相对速度 `≤1 kn` 和原有距离/时长/点数/平行度阈值确认。
- `UNDERWAY_PARALLEL_COMPANIONSHIP` 表示移动伴随。
- `STATIONARY_SIDE_BY_SIDE_REVIEW` 表示双方近静止且持续稳定接触，始终进入 REVIEW。AUTO 主船窗口仍从主船低速航行窗口开始；纯静止全量召回需要后续 Shadow 标注验证后再开启。
- 候选和确认匹配均按时间单调前进，每个目标点至多使用一次。
- 缺失 SOG、COG、Heading 保存为 `NaN + validity flag`，不会被解释为真实 0；无有效速度的点不能参与搭靠确认。
- 事件位置使用主/目标配对点中点；场景预查询使用开始、中间、结束三个配对位置的中点样本，不再使用整个主船窗口中心。
- 现有港口和锚地之外输出 `OUTSIDE_KNOWN_PORT_AND_ANCHOR`。`openSea` 兼容字段固定为 false；场景维表不完整时输出 `SCENE_CONTEXT_INCOMPLETE`。

## 5. 配置与兼容

规则版本：`ALONGSIDE_V3_ASYMMETRIC_COUNTERPARTY_V1`。

| 旧配置 | 新配置/语义 |
|---|---|
| `candidate.merchant-filter-enabled` | 保留读取并只告警一次；不再过滤目标船 |
| 无 | `own-ship.merchant-required=true`，正式主船严格准入 |
| 无 | `target-ship.filter-mode=NONE`，目标宽召回 |
| 无 | `target-ship.role-classification-enabled=true` |
| 无 | `target-ship.unknown-allowed=true` |
| `candidate.min/max-speed=1/3` | 默认改为召回 `0/4`，确认阈值仍独立为 `1/3` |
| 无 | `ship.behavior.code-commit-sha=UNKNOWN`，发布时由外置配置注入实际 SHA |

旧 merchant 配置即使仍为 true 也不会恢复错误的目标过滤。warning 使用进程级一次性开关，不在候选循环重复打印。

## 6. 旧规则与新规则对照

| 维度 | 旧规则 | 新规则 |
|---|---|---|
| 主船准入 | 船对商船判断混合 | 独立、严格的主船资格 |
| 目标召回 | 已知非商船被删除 | 除无效身份/轨迹/同步/坐标外宽召回 |
| 目标角色 | merchant true/false/null | 七类角色 + 置信度 + reasonCodes |
| 行为确认 | 主目标同速带，非商船提前退出 | 船型不参与事实门槛；单调一对一匹配 |
| 风险解释 | 单一 merchantPair 加分 | 按目标角色和场景给出非法律定性的解释码 |
| 置信度 | 单一 confidenceLevel | behavior / targetType / interpretation 三层 |
| 场景 | 窗口中心；港锚外即 OPEN_SEA | 配对事件点；港锚外仅表示已知区域之外 |
| 事件输出 | 基础列 + V3 score | 正式列兼容，扩展事实和评估写 detail JSON |

## 7. Shadow 验证

`AlongsideAsymmetricShadowReport` 是纯内存、无 Repository 依赖的本地 dry-run 汇总器，可记录：旧/新召回、旧/新事件、旧 merchantPair 漏报、轨迹质量淘汰、港内作业降权、港外 REVIEW，以及按目标角色分布。它不会写 `ship_behavior_event`。

建议在上线前对人工抽样集合统计新增候选/事件、UNKNOWN/补给/驳船/拖轮/小型船占比，并重点复核候选速度 `0–4 kn`、30 分钟、6 点、0.27 nm、相对速度 1 kn 和距离方差阈值。

## 8. 测试矩阵

- 主船：原油/成品油/化学品/集装箱/散货/杂货允许；拖轮/渔船拒绝；未知不静默放行。
- 目标角色：大型商船、补给/加油、驳船、拖轮/工作艇、渔船/小艇、缺失和不可识别。
- 召回：商船、渔船、补给、驳船、拖轮、小艇、缺失画像均保留；远距和点数不足淘汰。
- 检测：移动伴随、静止 REVIEW、非商船 REVIEW、未知目标高行为置信度、单目标点不可复用、反向航行/不同步/短暂接近淘汰。
- 缺失：SOG/COG/Heading 不成为 0；未知目标类型不影响行为置信度。
- 回归：搭靠生产测试、徘徊 V3、Region V1、完整 Maven 测试。

## 9. 示例结果

| 场景 | behaviorMode | role | behaviorConfidence | targetTypeConfidence | interpretationConfidence | interpretation | decision |
|---|---|---|---|---|---|---|---|
| 油轮 + 小型加油船，港外 | UNDERWAY_PARALLEL_COMPANIONSHIP | BUNKER_OR_SUPPLY | HIGH | HIGH | HIGH | OFF_PORT_BUNKERING_REVIEW | REVIEW |
| 散货船 + 驳船 | UNDERWAY_PARALLEL_COMPANIONSHIP | BARGE_OR_LIGHTER | HIGH | HIGH | HIGH | POSSIBLE_LIGHTERING | REVIEW |
| 油轮 + 拖轮，港内 | UNDERWAY_PARALLEL_COMPANIONSHIP | TUG_OR_SERVICE | HIGH | HIGH | HIGH | PORT_ASSISTANCE_LIKELY | REVIEW |
| 油轮 + 拖轮，已知港口外 | UNDERWAY_PARALLEL_COMPANIONSHIP | TUG_OR_SERVICE | HIGH | HIGH | HIGH | OFF_PORT_SERVICE_CONTACT_REVIEW | REVIEW |
| 商船 + 未知小艇 | UNDERWAY_PARALLEL_COMPANIONSHIP | UNKNOWN | HIGH | LOW | LOW | UNKNOWN_COUNTERPARTY_CONTACT | REVIEW |

各结果同时带有目标分类、行为模式、场景和航向质量 reasonCodes。

## 10. 已知限制与后续建议

- 当前船舶查询链只稳定提供 type，minType、IMO、船长和 DWT 尚未接入，因此这些扩展字段以缺失状态输出；应先完成字段质量盘点再提高分类置信度。
- 未覆盖政府/执法、海工工程船的独立角色，当前落入 UNKNOWN 或服务船；需人工标注后决定是否扩枚举。
- 纯静止 AUTO 主船窗口仍受现有低速窗口入口限制；本轮只在明确 pair/detector 路径支持静止 REVIEW，避免未经验证扩大生产召回。
- 港口和锚地数据不完整时不能推断开放海域；需要更完整图层时应作为独立数据发布评审，不与本规则耦合。
- 发布前应使用 dry-run Shadow 样本校准不同角色阈值，并人工复核 UNKNOWN、港外补给/驳运和长时拖轮接触。
