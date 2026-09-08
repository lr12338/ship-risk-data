# STS DEVELOPMENT

Document Status: APPROVED

Rule:
STS_RULE_SPEC_V1
APPROVED

Target Role:
TARGET_ROLE_POLICY_V1
APPROVED

Event Taxonomy:
EVENT_TAXONOMY_V1
APPROVED

Scene Policy:
SCENE_ISOLATION_DECISION_C
APPROVED

Fleet:
SEED_AUDIT_PENDING

currentStrictCodeExpectation:
757 IMO / 825 identity rows

Approved Fleet:
NONE_YET

Region:
MEETING_SCOPE_APPROVED
EXECUTABLE_REGION_MANIFEST_PENDING

Current Handler:
shipStsAlongsideHistoryJobHandler

Deprecated For New Development:
- shipStsHistoryBaselineJobHandler
- shipStsHistoryRegionShadowJobHandler
- shipStsHistoryRegionSetShadowJobHandler
- shipStsHistoricalFleetBackfillJobHandler

Risk Score:
DISABLED

Dark Algorithm:
OUT_OF_SCOPE

Formal Event Write:
PROHIBITED

---

# Current STS Calculation

本节是当前 STS 计算的首要阅读入口。它记录 2026-09-08 在本地
`codex/sts-phase1-4-fleet-backfill-v1`、Git HEAD
`d7482152b0f65fc9ae53aa71a3a7404f15f65eaa` 及当前 working tree 上核实的
RULE → CODE → EVIDENCE → VALIDATION 事实。

## 1. ONE_LINE_STS_RULE

当前程序先从待审计的原油船身份种子读取各历史 MMSI，在重点区域内筛出海上低速轨迹并回放附近 AIS；两船经时间对齐后满足持续近距离、低速、SOG 差和 COG 平行条件即形成 Alongside 物理证据，再排除同 IMO，但 Target Role 尚未真正把结果分成 `STS_CANDIDATE` 与 `STS_REVIEW_REQUIRED`。

注意：这句话描述的是当前真实代码，不表示 757 IMO / 825 identity rows 已成为 Approved Fleet。

## 2. ACTUAL_END_TO_END_PIPELINE

| Call | Input | Output | Main responsibility |
| --- | --- | --- | --- |
| `StsJobs.shipStsAlongsideHistoryJobHandler(String)` | XXL JSON | `ReturnT<String>` | 解析参数并调用当前唯一 STS Handler 路径。 |
| `StsAlongsideHistoryJobParams.parse/validate` | JSON | 已校验参数 | 强制 `STS_ALONGSIDE_HISTORY_V1`、`SHADOW_ONLY`、串行、1–7 日 slice。 |
| `StsAlongsideHistoryService.runOneInvocation` | 参数 | `RunResult` | 校验 757/825 strict seed、region set、fingerprint；claim 一个 slice task。 |
| `StsAlongsideHistoryService.processSlice` | slice、按 IMO 分组的 aliases | 每个 alias 的 Alongside traces | 将 slice 与 alias `first_seen/last_seen` 相交，建立 Region/Identity policy。 |
| `ShipBehaviorAnalysisService.computeAlongsideHistory` | OWN MMSI、时间、policy | `AlongsideWindowResult` | 进入 Existing Alongside Core，明确 `enableInsert=false`，不写正式事件表。 |
| `ShipBehaviorAnalysisService.analysisAlongsideOneWindow` | history window | traces + counters | HBase 轨迹、安全分片、World Sea 切分、低速窗口、local replay、候选过滤、detector、World Sea event gate。 |
| `TrajectoryLoaderService.loadSafely/load` | MMSI、时间、zoom | 排序去重后的 OWN points | 以 6 小时子查询从 HBase 读取简化轨迹，最多 50,000 点。 |
| `ShipAlongsideDetector.extractLowSpeedWindows` | OWN points | low-speed windows | 按 SOG 范围和 30 分钟 gap 切分，过滤不足 20 分钟窗口。 |
| `StsAlongsideHistoryService.RegionIdentityPolicy.acceptWindow` | window points | boolean | 只保留至少一点进入任一 analysis region 的窗口。 |
| `AlongsideReplayQueryPlanner.plan` | windows + OWN points | 去重 replay query plan | 每 30 分钟切 query，按整窗轨迹 bbox 外扩 1 海里。 |
| `ShipAlongsideReplayCandidateProvider.findCandidates` | replay plan | candidate targets | 调用 `SpatialAisReplayClient.query`，截断则二分至最小 10 分钟，按时距/空间/点数/时长预筛。 |
| `RegionIdentityPolicy.acceptCandidate` | target MMSI | boolean | 以 seed alias universe 排除同 MMSI、同 IMO。 |
| `ShipAlongsideDetector.detectWithTrace` | OWN/TARGET points、rule、context | `ShipBehaviorTrace` | 贪心一对一时间同步，应用 Physical Alongside 规则，片段内 merge，生成事件与证据。 |
| `WorldSeaEventGate.filterAndAnnotate` | traces | sea-mask PASS traces | 要求全部 matched pairs、pair midpoints 和 event center 满足 World Sea Mask。 |
| `StsAlongsideHistoryService.persistCandidate` | Alongside event | shadow row 或 skip | 再次同 IMO 排除、按 core region 归属、生成 eventKey、overlap skip，写 shadow。 |
| `StsShadowEventRepository.insertIfNoOverlappingPhysicalEvent` | `StsShadowEvent` | insert 1 / skip 0 | 仅写 `dataanalysis.ship_sts_shadow_event`；不写 formal event/detail。 |

简写：

`StsJobs.shipStsAlongsideHistoryJobHandler`
→ `StsAlongsideHistoryService.runOneInvocation`
→ `processSlice`
→ `ShipBehaviorAnalysisService.computeAlongsideHistory`
→ `analysisAlongsideOneWindow`
→ `TrajectoryLoaderService.loadSafely`
→ `ShipAlongsideDetector.extractLowSpeedWindows`
→ `AlongsideReplayQueryPlanner.plan`
→ `ShipAlongsideReplayCandidateProvider.findCandidates`
→ `ShipAlongsideDetector.detectWithTrace`
→ `WorldSeaEventGate.filterAndAnnotate`
→ `StsAlongsideHistoryService.persistCandidate`
→ `StsShadowEventRepository.insertIfNoOverlappingPhysicalEvent`。

当前真实链中不存在独立的 STS Classification method；这是规则对齐缺口，不得把
`persistCandidate` 的固定命名当成已经完成分类。

## 3. ALONGSIDE_PHYSICAL_RULE_V1

### 3.1 Trajectory and activity windows

- OWN 数据源：`TrajectoryLoaderService` 调用 `GetTrajectoryFromHbase`；`simplifyZoom=15` 时使用 `getVoffersFromHbase`。
- history range：父范围必须显式给出，最长约 5 年；task 默认 1 日、允许 1–7 日；每个 alias 实际范围为 task slice 与 `first_seen/last_seen` 的交集。
- safe load：按 6 小时查询、以 MMSI/time/lon/lat 去重、按时间排序，超过 50,000 点 fail closed。
- 清洗：丢弃空时间、非法经纬度、无效 SOG、SOG < 0 或 > 80；相同 time/lon/lat 连续重复点去重。
- World Sea：先将 OWN 轨迹按 sea mask 内外切段；mask 外点中断窗口。
- low-speed：当前 REMOTE_EFFECTIVE 为 `1.0 <= OWN SOG <= 3.0 kn`、持续至少 20 分钟、相邻点 gap > 30 分钟时切段、至少 2 点。near-zero SOG 被排除；没有 window merge。

### 3.2 Local replay and candidate selection

- temporal expansion：`0`；query 使用 low-speed window 原始起止时间。
- query slicing：每 30 分钟一段；最后一段可短于 30 分钟。
- spatial query：始终构造整窗 OWN points bbox，并向四周外扩 1.0 海里；request 中虽携带 default radius 0.5 海里，但 bbox 存在时 HTTP client 使用 bbox。
- query splitting：结果点数达到 `devSize=50,000` 视为 truncated；递归二分，直到子区间不能再大于 10 分钟；终端仍 truncated 则该 query incomplete，STS slice fail closed。
- max query span：实际 planner 为 30 分钟；配置 `replay.max-window-minutes=60` 没有 Java binding，当前不生效。
- candidate data source：`https://gm.hifleet.com/spatialais/getRecentAis` 的 Spatial AIS replay。
- candidate target 预筛：有效 MMSI 且不等于当前 OWN MMSI；TARGET 至少 3 个 clean points，并至少存在一个 `0 <= SOG <= 3.0 kn` 点。配置 `candidate.min-speed=1.0` 虽被注入，但代码未使用。
- synchronization：对按时间排序的 OWN point，按 TARGET 剩余序列寻找最近时间点；匹配后 target index 单调前进，形成一对一贪心同步。
- candidate match：时间差 <= 10 分钟、距离 <= 0.27 海里、matched points >= 3、首末 OWN matched time 持续 >= 20 分钟。

### 3.3 Detector

- confirm distance：每个 matched pair <= 0.27 海里。
- OWN/TARGET speed：OWN 处于 1.0–3.0 kn，或 OWN/TARGET 同时处于 0–<1.0 kn stationary pair；TARGET 始终 <= 3.0 kn。
- speed difference：`abs(OWN SOG - TARGET SOG) <= 1.0 kn`。这是 **SOG scalar difference**，不是 vector relative velocity。
- alignment：每个 pair 时间差 <= 10 分钟；<= 5 分钟用于 HIGH confidence。
- course：使用 AIS `COG/course`，不是 true heading。非 stationary 且两边 COG 有效时，pair course difference <= 30°；COG 缺失时该 pair 不被拒绝。
- segment admission：matched points >= 3 且持续 >= 20 分钟即可产生 weak/formal trace。
- formal vs weak：持续 >= 30 分钟、points >= 6、`headingParallelRatio >= 0.8` 才不是 weak；否则为 `WEAK_ALONGSIDE_CANDIDATE`。parallel ratio 分母是全部 matched points，缺失 COG 会降低 ratio。
- stationary behavior：stationary pair 可进入 detector；如果任一方平均匹配 SOG < 1.0，则 `behaviorMode=STATIONARY_SIDE_BY_SIDE_REVIEW`。
- confidence：HIGH 要求 points >= 6、coverage >= 0.8、最大对齐 gap <= 5 分钟、无 >30 分钟 AIS gap、parallel ratio >= 0.8；LOW 条件为 coverage < 0.6、points < 3 或最大对齐 gap >10 分钟；其余 MEDIUM。
- 当前 STS adapter 未过滤 weak trace，也未将 formal/weak 映射成批准的 Physical taxonomy；因此 shadow row 不等同于已正确分类的 `OBSERVED_ALONGSIDE`。

### 3.4 Merge, dedup and slice behavior

- detector fragment merge：同一次 target/window 内，相邻片段 gap 0–30 分钟且片段中心距离 <=1.0 海里时 merge；merged matches 会重新计算统计。
- cross-window / cross-alias / cross-slice：没有 fragment reconciliation；00:00、day boundary、history slice 或 7-day slice 都可能将一个行为切成多条候选。
- eventKey：SHA-256 of `primaryRegion|ownPhysicalIdentity|ownPhysicalIdentity|targetPhysicalIdentity|exactStart|exactEnd|ruleVersion|ruleHash`；精确起止变化会改变 key。
- duplicate：唯一键 `(batch_no,event_key)`；另在同 batch + primary region + own IMO + target IMO（未知 IMO 时 target MMSI）范围内，任何时间 overlap 即整条 skip，不 merge、不重算统计。
- A/B direction：未 canonical normalization；OWN/TARGET 方向不同可形成不同 identity/eventKey。
- region overlap：按 priority 升序、core area 较小、region code 排序选一个 primary；`allMatches` 没有写入当前 shadow row。eventKey/overlap 都包含 primary region，因此同一物理行为在不同 primary region/batch 可重复。
- region gate：window 只需至少一个点落入 analysis region；最终 event center 必须落入 core region。
- slice halo：不存在；slice 是实际 query boundary。

### 3.5 Scene and sea answers

| Facility | PHYSICAL_RULE | CONTEXT | PROVENANCE_ONLY | Actual behavior |
| --- | --- | --- | --- | --- |
| Port/Anchorage Scene | NO | YES | NO | 不决定 pair 的距离/速度 PASS；会写 scene/exclusion/score，因此不是 provenance-only。STS rule hash 不再读取 Loitering Scene snapshot/fingerprint；Scene unavailable 时显式为 `sceneType=UNKNOWN`、`sceneDataAvailable=false`、`sceneContextComplete=false`，nearPort/nearAnchor/outside/openSea 字段不输出，且不作 offshore 加分。 |
| World Sea Mask | YES | YES | NO | 先切 OWN low-speed windows；event gate 再要求全部 detector pair、pair midpoint、event center 都在 mask 内，并写 mask area/fingerprint，因此不是 provenance-only。 |

`SCENE_ISOLATION_SAFETY_FIX_V1` 已恢复 Loitering fail-closed 几何完整性，并完成 STS Scene 隔离与 UNKNOWN 表达。

## 4. STS_RULE_CARD_V1

| Step | Rule | Business Meaning | Actual Threshold / Condition | Code | Evidence | Output |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | OWN Fleet | 哪些物理船可作为 OWN | 当前代码硬校验 757 IMO / 825 enabled unique MMSI rows，且每行 `minotype='原油船'`；Approved Fleet 仍 PENDING SEED AUDIT | `StsAlongsideHistoryService.validateSeed`; `StsSeedIdentityRepository` | seed batch/count/fingerprint | selected IMO/aliases；不是 Approved Fleet |
| 2 | Identity / Alias | IMO 是物理身份，MMSI 是历史广播身份 | 按 IMO 分组所有 enabled seed rows；alias 查询范围与 observed bounds 相交 | `groupByImo`; `intersectObservedBounds` | own IMO/MMSI、identityMode、seed fingerprint | alias universe for query/self-exclusion |
| 3 | Trajectory Load | 读取 OWN 历史 AIS | HBase；zoom 15；6h 子查询；最多 50k 点；排序去重；非法位置/SOG 剔除 | `TrajectoryLoaderService.loadSafely/load`; detector `cleanAndSort` | traceRecipe OWN source/query range/zoom/point count/fingerprint | clean OWN points |
| 4 | Low-speed Window | 找到可能持续靠船的活动时段 | REMOTE: OWN SOG 1–3 kn；>=20 min；gap >30 min split；>=2 points；World Sea 外点切断 | `analysisAlongsideOneWindow`; `extractLowSpeedWindows`; `WorldSeaTrackSegmenter.splitInside` | window count/start/end/duration/center | low-speed windows |
| 5 | Local Replay | 只回放局部时间空间 AIS | 原时窗、无 halo；30min query；轨迹 bbox +1nm；50k 截断后下切至10min；不完整 fail closed | `AlongsideReplayQueryPlanner.plan`; `ShipAlongsideReplayCandidateProvider.executeReplay`; `SpatialAisReplayClient.query` | replay query/count/failure/truncated/raw/valid/parse/elapsed | replay tracks |
| 6 | Candidate Selection | 在回放船中找可能接近的 TARGET | target >=3 clean points；至少一 target point SOG 0–3；同步后 distance <=0.27nm、gap <=10min、>=3 pairs、>=20min | `collectCandidates`; `matchCandidate` | candidate match count/min/max/avg distance | `AlongsideCandidateTarget` |
| 7 | Trajectory Synchronization | 双船点按时间配对 | 按时间排序，OWN 顺序扫描，TARGET index 单调前进；最近时间且 <=10min | provider `findNearestTimePoint`; detector `findNearest` | exact `AlongsideEvidencePair` 在内存 trace；time-gap aggregates in JSON | matched pairs/segments |
| 8 | Physical Alongside | 两船持续近距离、相近速度和方向 | <=0.27nm；OWN 1–3kn 或双方<1kn；TARGET<=3kn；SOG difference<=1kn；COG diff<=30°（已知时）；weak >=3 points/20min，formal >=6/30min/parallel ratio>=0.8；World Sea gate PASS | `ShipAlongsideDetector.detectWithTrace`; `WorldSeaEventGate.filterAndAnnotate` | distance/duration/SOG difference/COG/time gap/parallel/coverage/mask | 当前 `ALONGSIDE` formal/weak trace；尚未显式输出 `OBSERVED_ALONGSIDE` |
| 9 | Same IMO Exclusion | 同一物理船换 MMSI 不得成为双船事件 | own alias set 命中、seed 中 target IMO=own IMO、或 target=current OWN MMSI 均 reject；detector 前后双重检查 | `RegionIdentityPolicy.acceptCandidate`; `persistCandidate` | seed lookup、selfIdentityRejected/selfPairEvents | no shadow row |
| 10 | Target Role | 区分 tanker、service、unknown 等替代解释 | 只读取 `recentships.type`，minotype=null；`LARGE_MERCHANT` 包含 tanker/bulk/container/cargo 等，不等于 tanker confirmed | `loadShipTypes`; `ShipTypeClassifier.classifyTargetShipWithReason` | targetShipTypeRaw、role/confidence/reasonCodes in JSON | detector decisionStatus only；shadow `target_role` 未赋值 |
| 11 | STS Classification | Physical 后再决定 Candidate/Review | 当前没有 STS classifier；adapter 对所有 region-accepted traces固定 `ALONGSIDE_DERIVED_STS_CANDIDATE` | `persistCandidate` | detector `decisionStatus` 未被 adapter 消费 | **未实现批准的 `STS_CANDIDATE` / `STS_REVIEW_REQUIRED`** |
| 12 | Context | Port/Anchorage/Service/Region 解释事件，不制造 physical PASS | Scene 在 detector 后影响 exclusion/score；region 是 window/persistence gate；Scene unavailable 显式 UNKNOWN 且不作 offshore 加分；SERVICE_INTERACTION 未生成 | `AlongsideSceneService`; `AlongsideContext`; `StsRegionSetGeometry` | scene fields、region/world sea fingerprints | context JSON / primary region；Service evidence 仍缺失 |
| 13 | Review Priority | 人工复核顺序与 physical confidence 分离 | STS `reviewPriority` 不存在；shadow `reviewStatus=PENDING`；Alongside `behaviorScore/severity` 被原样保存但不是批准的 STS Risk Score | detector `calculateScore`; `persistCandidate` | behaviorScore/severity/confidence | no reviewPriority；Risk Score 未作为 STS V1 正式能力启用 |
| 14 | Event Merge / Dedup | 同一物理行为不重复 | 窗内 merge gap<=30min且中心<=1nm；跨窗/跨 slice 不 merge；同 batch/region/own/target 时间 overlap 直接 skip | detector `mergeSegments`; repository `existsOverlappingPhysicalEvent`; `computeEventKey` | exact times/pair/region/rule hash | inserted or duplicate skipped；存在 lifecycle gap |
| 15 | Shadow Persistence | 仅保存可审计实验结果 | event center 必须在 core region；`INSERT IGNORE`; 仅 `ship_sts_shadow_event` | `persistCandidate`; `StsShadowEventRepository` | row + scoreDetailJson + fingerprints | shadow row；formal writer=0 |

## 5. STS_RULE_TO_CODE_MAPPING_V1

| Rule | Class | Method | Config | Evidence / Output | Alignment |
| --- | --- | --- | --- | --- | --- |
| R1 OWN Fleet | `StsAlongsideHistoryService`; `StsSeedIdentityRepository` | `validateSeed`; `selectImos/selectIdentityRows` | hard-coded 757/825; seedBatchNo | counts、seed fingerprint | PARTIAL |
| R2 Identity / Alias | `StsAlongsideHistoryService.RegionIdentityPolicy` | `acceptCandidate`; `persistCandidate` | seed batch | own aliases、IMO lookup、reject counters | PARTIAL |
| R3 Existing Alongside Core | `ShipBehaviorAnalysisService`; `ShipAlongsideDetector` | `computeAlongsideHistory`; `detectWithTrace` | Alongside rule keys | `ShipBehaviorTrace`, matched-pair aggregates | ALIGNED |
| Physical low-speed | `ShipAlongsideDetector` | `extractLowSpeedWindows` | candidate max speed; confirm min speed; duration/split gap | window metrics | ALIGNED |
| Replay planning/recovery | `AlongsideReplayQueryPlanner`; `ShipAlongsideReplayCandidateProvider` | `plan`; `executeReplay` | window=30, bbox=1nm, min split=10, devSize=50k | query/traceRecipe/replay counters | ALIGNED |
| Candidate min-speed | `ShipAlongsideReplayCandidateProvider` | `hasLowSpeedPoint` | `candidate.min-speed` injected but unused | none proving lower bound | PARTIAL |
| Physical pair rule | `ShipAlongsideDetector` | `isAlongsideCandidate`; `calculateFeature` | distance/speed/SOG diff/time/COG/points/duration | event fields + scoreDetailJson | ALIGNED |
| World Sea physical gate | `WorldSeaTrackSegmenter`; `WorldSeaEventGate` | `splitInside`; `filterAndAnnotate` | world-sea properties | mask decision/coverage/area/fingerprint；actual code contract | ALIGNED |
| Same IMO exclusion | `RegionIdentityPolicy`; `persistCandidate` | `acceptCandidate`; `persistCandidate` | seed batch | reject counters / no row；只覆盖当前 seed 已知 aliases | PARTIAL |
| R4 Target Role | `ShipTypeClassifier`; `ShipBehaviorAnalysisService` | `classifyTargetShipWithReason`; `buildAlongsideContext` | no active role config | role/raw type/reasons in JSON | PARTIAL |
| Tanker confirmed | `ShipTypeClassifier` | `classifyTargetShipWithReason` | none | `LARGE_MERCHANT` conflates tanker and other merchant | NOT_IMPLEMENTED |
| Service interaction | detector context | `decisionStatus/reasonCodes` | none | role may be present; `SERVICE_INTERACTION` absent | NOT_IMPLEMENTED |
| R5 STS Classification | no STS classifier | none | none | fixed `ALONGSIDE_DERIVED_STS_CANDIDATE` | NOT_IMPLEMENTED |
| R6 Context UNKNOWN | `AlongsideSceneService`; `AlongsideContext`; `ShipBehaviorAnalysisService` | `classify`; `asymmetricWithScene`; `alongsideRuleConfigHash` | `sceneAvailabilityPolicy=OPTIONAL_CONTEXT_UNKNOWN_ON_UNAVAILABLE` | UNKNOWN/availability/outside fields；STS hash 无 Scene snapshot 硬依赖 | ALIGNED |
| R7 Risk separation | detector + STS adapter | `calculateScore`; `persistCandidate` | score/severity keys | behaviorScore saved; reviewPriority absent | PARTIAL |
| Fragment merge | `ShipAlongsideDetector` | `mergeSegments/shouldMerge` | 30min/1nm | 单次 detector call 内重算 merged event | ALIGNED |
| Cross-slice lifecycle | no reconciler | repository overlap check only | sliceDays 1–7 | overlap skip without merged evidence | NOT_IMPLEMENTED |
| A/B canonical pair | no canonical normalizer | none | none | directional key | NOT_IMPLEMENTED |
| Region overlap ownership | `StsRegionSetGeometry` | `attributePrimary` | DB priority/geometry | primary only | PARTIAL |
| Shadow-only safety | `computeAlongsideHistory`; `StsShadowEventRepository` | `analysisAlongsideOneWindow(...enableInsert=false...)`; `insertIfNoOverlappingPhysicalEvent` | `writeMode=SHADOW_ONLY` | shadow row only | ALIGNED |
| Trace/provenance | `TraceRecipeService`; adapter | `attachAlongside`; `persistCandidate` | rule/config/code/seed/region fingerprints | JSON fingerprints/query recipe；exact pairs 未持久化且 runtime code SHA unknown | PARTIAL |

## 6. CURRENT_EVENT_EVIDENCE

当前 shadow row 直接保存：batch/eventKey、OWN IMO/MMSI、TARGET IMO（仅当 target 在同 seed 中可解析）/MMSI、OWN identityMode、start/end/duration、center、primary region/version、min/avg distance、OWN avg SOG、Alongside behaviorScore、baseline rule、reviewStatus。

`score_detail_json` 当前保存或继承：

- distance：min/avg/median/p90/max variance；
- timing：matched count、max/median/p90 pair time gap、high-gap ratio；
- motion：SOG scalar difference（JSON 键仍名 `avgRelativeSpeed`）、COG difference、parallel ratio、valid COG ratio、coverage、AIS gap；
- target：raw type、coarse Target Role、role confidence、reason codes；
- scene/context：Scene available 时有 nearPort、nearAnchor、sceneType、nearest port、outsideKnownPortAndAnchor；unavailable 时为 UNKNOWN 且不输出真假位置断言；另有 World Sea mask/area；
- replay：query count/failure/truncation/raw/valid/parse/elapsed；
- provenance：trace recipe、OWN/TARGET trajectory source/query range/point count/fingerprint、Alongside rule/config hash、seed batch、region fingerprint、configured code SHA。

当前不存在或未正确保存：

- 显式 `physicalEventType=OBSERVED_ALONGSIDE`；
- 显式 `stsClassification=STS_CANDIDATE/STS_REVIEW_REQUIRED`；
- `SERVICE_INTERACTION` / `ANCHORAGE_PROXIMITY` / `WAITING_OR_CONGESTION` taxonomy；
- shadow `target_role`（字段存在但当前 adapter 未赋值）；
- shadow `target_avg_speed_kn`（字段存在但当前 adapter 未赋值）；
- target minotype、metadata source/asOf/classifierVersion；
- canonical physical pair；
- associated regions；
- reviewPriority；
- supported duration、boundary/censored state、max unsupported gap 的明确字段；
- exact matched trajectory points / exact `AlongsideEvidencePair` 的 shadow 持久化。它们仅在运行时 trace 中存在，shadow 只保留 aggregates 和 trajectory fingerprints；
- 可信 deployed Git SHA（远程 configured SHA 与当前 JAR 构建不一致，JAR 无 git.properties）。

## 7. CURRENT_RUNTIME_PARAMETERS

来源定义：`CODE_DEFAULT` 来自 `@Value`/rule field；`LOCAL_CONFIG` 来自仓库
`src/main/resources/application.properties`；`REMOTE_EFFECTIVE` 来自 242 当前进程使用的
`/data/zhengyi/application.properties` 覆盖 JAR 内置 properties。242 检查为 READ ONLY。

| Rule | Java Field | Config Key | CODE_DEFAULT | LOCAL_CONFIG | REMOTE_EFFECTIVE | Effective Status |
| --- | --- | --- | --- | --- | --- | --- |
| Trajectory simplify | `trajectorySimplifyZoom` | `ship.behavior.trajectory.simplify-zoom` | 15 | 15 | 15 | REMOTE_EFFECTIVE |
| Safe load slice | `alongsideBatchTrajectoryLoadSliceHours` | `...batch.trajectory-load-slice-hours` | 6h | 6h | 6h | REMOTE_EFFECTIVE |
| Max OWN points | `alongsideBatchMaxPointsPerWindow` | `...batch.max-points-per-window` | 50000 | 50000 | 50000 | REMOTE_EFFECTIVE |
| Low-speed min | `alongsideConfirmMinSpeed` | `...confirm.min-speed` | 1.0kn | 1.0kn | 1.0kn | REMOTE_EFFECTIVE |
| Low-speed max | `alongsideCandidateMaxSpeed` | `...candidate.max-speed` | 4.0kn | 4.0kn | 3.0kn | REMOTE_EFFECTIVE |
| Low-speed min duration | `alongsideCandidateMinDurationMinutes` | `...candidate.min-duration-minutes` | 20min | 20min | 20min | REMOTE_EFFECTIVE |
| Low-speed split gap | `alongsideCandidateSplitGapMinutes` | `...candidate.split-gap-minutes` | 30min | 30min | 30min | REMOTE_EFFECTIVE |
| Replay enabled | `replayEnabled` | `...replay.enabled` | true | true | true | REMOTE_EFFECTIVE |
| Replay window | `replayWindowMinutes` | `...replay.window-minutes` | 30min | 30min | 30min | REMOTE_EFFECTIVE |
| Replay min split | `replayMinWindowMinutes` | `...replay.min-window-minutes` | 10min | 10min | 10min | REMOTE_EFFECTIVE |
| Replay max window | no Java field | `...replay.max-window-minutes` | UNKNOWN | 60min | 60min configured but unused | CONFIG_ONLY / ineffective |
| Replay bbox buffer | `replayBboxBufferNm` | `...replay.bbox-buffer-nm` | 1.0nm | 1.0nm | 1.0nm | REMOTE_EFFECTIVE |
| Replay dev size | `replayDevSize` | `...replay.dev-size` | 50000 | 50000 | 50000 | REMOTE_EFFECTIVE |
| Replay truncated split | `splitOnTruncated` | `...replay.split-on-truncated` | true | true | true | REMOTE_EFFECTIVE |
| Replay read timeout | `readTimeoutMs` | `...replay.read-timeout-ms` | 8000ms | 8000ms | 15000ms | REMOTE_EFFECTIVE |
| Max replay queries/window | `alongsideBatchMaxReplayQueriesPerWindow` | `...batch.max-replay-queries-per-window` | 300 | 300 | 300 | REMOTE_EFFECTIVE |
| Candidate distance | `candidateMaxDistanceNm` | `...candidate.max-distance-nm` | 0.27nm | 0.27nm | 0.27nm | REMOTE_EFFECTIVE |
| Candidate min SOG | `candidateMinSpeed` | `...candidate.min-speed` | 1.0kn | 0.0kn | 1.0kn | CONFIG_ONLY / field unused |
| Candidate max SOG | `candidateMaxSpeed` | `...candidate.max-speed` | 3.0kn | 4.0kn | 3.0kn | REMOTE_EFFECTIVE |
| Candidate align tolerance | `candidateMaxTimeGapMinutes` | `...candidate.max-time-gap-minutes` | 10min | 10min | 10min | REMOTE_EFFECTIVE |
| Candidate min points | `candidateMinPoints` | `...candidate.min-points` | 3 | 3 | 3 | REMOTE_EFFECTIVE |
| Candidate min duration | `candidateMinDurationMinutes` | `...candidate.min-duration-minutes` | 20min | 20min | 20min | REMOTE_EFFECTIVE |
| Confirm distance | `alongsideConfirmMaxDistanceNm` | `...confirm.max-distance-nm` | 0.15nm | 0.27nm | 0.27nm | REMOTE_EFFECTIVE |
| Confirm OWN/TARGET min SOG | `alongsideConfirmMinSpeed` | `...confirm.min-speed` | 1.0kn | 1.0kn | 1.0kn | REMOTE_EFFECTIVE |
| Confirm max SOG | `alongsideConfirmMaxSpeed` | `...confirm.max-speed` | 2.0kn | 3.0kn | 3.0kn | REMOTE_EFFECTIVE |
| Confirm SOG difference | `alongsideConfirmMaxRelativeSpeed` | `...confirm.max-relative-speed` | 1.0kn | 1.0kn | 1.0kn | REMOTE_EFFECTIVE |
| Confirm align tolerance | `alongsideConfirmMaxTimeGapMinutes` | `...confirm.max-time-gap-minutes` | 10min | 10min | 10min | REMOTE_EFFECTIVE |
| HIGH align tolerance | `alongsideConfirmHighConfidenceTimeGapMinutes` | `...confirm.high-confidence-time-gap-minutes` | 5min | 5min | 5min | REMOTE_EFFECTIVE |
| Formal duration | `alongsideConfirmMinDurationMinutes` | `...confirm.min-duration-minutes` | 30min | 30min | 30min | REMOTE_EFFECTIVE |
| Formal points | `alongsideConfirmMinPoints` | `...confirm.min-points` | 6 | 6 | 6 | REMOTE_EFFECTIVE |
| Course rule | `alongsideHeadingEnabled` | `...heading.enabled` | true | true | true | REMOTE_EFFECTIVE |
| Course difference | `alongsideHeadingParallelThresholdDegree` | `...heading.parallel-threshold-degree` | 30° | 30° | 30° | REMOTE_EFFECTIVE |
| Parallel ratio | `alongsideMinParallelRatio` | `...heading.min-parallel-ratio` | 0.8 | 0.8 | 0.8 | REMOTE_EFFECTIVE |
| Distance variance scoring | `alongsideMaxDistanceVarianceNm2` | `...distance.max-variance-nm2` | 0.0025nm² | 0.0025nm² | 0.0025nm² | REMOTE_EFFECTIVE（score, not PASS gate） |
| Fragment merge | `alongsideMergeEnabled` | `...merge.enabled` | true | true | true | REMOTE_EFFECTIVE |
| Merge gap | `alongsideMergeMaxGapMinutes` | `...merge.max-gap-minutes` | 30min | 30min | 30min | REMOTE_EFFECTIVE |
| Merge center distance | `alongsideMergeMaxCenterDistanceNm` | `...merge.max-center-distance-nm` | 1.0nm | 1.0nm | 1.0nm | REMOTE_EFFECTIVE |
| Scene availability flag | `alongsideSceneDataAvailable` | `...scene-data.available` | false | false | true | REMOTE_EFFECTIVE（does not prove snapshot completeness） |
| Rule version | `alongsideRuleVersion` | `...rule-version` | `ALONGSIDE_V3_ASYMMETRIC_COUNTERPARTY_V1` | `ALONGSIDE_V3_INSURANCE_SCENE_V2` | same | REMOTE_EFFECTIVE |
| Code SHA provenance | `codeCommitSha` | `ship.behavior.code-commit-sha` | UNKNOWN | UNKNOWN | `b006fb8...` configured; deployed source SHA UNKNOWN | UNKNOWN / stale provenance |

Remote deployed artifact evidence：

- JAR：`/data/zhengyi/ship-risk-data-0.0.1-SNAPSHOT.jar`
- SHA-256：`f53c89ff43a2d8268497ca20dc08bd1af04f0d238c21087dc55674013ec134f3`
- mtime：`2026-09-07 13:55:50 +0800`
- JAR 含当前 Handler/Service class，但无 `git.properties`，不能证明 exact Git SHA。

## 8. KNOWN_RULE_GAPS

| Classification | Gap | Evidence / impact |
| --- | --- | --- |
| ALREADY_IMPLEMENTED | Existing Alongside Core reuse | 当前 Handler 直接调用 `computeAlongsideHistory`；未新建第二套 detector/replay。 |
| ALREADY_IMPLEMENTED | Same-seed IMO alias exclusion | detector 前后均检查 own aliases / same IMO。 |
| ALREADY_IMPLEMENTED | Shadow-only writer safety | `enableInsert=false`，只调用 STS shadow repository。 |
| CONFIG_ONLY | `replay.max-window-minutes` | 配置存在但没有 Java binding；当前实际 max planner step 为 30 分钟。 |
| CONFIG_ONLY | `candidate.min-speed` | field/config 存在但 `hasLowSpeedPoint` 使用固定下界 0.0；REMOTE 1.0 不生效。 |
| ALREADY_IMPLEMENTED | Scene isolation safety | Loitering 遇到不可读、空、非法或非 Polygon/MultiPolygon 几何即 fail closed；STS hash 不依赖 Loitering snapshot；Scene 缺失显式 UNKNOWN 且不作 offshore 加分。 |
| RULE_NOT_APPROVED | Approved Fleet | 787、786/857、757/825 尚未通过 Seed Audit；不得选择其一升级为正式 Fleet。 |
| FUTURE | STS Classification alignment | 当前没有真正 classifier，`LARGE_MERCHANT` 不等于 tanker confirmed，service/unknown/barge 未映射批准 taxonomy；需要独立 focused task 同步 schema/evidence/tests。 |
| FUTURE | Event lifecycle | 缺少 cross-window/slice reconciliation、canonical A/B、跨 region identity、merge 后持久化统计重算。 |
| FUTURE | Exact evidence persistence | exact matched pair points 只存在内存 trace，shadow 没有 detail/trace row。 |
| FUTURE | Review Priority | 当前只有 Alongside behaviorScore/severity 和 `reviewStatus=PENDING`，无独立 reviewPriority。 |
| FUTURE | Target metadata provenance | minotype/source/asOf/classifierVersion 未加载或保存。 |

`STS_CANDIDATE / STS_REVIEW_REQUIRED` 结论：**NOT_IMPLEMENTED**。当前
`ALONGSIDE_DERIVED_STS_CANDIDATE` 是 runner/behavior label，不满足批准的分层语义。

## 9. STS_DEVELOPMENT_PROGRESS

| Step | Goal | Rule | Code | Evidence | Test | Status | Next |
| --- | --- | --- | --- | --- | --- | --- | --- |
| STEP 0 — RULE DEFINITION | 明确 STS 如何计算 | Rule Card + mapping | 本节已映射真实方法 | code/config/remote read-only evidence | targeted tests（见本任务记录） | VALIDATED | 保持本节为首要入口 |
| STEP 1 — PHYSICAL ALONGSIDE BASELINE | history→low speed→replay→physical | Existing Alongside Core | 已实现实际链；World Sea 是 physical gate | trace、pair aggregates、mask evidence | targeted unit tests | IMPLEMENTED | 后续用标注样本验证 precision/recall |
| STEP 2 — STS CLASSIFICATION | Crude OWN + identity + role + context | `STS_RULE_SPEC_V1` / `TARGET_ROLE_POLICY_V1` | identity 部分实现；Scene UNKNOWN 已对齐；分类 taxonomy 未实现 | coarse role/Scene 在 JSON；shadow classification 缺失 | Scene/Alongside targeted regression 73 tests PASS；批准 taxonomy 无验收测试 | IN_PROGRESS | 按冻结顺序先完成 Seed Audit，再独立对齐 classification adapter |
| STEP 3 — EVENT LIFECYCLE | cross-slice/dedup/A-B/region overlap | Event Merge / Slice | 仅窗内 merge + overlap skip | exact key/times/primary region | boundary/dedup 局部测试 | IN_PROGRESS | 设计并批准 fragment reconciliation 后再实现 |
| STEP 4 — HISTORICAL VALIDATION | 1→20→100→Approved Fleet | Gold/Review Dataset | 本任务未执行历史 batch | 无新增 runtime result | 未运行历史样本 | NOT_STARTED | 等 Step 2/3 收敛后小样本开始 |
| STEP 5 — CONTEXT / DARK EXTENSION | 最后处理 gap/spoof/dark/export context | 当前 OUT_OF_SCOPE | 未开发 | Independent Evidence names only | 未运行 | NOT_STARTED | 不提前启动 |

## 10. MANAGER_SUMMARY

- 一句话规则：当前程序先从待审计的原油船身份种子读取各历史 MMSI，在重点区域内筛出海上低速轨迹并回放附近 AIS；两船经时间对齐后满足持续近距离、低速、SOG 差和 COG 平行条件即形成 Alongside 物理证据，再排除同 IMO，但 Target Role 尚未真正把结果分成 `STS_CANDIDATE` 与 `STS_REVIEW_REQUIRED`。
- 当前代码实现：`StsJobs.shipStsAlongsideHistoryJobHandler` → `StsAlongsideHistoryService.runOneInvocation/processSlice` → `ShipBehaviorAnalysisService.computeAlongsideHistory` → `ShipAlongsideDetector.detectWithTrace` → `StsAlongsideHistoryService.persistCandidate` → `StsShadowEventRepository.insertIfNoOverlappingPhysicalEvent`。
- 事件依据：实际存在距离、持续时间、OWN/TARGET SOG 与 SOG difference、COG difference/parallel ratio、轨迹同步时间差、seed identity、粗粒度 Target Role、Scene/World Sea、primary region 与 replay/trajectory fingerprints；exact pair points 未持久化。
- 当前进度：Physical Alongside 复用链、同 seed IMO 排除、shadow-only 写和 Scene isolation/UNKNOWN 已实现；Approved Fleet、严格 tanker role、STS 两类 classification、cross-slice lifecycle、reviewPriority 和完整 evidence persistence 尚未收敛验证。
- 下一步：单独执行 `STS_SEED_AUDIT_V1`，只审计 787、786/857、757/825 差异，不在本任务自动开始。

## 11. VALIDATION_RECORD

### SCENE_ISOLATION_SAFETY_FIX_V1

- Runtime：OpenJDK 1.8.0_472、Maven 3.9.11。
- Command：`mvn -q -Dtest=TrajectoryLoaderServiceTest,AlongsideReplayQueryPlannerTest,ShipAlongsideReplayCandidateProviderTest,ShipAlongsideDetectorTest,AlongsideComputePolicyTest,StsAlongsideHistoryServiceTest,ShipTypeClassifierTest,WorldSeaTrackSegmenterTest,WorldSeaEventGateTest,LoiteringPortSceneTest,AlongsideSceneServiceTest,AlongsideRuleConfigHashIsolationTest test`
- Result：`Tests run: 73, Failures: 0, Errors: 0, Skipped: 0`。
- Package：`./deploy/package.sh` 成功（脚本使用 `-DskipTests`；不替代上述测试结果）。
- Base Git SHA：`d7482152b0f65fc9ae53aa71a3a7404f15f65eaa`；working tree 未提交。
- JAR SHA-256：`8cfdece19515a64d82292e76c25027620d6d78b34338d45c5491cce67b7c1ef5`。
- 未运行：历史 AIS batch、DB data validation、XXL trigger、全仓测试、部署。

### STS_RULE_AND_CODE_ALIGNMENT_V1

- Runtime：OpenJDK 1.8.0_472、Maven 3.9.11。
- Command：`mvn -Dtest=TrajectoryLoaderServiceTest,AlongsideReplayQueryPlannerTest,ShipAlongsideReplayCandidateProviderTest,ShipAlongsideDetectorTest,AlongsideComputePolicyTest,StsAlongsideHistoryServiceTest,ShipTypeClassifierTest,WorldSeaTrackSegmenterTest,WorldSeaEventGateTest test`
- Result：`Tests run: 61, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- 覆盖：trajectory clean/sort/safe load、low-speed/physical detector、replay planner/truncation、World Sea physical gate、same IMO exclusion、Target Role 当前 matrix、overlap skip、成功计算路径 formal event/detail repositories `never()`。
- 未运行：历史 AIS batch、DB data validation、XXL trigger、全仓测试、package/deploy。

---

## Governance Rule

本文件是 STS 业务与技术规则的唯一长期 Source of Truth。

会议、聊天、Agent 建议、历史 handoff 或旧 Phase 文档中的内容，
只有在写入本文件并取得人工确认后，
才成为 Coding Agent 可执行规则。

STS 规则变化优先修改本文件。

不再通过持续新增：

- Phase1.x 文档
- handoff 文档
- 独立 rule 文档

维护当前规则。

历史版本通过 Git history 和必要的 release tag 追溯。

开发原则固定为：

RULE FIRST
→ ARCHITECTURE SECOND
→ CODE LAST

禁止跨 Step 自动开发。

---

# 1. Business Scope

STS 第一阶段仅针对历史 AIS 数据进行船对船持续接触与 STS Candidate 识别。

OWN 船业务范围：

Crude Oil Tanker。

当前业务重点区域：

- Persian Gulf / Arabian Sea
- Singapore / Malacca Strait
- Venezuela
- Russia surroundings

当前计算模式：

Target-first。

第一阶段目标是：

Approved OWN Fleet
→ 历史轨迹
→ Alongside Physical Evidence
→ STS Classification
→ Context Evidence
→ Review Priority
→ Shadow Output

当前不包含：

- 实时 STS 识别
- Dark STS 算法
- 全船队 50k 扩展
- 大规模多年自动跑数
- STS Risk Score
- 非法行为自动认定
- Shadow Fleet 自动认定
- 正式行为事件写入

STS 本身可能是合法业务操作。

系统不得使用：

ILLEGAL_STS

作为 AIS-only 自动分类结果。

---

# 2. Terminology

## OBSERVED_ALONGSIDE

AIS 观测支持两艘不同物理船在一定时间范围内存在可信持续搭靠 / 并靠行为。

这是 Physical Evidence。

它不等于：

- 已发生货物转移
- 已发生原油转移
- 违法 STS
- 制裁规避
- Shadow Fleet 活动

## STS_CANDIDATE

在 OBSERVED_ALONGSIDE 成立基础上，
经过 Identity、Target Role 和 STS Classification 后，
满足当前 STS V1 分类条件的候选事件。

它仍然是：

Candidate。

不是货物转移的最终法律或事实确认。

## STS_REVIEW_REQUIRED

Physical Evidence 已成立，
但 Target Role、Metadata、Scene 或其他关键分类信息存在 UNKNOWN、冲突或不足，
无法自动升级为 STS_CANDIDATE，
同时也没有足够依据直接排除。

需要保留并进入人工复核。

## Context Evidence

用于解释事件场景、替代解释或调查优先级的信息。

Context Evidence 不能反向证明 Physical STS。

## Independent Evidence

与 Physical STS 分离维护的异常证据，例如：

- AIS_GAP_EVIDENCE
- AIS_MANIPULATION_EVIDENCE
- DARK_RENDEZVOUS_CANDIDATE

## 核心语义

Observed Alongside
≠ Confirmed Cargo Transfer
≠ Illegal Activity

---

# 3. Observability Boundary

## Directly Observable

AIS 中能够直接观察：

- 接收到的 MMSI
- 时间
- 经纬度
- SOG
- COG
- Navigation Status
- AIS 广播静态字段
- 双船时空距离关系
- 在有效 AIS 数据支持下的持续接触

## Inferable

可以在明确限制条件下推断：

- 持续并靠
- 共同低速运动
- 等待 / 会合行为
- Rendezvous plausibility
- 航程前后变化

推断必须保留证据和限制条件。

## Requires External Data

AIS 无法单独确认：

- 是否真正进行货物转移
- 转移货物种类
- 转移方向和数量
- STS Operation Plan
- 商业合同
- 许可情况
- 实际 Cargo Records
- Beneficial Ownership
- 有效保险 / P&I
- 是否违反制裁或法律

## AIS-only Not Detectable

双方同时 AIS-dark 时：

AIS 无法直接确认真实双船接触。

可以形成：

DARK_RENDEZVOUS_CANDIDATE

但不能将缺失轨迹插值成：

OBSERVED_ALONGSIDE。

AIS gap 本身不得自动解释为主动关闭 AIS。

---

# 4. Current Fleet

当前 Fleet 状态必须严格区分以下三个口径。

## Meeting Approximation

meetingApproxCount:

≈ 787

其含义是会议阶段用于需求讨论的近似规模。

它不是数据库或运行时精确 Fleet。

## Raw Identity Candidate

rawIdentityCandidate:

786 IMO
857 identity rows

这是历史 Raw Identity Candidate 口径。

## Current Strict Code Expectation

currentStrictCodeExpectation:

757 IMO
825 identity rows

这是当前代码中存在的 Strict Expectation。

它当前仅代表：

currentStrictCodeExpectation。

它不是：

Approved Fleet。

## Approved Fleet

Approved Fleet:

PENDING SEED AUDIT

在完成 STS_SEED_AUDIT_V1 之前：

757 / 825 不得被描述为正式 Fleet。

Java 中的固定：

EXPECTED_IMOS
EXPECTED_IDENTITIES

不得继续作为长期业务 Source of Truth。

后续 Fleet 必须由 Approved Seed Manifest 记录和校验。

## Required Seed Audit

STS_SEED_AUDIT_V1 必须确认：

786 / 857
vs
757 / 825

真实差异，包括：

- 删除 / 过滤的 identity rows
- 涉及哪些 IMO
- 每条 type
- 每条 minotype
- 删除或过滤原因
- 哪些 IMO 完全退出 Eligible Fleet
- 哪些只是某个 alias 被过滤
- 哪些 alias 仍需要保留在 Identity Alias Universe

Seed Audit 完成前不得初始化正式 Manifest 数据。

---

# 5. Identity Model

STS 以：

IMO

作为物理船舶主身份。

MMSI 是随时间变化的 AIS 广播身份。

原则：

IMO
= Physical Vessel Identity

MMSI
= Observed AIS Identity

first_seen / last_seen
= 数据中观察到的活动范围

不得自动解释为：

官方 MMSI 法定生效 / 失效时间。

## Identity Quality

允许使用：

- SINGLE_OBSERVED
- DUAL_NON_OVERLAP
- DUAL_OVERLAP
- AMBIGUOUS
- UNKNOWN

无法证明历史 MMSI 时段不重叠时，
不得强行标记为 DUAL_NON_OVERLAP。

---

## Eligible Fleet

Eligible Fleet 用于回答：

哪些 IMO 可以作为 OWN 进入当前 STS 业务计算。

Eligibility 是：

IMO-level business decision。

第一阶段要求：

OWN = Approved Crude Oil Tanker。

---

## Identity Alias Universe

Identity Alias Universe 保存 Eligible IMO 已知的历史 MMSI Identity。

用途包括：

- trajectory query
- self-exclusion
- identity continuity
- traceability
- replay
- historical reconciliation

一个 Eligible IMO 的可信历史 MMSI，
即使某一 alias 的当前 metadata：

- minotype 缺失
- minotype 不满足当前业务筛选
- type/minotype 与另一 alias 不一致

也不能因此从身份知识中直接彻底删除。

业务资格和身份知识必须分离：

Eligible Fleet
≠ Identity Alias Universe。

同一物理 IMO 的可信 alias 必须用于 self-exclusion，
防止同一艘物理船不同 MMSI 被误识别成 STS 双船事件。

---

# 6. Region Scope

会议批准的 STS 第一阶段业务范围：

- Persian Gulf / Arabian Sea
- Singapore / Malacca Strait
- Venezuela
- Russia surroundings

业务范围和可执行 Region Geometry 是两个层次。

## Region Roles

Region 用于：

1. Analysis Gate
2. Event Attribution
3. Context Evidence

第一阶段计算必须继续采用：

Target-first。

禁止恢复默认：

Region-wide AIS scan
→ discover Fleet

作为当前小 Fleet 的主要计算路线。

## Core Region / Analysis Region

coreRegion：

业务归属区域。

analysisRegion：

用于保留边界附近完整行为上下文的扩大分析区域。

Region boundary 不等于 Event boundary。

## Region Manifest

当前状态：

MEETING_SCOPE_APPROVED
EXECUTABLE_REGION_MANIFEST_PENDING

设计已批准：

existing ship_sts_risk_region
+
existing ship_sts_region_set_item
+
ship_sts_region_set_manifest

未来 Java 不再 hard-code 固定 Region Code 列表。

运行时应通过：

Approved Region Manifest
+
resolved Region definitions
+
fingerprint validation

确认空间资产完整性。

Region Manifest DDL 当前未获批准执行。

---

# 7. Alongside Reuse Contract

STS 不重新实现 Physical Alongside Detection。

当前共享入口优先使用：

computeAlongsideHistory()

STS 必须直接复用现有 Alongside Core 的：

- Trajectory Loader
- Low-speed / Activity Window
- Local Replay
- Candidate Matching
- ShipAlongsideDetector
- Trace / Evidence generation

禁止为 STS 新建第二套：

- Trajectory Loader
- Low-speed Window
- Replay Engine
- Candidate Matching
- ShipAlongsideDetector

STS 自己负责：

- Fleet / Identity
- Alias self-exclusion
- Target Role
- STS Classification
- Context Evidence
- Review Priority
- STS Shadow persistence
- STS traceability

## Critical Boundary

Alongside Event 只是：

Physical Evidence。

禁止：

Alongside PASS
→ 自动 STS_CANDIDATE。

必须继续经过：

Identity
→ Target Role
→ STS Classification
→ Context

后才能产生最终 STS 分类。

第一阶段不因 STS 修改 Production Alongside thresholds。

---

# 8. STS Rule Spec

Current Rule:

STS_RULE_SPEC_V1
APPROVED

## R1 — OWN Eligibility

OWN 必须来自 Approved Eligible Fleet。

第一阶段：

Crude Oil Tanker。

Approved Fleet 当前仍为空，
等待 Seed Audit。

## R2 — Physical Identity

OWN 和 TARGET 必须是不同物理船舶。

必须使用：

IMO
+
Identity Alias Universe

执行 self-exclusion。

无法可靠区分物理身份时：

Identity = UNKNOWN / AMBIGUOUS

不得自动生成高置信度 STS_CANDIDATE。

## R3 — Physical Alongside

Physical Detection 使用现有 Alongside Core。

STS V1 不另建 Physical Detector。

Physical Evidence 通过后形成：

OBSERVED_ALONGSIDE。

## R4 — Target Role

应用：

TARGET_ROLE_POLICY_V1。

TARGET Role 不作为 OBSERVED_ALONGSIDE 的 hard gate。

## R5 — STS Classification

OBSERVED_ALONGSIDE 只有在：

- OWN eligibility 有效
- physical identity 有效
- Alongside Physical Evidence 有效
- Target Role 与 cargo STS 兼容
- 不存在高置信度明确 Service/Pilot/Tug/Bunker 替代解释
- 其他必要信息满足当前规则

时才可升级：

STS_CANDIDATE。

如果 Physical Evidence 成立，
但 Target Role 或关键分类信息 UNKNOWN / conflicting：

STS_REVIEW_REQUIRED。

## R6 — Context Evidence

Context Evidence 用于：

- alternative explanation
- event interpretation
- reviewPriority

不得反向改变 Physical Evidence 为 PASS。

## R7 — UNKNOWN

UNKNOWN 不等于：

false。

UNKNOWN 也不等于：

safe。

所有关键 UNKNOWN 必须显式保存。

## R8 — Risk Separation

当前不计算 STS Risk Score。

以下证据可以影响 reviewPriority：

- sanctions
- ownership opacity
- old vessel
- flag changes
- classification / P&I concern
- repeated suspicious history
- relevant export-port context

这些信息不能提高：

physicalDetectionConfidence。

AIS gap / manipulation 如果破坏事件时段数据，
可以降低 Physical Confidence。

不得因为 AIS gap 可疑而提高 Physical Confidence。

---

# 9. Target Role Policy

Current Policy:

TARGET_ROLE_POLICY_V1
APPROVED

Target Role 用于：

STS Classification。

不用于决定：

OBSERVED_ALONGSIDE 是否存在。

## Known Tanker / Oil Tanker

明确、可信的：

Tanker
Oil Tanker

属于：

Strong Supporting Evidence。

如果其他 STS 条件满足：

可升级为 STS_CANDIDATE。

但：

Target = Tanker

本身不能证明发生货物转移。

## Tug / Pilot / Service / Bunker

明确：

- Tug
- Pilot
- Service Vessel
- Work Boat
- Bunker Vessel
- Supply Vessel

时：

保留：

OBSERVED_ALONGSIDE。

但默认不升级为严格：

STS_CANDIDATE。

记录：

SERVICE_INTERACTION。

## Unknown / Conflicting Metadata

TARGET type/minotype：

- missing
- unknown
- unrecognized
- conflicting

时：

不得直接排除 Physical Event。

输出：

OBSERVED_ALONGSIDE
+
STS_REVIEW_REQUIRED。

目标：

优先 Precision，
但不能因 Metadata UNKNOWN 制造明显 False Negative。

## Barge / Lighter

明确：

Barge
Lighter

时：

不直接排除。

输出：

OBSERVED_ALONGSIDE
+
STS_REVIEW_REQUIRED。

原因：

lightering / barge interaction 可能具有真实货物转移含义，
需要结合业务与轨迹人工判断。

## Target Metadata Provenance

Target Role 应尽量保存：

- targetRole
- targetRoleConfidence
- rawType
- rawMinotype
- metadataSource
- metadataAsOf
- classifierVersion

如果 metadata 的历史有效时间未知：

metadataAsOf = UNKNOWN。

---

# 10. Scene / Context Evidence

Current Policy:

SCENE_ISOLATION_DECISION_C
APPROVED

Scene 不是：

Physical STS Detection 的必要输入。

## Scene Available

可以产生：

- near port
- near anchorage
- outside known port / anchorage
- nearest port
- distance
- scene fingerprint

等 Context Evidence。

## Scene Unavailable

Physical：

OBSERVED_ALONGSIDE

仍可以存在。

但是必须：

Scene = UNKNOWN。

禁止将 Scene unavailable 自动表示为：

offshore = true

禁止自动表示为：

nearPort = false

禁止自动表示为：

nearAnchorage = false

禁止因为 Scene 缺失：

提高 STS confidence。

Context UNKNOWN 不能被消费者解释为已证明 Offshore。

## Shared Safety Contract

STS 不得为了：

Smoke
测试通过
任务可运行

而改变 Loitering 的共享生产安全语义。

当前批准的安全方向：

1. 恢复 LoiteringPortSceneLoader 原 fail-closed 几何完整性语义；
2. 解除 STS provenance / rule fingerprint 对 Loitering Scene Snapshot 的不必要硬依赖；
3. STS Scene unavailable 时使用 Context = UNKNOWN；
4. 不修改 Loitering 业务规则。

该修改属于：

SCENE_ISOLATION_SAFETY_FIX_V1。

Status:

VALIDATED

已恢复 Loitering fail-closed 几何完整性，STS rule fingerprint 不再需要 Loitering Scene Snapshot；
Scene unavailable 明确输出 UNKNOWN 且不推断 offshore / near port / near anchorage。
未修改 Loitering 速度、分段、区域或事件门禁规则。

---

# 11. Event Classification

Current Taxonomy:

EVENT_TAXONOMY_V1
APPROVED

第一阶段保持最少分类。

## Physical

OBSERVED_ALONGSIDE

## STS

STS_CANDIDATE

STS_REVIEW_REQUIRED

## Context Evidence

允许：

- SERVICE_INTERACTION
- ANCHORAGE_PROXIMITY
- WAITING_OR_CONGESTION

这些是 Context Evidence，
不是新的 Physical Event Type。

## Independent Evidence

保留名称：

- AIS_GAP_EVIDENCE
- AIS_MANIPULATION_EVIDENCE
- DARK_RENDEZVOUS_CANDIDATE

当前：

Dark Algorithm = OUT_OF_SCOPE。

第一阶段不增加更多 STS 枚举。

---

## Review Priority

V1 不输出 Risk Score。

允许输出：

reviewPriority。

reviewPriority 是：

人工复核顺序。

不是：

违法概率
风险概率
STS 概率。

### 可影响 Physical Detection Confidence

仅包括与直接观测质量有关的信息：

- matched AIS points
- alignment timing
- duration support
- distance support
- source coverage
- unsupported gap
- trajectory quality
- positioning anomaly
- geometry quality

### 不得提高 Physical Detection Confidence

包括但不限于：

- sanctions
- ownership opacity
- old vessel
- flag changes
- P&I / class concern
- AIS gap
- high-risk region
- export-port history

这些只能进入：

Context Evidence
或
Review Priority。

---

# 12. Event Merge / Slice

历史计算中的：

Slice

只是计算单位。

它不是：

Event boundary。

Region 也不是：

Event identity。

## Definitions

Core Slice：

当前任务拥有的主时间窗口。

Context Window：

为处理边界事件而加载的前后上下文。

Fragment：

某一次 Slice 内检测到的 Physical Evidence 片段。

Event：

多个兼容 Fragment 合并后的最终物理交互。

## Canonical Pair

事件双船身份必须基于：

physical IMO pair。

A → B

与：

B → A

不得形成两条物理事件。

## Merge Principles

允许合并的 Fragment 应满足：

- same physical vessel pair
- 时间连续或满足允许间断
- 空间行为连续
- movement behavior 兼容
- 无明确行为中断
- rule/data provenance 可兼容

Merge 后必须重新计算统计。

禁止仅：

min(start)
+
max(end)

后直接保留旧统计。

## Cross-slice

跨午夜或跨 Slice 的长事件不得被截断成多个独立 STS Event。

固定 halo 不能代替真正的 fragment reconciliation。

当前跨 Slice Event Merge：

仍需在后续 Observed STS Candidate V1 阶段正式收敛。

---

# 13. Output Schema

第一阶段 STS Shadow Event 应具备以下语义字段。

## Identity

- own IMO
- own actual MMSI
- target IMO
- target actual MMSI
- canonical physical pair
- identity quality
- target role
- target role confidence

## Time

- start time
- end time
- elapsed duration
- supported duration
- boundary / censored state

统一使用 UTC 语义。

## Location

- start location
- end location
- center location
- contact spatial extent

## Physical Evidence

- distance statistics
- own speed statistics
- target speed statistics
- SOG difference
- course difference
- matched point count
- alignment timing statistics
- max unsupported gap
- source coverage status

如果仅使用 COG：

字段语义必须称：

course

不得声称：

true heading。

## Classification

- physicalEventType
- stsClassification
- classification reason
- targetRole
- scene status

## Context

- primary region
- associated regions
- Scene Context
- Context Evidence
- Independent Evidence
- reviewPriority

## Traceability

- STS rule version
- Alongside rule/config fingerprint
- Seed Manifest / identity fingerprint
- Region Set fingerprint
- code SHA
- source/data fingerprint
- trace recipe / trace fingerprint
- review status

## Risk

STS Risk Score：

DISABLED。

---

# 14. Explainability

每个 Candidate 必须能够解释：

RULE
STATUS
OBSERVED VALUE
CONDITION
SOURCE
REASON

STATUS 至少允许：

- PASS
- FAIL
- UNKNOWN
- NOT_APPLICABLE

例如：

PHYSICAL_ALONGSIDE = PASS

TARGET_TANKER_ROLE = UNKNOWN

SCENE = UNKNOWN

STS_CLASSIFICATION = STS_REVIEW_REQUIRED

任何 UNKNOWN 都必须保留。

不得将缺失证据静默转换为：

PASS
或
negative fact。

必须能够从输出定位：

- 规则版本
- Seed / Alias 版本
- Region 版本
- Alongside config
- code SHA

以支持历史回放。

---

# 15. Validation Dataset

正式验证数据分为：

Gold Dataset

和：

Review Dataset。

## Gold Dataset

具有较强人工或外部事实依据的案例。

用于评估：

- Physical Alongside
- STS Classification

两个不同层次。

## Review Dataset

真实生产候选、负例和无法确认案例。

必须保留：

UNVERIFIABLE

而不是强行标记 TP / FP。

## Labels

允许：

- TRUE_POSITIVE
- FALSE_POSITIVE
- FALSE_NEGATIVE
- UNVERIFIABLE
- INSUFFICIENT_DATA

Label 必须明确是针对：

Physical Layer

还是：

STS Classification Layer。

## Required Negative / Control Cases

至少包括：

- bunkering
- service interaction
- anchorage adjacency
- port operation
- following / overtaking
- waiting / congestion
- AIS coverage dropout
- identity aliases
- cross-slice event
- region overlap
- no-event control

验证必须报告：

无法判断样本比例。

不得只删除 UNKNOWN 后报告高精度。

---

# 16. Runtime / XXL

## Current Handler

shipStsAlongsideHistoryJobHandler

这是新的 STS 开发唯一当前入口。

## Deprecated For New Development

以下 Handler 当前保留，
但禁止继续叠加新 STS 能力：

- shipStsHistoryBaselineJobHandler
- shipStsHistoryRegionShadowJobHandler
- shipStsHistoryRegionSetShadowJobHandler
- shipStsHistoricalFleetBackfillJobHandler

Deprecated 不表示：

- 已删除
- 已停止 XXL
- 已允许清理
- 已完成迁移

只有新 Alongside-derived 路径完成验证后，
才进入统一历史清理。

## Runtime Mode

当前：

SHADOW ONLY。

Formal Event Write:

PROHIBITED。

不得写：

ship_behavior_event

不得写：

ship_behavior_event_detail。

## Scale-up Order

后续历史验证必须逐步扩大：

small known case
→ small batch
→ medium validation
→ Approved Fleet

不得从当前状态直接：

50k
或
多年全量运行。

## XXL

查看日志和状态：

READ-ONLY。

任何：

- trigger
- stop
- schedule change
- parameter change

需要人工批准。

---

# 17. Known Limitations

当前已知限制：

1. Approved Fleet 尚未形成；
2. 786/857 与 757/825 差异尚未完成只读 Seed Audit；
3. Target type/minotype 可能缺失、过期或冲突；
4. MMSI 历史有效期并不完全等于 first_seen / last_seen；
5. Scene 数据可能不可用；
6. Scene UNKNOWN 不能证明 Offshore；
7. AIS gap 不能证明主动关闭 AIS；
8. 双方同时 AIS-dark 时无法用 AIS 直接确认 Physical STS；
9. Actual Cargo Transfer 无法仅靠 AIS 确认；
10. 当前跨 Slice Fragment Merge 尚未完成正式 V1 收敛；
11. 当前 Alongside Core 的 Source / Runtime Effective Config 尚需在实际运行环境核验；
12. SOG difference 不等于真正的 vector relative velocity；
13. COG 不等于 true heading；
14. Region Manifest 尚未实现；
15. Seed Manifest 尚未实现；
16. Risk Score 当前禁用。

以上限制不得由 Coding Agent 顺手修改。

每项进入实现前必须成为独立 Approved Task。

---

# 18. Current Approved Version

Document Status:
APPROVED

Rule:
STS_RULE_SPEC_V1
APPROVED

Target Role:
TARGET_ROLE_POLICY_V1
APPROVED

Event Taxonomy:
EVENT_TAXONOMY_V1
APPROVED

Scene Policy:
SCENE_ISOLATION_DECISION_C
APPROVED

Fleet:
SEED_AUDIT_PENDING

meetingApproxCount:
≈ 787

rawIdentityCandidate:
786 IMO / 857 identity rows

currentStrictCodeExpectation:
757 IMO / 825 identity rows

Approved Fleet:
NONE_YET

Region:
MEETING_SCOPE_APPROVED
EXECUTABLE_REGION_MANIFEST_PENDING

Current Handler:
shipStsAlongsideHistoryJobHandler

Deprecated For New Development:
- shipStsHistoryBaselineJobHandler
- shipStsHistoryRegionShadowJobHandler
- shipStsHistoryRegionSetShadowJobHandler
- shipStsHistoricalFleetBackfillJobHandler

Risk Score:
DISABLED

Dark Algorithm:
OUT_OF_SCOPE

Formal Event Write:
PROHIBITED

Git Model:
APPROVED_AS_TARGET_MODEL
PENDING_ONE_TIME_CONSOLIDATION

Current STS Implementation Source:
LATEST_STS_FEATURE_BRANCH_UNTIL_CONSOLIDATION

---

# 19. Changelog

## 2026-09-08 — SCENE_ISOLATION_SAFETY_FIX_V1

Status:

VALIDATED

- 恢复 `LoiteringPortSceneLoader` 对不可读、空、非法或非 Polygon/MultiPolygon 几何的 fail-closed 行为。
- `alongsideRuleConfigHash()` 保留 World Sea fingerprint，但不再加载 Loitering Scene snapshot；指纹显式记录 `OPTIONAL_CONTEXT_UNKNOWN_ON_UNAVAILABLE`。
- Scene unavailable 输出 `sceneType=UNKNOWN`、`sceneDataAvailable=false`、`sceneContextComplete=false`；不输出 nearPort/nearAnchor/outside/openSea 真假断言，也不作 offshore 加分。
- 针对性回归：73 tests PASS；本地 package 成功；未运行历史 batch、DB 写入、XXL trigger 或部署。

## 2026-09-08 — Governance Baseline

Status:

APPROVED_WITH_EXECUTION_ORDER

Approved:

- STS_RULE_SPEC_V1
- TARGET_ROLE_POLICY_V1
- EVENT_TAXONOMY_V1
- SCENE_ISOLATION_DECISION_C
- Minimal Seed Manifest design
- Minimal Region Manifest design
- Git long-term target model
- Handler deprecation policy

Fleet remains:

SEED_AUDIT_PENDING.

757 IMO / 825 identity rows remain:

currentStrictCodeExpectation only.

No Approved Fleet exists yet.

No DDL is approved.

No Dark Algorithm is approved.

No Risk Score is approved.

Formal Event Write remains prohibited.

## Frozen Execution Order

STEP 0

Governance baseline:

AGENTS.md
+
docs/sts/STS_DEVELOPMENT.md

↓

STEP 1

SCENE_ISOLATION_SAFETY_FIX_V1

↓

STEP 2

STS_SEED_AUDIT_V1

↓

STEP 3

Seed / Region Manifest de-hardcode

↓

STEP 4

Observed STS Candidate V1
+
cross-slice event merge

↓

STEP 5

Historical sample calibration

↓

Later only after approval:

Dark Evidence
Fleet Expansion

禁止跨 Step 自动开发。

---

# 20. Next Approved Task

Next Approved Task:

STS_SEED_AUDIT_V1

Status:

APPROVED_FOR_NEXT_INDEPENDENT_TASK

`SCENE_ISOLATION_SAFETY_FIX_V1` 已完成并验证；本轮到此 STOP。

下一独立任务才允许 `STS_SEED_AUDIT_V1`，不得由 Scene Fix 自动进入。

Seed Audit 不得和 Scene Fix 放在同一个 Commit。

Seed Audit 完成后，
才决定：

ship_sts_seed_manifest

的实际 DDL 和初始化值。

Region Manifest 同样后置，
不得和 Scene Fix 或 Seed Audit 混合实现。
