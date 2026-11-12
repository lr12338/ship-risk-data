# STS DEVELOPMENT

## 2026-11-12 当前运行版本公开学习快照

- 本次发布以242当前在用源码 `d48eec580960a96b258ce05146afc64946c75bc9` 为基线，保持该提交的Java实现与测试；当前Handler为 `shipStsAlongsideHistoryJobHandler`，规则版本为 `STS_FALSE_POSITIVE_REDUCTION_V1`，复用Alongside Core，`SHADOW_ONLY`，STS评分禁用。此条仅同步已有运行事实及阅读入口，不批准新规则或新运行任务。
- 2026-11-10已人工批准恢复旧5K精确运行包；2026-11-12只读核对JAR SHA-256仍为 `6b4c0540a661cf68fd68d8a9b19ba71f465bb8d3b0a9fc1d5f22f9a88422b2b7`。这是包的历史名称，不代表当前名单规模。
- 当前已批准商船Seed/徘徊目标批次为 `sts-merchant-cargo-20261110-v1`，21,881 IMO / 43,200 MMSI aliases；固定Asia/Shanghai `[2026-08-05,2026-11-05)`。仅保留原完整资料名单内CONTAINER/OIL/CHEMICAL/GENERIC_TANKER/LNG/LPG/GENERIC_GAS等旧5K同类船型，全部完整无已知冲突aliases；无挂港、国际资格、70米或固定5,000数量门槛。国际与历史身份仍 `NOT_VERIFIED`。STS按IMO计数，徘徊按MMSI行计数。
- 当前STS批次 `sts-merchant-cargo-20261110-v1-prod-20260805-1105-r2`、徘徊批次 `loitering-merchant-cargo-20261110-v1-prod-20260805-1105` 仍在计算。事件记录、候选线索和批次完成度分别核对，不能以ReturnT=200或已导出事件数替代全量验收。
- 公开快照只替换连接凭据为环境变量占位符，补充README及版本验证记录，不包含名单、AIS、结果附件或本地后续实验实现。阅读入口：[STS学习指南](README.md)；源码/JAR区分及本轮验证：[PUBLISHED_VERSION.md](PUBLISHED_VERSION.md)。下文旧批次和审批内容保留其历史上下文。

---

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
LOGIC_AUDIT_COMPLETE
DB_VERIFICATION_COMPLETE

meetingApproxCount:
≈ 787

rawIdentityCandidate:
786 IMO / 857 identity rows
Source: var/sts/sts-crude-identity-20260902.csv
SHA-256: 5a699db07bdada8d66d7271665c0f1a8e2f21017bf3f16a7fcfb217b812b61b0

currentStrictCodeExpectation:
757 IMO / 825 identity rows
= raw rows where minotype='原油船'

Audited Eligible Fleet Candidate:
PENDING HUMAN APPROVAL
(see Seed Audit section below for full analysis)

Audited Alias Universe Candidate:
PENDING HUMAN APPROVAL
(see Seed Audit section below for full analysis)

Approved Fleet:
PENDING HUMAN APPROVAL

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

# Exact Mixed 5K Fleet and Seed Enablement

## STS_EXACT_5K_FLEET_BUILD_AND_SEED_ENABLEMENT_V1 — APPROVED（2026-09-11）

- Current Approved Version: `STS_EXACT_5K_FLEET_BUILD_AND_SEED_ENABLEMENT_V1`
- Current Handler: `shipStsAlongsideHistoryJobHandler`
- Current Approved Task: Exact Mixed 5K dry-run selection, fail-closed Seed enablement, targeted tests, JDK8 package, focused commit, and 242 read-only deployment preflight.
- Acceptance: preserve the valid 7-digit IMO + valid 9-digit MMSI canonical identity contract; `shipCount` and `shipOffset` operate on canonical IMO groups; retain legal aliases; reject empty/mismatched/invalid/colliding/unstably ordered Seed content; bind new Seed fingerprints to `seedBatchNo`, ordinal, canonical IMO and all aliases while preserving the legacy 0902 Seed identity fingerprint.
- Prohibited without separate approval: Seed DML, DDL, remote upload/replace/config edit/restart, XXL trigger/schedule change, and any 5K run.

### Candidate universe and Exact 5K selection

Read-only database audit combined the formally used open-sea target snapshots, `ship_behavior_target_ship`, optional `crudefleet`, and the existing STS Seed. The universe contains 65,603 source rows, 51,916 valid source rows, 41,216 distinct valid IMO/MMSI pairs, and 34,814 canonical format-valid IMOs before collision isolation. It collapses 10,700 duplicate source aliases and rejects 13,687 invalid rows. Twenty-four MMSIs map to two IMOs; all 48 affected IMOs are isolated. The remaining executable universe is 34,766 canonical IMOs / 41,134 distinct alias pairs.

The approved proposed Seed batch is `sts-mixed-5000-identity-20260911-v1`, permanently labeled `MIXED_5K_SCALE_VALIDATION_FLEET`. Deterministic selection excludes collision-related IMOs, groups by canonical IMO, then ranks by IMO check-digit consistency, stable non-empty ship name, clear type, metadata timestamp, single-name/type consistency, low alias ambiguity, and ascending IMO. Tanker-like identities occupy ordinals 1–2,500; container identities occupy ordinals 2,501–5,000. All aliases of an IMO share its ordinal and are ordered by MMSI.

Approved dry-run result:

- selected canonical identities: 5,000
- selected current MMSIs: 5,000, all unique and valid
- selected alias rows / expected distinct Seed MMSIs: 5,135
- canonical identities with aliases: 134; additional aliases: 135; maximum aliases per IMO: 3
- composition: 2,500 container; 1,902 chemical tanker-like; 342 gas tanker-like; 228 other tanker-like; 28 oil tanker-like
- IMO check-digit quality: 5,000 PASS / 0 FAIL; this is a Fleet quality gate and does not change the production Handler format contract
- selected identity collisions / invalid identities / large alias anomalies: 0 / 0 / 0

`SELECTION_DIGEST = 42532213d9945633cffbd89c6be6b9a8fe0d2331c424714275dae54320162d16`

Digest input is exactly 5,135 UTF-8 lines formatted `shipOrdinal|canonicalIMO|aliasMMSI|fleetRole|typeGroup`, ordered by approved Fleet rank, quality tuple, IMO and alias MMSI. The same read-only source snapshot and policy were executed twice and produced the same SHA-256.

### Seed enablement contract

The Historical Handler may consume the requested `seedBatchNo` only after the DB query returns a non-empty batch and validation confirms row batch consistency, valid IMO/MMSI formats, global MMSI uniqueness, positive ordinal, one ordinal per IMO, one IMO per ordinal, canonical count greater than zero, and repository order `ship_ordinal, imo, mmsi`. Same-IMO aliases are legal and count as one OWN identity. No permanent 786 or 5,000 count is allowed.

For `sts-crude-identity-20260902-v1`, the legacy identity digest remains unchanged. New Seed digests additionally bind `seedBatchNo` and `ship_ordinal`, so alias or ordinal-only drift changes the batch target fingerprint. Existing config fingerprint continues to bind requested Seed, region, selection, time range, rule/config and code SHA. Formal writes remain prohibited.

Materialization is INSERT-only and separately approval-gated. Expected post-insert invariants for the proposed Seed are 5,135 rows, 5,000 distinct IMO, 5,135 distinct MMSI, 5,000 distinct ordinals, and zero format/collision/ordinal violations. If any row already exists for the proposed batch, materialization must fail closed; UPDATE/UPSERT is prohibited.

Local validation: Amazon JDK `1.8.0_472`; targeted Seed/ordinal/fingerprint/Params plus Physical detector, Candidate provider, classification, repository writer safety and rule-hash isolation regression = 91 tests / 0 failures / 0 errors / 0 skipped. `deploy/package.sh` passed; pre-commit local JAR SHA-256 is `6b4c0540a661cf68fd68d8a9b19ba71f465bb8d3b0a9fc1d5f22f9a88422b2b7`. Deployment, Seed materialization and XXL execution remain unapproved and were not performed.

---

# STS False Positive Validation

## STS_FALSE_POSITIVE_REDUCTION_V1 — APPROVED（2026-09-09）

- Current Approved Version: `STS_FALSE_POSITIVE_REDUCTION_V1`
- Current Handler: `shipStsAlongsideHistoryJobHandler`
- Next Approved Task: 人工运行 `STS_FP_RECHECK_786_SAME_SLICE`，不 resume 旧 rule batch。
Acceptance: Physical detector 阈值不变；FORMAL 才允许 STS promotion；明确 service、WEAK、known port/berth stationary 保留 Physical evidence 但不进入 STS candidate/review queue；Scene UNKNOWN 不推断 offshore；只准备 5K 方案，不执行。

### Run Completeness

242 DB 与 XXL 日志只读核验：`sts-hev1-full-2y-20260909-v1` 为 `PARTIAL_1_OF_105`，不是 Full 2Y complete。Parent range 为 `[2024-09-02 00:00:00, 2026-09-03 00:00:00)`；`total_tasks=105`、`completed_tasks=1`、`failed_tasks=0`、`total_processed_ships=786`、`total_shadow_inserted=26`。唯一完成 task 为 slice 0：`[2024-09-02 00:00:00, 2024-09-09 00:00:00)`；slice 1–104 全部 PENDING。后续统一称 `FIRST_7D_SLICE_VALIDATION`。Agent 未 Trigger 剩余 slice。

### 26-event audit — STS_FALSE_POSITIVE_AUDIT_V1

真实 `ship_sts_shadow_event` 逐条 JSON 与列值核验结果如下。类别 A–E、F–G 互斥；H–J 是重叠审计维度。

| Category | Count | Root Cause | Current Behavior | Expected Behavior |
| --- | ---: | --- | --- | --- |
| A. EXPLICIT_SERVICE | 14 | classifier 已正确识别 SERVICE，但旧 promotion policy 把正常替代也写成 review | 14 × `STS_REVIEW_REQUIRED` | Physical row retained；`SERVICE_INTERACTION`；`PHYSICAL_ONLY` |
| B. TANKER_LIKE | 9 | metadata 支持 tanker，但 9 条 Physical 全为 WEAK | 9 × review | WEAK 不 promotion；未来仅 FORMAL 进入 candidate |
| C. UNKNOWN_TARGET | 1 | target metadata 缺失且 Physical 为 WEAK | review | Physical diagnostic only |
| D. GENERIC_MERCHANT | 1 | generic merchant 且 Physical 为 WEAK | review | Physical diagnostic only；FORMAL 才 review |
| E. OTHER | 1 | fishing/other metadata 且 Physical 为 WEAK | review | Physical diagnostic only |
| F. WEAK_PHYSICAL | 25 | adapter 把 weak physical uncertainty 当成 business review uncertainty | 25 × review | `physicalSupportLevel=WEAK`；not promoted |
| G. FORMAL_PHYSICAL | 1 | 唯一 FORMAL 的 target 为明确 SERVICE | review | service suppressed，0 candidate/review |
| H. PORT_BERTH_LIKELY | 6 | raw operational port polygon 覆盖 event center，但 runtime Scene snapshot fail-closed | UNKNOWN | 保持 UNKNOWN，等待独立 Scene 数据修复；不得伪造 nearPort |
| I. SCENE_UNKNOWN | 26 | Scene snapshot runtime unavailable | UNKNOWN | 仍 UNKNOWN；不推断 offshore |
| J. POSSIBLE_TRUE_STS | 0 FORMAL | 9 个 tanker-like 都是 WEAK；没有 FORMAL tanker positive | review noise | 不 promotion；保留 unit positive control，历史中另找正样本 |

关键结论：Tug classifier 没有识别失败，错误在 promotion policy；Weak evidence 被过度提升；Physical detection 的近距、同速、同向事实没有证据表明错误，因此 detector 不改。

### Frozen validation set — STS_FP_REVIEW_SET_20240902_0908_V1

复现 filter：`batch_no='sts-hev1-full-2y-20260909-v1'` 且 slice `[2024-09-02 00:00:00, 2024-09-09 00:00:00)`。冻结 event keys：

- SERVICE（14）：`5ea89097f79686104af21eb2ffc5b8910085a96be97ff273663ea4b743117323`、`123279e23c4473f05e102627014a5bdd2c38fce8e3736981cf6e88ac7053236e`、`2c254ac8f217d8081c3712236585d2825e6e5f2ab4930a6a9dbe6096ba8a64a1`、`d475411accf5f9e77c46823c462b3ecb1e3f2717b8f341a3448482005cdb4eae`、`56a87269842065fece78f62e65c13db02f111c21e0ea7ec601f07a3ed3a14711`、`636bd1fa89f6fa728b7ca07d7ec0e19ae166f24f7d3d40d7a32b2cfad61d5d8a`、`2ffd573aeb7c828673a7098da718ba83cc881a5f62bb2f7db6a71795bc164a60`、`56f277823a8e8c494962d84d6766e75d51b8d213313edd11340218a6d20d78e3`、`5dfd2971b658ce00b10c05b05f6dadc7ce38823a069b4205696e9f37306bbca6`、`bf0a9de6aa88b424ea8725a5471acf3318b2e4c9072f95f890813d2d2a1aa952`、`4bbaf93bf1908c196742816891aeebe7de0c2b0adc3e53dc4ff5a09841d9ec6c`、`f488cabe00602661e740a5ce4fdf6312002bb78166dbfb8bb895306f1cc5cba0`、`8b8ec6ba2b65fb88d4727b104369f2699cbab5653ad5c12d7245cda993828e3b`、`398e452bbb4115d92a0c460ee50999a8d922d78024ef96f4d9b85f1b5892d20e`。
- TANKER_LIKE（9，全部 WEAK）：`eb7e7413b666b83d6f0abe5676d21607b8d232b594d46bb96891b078b6ed7137`、`1636069b0830e9ce8925501ff48bc3de46c2764551b6c2e41d7c1304f7b48e05`、`f8079148fba4f2bcff4d19501a21979aa503c1424863e60b8061982955129074`、`0b1a4c5e57d235b86f1418394c5b6a4781364d96d1b2068c82313da1c8d20588`、`920f2544a710eec0a0dde25a81afb83bba0bbbd1666f67a198a5015f038e7aba`、`0dca3ecee962c15a6abe5cc437a09ac330a9b02ebd4899eaa2562708476452cd`、`d928c5c6e4c04887b15566d24d4c05b9c211c360867ca123be4affaec1b2a107`、`dbfc39760b9ed2ac026003ad82e9940eb216030a98b13e1c24f4c188a7be6f40`、`24a5b4ba54c23a222dba288b13e475e8b6d3aed4a220e57a60589582ca583939`。
- UNKNOWN / GENERIC_MERCHANT / OTHER：`14395e0de5069cf2ec19d8b9c564850854b67faf67301aff0430e6dfe221b1c1`、`79db70f5e53b9965dbc411f9f71a43d352a3bd6dc36a93460a6b7b8e8b42d374`、`4f25869d456c9101de311275b5214dcdc85eead035742bcf20f5438cf30bace4`。

BEFORE：26 Physical rows、26 review、14 service、25 weak、0 candidate。AFTER_EXPECTED（相同 AIS/physical config）：26 Physical rows retained；14 service suppressed；25 weak suppressed；0 candidate、0 review；Scene 仍为 26 UNKNOWN。唯一 FORMAL row 是 service，因此本 slice 不能证明 candidate recall。

### Scene coverage status — SCENE_COVERAGE_AUDIT_V1

STS wiring 已调用 `AlongsideSceneService.classify`，UNKNOWN 也正确传播到 evidence。根因是 `LoiteringPortSceneLoader` 对全量 snapshot fail-closed：operational polygons 8,764、port references 9,251 均通过 JTS 校验；3,026 个 anchor polygon 中 `ais41.alertzones` oid `2569774`（`outer anchorage`）与 `2572474`（`南通港2号锚地`）为无效 JTS geometry，导致整个 snapshot unavailable。分类为 A `Scene snapshot runtime unavailable` + F `underlying geometry fail-closed`，并登记 `PORT_CONTEXT_DATA_BLOCKER`。不修改 Loitering fail-closed，不在本任务修共享 Scene 数据。Raw operational polygon 能覆盖 6 个 event centers，但 26 条 behaviorMode 均为 `UNDERWAY_PARALLEL_COMPANIONSHIP`；当前样本没有 port-stationary suppression 正样本。`PORT_BERTH_CIRCULAR_RULE_GAP` 保留为 NEXT_TASK_CANDIDATE，不临时造 Circle Detector。

### New Promotion Matrix

| Physical Support | Target / Alternative | Scene | STS output |
| --- | --- | --- | --- |
| FORMAL | TANKER_LIKE | 无明确正常替代 | `STS_CANDIDATE` |
| FORMAL | UNKNOWN / CONFLICTING / GENERIC_MERCHANT / BARGE_LIGHTER / OTHER | 无明确正常替代 | `STS_REVIEW_REQUIRED` |
| FORMAL | SERVICE / TUG / PILOT / WORK_BOAT / BUNKER / SUPPLY | any | `PHYSICAL_ONLY` + `SERVICE_INTERACTION` |
| WEAK | any role | any | `PHYSICAL_ONLY` diagnostic evidence |
| FORMAL | any | known port/berth/terminal + stationary | `PHYSICAL_ONLY` + `PORT_BERTH_STATIONARY` |
| FORMAL | otherwise eligible | anchorage | anchorage only as context；不自动删除 |

Shadow row 保留 Physical evidence，不做 DDL。`score_detail_json` 新增/明确：`physicalSupportLevel`、`stsPromotionStatus`、`reviewQueueEligible`；非 promotion row 使用 `stsClassification=PHYSICAL_ONLY`、`review_status=NOT_REQUIRED`。默认 candidate/review SQL 必须按 `reviewQueueEligible=true` 或 `stsClassification IN ('STS_CANDIDATE','STS_REVIEW_REQUIRED')` 统计，不能用 shadow row 总数代表 STS 数。

Rule/provenance 改为 `STS_FALSE_POSITIVE_REDUCTION_V1`，使旧 batch 的 runner/config fingerprint 不能与新结果混用。旧 26 rows 不 UPDATE/DELETE。

### Validation and summary contract

Targeted tests 覆盖 FORMAL tanker/unknown/generic/barge/service/bunker/supply、WEAK tanker/unknown、service evidence trace、Scene UNKNOWN、known port stationary、region priority、formal writer safety、Physical thresholds regression 与 counters。Job summary 分开输出 `physicalObserved`、`physicalFormal`、`physicalWeak`、`stsCandidates`、`stsReviewRequired`、`serviceSuppressed`、`portBerthSuppressed`、`weakSuppressed`、`shadowInserted`、`duplicateSkipped`，不再输出含混的 `events=26`。

### Scale-up plan — read-only assessment

`ship_behavior_tanker_open_sea_target_ship` batch `liquid-container-5000-2025h2-2026h1-v1`：表无 enabled 列，batch 现有 5,000 rows / 5,000 distinct MMSI / 4,012 distinct non-empty IMO；45 empty IMO、792 non-empty invalid IMO、MMSI format invalid=0、重复 `(IMO,MMSI)`=0。Alias group 分布为 1×3716、2×242、3×35、4×6、5×2、6×2、7×2、9×2、10×1、12×2、13×1、525×1，显示一个明显 placeholder/identity 聚簇，不能当 crude approved fleet。`metadata_quality`: HIGH 4,981 / MEDIUM 19。Standard types：CONTAINER 2,500；TANKER_UNSPECIFIED 1,680；TANKER_CHEMICAL 767；TANKER_OIL 52；TANKER_GAS 1。Selection reason：EXPLICIT_CONTAINER 2,500；EXPLICIT_LIQUID_CARGO 2,021；EXPLICIT_LIQUID_CARGO_SUBTYPE_CONFLICT 479。该表可用于 mixed negative-control/performance，不解释为 Crude business accuracy。

`crudefleet`：4,346 rows / 2,171 distinct IMO / 2,168 distinct MMSI；8 invalid MMSI。仅 2,171 distinct `(IMO,MMSI)`，说明 2,175 duplicate rows；alias rows per IMO 为 2×2,169、4×2，主要是重复记录而非已审计 alias universe。Type：VLCC 1,754 / suez 1,298 / Aframax 1,294；ShiptypeLevel5：Crude Oil Tanker 4,216 / Crude/Oil Products Tanker 128 / empty 2。Crude semantics 较一致，但 identity duplicates、MMSI collision/format 与 2 个空 subtype 必须先审计；本任务不接入。

后续分两条：PLAN A `CRUDE SCALE VALIDATION` 先对 crudefleet 做 identity + type quality audit；PLAN B `MIXED 5K STRESS VALIDATION` 使用 approved 5K batch，只用于性能、mixed negative-control、role robustness、false-positive exposure。两者均按 100 → 500 → 1000 → 5000，先短窗口；禁止直接 5000 × 2 years。本任务不运行 5K。

Manager Summary：当前 26 条不是“两年发现 26 起 STS”，而是首个 7-day slice 的 26 个 Physical Alongside observations。旧 adapter 把 14 个已识别 service 和 25 个 weak physical 全部提升到 review；V1 修复在 STS promotion 层分离 Physical / Candidate / Review，保留全部证据且不调 detector。Scene UNKNOWN 来自两条无效 anchorage geometry 触发全量 fail-closed，作为独立数据 blocker 处理。

### Approved deployment record（2026-09-09）

- Code commit：`0a5422e4eef83ebf7e2a9d40b82da0d44297978a`；JDK8 committed package JAR SHA-256：`e7122bc6790cb9d16a6093ea6a594db8eddea8c500a0b98c6d509d91ffa78af1`。
- 242 原 JAR SHA：`64c3b2d29df638f5d094b92440ebf10fae57bc09a885db91318be2de75b92c0f`；备份：`/data/zhengyi/ship-risk-data-0.0.1-SNAPSHOT.jar.bak.sts-fpfix-20260909-0a5422e`。
- 原 config SHA：`3df3dcbedb130fe8e479162f359ce41f091dd0c7ed06d455398dc7c1368ee777`；备份：`/data/zhengyi/application.properties.bak.sts-fpfix-20260909-0a5422e`。仅 `ship.behavior.code-commit-sha` 更新为 code commit；新 config SHA：`42c3c3096c3222a636759d6948e1d1718c105b3c9f92b595a7334bbbe3d560de`。
- 经人工 `APPROVE_DEPLOY` 后完成 checksum、JAR 原子替换和现有 `restart.sh`。新 PID `1825822`；15:58:58 注册 `shipStsAlongsideHistoryJobHandler`，Main started；15:58:59 XXL remoting server started；HTTP 8090 / XXL 30008 均监听。
- Startup schema 全部 `exists, skip ddl` / `column ready`；`formal-write-enabled=false`、`schema-auto-migration-enabled=false`。部署后只读 DB：Seed 857 rows；旧 Shadow 26 rows；旧 Full 仍 1/105；新 same-slice batch/shadow/formal event/formal detail 均 0。
- Agent XXL Trigger=NO；DB writes=NO；DDL=NO。部署后停止，等待人工运行 `STS_FP_RECHECK_786_SAME_SLICE`。

---

# Historical Runner Observability

## STS_HISTORICAL_RUNNER_RESILIENCE_AND_OBSERVABILITY_V1 — APPROVED（2026-09-09）

- Current Approved Version: `STS_HISTORICAL_RUNNER_RESILIENCE_AND_OBSERVABILITY_V1`
- Current Handler: `shipStsAlongsideHistoryJobHandler`
- Current development state: `LOCAL_TARGETED_TESTS_PASSED_PENDING_COMMITTED_JDK8_PACKAGE`
- Next Approved Task: 完成 targeted tests、JDK8 package、focused commit 与 242 read-only preflight；随后停在 `DEPLOY_APPROVAL_REQUIRED`。
- Acceptance: 定位 `sts-hev1-full-1y-20260909-v1` 的两艘失败船；稀疏单船 replay 失败不再使其他已可靠完成船舶整 slice 重跑；失败事实不得静默丢弃；系统性故障仍 fail closed；XXL Console 可观察 Batch → Slice → Fleet Progress → Failed Ship → Slice Summary → Batch Progress。Physical thresholds、promotion matrix、service/weak suppression、Scene UNKNOWN、Region/context 语义不变。

### Failed run analysis and root cause

242 只读 DB 与时间窗日志核验：Parent 范围为 `[2025-09-02 00:00:00, 2026-09-03 00:00:00)`，`sliceDays=7`，共 53 slices。16:25:49–16:40:07 的 invocation 仅 claim DB `slice_seq=0`，即用户可见 `slice 1/53`，实际范围 `[2025-09-02 00:00:00, 2025-09-09 00:00:00)`。Task 197 最终为 `FAILED`，`processed_ship_count=784`、`replay_queries=1708`、`replay_failures=2`、`replay_truncated=0`、`last_error=FAILED_SHIPS=2`；后 52 slices 仍 `PENDING`。

| IMO | MMSI | Stage | Exception | Root Cause | Retryable | Data Integrity Impact |
| --- | --- | --- | --- | --- | --- | --- |
| 9453975 | 235076276 | REPLAY_QUERY | `SocketTimeoutException: Read timed out` | 2025-09-04 07:59:42–08:29:42 replay request 在已有 bounded retry（`retry-times=1`，共 2 attempts）后仍超时 | YES | 该船 replay 结果不完整，不得产生该船可信结果；不影响其他船已完成计算 |
| 9933547 | 538010019 | REPLAY_QUERY | `SocketTimeoutException: Read timed out` | 2025-09-04 07:39:08–08:00:12 replay request 在同样 bounded retry 后仍超时 | YES | 该船 replay 结果不完整，不得产生该船可信结果；不影响其他船已完成计算 |

同一 slice 其余约 1706 个 replay query 成功，因此本次是稀疏的 ship-local transient replay timeout，不是整个 replay source unavailable。16:32:59 还出现过一次超时但后续 retry 成功，也证明 bounded retry 已存在且有效。本轮不修改共享 replay retry 配置。

### Failure taxonomy and completion semantics

- `SYSTEMIC_FATAL`：Seed/identity/config 完整性、World Sea required snapshot、task state、persistence infrastructure、未分类运行时异常，以及 claimed slice 全部选中船均因 replay 失败且没有任何成功 replay 查询，均 fail closed；task=`FAILED`，XXL code=500，Console 输出 `outcome=FATAL`、`FATAL_STAGE`、root cause。
- `SHIP_LOCAL_FAILURE`：仅限已明确局部化的 replay query failure / terminal truncation；记录 IMO、MMSI、stage、retryable、reason，不为该船生成不完整结果，继续后续船。Slice 写 `COMPLETE_WITH_WARNINGS`，`last_error` 保存 bounded JSON failure detail，XXL code=200 但返回 `outcome=PARTIAL`、`completedShips`、`failedShips`、`partial=true`，不得显示为无条件 SUCCESS。
- 现有 task 表没有 `failed_ship_count` 或 detail JSON 专用列；V1 复用 `VARCHAR(1024) last_error`，不做 DDL，不新建 failure table。

### Resume behavior

修复前：当 784 船完成、2 船局部失败时，task 写 `FAILED`；下次 `resume=true` 会 claim 同一个整 slice，清零 task counters 并重跑 786 船。Shadow overlap/dedup 可防止重叠数据，但 784 船计算会浪费。

修复后：Ship-local partial task 为终态 `COMPLETE_WITH_WARNINGS`，批次聚合将它计入 completed slice，claim SQL 不会再 claim，因此不重跑已完成的 784 船。Systemic `FAILED` task 仍按既有最多 3 次 task retry 预算整 slice 重跑。V1 不开发 per-ship retry scheduler；对局部失败船的后续精准补算仍是 `RUNNER_RESUME_GAP`，需使用新的 scoped batch/recheck，不 resume 旧 1Y batch。

### Structured progress log V1

`XxlJobLogger.log(...)` 直接输出：`[STS][JOB_ACCEPTED]`、`[STS][BATCH_STATE]`、`[STS][SLICE_CLAIMED]`、`[STS][PROGRESS]`、`[STS][SHIP_FAILED]`、`[STS][SLICE_SUMMARY]`、`[STS][BATCH_PROGRESS]`、`[STS][JOB_RETURN]`。Fleet progress 每 50 船输出，且最后一船 always print；失败逐船输出精简 root reason；完整 stack 仅保留在应用日志。不输出 connection string，不按 AIS point/query/candidate/成功船刷屏。`SLICE_CLAIMED slice=1/53` 是用户可见的 one-based 序号，DB 仍保留 `slice_seq=0`。

### Development Progress

本轮仅修改 historical Runner 失败传播/任务状态、replay root reason 透传、XXL structured logging 及定向测试。`STS_FALSE_POSITIVE_REDUCTION_V1` promotion matrix、Physical Alongside thresholds、service/weak suppression、Scene UNKNOWN fail-closed、Region/context semantics、Loitering 均不变；Formal writer 仍禁止。不部署、不写远程/DB、不做 DDL、不 Trigger XXL。

Manager Summary：失败的不是“一年任务跑到末尾”，而是 53 个 slice 中第 1 个 7-day slice。784 船已完成，2 船因单个 replay request 在两次尝试后仍 read timeout 而不完整。V1 保留这两个失败事实并返回 PARTIAL，同时不再让 784 船被整 slice 重跑；整个 replay source 不可用或其他系统性错误仍 code=500。一次 invocation 仍只处理一个 slice，自动推进 53 slices 属于未批准的 `RUNNER_ORCHESTRATION_V2`。

---

# Continuous Slice Orchestration

## STS_CONTINUOUS_SLICE_ORCHESTRATION_V1 — APPROVED（2026-09-10）

- Current Approved Version: `STS_CONTINUOUS_SLICE_ORCHESTRATION_V1`
- Current Handler: `shipStsAlongsideHistoryJobHandler`
- Next Approved Task: 完成 targeted tests、JDK8 package、focused commit 与 242 read-only preflight；然后停在 `DEPLOY_APPROVAL_REQUIRED`。
- Acceptance: 仅增加 parent batch 的 multi-slice auto-continue orchestration；不改 Physical Alongside thresholds、STS classification matrix、service/weak suppression、Scene、Region、Seed、Manifest、event merge、Dark 或 Risk Score。Formal writer 仍为 0。

### Parent Batch / Slice / Invocation

- Parent Batch 是完整历史范围，例如 1 year。
- Slice 是独立计算、状态、持久化和恢复单元，例如 7 days；禁止将所有 slices 合并为一个巨大查询或长事务。
- `autoContinue=false`（默认）：1 invocation = 1 slice，保持旧任务兼容。
- `autoContinue=true`：1 invocation = N slices，每个 slice 独立 claim、处理、提交 task state/shadow/counters 并刷新 batch progress，直到 batch complete、安全边界或 `SYSTEMIC_FATAL`。

### Safety bounds

- `maxSlicesPerInvocation`: CODE_DEFAULT = 8。missing 或 0 使用 CODE_DEFAULT；负数非法。
- `maxRuntimeMinutes`: CODE_DEFAULT = 90。missing 或 0 使用 CODE_DEFAULT；负数非法。
- 两个边界只在 `autoContinue=true` 时决定是否 claim 下一 slice。Runtime 达限时不中断已 claim slice，而是完成并持久化当前 slice 后以 `SUCCESS_PAUSED_LIMIT` / `MAX_RUNTIME` 退出。Slice 数达限以 `SUCCESS_PAUSED_LIMIT` / `MAX_SLICES` 退出。实际 Job JSON 的非零值为 JOB_PARAM_EFFECTIVE，不是 STS 业务规则。

### Continue / resume / concurrency contract

- `SUCCESS` 继续；仅含 `SHIP_LOCAL_FAILURE` 的 `COMPLETE_WITH_WARNINGS` 保留 warning 并继续；`SYSTEMIC_FATAL` 立即 code=500，不 claim 后续 slice。
- `resume=true` 仅 claim `PENDING` 或在重试预算内的 `FAILED`；`COMPLETE` 和 `COMPLETE_WITH_WARNINGS` 是终态，不重跑。
- Parent batch 在尚有 pending/running/retryable failed slice 时保持 `RUNNING`；只有 remaining=0 才能为 `COMPLETE` 或 `COMPLETE_WITH_WARNINGS`。历史 warning 通过 task 聚合保留，不被新 slice 的 `failedShips=0` 清零。
- Claim 继续使用 task row 事务内 `SELECT ... FOR UPDATE` 和带状态条件的 guarded update，同一 batch/slice 最多一个 executor owner；XXL 仍建议单实例串行，不由 Agent 修改调度。
- Invocation controls 不进入 batch fingerprint，但当前 provenance contract 包含 `codeSha`；代码升级后不得绕过 fingerprint 去 resume 旧 code SHA batch，必须使用新 batchNo。

Manager Summary：以前 `runOneInvocation` 在 claim/process/commit 一个 task 后直接组装 `JOB_RETURN`，所以 `resume=true` 只保证下次 invocation 能从下一 pending slice 继续。V1 保持该 slice 安全单元，仅在外层增加可显式开启且有界的 batch auto-continue loop。

---

# Candidate Funnel Observability

## STS_CANDIDATE_FUNNEL_OBSERVABILITY_V1 — APPROVED（2026-09-10）

- Current Approved Version: `STS_CANDIDATE_FUNNEL_OBSERVABILITY_V1`
- Next Approved Task: implement the approved instrumentation, targeted tests, JDK8 package, focused commit and 242 read-only preflight; then stop at `DEPLOY_APPROVAL_REQUIRED`.
- Current Handler: `shipStsAlongsideHistoryJobHandler`
- Development status: `LOCAL_IMPLEMENTED_VALIDATED`
- Acceptance: instrumentation only。在真实 `ShipAlongsideReplayCandidateProvider` 判断分支上增加 Replay parsing、Target Track 与 Candidate first-fail funnel；每个 slice 向 XXL Console 输出一条 bounded `[STS][CANDIDATE_FUNNEL]` summary，每种主要 rejection 最多 3 个 sample。Candidate List、Replay query/order、matching、Physical thresholds、STS classification、Scene、Region、Seed 与 persistence 语义必须完全不变；无 DDL，Formal writer 保持 disabled，Agent 不 Trigger XXL。

### Approved baselines to preserve

- Development/runtime base Git SHA: `f898433b22c3261af96cae48260862d8b0d41cc6`。Deployed JAR SHA-256: `d9270be623960d398cb418704e2d211b1c0c954e6d7c95b8e0d314abf6c804ff`。
- `POSITIVE_CONTROL_2024`：`[2024-09-02 00:00:00, 2024-09-09 00:00:00)`，786 ships，1 slice，`autoContinue=false`。已验证 `trajectoryLoaded=644`、`physicalObserved=26`、`physicalFormal=1`、`physicalWeak=25`、`serviceSuppressed=14`、`weakSuppressed=25`、`shadowInserted=26`、`failedShips=0`；历史 candidate baseline 约 59。
- `ZERO_CONTROL_2026`：`[2026-08-13 00:00:00, 2026-08-20 00:00:00)`，786 ships，1 slice，`autoContinue=false`。已验证 `trajectoryLoaded=719`、`activityWindows=1140`、`replayQueries=2458`、`replayRawPoints=109652`、`candidateTargets=0`、`physicalObserved=0`。
- 2026 三个 continuous slices 累计：`selected ship-slices=2358`、`trajectoryLoaded=2158`、`lowSpeedWindows=3858`、`replayQueries=8553`、`replayRawPoints=428606`、`candidateTargets=0`、`physicalObserved=0`。Continuous Slice orchestration 已 VALIDATED，本任务不修改 orchestration。

### Actual Replay to Candidate gate order

`SpatialAisReplayClient.parseBody`
→ per-query raw point parsing by `ReplayAisPointParser.parse`
→ `ReplayQueryResult.tracksByMmsi`
→ `ShipAlongsideReplayCandidateProvider.collectCandidates`
→ valid target MMSI / current OWN MMSI exclusion
→ `cleanAndSort`
→ target clean points >= configured minimum
→ at least one target point with valid `0 <= SOG <= candidateMaxSpeed`
→ `matchCandidate` greedy nearest-time synchronization
→ time gap <= configured tolerance
→ distance <= configured candidate distance
→ matched points >= configured minimum
→ matched OWN support duration >= configured minimum
→ `AlongsideCandidateTarget`
→ `RegionIdentityPolicy.acceptCandidate` identity alias / same-IMO exclusion。

Identity policy 位于 Candidate Provider 之后，必须单独统计；不得用 same-IMO rejection 解释 Provider `candidateTargets=0`。

### Counter units

- Point-level（AIS point）：`replayRawPoints`、`replayValidPoints`、`replayInvalidPoints`、可低成本获取时的 `replayPointsOutsideRequestedTime`。
- Query-level（Replay query）：`replayQueries`、`replaySuccessfulQueries`、`replayFailedQueries`。
- Target-track-level（`TARGET_TRACK_EVALUATION`）：一个 query/window context 中按 Target MMSI 聚合的轨迹。同一 MMSI 在不同 query/window 可重复计数，不是 distinct global vessels。`targetTracksGrouped`、`selfMmsiExcluded`、`targetTracksEvaluated`、所有 first-fail rejection 与 Provider accepted 均使用此单位。
- Candidate-level（Candidate Provider 输出对象）：`candidateAccepted`、`identityPolicyAccepted`；`identityPolicyRejected` 是 Provider 后 identity gate 拒绝的 Candidate 数。

### Candidate first-fail semantics

每个 `TARGET_TRACK_EVALUATION` 只允许一个 terminal outcome，顺序与现有代码 gate 一致：

1. `REJECT_TOO_FEW_POINTS`：clean target points < configured minimum。
2. `REJECT_NO_LOW_SPEED_POINT`：points 足够，但不存在有效 `0 <= SOG <= candidateMaxSpeed` 点。
3. `REJECT_NO_TIME_MATCH`：贪心最近时间匹配中从未出现 `timeGap <= configured tolerance`。
4. `REJECT_DISTANCE`：至少一对 time-aligned，但从未出现 `distance <= configured candidate distance`。
5. `REJECT_MATCHED_POINTS`：存在 distance-eligible pair，但最终 matched points < configured minimum。
6. `REJECT_DURATION`：matched points 足够，但首末 matched OWN support duration < configured minimum。
7. `ACCEPTED`：通过 Candidate Provider。

Invariant：

`targetTracksEvaluated = accepted + rejectTooFewPoints + rejectNoLowSpeedPoint + rejectNoTimeMatch + rejectDistance + rejectMatchedPoints + rejectDuration (+ bounded explicit rejectOther if real code requires)`。

Funnel 还必须记录 `targetTracksWith1Point`、`targetTracksWith2Points`、`targetTracksWith3PlusPoints`、`cleanPointsGeMin`、`hasLowSpeedPoint`、`hasTimeAlignedPair`、`hasDistanceEligiblePair`、`matchedPointsGeMin`、`matchedDurationGeMin`，但 terminal rejection 不得重复计数。

### Bounded diagnostics and output

- 每种主要 terminal rejection 最多保留 3 个 sample；只输出 own/target MMSI、query range、point count 及该 gate 的最小/最大观测值，禁止输出完整 target track。
- 每个 slice 结束复用 `progressLog` + `XxlJobLogger`，输出一条 `[STS][CANDIDATE_FUNNEL]`：Replay query/point、track grouping/self exclusion/evaluation、first-fail outcomes、candidate accepted、identity rejected/accepted。V1 不增加 DB column，不做 DDL。
- Parser V1 最低区分 raw/valid/invalid；只在不重构 parser 的前提下才增加 invalid MMSI/time/position/SOG 子类。对 valid point 统计 timestamp 是否位于请求时间范围外。

### Validation and stop condition

Targeted tests 必须覆盖六种 rejection、accepted、terminal invariant、Candidate output unchanged、positive Physical fixture、service/weak promotion regression 与 formal writer=0。随后使用已验证 JDK8 执行 `deploy/package.sh`，形成 focused commit，再做 242 SSH read-only preflight。部署必须等待独立 `APPROVE_DEPLOY`；Agent 仍禁止 Trigger XXL。部署后仅准备两组人工 SHADOW_ONLY 参数：

- `sts-candidate-funnel-positive-20240902-0909-v1`：2024-09-02 → 2024-09-09，786 ships，`sliceDays=7`，`autoContinue=false`。
- `sts-candidate-funnel-zero-20260813-0820-v1`：2026-08-13 → 2026-08-20，786 ships，`sliceDays=7`，`autoContinue=false`。

人工返回两组 funnel 后，才能按首个显著差异 gate 将根因分类为 `DATA_FRAGMENTATION`、`TARGET_SPEED_DISTRIBUTION`、`TIME_ALIGNMENT_GAP`、`SPATIAL_DISTANCE_GAP`、`MATCH_CONTINUITY_GAP`、`REPLAY_PARSE_OR_SCHEMA`、`REPLAY_TIME_RANGE_ANOMALY`、`EXPECTED_DATA_BEHAVIOR`、`SMALL_CODE_BUG` 或 `UNKNOWN`。在根因解释前暂停 1Y/2Y/1000/5000 ships 扩大运行。

Manager Summary：本任务不追求让 2026 出现事件，只解释十万级 Replay raw AIS points 为什么在真实 Candidate Provider 得到 0 Candidate。2026-09-10 已获人工批准，允许在上述 instrumentation-only 边界内实现。

### Local implementation record

- `CandidateFunnelStats` 是 per-compute 创建、per-slice 聚合的 in-memory diagnostics；不持久化，不修改 DB schema。
- `SpatialAisReplayClient` 在原 parser 路径记录 raw / valid / invalid，并对 valid point 记录 requested time 范围外数量。
- `ShipAlongsideReplayCandidateProvider` 保留原清洗、贪心时间匹配、距离、points 和 duration 判断顺序；仅在每个真实分支记录 first-fail outcome。`acceptedTrackEvaluations` 是 Target-track-level，`candidateAccepted` 是 Provider 最终去重输出对象，两者不混用。
- `ShipBehaviorAnalysisService` 在 Provider 之后单独记录 Identity policy accepted/rejected；`StsAlongsideHistoryService` 在每个 slice 结束输出一条聚合 `[STS][CANDIDATE_FUNNEL]` 和每种 rejection 最多 3 条 sample。
- Business rule changed = NO。Candidate output ordering/filtering = unchanged。Physical thresholds / STS classification / Scene / Region / Seed / orchestration / persistence = unchanged。
- Targeted regression command covers Replay client/planner/provider、Physical detector、compute policy、history writer safety、STS runner/params/classification/repository 与 World Sea gate。本地 JDK `1.8.0_472`：98 tests / 0 failures / 0 errors / 0 skipped；`deploy/package.sh` PASS。最终 committed clean SHA/JAR SHA 在 focused commit 后重新生成。

### Manual control parameters

`STS_CANDIDATE_FUNNEL_POSITIVE_2024`：

```json
{
  "mode": "STS_ALONGSIDE_HISTORY_V1",
  "seedBatchNo": "sts-crude-identity-20260902-v1",
  "regionSetCode": "STS_MEETING_PRIORITY_REGION_SET_V1",
  "shipOffset": 0,
  "shipCount": 786,
  "from": "2024-09-02 00:00:00",
  "to": "2024-09-09 00:00:00",
  "sliceDays": 7,
  "batchNo": "sts-candidate-funnel-positive-20240902-0909-v1",
  "parallelism": 1,
  "writeMode": "SHADOW_ONLY",
  "resume": true,
  "autoContinue": false
}
```

`STS_CANDIDATE_FUNNEL_ZERO_2026`：

```json
{
  "mode": "STS_ALONGSIDE_HISTORY_V1",
  "seedBatchNo": "sts-crude-identity-20260902-v1",
  "regionSetCode": "STS_MEETING_PRIORITY_REGION_SET_V1",
  "shipOffset": 0,
  "shipCount": 786,
  "from": "2026-08-13 00:00:00",
  "to": "2026-08-20 00:00:00",
  "sliceDays": 7,
  "batchNo": "sts-candidate-funnel-zero-20260813-0820-v1",
  "parallelism": 1,
  "writeMode": "SHADOW_ONLY",
  "resume": true,
  "autoContinue": false
}
```

两组 JSON 已由 `StsAlongsideHistoryJobParams.parse/validate` 定向测试验证。Agent 不 Trigger。

### Positive vs Zero comparison

| Candidate Stage | 2024 Positive | 2026 Zero | Interpretation |
| --- | ---: | ---: | --- |
| Replay queries | PENDING MANUAL RUN | PENDING MANUAL RUN | 对比 query 工作量 |
| Replay raw points | PENDING MANUAL RUN | PENDING MANUAL RUN | 原始 payload point |
| Replay valid points | PENDING MANUAL RUN | PENDING MANUAL RUN | parser 通过 point |
| Target tracks grouped | PENDING MANUAL RUN | PENDING MANUAL RUN | query/window/MMSI track |
| Self excluded | PENDING MANUAL RUN | PENDING MANUAL RUN | current OWN MMSI track |
| Target tracks evaluated | PENDING MANUAL RUN | PENDING MANUAL RUN | first-fail 分母 |
| Too few points | PENDING MANUAL RUN | PENDING MANUAL RUN | `DATA_FRAGMENTATION` evidence |
| No low-speed | PENDING MANUAL RUN | PENDING MANUAL RUN | `TARGET_SPEED_DISTRIBUTION` evidence |
| No time match | PENDING MANUAL RUN | PENDING MANUAL RUN | `TIME_ALIGNMENT_GAP` evidence |
| Distance fail | PENDING MANUAL RUN | PENDING MANUAL RUN | `SPATIAL_DISTANCE_GAP` evidence |
| Matched-points fail | PENDING MANUAL RUN | PENDING MANUAL RUN | `MATCH_CONTINUITY_GAP` evidence |
| Duration fail | PENDING MANUAL RUN | PENDING MANUAL RUN | `MATCH_CONTINUITY_GAP` evidence |
| Candidate accepted | PENDING MANUAL RUN | PENDING MANUAL RUN | Provider 最终输出 |
| Identity rejected | PENDING MANUAL RUN | PENDING MANUAL RUN | Provider 后 identity gate |
| Physical observed | PENDING MANUAL RUN | PENDING MANUAL RUN | Existing Alongside Core output |

Remaining root cause: `UNKNOWN`。必须等待部署后的 2024/2026 人工对照 funnel，禁止把当前 0 event 直接解释为 `EXPECTED_DATA_BEHAVIOR`。

---

# Current STS Calculation

## STS_HISTORICAL_EVENT_V1 — 当前人工批准的实施规则（2026-09-09）

本节覆盖下文历史实现描述中冲突的 Fleet/Region/时间门禁。当前状态：分类/identity/context/时间初始化修复已通过 targeted tests 和 committed JDK8 package；收到 `APPROVE_DEPLOY` 后已于 2026-09-09 部署 242 并验证重启，READY_FOR_MANUAL_RUN，尚未运行 Smoke。
Current Handler: `shipStsAlongsideHistoryJobHandler`。Acceptance 来自本轮人工批准任务。

- Seed DB 通过 SSH 242 只读核验：唯一 batch `sts-crude-identity-20260902-v1`，857 rows / 786 IMO / 857 MMSI；enabled=1 为 825 rows / 757 IMO，enabled=0 为 32 rows。32 行保留在 DB，没有物理删除。
- enabled=0：UNKNOWN 7 rows / 6 IMO，EXPLICIT_NON_CRUDE 25 rows / 25 IMO；enabled=1 全为 CRUDE。9212929、9240407 各 1 enabled + 1 disabled；9088689 的 2 aliases 均 disabled。
- DB 与 Raw CSV 的 11 字段（imo/mmsi/first_seen/last_seen/name/callsign/length/width/type/minotype/identity_mode）逐行 SHA-256 全部一致；857/857 hashes matched。此时 DB-vs-Raw 无缺行、无额外行、无上述 metadata 改变。
- 源文件只读复核：总名单 2571 IMO；single 715 IMO/715 MMSI；dual 71 IMO/142 MMSI；两份 XLSX 的身份/metadata 与 Raw CSV 完全一致。
- Runtime scope 为明确获准 Shadow Validation 的 `EXECUTABLE_0902_IDENTITY_CANDIDATE`。按指定 seed batch 的全部 identity rows 选择 IMO 和 aliases，包括上述 disabled rows；不以 enabled/minotype 作本路径业务资格 gate，不改 DB enabled。Approved Fleet 仍 `PENDING HUMAN APPROVAL`。Java 不硬编码新的 786/857。
- 同 IMO 所有 aliases 保留轨迹查询、self-exclusion 和 identity continuity。其他 Alongside 调用的 OWN eligibility 不变。
- Existing Alongside Core 和 World Sea safety gate 保持；Physical PASS 明确 `OBSERVED_ALONGSIDE`。弱 Physical trace 保留 evidence 并进入 review。
- 保守解析 raw type/minotype：明确 tanker/oil/petroleum/chemical/liquid tanker 且无正常替代场景可为 `STS_CANDIDATE`；tug/pilot/service/work boat/bunker/supply 保留 Physical、记录 `SERVICE_INTERACTION`，不升级严格 STS；barge/lighter/unknown/conflicting/generic merchant 为 `STS_REVIEW_REQUIRED`。tanker 不代表 confirmed cargo transfer。
- Scene KNOWN port/berth/terminal + Existing Core stationary evidence 抑制 strict candidate；Anchorage proximity 仅 context，不单独 hard reject。Scene unavailable 保持 UNKNOWN，禁止产生无证据的 offshore/nearPort/nearAnchorage 断言。
- Region 全面改为 attribution/context/review priority；regionSetCode 保留为上下文来源，窗口和持久化不按是否落入 monitored region 拒绝事件。World Sea 是独立 Physical gate。Review priority 仅 NORMAL/ELEVATED，不影响 physical confidence；Risk Score DISABLED。
- 沿用 shadow 表现有列及 score_detail_json，记录 classification/reason/role/context/trace。Formal writer=0；无 DDL、Seed 写入、Manifest。
- 允许 canonical pair 小修复；跨 slice 仍 fragment + overlap skip 时记录 `EVENT_LIFECYCLE_GAP`。无成熟圆形轨迹规则时记录 `PORT_BERTH_CIRCULAR_RULE_GAP`，不临时创建 detector。
- 开发验收：runtime fleet/aliases、same IMO、Core、全部 target roles、port stationary、Scene UNKNOWN、region context、shadow/formal safety、pair dedup targeted tests；JDK8 package、focused commit、clean tracked tree、Git/JAR SHA、242 read-only preflight。部署等待单独 `APPROVE_DEPLOY`；Agent 永不 Trigger XXL。

Seed Audit DB 状态在本次核验后为 `DB_VERIFICATION_COMPLETE`；下方 S1–S9 保留独立 Seed Audit commit 时的 DB pending 记录。

### 本轮验证及待决项（2026-09-09）

- Seed doc commit：`744944ec247f1237be17b1c0c35a45814932c627`。本轮 Java focused commit 及其重新打包 JAR 的最终 SHA 记录在部署审批报告中。
- Targeted tests：`StsHistoricalClassificationTest`、`StsAlongsideHistoryServiceTest`、`StsAlongsideHistoryJobParamsTest`、`StsHistoricalRepositoryTest`、`AlongsideComputePolicyTest`、`ShipAlongsideDetectorTest`、`AlongsideSceneServiceTest`、`AlongsideRuleConfigHashIsolationTest`、`WorldSeaEventGateTest`、`LoiteringPortSceneTest`；67 tests / 0 failures / 0 errors / 0 skipped。
- JDK `8.0.472-amzn` 执行 `deploy/package.sh` 成功。当前 dirty-worktree 验证 JAR SHA-256：`1d425f3ab14c77de5eca057f09641bd1405f254f664f45eddb6747ab6100e61c`。这不是 committed release JAR；最终修改/commit 后必须重新 package。
- `TIMEZONE_CONTRACT_APPROVED_ASIA_SHANGHAI`：人工已批准本轮参数/seed/event 沿用现网 `Asia/Shanghai`。范围为 `[from, to)`：from inclusive、to exclusive。原 planner 将日期视为 UTC，再经 Timestamp 在 Asia/Shanghai 展示；JDK8 复现测试输入 `2024-09-02 00:00:00` 时 task 为 `08:00`。仅在新 STS task 初始化调用 `planHistoricalTasks`，将 shared planner 的 UTC-encoded 字段恢复为本地 Timestamp；修复后首尾、相邻 slice 连续性和 105 slices 验收通过。共享 planner/HBase/JVM 配置未修改。
- `PORT_BERTH_CIRCULAR_RULE_GAP`：直接相关 Loitering/Scene 代码未找到可复用的成熟 circular/swing 分类规则。使用 Existing Core stationary behavior，不新建 circle detector。
- `EVENT_LIFECYCLE_GAP`：canonical pair event key 和跨方向 overlap 检查已实现；跨 slice 仍可能 fragment，overlap 为 skip，不进行完整 merge/statistics reconciliation。FULL 2Y 前需告知。
- 每次 Handler invocation 只 claim 一个 slice；7-day Smoke 为一个 slice，30-day + sliceDays=7 为 5 slices，2Y 为 105 slices。一次 Trigger 不等于跑完整个多 slice parent；Agent 不执行任何 Trigger。
- Smoke 候选：IMO `1014876` / MMSI `636023596` / IMOLA，single alias，metadata 完整，observed bounds 2019-09-24 至 2026-09-02；242 查询 recentships 仍有 2026-09-09 记录。AIS 历史覆盖仍需 Smoke 验证。
- 242 preflight：JAR `/data/zhengyi/ship-risk-data-0.0.1-SNAPSHOT.jar`，当前 SHA `f53c89ff43a2d8268497ca20dc08bd1af04f0d238c21087dc55674013ec134f3`；PID `1618401`；HTTP `8090`，XXL `30008`；Java Temurin `1.8.0_452`；磁盘可用 121G。
- restart 实际为 `/data/zhengyi/restart.sh` → `stop.sh`（kill -9）→ sleep 2 → `start.sh`。启动日志 `/data/zhengyi/logs/startup.log`，应用日志 `/data/zhengyi/logs/log.log`。未执行这些脚本。
- 当前 Handler 注册证据：`logs/archive/log.2026-09-07.0.log.gz` 中 `2026-09-07 13:56:19.623` 明确记录 `shipStsAlongsideHistoryJobHandler` register success。新 JAR 部署后须再次核验。
- external config `/data/zhengyi/application.properties` SHA `d42e7acf2568b1451e8b45dadfccbad346934b34339c352f9ffcf4f03acc2469`；`ship.behavior.code-commit-sha` 为旧值 `b006fb8ec9167b5c62d974e9c08a751be3ab2c09`，会覆盖 evidence 中 code SHA。部署审批必须包含该键更新为最终 Git SHA；其他业务配置保留。
- DB schema 已只读核验 seed/shadow/backfill batch/task/formal event/detail。两张 formal 表均存在 `batch_no`，可精确按新 batch 核验 formal writes=0。无 DDL、Seed 写入、远程文件写入、部署、重启或 XXL Trigger。

### 当前 Rule → Code → Evidence

| Rule | 实际代码 | 结果与证据 |
| --- | --- | --- |
| Runtime OWN / aliases | `selectHistoricalCandidateRows`、`validateSeed`、`selectImos` | verified 0902 batch 全 aliases，校验身份唯一性和实际可选范围；不按 minotype/enabled 删除 alias；batch identity fingerprint 覆盖全 universe |
| Physical reuse | `computeAlongsideHistory` + STS `ownsEligibilityDecision` | 同一 Core/threshold/World Sea gate；STS 接管 OWN eligibility，默认 Alongside policy 不变；`enableInsert=false` |
| Classification | `StsHistoricalClassification.classify` | `physicalEventType=OBSERVED_ALONGSIDE`；`stsClassification`、reason、targetRole/confidence/raw metadata 在 JSON，service/unknown/barge/generic/weak 进入 review |
| Port/Scene | Core behaviorMode + Scene available | KNOWN port/berth/terminal stationary 抑制；unknown 保持 UNKNOWN；anchorage 仅 context |
| Region | policy `acceptWindow=true`、persistence 无 core gate | outside 仍计算/落库，region associations；NORMAL/ELEVATED 与 physical confidence 分离 |
| Lifecycle | canonical sorted pair event key + historical overlap SQL | 双向同 pair 跨 region overlap skip；保留真实 OWN/TARGET 字段；跨 slice merge gap 明确保留 |
| Evidence | `addPhysicalEvidence` + `persistCandidate` | matched-pair speed/fingerprint、course/time/coverage/gap、own alias fingerprint/universe、target seed/recent IMO、Scene、classification/context/priority、rule/config/seed/code SHA、traceRecipe |
| Time | `planHistoricalTasks` | Asia/Shanghai 本地参数字段保留；`2024-09-02 00:00:00` → `2026-09-03 00:00:00`，sliceDays=7，105 slices |
| Output | `ship_sts_shadow_event` 现有列和 JSON | formal writer=0；behavior_score 不生成 STS Risk Score，旧 Core scoreDetail 留作来源证据；riskScoreStatus=DISABLED |

Manager summary：对 0902 身份候选的历史 AIS 复用 Alongside Core 形成不同物理船的持续搭靠证据，再按 tanker role 和正常替代场景输出 candidate/review；67 targeted tests 已通过，242 新 JAR 已成功重启，等待人工 Smoke，不代表历史识别 precision 已验证。

### 已批准部署记录与人工运行边界（2026-09-09）

以下为最终部署事实，覆盖上方保留的 preflight/待审批记录；本记录不改变已部署代码。

- Seed audit commit：`744944ec247f1237be17b1c0c35a45814932c627`；deployed code commit：`b243bc73d8f7626c9eb8d19322bff1b98daab135`。
- 该代码 commit 后重新 `deploy/package.sh` 成功；最终 JAR SHA-256：`64c3b2d29df638f5d094b92440ebf10fae57bc09a885db91318be2de75b92c0f`，本地/上传临时文件/远程最终文件校验一致。
- Remote JAR：`/data/zhengyi/ship-risk-data-0.0.1-SNAPSHOT.jar`；old SHA：`f53c89ff43a2d8268497ca20dc08bd1af04f0d238c21087dc55674013ec134f3`。
- Old JAR backup：`/data/zhengyi/ship-risk-data-0.0.1-SNAPSHOT.jar.bak.sts-hev1-20260909-b243bc7`；config backup：`/data/zhengyi/application.properties.bak.sts-hev1-20260909-b243bc7`。两份均保留，未清理。
- 批准后仅将 external config 的 `ship.behavior.code-commit-sha` 更新为 deployed code SHA，diff 恰好一行；其他配置不变。新 config SHA：`3df3dcbedb130fe8e479162f359ce41f091dd0c7ed06d455398dc7c1368ee777`。
- 11:06:29 原子替换 JAR，现有 restart.sh 执行成功；新 PID `1730910`，JVM `-Duser.timezone=Asia/Shanghai`，HTTP `8090` / XXL `30008` 均监听。
- 11:06:50.278 新进程注册 `shipStsAlongsideHistoryJobHandler`；11:06:50.492 Main started；11:06:50.617 XXL remoting server started。HTTP 根路径返回 404（没有根路由，不作为业务健康端点）。
- 启动 DB schema 检查成功，日志逐项 column ready / exists, skip ddl；DDL-auto=none、schema-auto-migration-enabled=false、formal-write-enabled=false。只读 DB 复核：11:05:00 起至 11:07:48，两张 formal 表新增均为 0。未执行历史计算，因此不是 Smoke 后的 formal-write 验证。
- 部署后再次 DESC 六张 seed/shadow/batch/task/formal 表，SHOW CREATE seed；Seed 仍 857 rows / 786 IMO / 825 enabled rows。四个新运行 batch 尚不存在，Smoke shadow count=0。
- 参数基于已部署 commit 的 `StsAlongsideHistoryJobParams`：mode=STS_ALONGSIDE_HISTORY_V1，seedBatchNo=sts-crude-identity-20260902-v1，regionSetCode=STS_MEETING_PRIORITY_REGION_SET_V1（CONTEXT_ONLY），sliceDays=7，parallelism=1，writeMode=SHADOW_ONLY，resume=true。时间全部 Asia/Shanghai，左闭右开。
- Smoke：batchNo=sts-hev1-smoke-1-20260909-v1，imos=[1014876]，2026-08-01 00:00:00 至 2026-08-08 00:00:00。单 alias MMSI 636023596 / IMOLA，metadata 完整，不属问题行；实际 AIS coverage 待运行确认，0 event 不自动失败。
- 20/100：batchNo 分别 sts-hev1-validate-20-20260909-v1 / sts-hev1-validate-100-20260909-v1；shipOffset=0、shipCount=20/100；2026-08-04 至 2026-09-03，5 slices。选船按 seed ship_ordinal、imo 排序去重，复现时不加 enabled/minotype 条件。
- Full：batchNo=sts-hev1-full-2y-20260909-v1；shipOffset=0、shipCount=786；2024-09-02 至 2026-09-03，105 slices。786 是本次已核验 batch 的参数值，不是 Java 永久 gate。Known cross-slice fragmentation gap 保留。
- 一次 invocation 只处理一个 slice，30D/2Y 不会在一次 Trigger 内自动跑完。当前只允许用户手工 Smoke，完成后检查 task status、Physical/identity/role/Scene/classification/evidence，以及两张正式表精确 batch_no count=0；停止并反馈，不自动执行 20 ships。
- Agent XXL Trigger=NO；DB writes=NO；DDL=NO；GitLab writes=NO；Production Alongside / Loitering thresholds changed=NO。Remote JAR/config 单项更新/restart 均来自单独部署批准。

提交前 Karpathy review：范围仅本次 diff；8 小时时区错误已由先失败后通过的测试验证修复；补齐 metadata 冲突来源、alias/matched-pair fingerprints、gap evidence 和双向 overlap SQL 测试。未发现阻止本地提交的问题；圆形泊位规则和跨 slice reconciliation 属已声明 gap，需人工 Smoke 验证实际 precision。整体合规评分 9/10；优先级为人工 Smoke 验证，不自动扩展算法。

下文至旧 `VALIDATION_RECORD` 为 2026-09-08 实现快照，保留历史核验依据；当前规则和实现以上述本轮章节为准。

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
| 1 | OWN Fleet | 哪些物理船可作为 OWN | 当前代码硬校验 757 IMO / 825 enabled unique MMSI rows，且每行 `minotype='原油船'`；Seed logic audit complete / DB verification pending；Approved Fleet 仍 PENDING HUMAN APPROVAL；Alias Universe 与 Eligibility 当前混为单一 enabled 标志 | `StsAlongsideHistoryService.validateSeed`; `StsSeedIdentityRepository` | seed batch/count/fingerprint；Seed Audit S1–S10 | selected IMO/aliases；不是 Approved Fleet；2 个 mixed-alias IMO 的 self-exclusion 可能不完整 |
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
| R1 OWN Fleet | `StsAlongsideHistoryService`; `StsSeedIdentityRepository` | `validateSeed`; `selectImos/selectIdentityRows` | hard-coded 757/825; minotype filter; seedBatchNo | counts、seed fingerprint；Seed logic audit complete / DB verification pending；alias universe = eligibility conflation | PARTIAL |
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
| LOGIC_AUDIT_COMPLETE / DB_VERIFICATION_PENDING | Approved Fleet | Raw CSV 逻辑审计已完成；757/825 是对 Raw CSV 应用 `minotype='原油船'` 得到的 derived strict subset；完整被排除的 29 个 IMO 为 6 UNKNOWN + 21 液货船 + 2 游艇；2 个 mixed-alias IMO 各有一个 alias 未进入 strict subset；Actual DB 待 242 只读核验；Approved Fleet 仍待人工批准。 |
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
| STEP 2 — STS CLASSIFICATION | Crude OWN + identity + role + context | `STS_RULE_SPEC_V1` / `TARGET_ROLE_POLICY_V1` | identity 部分实现；Scene UNKNOWN 已对齐；分类 taxonomy 未实现；Seed logic audit 已完成、DB verification pending | coarse role/Scene 在 JSON；shadow classification 缺失 | Scene/Alongside targeted regression 73 tests PASS；批准 taxonomy 无验收测试 | IN_PROGRESS | 当前已批准任务：`STS_HISTORICAL_EVENT_V1` |
| STEP 3 — EVENT LIFECYCLE | cross-slice/dedup/A-B/region overlap | Event Merge / Slice | 仅窗内 merge + overlap skip | exact key/times/primary region | boundary/dedup 局部测试 | IN_PROGRESS | 设计并批准 fragment reconciliation 后再实现 |
| STEP 4 — HISTORICAL VALIDATION | 1→20→100→Approved Fleet | Gold/Review Dataset | 本任务未执行历史 batch | 无新增 runtime result | 未运行历史样本 | NOT_STARTED | 等 Step 2/3 收敛后小样本开始 |
| STEP 5 — CONTEXT / DARK EXTENSION | 最后处理 gap/spoof/dark/export context | 当前 OUT_OF_SCOPE | 未开发 | Independent Evidence names only | 未运行 | NOT_STARTED | 不提前启动 |

## 10. MANAGER_SUMMARY

- 一句话规则：当前程序先从待审计的原油船身份种子读取各历史 MMSI，在重点区域内筛出海上低速轨迹并回放附近 AIS；两船经时间对齐后满足持续近距离、低速、SOG 差和 COG 平行条件即形成 Alongside 物理证据，再排除同 IMO，但 Target Role 尚未真正把结果分成 `STS_CANDIDATE` 与 `STS_REVIEW_REQUIRED`。
- 当前代码实现：`StsJobs.shipStsAlongsideHistoryJobHandler` → `StsAlongsideHistoryService.runOneInvocation/processSlice` → `ShipBehaviorAnalysisService.computeAlongsideHistory` → `ShipAlongsideDetector.detectWithTrace` → `StsAlongsideHistoryService.persistCandidate` → `StsShadowEventRepository.insertIfNoOverlappingPhysicalEvent`。
- 事件依据：实际存在距离、持续时间、OWN/TARGET SOG 与 SOG difference、COG difference/parallel ratio、轨迹同步时间差、seed identity、粗粒度 Target Role、Scene/World Sea、primary region 与 replay/trajectory fingerprints；exact pair points 未持久化。
- 当前进度：Physical Alongside 复用链、同 seed IMO 排除、shadow-only 写和 Scene isolation/UNKNOWN 已完成；Seed logic audit 已完成但 DB verification pending；Approved Fleet 仍待人工批准，STS classification 等待当前已批准任务实现。
- 当前已批准任务：`STS_HISTORICAL_EVENT_V1`。Manifest 后置，不是当前 Approved Task。

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

# STS Seed Audit (STS_SEED_AUDIT_V1)

Audit Status:
LOGIC_AUDIT_COMPLETE
DB_VERIFICATION_PENDING

Audit Date:
2026-09-08

Branch:
codex/sts-phase1-4-fleet-backfill-v1

Base Git SHA:
3cf02a4f9ba4f63c0c1234ccb09e21dbf9090620

Task Type:
READ-ONLY DATA AUDIT

## S1. AUDIT_SOURCES

| Source | Location | Format | Identity Rows | Distinct IMO | Distinct MMSI |
|--------|----------|--------|--------------:|-------------:|--------------:|
| Raw Seed (normalized) | `var/sts/sts-crude-identity-20260902.csv` | CSV | 857 | 786 | 857 |
| Raw Source (meeting) | `0902/crude-oil-tanker-1imo-vs-1mmsi-20260902.xlsx` + `0902/crude-oil-tanker-1imo-vs-2mmsi-时间连续性强-20260902.xlsx` | XLSX × 2 | 857 (715+142) | 786 (715+71) | 857 |
| Reference List | `0902/crude-oil-tanker-list-20260902(总名单).csv` | CSV | — | 2,571 | — |
| DB Seed Table | `dataanalysis.ship_sts_seed_identity` (seed_batch_no=`sts-crude-identity-20260902-v1`) | MySQL | UNVERIFIED (DB host blocked; see S8) | UNVERIFIED | UNVERIFIED |
| Code Expectation | `StsAlongsideHistoryService.EXPECTED_IMOS/EXPECTED_IDENTITIES` | Java hard-code | 825 | 757 | 825 |

**Raw Seed SHA-256:**
`5a699db07bdada8d66d7271665c0f1a8e2f21017bf3f16a7fcfb217b812b61b0`

**Git evidence:**
- Commit `d748215` changed hard-coded counts from 786/857 → 757/825, with message "after removing 32 non-crude rows"
- Commit changed 4 Java files only; no data files changed in the same commit
- The actual filter is `minotype IS NULL OR minotype <> '原油船'` (combines UNKNOWN + EXPLICIT_NON_CRUDE)

## S2. COUNT_RECONCILIATION

| Dataset | Identity Rows | Distinct IMO | Distinct MMSI | Status |
|---------|--------------:|-------------:|--------------:|--------|
| meetingApproxCount | — | ≈ 787 | — | Meeting approximation |
| rawIdentityCandidate (full CSV) | 857 | 786 | 857 | **CONFIRMED** from file |
| └─ CRUDE (minotype=原油船) | 825 | 757 | 825 | Subset of raw |
| └─ UNKNOWN (minotype empty) | 7 | 6 | 7 | Subset of raw |
| └─ EXPLICIT_NON_CRUDE | 25 | 25 | 25 | Subset of raw |
| currentStrictCodeExpectation | 825 | 757 | 825 | Matches CRUDE subset exactly |
| actual DB (sts-crude-identity-20260902-v1) | UNVERIFIED | UNVERIFIED | UNVERIFIED | DB host connection blocked |

**Finding:** `currentStrictCodeExpectation = 757 IMO / 825 identity rows` is exactly the set of raw seed rows where `minotype = '原油船'`. The "32 rows removed" claim is arithmetically correct (857 − 825 = 32) and corresponds to filtering out all rows where `minotype IS NULL OR minotype <> '原油船'`.

## S3. ROW_DIFF_SUMMARY

| Diff Category | Count |
|--------------|------:|
| UNCHANGED | 825 |
| RAW_ONLY | 32 |
| DERIVED_STRICT_ONLY | 0 |
| RAW_VS_DERIVED_METADATA_CHANGED | 0 |
| **Total raw rows** | **857** |

**Conclusion:** Applying `minotype='原油船'` deterministically derives 825 rows from the 857-row Raw CSV, so `DERIVED_STRICT_FILTER_DIFF=32`. This is a Raw → derived strict subset comparison, not proof of `ACTUAL_DB_DELETION=32`. Actual DB row/enabled state remains pending read-only verification.

**Filter applied:**
```
minotype IS NULL OR minotype <> '原油船'
```
This combines two semantically distinct categories into one "non-crude" bucket.

## S4. SEED_IDENTITY_ROW_DIFF_V1

All 32 RAW_ONLY rows listed below. Canonical key: IMO|MMSI.

### S4.1 UNKNOWN Minotype (7 rows, 6 IMOs)

| IMO | MMSI | Name | Type | Minotype | Identity Mode | first_seen | last_seen |
|-----|------|------|------|----------|---------------|------------|-----------|
| 9004530 | 447879000 | FAHAD | (empty) | (empty) | SINGLE_OBSERVED | 2024-08-01 10:28:29 | 2026-08-29 15:01:02 |
| 9039626 | 620241000 | VARADA BLESSING | (empty) | (empty) | SINGLE_OBSERVED | (null) | 2026-09-02 08:39:12 |
| 9085376 | 613003126 | TORY | (empty) | (empty) | SINGLE_OBSERVED | 2026-06-23 18:17:24 | 2026-09-02 08:41:13 |
| 9108154 | 511725111 | ADA | (empty) | (empty) | SINGLE_OBSERVED | 2018-04-09 20:59:50 | 2026-08-10 00:08:03 |
| 9187772 | 371382000 | GALIEAN 43 | (empty) | (empty) | SINGLE_OBSERVED | 2015-07-19 01:52:25 | 2026-09-01 21:38:18 |
| 9088689 | 239027000 | OLYMPIC LEGACY | (empty) | (empty) | DUAL_NON_OVERLAP | (null) | 2015-08-14 17:17:31 |
| 9088689 | 538006511 | BELEMA SWEET | (empty) | (empty) | DUAL_NON_OVERLAP | 2015-08-14 17:29:29 | 2026-06-17 15:10:24 |

### S4.2 EXPLICIT_NON_CRUDE Minotype (25 rows, 25 IMOs)

| IMO | MMSI | Name | Type | Minotype | Identity Mode | first_seen | last_seen |
|-----|------|------|------|----------|---------------|------------|-----------|
| 1023425 | 538011161 | ADVANTAGE SOLO | 其他类型液货船 | 液货船 | SINGLE_OBSERVED | 2025-05-29 01:06:03 | 2026-09-02 08:39:21 |
| 1035545 | 538011665 | NISSOS PIPERI | 其他类型液货船 | 液货船 | SINGLE_OBSERVED | 2022-03-13 12:52:20 | 2026-09-02 08:41:24 |
| 1085497 | 538012798 | BIDIYA | 其他类型液货船 | 液货船 | SINGLE_OBSERVED | 2024-07-14 03:14:57 | 2026-09-02 08:33:21 |
| 9081174 | 351667000 | ZOYA1 | 其他类型液货船 | 液货船 | SINGLE_OBSERVED | 2015-08-01 00:07:49 | 2026-05-19 09:06:44 |
| 9212917 | 422221000 | HASNA | 其他类型液货船 | 液货船 | SINGLE_OBSERVED | 2019-02-24 02:19:10 | 2026-09-02 03:57:04 |
| 9212929 | 422222000 | GUIBEIYU88888 | 渔船 | 渔船 | DUAL_NON_OVERLAP | 2019-03-03 16:22:17 | 2026-09-02 08:36:13 |
| 9240407 | 268256504 | PRINCESS SOFIA | - | 液货船 | DUAL_NON_OVERLAP | 2025-12-27 23:06:08 | 2026-09-02 08:40:38 |
| 9361512 | 636013683 | NEW ABILITY | 其他类型液货船 | 液货船 | SINGLE_OBSERVED | 2015-07-24 06:09:48 | 2026-09-02 08:38:19 |
| 9397793 | 356075000 | CORAL PEARL | 其他类型液货船 | 液货船 | SINGLE_OBSERVED | 2015-07-17 05:05:30 | 2026-09-02 08:40:30 |
| 9413834 | 249848000 | PATROCLUS | 其他类型液货船 | 液货船 | SINGLE_OBSERVED | 2015-07-17 01:00:16 | 2026-09-02 06:03:16 |
| 9432062 | 636014502 | ELENI | 游艇 | 游艇 | SINGLE_OBSERVED | 2015-07-17 05:05:42 | 2026-09-02 08:39:06 |
| 9468322 | 477477900 | NEW PEARL | 其他类型液货船 | 液货船 | SINGLE_OBSERVED | 2015-07-17 05:02:57 | 2026-09-02 08:41:23 |
| 9530890 | 241079000 | MARIA | 其他类型液货船 | 液货船 | SINGLE_OBSERVED | 2015-07-17 00:53:34 | 2026-09-02 08:39:37 |
| 9679593 | 229597000 | ZOURVA | 其他类型液货船 | 液货船 | SINGLE_OBSERVED | 2017-01-01 00:20:11 | 2026-09-02 08:38:47 |
| 9749518 | 241510000 | RUNNER | 其他类型液货船 | 液货船 | SINGLE_OBSERVED | 2017-03-08 13:39:35 | 2026-09-02 08:34:18 |
| 9759757 | 538007001 | FRONT CROWN | 其他类型液货船 | 液货船 | SINGLE_OBSERVED | 2016-07-09 14:05:13 | 2026-09-02 08:41:05 |
| 9790270 | 241519000 | MARAN AJAX | 其他类型液货船 | 液货船 | SINGLE_OBSERVED | 2017-07-11 09:25:42 | 2026-09-02 08:40:28 |
| 9826732 | 636018688 | ANDREIOS | 其他类型液货船 | 液货船 | SINGLE_OBSERVED | (null) | 2026-09-02 08:41:02 |
| 9829629 | 538008147 | DIJILAH | - | 液货船 | SINGLE_OBSERVED | 2017-10-07 05:56:13 | 2026-09-02 08:40:23 |
| 9844277 | 538008215 | OLYMPIC LIFE | 游艇 | 游艇 | SINGLE_OBSERVED | 2017-08-17 12:32:45 | 2026-09-02 08:40:14 |
| 9866380 | 273618730 | OKEANSKY PROSPECT | - | 液货船 | SINGLE_OBSERVED | 2022-08-23 06:43:58 | 2026-09-02 08:40:01 |
| 9870812 | 538009083 | BAHLA | 其他类型液货船 | 液货船 | SINGLE_OBSERVED | 2017-11-02 12:33:26 | 2026-09-02 08:41:34 |
| 9892432 | 230687000 | JAARLI | 其他类型液货船 | 液货船 | SINGLE_OBSERVED | 2021-07-12 11:08:10 | 2026-09-02 08:40:22 |
| 9988695 | 352005771 | ENERGIA VIKING | - | 液货船 | SINGLE_OBSERVED | 2025-06-14 10:24:53 | 2026-09-02 08:39:34 |
| 9989089 | 352004766 | SOUTHERN RESPECT | 其他类型液货船 | 液货船 | SINGLE_OBSERVED | 2024-08-13 19:54:00 | 2026-09-02 08:39:32 |

**Minotype distribution among non-crude:**
| Minotype | Rows | IMOs |
|----------|-----:|-----:|
| (empty / unknown) | 7 | 6 |
| 液货船 | 22 | 22 |
| 渔船 | 1 | 1 |
| 游艇 | 2 | 2 |

## S5. IMO_IMPACT_SUMMARY_V1

**Affected IMOs: 31 distinct IMOs** (not 29 — 29 fully excluded + 2 mixed-alias IMOs)

### S5.1 IMO_CODE_FILTERED_OUT — 29 IMOs completely removed (no crude alias at all)

These IMOs disappear entirely from the derived strict subset because ALL their aliases fail the minotype filter.

| Category | Count | IMOs |
|----------|------:|------|
| UNKNOWN minotype, all aliases | 6 | 9004530, 9039626, 9085376, 9088689, 9108154, 9187772 |
| 液货船, 1 alias each | 21 | 1023425, 1035545, 1085497, 9081174, 9212917, 9361512, 9397793, 9413834, 9468322, 9530890, 9679593, 9749518, 9759757, 9790270, 9826732, 9829629, 9866380, 9870812, 9892432, 9988695, 9989089 |
| 游艇, 1 alias each | 2 | 9432062, 9844277 |

Classification note: "CODE_FILTERED_OUT" means the current code filter removes these IMOs. It does NOT by itself prove the physical vessel is not a crude oil tanker. Business eligibility requires separate confirmation.

### S5.2 CURRENT_STRICT_ELIGIBLE — 2 IMOs with mixed crude/non-crude aliases

These IMOs remain in the derived strict subset (counted in the 757) because they have at least one alias with minotype='原油船'. However, one alias each is outside that subset.

| IMO | Crude Alias (retained) | Non-Crude Alias (filtered) | Non-crude minotype |
|-----|----------------------|--------------------------|-------------------|
| 9212929 | 370969000 (HENNA, 石油化学品船/原油船) | 422222000 (GUIBEIYU88888, 渔船/渔船) | 渔船 |
| 9240407 | 373930000 (PRINCESS SOFIA, 石油化学品船/原油船) | 268256504 (PRINCESS SOFIA, -/液货船) | 液货船 |

### S5.3 IMO_CONFIRMED_INELIGIBLE

NONE based on seed data alone.

The seed minotype field is a single metadata observation, not a definitive IMO-level type determination. An IMO with a non-crude alias minotype could be:
- A vessel that genuinely is not a crude tanker
- A vessel whose metadata is stale/incorrect for that MMSI/alias
- A vessel that changed type over time (historical MMSI from a different vessel identity period)

No IMO can be confirmed ineligible from seed data alone without external vessel reference data.

### S5.4 IMO_ELIGIBILITY_UNRESOLVED — 6 IMOs with UNKNOWN minotype

IMO 9004530, 9039626, 9085376, 9088689, 9108154, 9187772 have empty minotype. Their vessel type is simply unknown from seed data. They are currently filtered out by the code (treated as "non-crude") but this is an absence of evidence, not evidence of non-crude.

## S6. IDENTITY_ALIAS_UNIVERSE_AUDIT

### Critical finding

The current implementation conflates:

**Fleet Eligibility** (which IMOs are Crude Oil Tankers)
with
**Identity Alias Universe** (which MMSIs belong to which physical vessels)

The code filters aliases by `minotype='原油船'` via the `enabled=1` flag + `countNonCrudeRows == 0` check. This means if an IMO has a historical MMSI alias whose observed minotype is not "原油船", that alias is entirely removed from identity knowledge.

**Risk:** If a same-IMO different-MMSI pair is encountered where one alias was filtered out due to minotype, the self-exclusion policy (`ownMmsis.contains(targetMmsi)` + `ownImo.equals(targetImo)`) would FAIL, potentially causing a same-physical-vessel pair to be recorded as an STS event.

### Alias retention analysis for all 32 RAW_ONLY rows

| IMO | MMSI | Eligibility Status | Alias Retention Decision | Reason |
|-----|------|-------------------|------------------------|--------|
| 9004530 | 447879000 | IMO_CODE_FILTERED_OUT (UNKNOWN) | UNRESOLVED | Minotype unknown; IMO eligibility unknown; if IMO later confirmed crude, this alias must be RETAINED for self-exclusion |
| 9039626 | 620241000 | IMO_CODE_FILTERED_OUT (UNKNOWN) | UNRESOLVED | Minotype unknown; IMO eligibility unknown; if IMO later confirmed crude, this alias must be RETAINED |
| 9085376 | 613003126 | IMO_CODE_FILTERED_OUT (UNKNOWN) | UNRESOLVED | Minotype unknown; IMO eligibility unknown; if IMO later confirmed crude, this alias must be RETAINED |
| 9108154 | 511725111 | IMO_CODE_FILTERED_OUT (UNKNOWN) | UNRESOLVED | Minotype unknown; IMO eligibility unknown; if IMO later confirmed crude, this alias must be RETAINED |
| 9187772 | 371382000 | IMO_CODE_FILTERED_OUT (UNKNOWN) | UNRESOLVED | Minotype unknown; IMO eligibility unknown; if IMO later confirmed crude, this alias must be RETAINED |
| 9088689 | 239027000 | IMO_CODE_FILTERED_OUT (UNKNOWN) | UNRESOLVED | Minotype unknown; dual-MMSI IMO; if IMO later confirmed crude, both aliases must be RETAINED |
| 9088689 | 538006511 | IMO_CODE_FILTERED_OUT (UNKNOWN) | UNRESOLVED | Same as above |
| 1023425 | 538011161 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | Explicitly 液货船 not 原油船; but IMO-level confirmation needed before permanently excluding; seed source itself was labeled "crude-oil-tanker" list |
| 1035545 | 538011665 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | Same as above |
| 1085497 | 538012798 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | Same as above |
| 9081174 | 351667000 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | Same as above |
| 9212917 | 422221000 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | Same as above |
| 9212929 | 422222000 | CURRENT_STRICT_ELIGIBLE (mixed) | **RETAIN_FOR_SELF_EXCLUSION** | Raw Identity Source maps this MMSI to the same IMO as OWN; at least one current alias has `minotype='原油船'`, so the IMO remains in the strict subset. This does not externally confirm its true historical vessel type. |
| 9240407 | 268256504 | CURRENT_STRICT_ELIGIBLE (mixed) | **RETAIN_FOR_SELF_EXCLUSION** | Raw Identity Source maps this MMSI to the same IMO as OWN; at least one current alias has `minotype='原油船'`, so the IMO remains in the strict subset. This does not externally confirm its true historical vessel type. |
| 9361512 | 636013683 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | See 液货船 note above |
| 9397793 | 356075000 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | See 液货船 note above |
| 9413834 | 249848000 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | See 液货船 note above |
| 9432062 | 636014502 | IMO_CODE_FILTERED_OUT (游艇) | UNRESOLVED | Alias minotype alone is insufficient to delete identity knowledge; IMO eligibility/identity evidence is still required |
| 9468322 | 477477900 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | See 液货船 note above |
| 9530890 | 241079000 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | See 液货船 note above |
| 9679593 | 229597000 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | See 液货船 note above |
| 9749518 | 241510000 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | See 液货船 note above |
| 9759757 | 538007001 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | See 液货船 note above |
| 9790270 | 241519000 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | See 液货船 note above |
| 9826732 | 636018688 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | See 液货船 note above; also first_seen is null |
| 9829629 | 538008147 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | See 液货船 note above; type is "-" |
| 9844277 | 538008215 | IMO_CODE_FILTERED_OUT (游艇) | UNRESOLVED | Alias minotype alone is insufficient to delete identity knowledge; IMO eligibility/identity evidence is still required |
| 9866380 | 273618730 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | See 液货船 note above; type is "-" |
| 9870812 | 538009083 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | See 液货船 note above |
| 9892432 | 230687000 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | See 液货船 note above |
| 9988695 | 352005771 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | See 液货船 note above; type is "-" |
| 9989089 | 352004766 | IMO_CODE_FILTERED_OUT (液货船) | UNRESOLVED | See 液货船 note above |

### Alias universe impact summary

| Retention Decision | Count | IMOs |
|--------------------|------:|------|
| RETAIN_FOR_SELF_EXCLUSION | 2 | 9212929 (1 alias), 9240407 (1 alias) |
| UNRESOLVED | 30 | 29 completely filtered IMOs (6 UNKNOWN + 21 液货船 + 2 游艇; IMO 9088689 has 2 aliases) |

**Key risk:** 2 IMOs (9212929, 9240407) are `CURRENT_STRICT_ELIGIBLE` because each has at least one current `minotype='原油船'` alias, but each also has one Raw Identity Source alias outside the strict subset. Those aliases are `RETAIN_FOR_SELF_EXCLUSION` based on the same-IMO identity mapping, not on their alias type/minotype and not as external confirmation of historical vessel type.

## S7. DATA_QUALITY_FINDINGS

### S7.1 Duplicate Identity Keys
NONE FOUND. All IMO|MMSI pairs are unique in the raw CSV. (Consistent with DB UNIQUE KEY `uk_seed_imo_mmsi`.)

### S7.2 MMSI → Multiple IMO
NONE FOUND. All 857 MMSIs map to exactly one IMO.

### S7.3 Invalid first_seen > last_seen
NONE FOUND. All rows with both dates have first_seen <= last_seen.

### S7.4 Null / Missing Observed Bounds
16 rows have null/empty first_seen. 0 rows have null last_seen.

Affected IMOs (all SINGLE_OBSERVED or one alias of DUAL_NON_OVERLAP):
1052878, 9039626, 9079066, 9079107, 9826732, 9878826, 9891672, 9891684, 9900801, 9920760, 9923994, 9088689 (alias 239027000), 9427639 (alias 240973000), 9478664 (alias 566152000), 9802152 (alias 635122100), 9926738 (alias 636021534).

Note: `intersectObservedBounds` treats null first_seen as unbounded-left (uses sliceFrom), so these rows are still queryable from the start of the requested time range.

### S7.5 identity_mode distribution
| Mode | Rows |
|------|-----:|
| SINGLE_OBSERVED | 715 |
| DUAL_NON_OVERLAP | 122 |
| DUAL_OVERLAP | 20 |
| **Total** | **857** |

71 IMOs have dual MMSI (61 DUAL_NON_OVERLAP + 10 DUAL_OVERLAP).

### S7.6 Mixed Enabled per IMO (enabled=1 vs enabled=0)
NOT VERIFIABLE FROM CSV ALONE. The raw CSV does not contain an `enabled` column; this is a DB-level field set during import. Expected state: 825 rows enabled=1, 32 rows enabled=0 (if the seed batch was imported with the filter applied via enabled flag). Requires DB access to confirm.

### S7.7 Metadata Inconsistency within Same IMO
Two mixed-alias IMOs show type/minotype divergence between aliases:
- **IMO 9212929**: alias 370969000 (HENNA, 石油化学品船/原油船) vs 422222000 (GUIBEIYU88888, 渔船/渔船). Different names, different types — possible MMSI reassignment or data quality issue.
- **IMO 9240407**: both aliases named PRINCESS SOFIA; type 石油化学品船 vs -, minotype 原油船 vs 液货船. Consistent identity but metadata inconsistency across aliases.

## S8. REMAINING_UNKNOWNS

1. **Actual DB seed batch unverified.** MySQL host `60.205.109.122:3309` blocked connection from current IP (`58.37.50.125`) due to "many connection errors". Cannot confirm:
   - Whether `sts-crude-identity-20260902-v1` batch exists in DB with expected row counts
   - Whether the 32 non-crude rows are `enabled=0` or absent entirely
   - Whether there are additional seed batches
   - `created_at` timestamps, `ship_ordinal` values, actual DB schema

   Mitigation: The raw CSV source and Java filter logic are deterministic and independently reproducible. The 757/825 code expectation matches exactly what the minotype filter would produce from the raw CSV. The logical structure of the diff is confirmed. DB-level verification is pending the current task's SSH 242 read-only closure.

2. **Vessel type confirmation for 29 excluded IMOs.** Seed minotype alone is insufficient to confirm IMO-level vessel type. External vessel reference data (e.g., Clarksons, IHS, or internal ship database) would be needed to definitively classify each of the 29 excluded IMOs as in-scope or out-of-scope.

3. **Alias provenance for 2 mixed-alias IMOs.** The reason why IMOs 9212929 and 9240407 have one crude and one non-crude alias is not documented. Possibilities include: MMSI reassignment across different physical vessels sharing an IMO (unlikely for IMO), data quality error in minotype, or historical vessel type change. This affects whether the non-crude alias genuinely belongs to the same physical ship.

4. **Source of "≈787" meeting approximation.** The meetingApproxCount of ~787 does not match 786 or 757 exactly. Its derivation is not documented in a verifiable data source.

## S9. ANSWERS_TO_4_CORE_QUESTIONS

### Q1: 786 / 857 为什么变成当前数据？

**Answer:** `currentStrictCodeExpectation` of 757 IMO / 825 identity rows is obtained by applying a single filter to the raw seed: `minotype = '原油船'`. The code checks `countNonCrudeRows == 0` where non-crude is defined as `minotype IS NULL OR minotype <> '原油船'`. This filter removes 32 rows and 29 IMOs from the raw 857/786.

Source: raw CSV (`var/sts/sts-crude-identity-20260902.csv`) + Java code (`StsSeedIdentityRepository.countNonCrudeRows`, `StsAlongsideHistoryService.validateSeed`).

### Q2: 所谓 32 rows 到底是什么？

**Answer:** `DERIVED_STRICT_FILTER_DIFF=32`: applying `minotype='原油船'` to the Raw CSV excludes exactly 32 identity rows. This is deterministic Raw → derived subset evidence; it is not an actual DB deletion fact. DB-only/current-only and DB metadata-change facts remain unverified until DB read-only closure.

Composition:
- **7 rows (6 IMOs)** with unknown/empty minotype → filtered as "non-crude" by NULL check
- **25 rows (25 IMOs)** with explicit non-crude minotype (22 液货船, 2 游艇, 1 渔船)
- **2 of the 32 rows** belong to IMOs that also have a crude alias (9212929, 9240407) — these IMOs remain in the fleet but lose one alias each
- **30 of the 32 rows** cause 29 IMOs to be completely absent from the derived strict subset (28 single-alias IMOs + 1 dual-alias IMO 9088689)

### Q3: 哪些 IMO 真正失去 OWN eligibility？

**Answer:** Based on seed data alone, NONE can be confirmed ineligible with high confidence. The minotype field is a single metadata observation, not a definitive vessel-type determination.

Classification of the 29 IMOs currently filtered out:
- **6 IMOs (UNKNOWN minotype):** Eligibility UNKNOWN. Filtered out by the code's aggressive NULL treatment, but absence of evidence ≠ evidence of non-crude.
- **21 IMOs (液货船 minotype):** Eligibility UNRESOLVED. `液货船` is broader than crude oil tanker and requires reliable IMO-level evidence.
- **2 IMOs (游艇 minotype):** Eligibility and alias retention remain UNRESOLVED from this single alias metadata field; do not delete identity knowledge without further evidence.

### Q4: 哪些 MMSI 应继续保留在 Identity Alias Universe？

**Answer:** Identity Alias Universe and Fleet Eligibility are separate concepts. An MMSI alias should be retained if it genuinely belongs to an eligible IMO, regardless of that alias's individual minotype.

Retain for self-exclusion:
- **9212929 / 422222000** — Raw Identity Source maps this MMSI to the same IMO as OWN; the IMO is `CURRENT_STRICT_ELIGIBLE`, so the alias must remain in identity knowledge. This is not external confirmation of vessel type.
- **9240407 / 268256504** — same basis: Raw Identity Source same-IMO mapping and current strict eligibility, independent of this alias's minotype.

Unresolved (30 rows across 29 completely filtered IMOs):
- 6 UNKNOWN-minotype IMOs (7 rows including dual-MMSI 9088689)
- 21 液货船 IMOs
- 2 游艇 IMOs

Without further identity/eligibility evidence, none of these 30 aliases is marked `DO_NOT_RETAIN`.

## S10. CURRENT_APPROVED_TASK

**STS_HISTORICAL_EVENT_V1**

Seed Manifest remains deferred and is not the current Approved Task.

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

当前 Fleet 状态必须严格区分以下口径。

## Meeting Approximation

meetingApproxCount:

≈ 787

其含义是会议阶段用于需求讨论的近似规模。

它不是数据库或运行时精确 Fleet。

## Raw Identity Candidate

rawIdentityCandidate:

786 IMO
857 identity rows

Source: `var/sts/sts-crude-identity-20260902.csv`
SHA-256: `5a699db07bdada8d66d7271665c0f1a8e2f21017bf3f16a7fcfb217b812b61b0`

这是 Raw Identity Candidate 口径。

## Current Strict Code Expectation

currentStrictCodeExpectation:

757 IMO
825 identity rows

= 原始 seed 中 `minotype = '原油船'` 的子集。

786 → 757：29 个 IMO 被 minotype 过滤完全移除。
857 → 825：32 条 identity row 被 minotype 过滤移除。
其中 2 个 IMO 仍有 crude alias 保留，但各有一个 non-crude alias 被移除。

这是当前代码中存在的 Strict Expectation。
它仅代表 currentStrictCodeExpectation。
它不是 Approved Fleet。

## Audited Eligible Fleet Candidate

Audited Eligible Fleet Candidate:

PENDING HUMAN APPROVAL

Seed logic audit 已完成，DB verification pending（见 STS Seed Audit 章节）。
审计产出了候选结论与 UNRESOLVED 列表，
但在人工批准前：

757 / 825
以及任何审计后的修订数字

都不是 Approved Fleet。

## Audited Alias Universe

Audited Alias Universe:

PENDING HUMAN APPROVAL

当前代码将 Fleet Eligibility 与 Alias Universe 混在同一个 `enabled` 标志中。
审计发现 2 个 `CURRENT_STRICT_ELIGIBLE` IMO (9212929, 9240407) 各有一个 alias 未进入 strict subset，
可能导致 self-exclusion 漏洞。
详见 Seed Audit S6。

## Approved Fleet

Approved Fleet:

PENDING HUMAN APPROVAL

Java 中的固定：

EXPECTED_IMOS
EXPECTED_IDENTITIES

不得继续作为长期业务 Source of Truth。
后续 Fleet 必须由 Approved Seed Manifest 记录和校验。

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

`STS_HISTORICAL_EVENT_V1` 当前批准语义：以下重点区域仅为 attribution/context/reviewPriority；不再作为本路径 Physical Analysis Gate。World Sea Mask 独立保持 Physical safety gate。以下 Manifest 设计仍后置。

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

本轮 `STS_HISTORICAL_EVENT_V1` 已获准以 verified 0902 identity candidate 全 aliases 执行 Shadow Validation，含 DB disabled rows；Approved Fleet 仍待人工批准。此限定覆盖下面的历史 eligibility 前置要求。

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

`STS_HISTORICAL_EVENT_V1` 经 2026-09-09 人工批准使用现网 `Asia/Shanghai` 本地日期时间，范围 `[from, to)`；仅本路径 task 初始化转换已修正。其他路径不在本轮变更范围。

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

1. Approved Fleet 尚未形成（Seed logic audit 已完成，DB verification pending，且待人工批准）；
2. Fleet Eligibility 与 Identity Alias Universe 混为单一 `enabled` 标志；2 个 `CURRENT_STRICT_ELIGIBLE` IMO (9212929, 9240407) 的 Raw Identity Source alias 未进入 strict subset，可能造成 self-exclusion 漏洞；
3. Seed 757/825 是 Java 硬编码，尚未通过 Seed Manifest 管理；
4. 29 个完整被过滤 IMO 中 6 个 minotype 为 UNKNOWN、21 个为 液货船、2 个为游艇，需可靠 IMO-level 证据确认业务资格和 alias retention；
5. Target type/minotype 可能缺失、过期或冲突；
6. MMSI 历史有效期并不完全等于 first_seen / last_seen；
7. Scene 数据可能不可用；
8. Scene UNKNOWN 不能证明 Offshore；
9. AIS gap 不能证明主动关闭 AIS；
10. 双方同时 AIS-dark 时无法用 AIS 直接确认 Physical STS；
11. Actual Cargo Transfer 无法仅靠 AIS 确认；
12. 当前跨 Slice Fragment Merge 尚未完成正式 V1 收敛；
13. 当前 Alongside Core 的 Source / Runtime Effective Config 尚需在实际运行环境核验；
14. SOG difference 不等于真正的 vector relative velocity；
15. COG 不等于 true heading；
16. Region Manifest 尚未实现；
17. Seed Manifest 尚未实现；
18. Risk Score 当前禁用。

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
LOGIC_AUDIT_COMPLETE
DB_VERIFICATION_COMPLETE

meetingApproxCount:
≈ 787

rawIdentityCandidate:
786 IMO / 857 identity rows
Source: var/sts/sts-crude-identity-20260902.csv
SHA-256: 5a699db07bdada8d66d7271665c0f1a8e2f21017bf3f16a7fcfb217b812b61b0

currentStrictCodeExpectation:
757 IMO / 825 identity rows
= rows where minotype='原油船'

Audited Eligible Fleet Candidate:
PENDING HUMAN APPROVAL
(see Seed Audit section for full analysis — 29 IMOs completely filtered out: 6 UNKNOWN + 21 液货船 + 2 游艇)

Audited Alias Universe Candidate:
PENDING HUMAN APPROVAL
(see Seed Audit section — 2 aliases are RETAIN_FOR_SELF_EXCLUSION on mixed IMOs; 30 aliases remain UNRESOLVED)

Approved Fleet:
PENDING HUMAN APPROVAL

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

Current Development Version:
STS_CANDIDATE_FUNNEL_OBSERVABILITY_V1
APPROVED

---

# 19. Changelog

## 2026-09-08 — STS_SEED_AUDIT_V1

Status:
LOGIC_AUDIT_COMPLETE
DB_VERIFICATION_PENDING

Scope:
Identity data audit only. No code changes, no DB writes, no DDL.

Audit confirmed:

- Raw seed source confirmed: `var/sts/sts-crude-identity-20260902.csv`, 857 rows / 786 IMOs, SHA-256 `5a699db07bdada8d66d7271665c0f1a8e2f21017bf3f16a7fcfb217b812b61b0`
- 757/825 is exactly the subset where `minotype = '原油船'`
- `DERIVED_STRICT_FILTER_DIFF=32`: applying `minotype='原油船'` to Raw CSV yields 825 rows; this is not yet an actual DB deletion fact
  - 7 rows (6 IMOs) with UNKNOWN (empty) minotype
  - 25 rows (25 IMOs) with explicit non-crude minotype (22 液货船, 2 游艇, 1 渔船)
- 29 IMOs are completely outside the derived strict subset: 6 UNKNOWN + 21 液货船 + 2 游艇
- 2 IMOs (9212929, 9240407) are `CURRENT_STRICT_ELIGIBLE` but each has 1 Raw Identity Source alias outside the strict subset
- Current code conflates Fleet Eligibility with Identity Alias Universe (single `enabled` flag)
- Those 2 aliases are `RETAIN_FOR_SELF_EXCLUSION` because Raw Identity Source maps them to the same IMO as OWN; this is not external vessel-type confirmation
- All remaining 30 aliases are `UNRESOLVED`; alias minotype alone does not justify `DO_NOT_RETAIN`
- DB verification deferred (MySQL host blocked connection from current IP)

No Java code modified. No seed data modified. No DB writes. No DDL.

Approved Fleet remains:
PENDING HUMAN APPROVAL.

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

STS_CANDIDATE_FUNNEL_OBSERVABILITY_V1

Status:

LOCAL_IMPLEMENTED_VALIDATED

完成 focused commit 和 committed clean SHA 的 JDK8 package 后，仅允许 SSH 242 read-only preflight，然后停在 `DEPLOY_APPROVAL_REQUIRED`。未经 `APPROVE_DEPLOY` 不部署；Agent 不 Trigger XXL。部署后由人工依次运行 POSITIVE_2024 和 ZERO_2026 并返回两组 funnel，在此之前 Root Cause 保持 `UNKNOWN`。

Manifest 和 Region Manifest 均后置，不是当前 Approved Task；本轮不做 DDL、Seed import 或 Seed 修改。
