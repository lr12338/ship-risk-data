# STS Phase 1.4 — Fleet Backfill Smoke Test Status & Handoff

**当前状态**: PHASE1.4.1 CODE_COMPLETE_REMOTE_VALIDATION_PENDING
**分支**: `codex/sts-phase1-4-fleet-backfill-v1`
**Phase1.4.1 基线**: `3040117`

---

## 1. 任务需求回顾

基于 `dataanalysis.ship_behavior_target_ship` 作为 STS 历史 Fleet Source，实现可恢复、可分片、Region-first 的 STS 多年历史回溯 Runner。

核心要求：
- 几千目标船 × 几年历史 × DB Region Set
- 物理船 IMO identity grouping（同船多 MMSI）
- 多年自动切 <=7-day task
- Region-first preselection（先发现哪些船进过区域，再加载轨迹）
- 可 resume（fingerprint guard + task claim + stale recovery）
- 零正式事件表写入

## 2. 已实现的功能

✅ 全部完成：

| 模块 | 文件 | 说明 |
|---|---|---|
| Job params | `StsFleetBackfillJobParams.java` | 9 个精简参数，无 WKT / IMO 列表 |
| Physical identity | `StsFleetShipIdentity.java` | IMO优先 + MMSI fallback，多别名 |
| Target fleet repo | `StsTargetFleetRepository.java` | READ-ONLY 访问 ship_behavior_target_ship |
| Backfill batch repo | `StsBackfillBatchRepository.java` | 父 batch CRUD + fingerprint |
| Backfill task repo | `StsBackfillTaskRepository.java` | Task claim（事务）+ stale recovery（180min, 3 retry） |
| Time planner | `StsBackfillPlanner.java` | 多年→7-day slice，5y/261-slice cap |
| Fleet catalog | `StsBackfillFleetCatalog.java` | StsSeedLookup 适配器 |
| Region preselector | `StsFleetRegionPreselector.java` | 复用 RegionAisDiscoveryService |
| Backfill service | `StsFleetBackfillService.java` | 主编排：init/resume/claim/run/finish |
| XXL handler | `StsJobs.java` | `shipStsHistoricalFleetBackfillJobHandler` |
| DDL | `ship_sts_backfill_batch.sql`, `ship_sts_backfill_task.sql` | 远端已执行 |

**测试**: 139 个 STS tests 全通过（31 个新增）
**构建**: `package.sh` 成功，sha256: `d5e79efc...`

## 3. Target List Audit 结论

| 批次 | Rows | 物理船 | 船型 |
|---|---|---|---|
| `merchant-50k-2026h1-v1` | 50,000 | **44,760** | 散货/油化/杂货/集装箱混合 |
| `liquid-container-5000-...-v1` | 5,000 | 4,677 | 集装箱/液货混合 |

- 55,000 = **ROWS**，不是物理船数
- MMSI identity conflict = 0（UNIQUE key 保证）
- 1,328 行缺 IMO → MMSI-only fallback
- 10,585 行 IMO 非 7 位（"0"/"1"等无效值）→ MMSI-only fallback

## 4. 远端部署状态

- ✅ DDL 已执行（batch + task 两张表）
- ✅ JAR 已上传替换
- ✅ 服务已重启（PID 1472297）
- ✅ Handler 已注册：`shipStsHistoricalFleetBackfillJobHandler`
- ✅ Smoke A batch 已创建（100 ships × 1 month × 5 slices）
- 🔄 **Slice 0 正在运行中**（已 17+ 分钟，仍在 region discovery 阶段）

## 5. 首次 Smoke 暴露的问题（Phase1.4.1 已在代码收敛）

### 问题 1: Region Discovery 阶段耗时过长

**现象**: 第一个 7-day slice 跑了 17+ 分钟还没完成 region preselection。

**背景**:
- 旧版自写 preselector：4 秒内失败（RIAU 区域 truncate）
- 修复后复用 `RegionAisDiscoveryService`：不再 truncate，但查询次数暴增
- 14 个 region × 每个 region 可能几十个时间+空间 slice × 每个 replay 查询几秒

**可能原因**:
1. `maxReplayPointsPerSlice = 50000` 太小，导致 slice 切得太细
2. 7 天时间跨度 × 14 个 region = 大量 replay 请求
3. replay API 本身延迟高（每次几秒）

**Phase1.4.1 处理原则**:
- 先输出逐 Region telemetry，再判断最贵 Region；不盲目调大 `devSize`。
- 使用 STS 专用 coarse initial planner；不改变通用 `RegionQueryPlanner`。
- 保留 Riau 作为高密度压力用例，不删除 Region。
- Replay 保持串行，不用并发掩盖 query explosion。
- truncated/timeout/incomplete 一律 fail closed。

### 问题 2: 单个 task 可能跑超过 XXL 超时

如果一个 7-day task 跑 30+ 分钟，可能超过 XXL-Job 的执行超时或触发重复调度。

**建议**: 确认 XXL-Job executor 的超时配置是否足够（建议 ≥ 1 小时）。

### 问题 3: 100 ships smoke 不代表真实性能

Region discovery 的开销基本与 target fleet 大小无关（它是查整个区域的 AIS），而与区域大小和密度相关。5000 ships 的 STS pipeline 阶段才是主要耗时。

## 6. 下一步

只允许执行本文第 13 节的 A→B→C→D 人工 Smoke 门禁。禁止复用旧 batch 做性能比较，禁止直接回到 1 month、扩大 fleet、增加 Replay 并发或进入 Strict STS Phase2。

## 7. 关键代码位置

| 关注点 | 文件 | 行 |
|---|---|---|
| Region preselector | `StsFleetRegionPreselector.java` | 核心：复用 RegionAisDiscoveryService |
| Task 主循环 | `StsFleetBackfillService.java` | `runOneTask()` 方法 |
| Task claim 原子性 | `StsBackfillTaskRepository.java` | `claimNextTask()`（事务+FOR UPDATE） |
| Fingerprint 验证 | `StsFleetBackfillService.java` | `validateBatchForResume()` |
| XXL handler | `StsJobs.java` | `shipStsHistoricalFleetBackfillJobHandler` |

## 8. 常用 SQL

```sql
-- batch 状态
SELECT * FROM dataanalysis.ship_sts_backfill_batch WHERE batch_no='sts-hotspot-202401-100ship-smokeA';

-- task 进度
SELECT slice_seq, status, candidate_ship_count, processed_ship_count,
       shadow_inserted, last_error
FROM dataanalysis.ship_sts_backfill_task
WHERE batch_no='sts-hotspot-202401-100ship-smokeA' ORDER BY slice_seq;

-- shadow 事件数
SELECT region_id, COUNT(*) FROM dataanalysis.ship_sts_shadow_event
WHERE batch_no='sts-hotspot-202401-100ship-smokeA' GROUP BY region_id ORDER BY 2 DESC;

-- self-pair 检查
SELECT COUNT(*) FROM dataanalysis.ship_sts_shadow_event
WHERE batch_no='sts-hotspot-202401-100ship-smokeA' AND own_imo = target_imo;
```

## 9. Smoke A 参数

```json
{"targetListBatchNo":"merchant-50k-2026h1-v1","expectedTargetCount":44760,"shipOffset":0,"shipCount":100,"regionSetCode":"STS_HOTSPOT_HIGH_YIELD_V2","from":"2024-01-01 00:00:00","to":"2024-02-01 00:00:00","sliceDays":7,"batchNo":"sts-hotspot-202401-100ship-smokeA"}
```

---

## 10. Phase1.4.1 diagnosis

2026-09-04 的只读检查结果：`sts-hotspot-202401-100ship-smokeA` 为 `RUNNING`，5 个 task 中 slice 0 为 `RUNNING`、其余为 `PENDING`。slice 0 已运行 61 分钟，task `updated_at` 仍停留在启动时间；batch/task 的 candidate、processed、replay、shadow 均为 0。日志只有 task start，没有逐 Region 结束记录，因此旧版本无法回答实时 query count、truncation、timeout 或当前精确 Region。Region 顺序以 Riau 为首，日志最后可确认的 Region 是 `RIAU_EOPL_NIPAH_STS_V2`，但不能把它伪装成精确的当前 Region。

根因已经从代码确认：

- 旧 Fleet preselector 的 root plan 是 `FULL_REGION_X_FULL_SLICE`，每个 Region 只有一个覆盖整个 analysis envelope 和整个 7-day task 的 root。
- `RegionAisDiscoveryService` 对 truncated query 先递归 `splitTime`，直到 5 分钟不能再切，之后才 `splitSpace`，空间最多 4 层。
- `RegionQueryPlanner` 在旧 preselector 中只是 import，并未调用；`time-slice-minutes=30` 不控制 Fleet 初始计划。
- 远端有效配置为：`max-query-slices=2000`（显式配置）；其余走代码默认：`time-slice-minutes=30`、`min-time-slice-minutes=5`、`max-points-per-slice=50000`、`spatial-cell-max-degrees=0.25`、`max-spatial-refinement-depth=4`、`max-parse-error-ratio=0.02`。
- 7-day hotspot 慢的原因是大时空 root 在高密度海区先产生大量必然 truncated 的父查询，再深度时间二分，随后才空间细分；14 个 Region 串行执行，且旧版没有可见进度和 task heartbeat。

## 11. Phase1.4.1 planner and telemetry

Fleet 专用 `StsFleetDiscoveryPlanner` 不改变通用 `RegionQueryPlanner`。默认初始计划为 1.0° spatial cell × 12h time bucket，只保留与 analysis polygon 相交的 cell；时间严格覆盖 `[from,to)`。若计划超过每 Region 512，按确定性顺序先放大 spatial cell，再放大 time bucket，直到预算内。Replay 仍然串行；真正 truncated 的初始 slice 继续交给 `RegionAisDiscoveryService` 做 time-first/space-second adaptive split，terminal truncation 仍 fail closed。

每个 Region 现在输出 `STS_FLEET_REGION_DISCOVERY` STARTED/terminal 两条 telemetry，包含 batch、Region/version、time range、initial/query/truncated/failed/timeout slices、raw vessels/points、unique vessels、target matches、elapsed、complete/status。汇总 `STS_FLEET_PRESELECT_SUMMARY` 包含 Region 数、initial/actual queries、raw totals、Region/target MMSI 数及 wall clock。由此可以直接按 `elapsedMs`、`querySlices` 定位最贵 Region，并计算 `queryExpansionRatio=actualQuerySlices/initialPlanSlices`。

基于 `STS_HOTSPOT_HIGH_YIELD_V2` 当前 14 个真实 geometry（含 10nm analysis buffer）的确定性初始计划计数：

| 场景 | spatial cells | time buckets | initial slices |
|---|---:|---:|---:|
| Riau 1 day | 12 | 2 | 24 |
| Augusta control 1 day | 9 | 2 | 18 |
| 14 Regions 1 day | 161 | 2 | 322 |
| 14 Regions 7 days | 161 | 14 | 2254 |

这些是 initial plan counts，不是远端 actual queries 或 wall clock。每个 Region 的 adaptive actual query 上限仍由 `max-query-slices=2000` fail-closed 约束；远端 Smoke 必须记录实际 expansion ratio，不能用计划数冒充执行结果。

## 12. Correctness and recovery policy

- 单船处理异常采用 fail closed：继续收集当前 task 的其余船用于诊断，但只记录前 20 个 `physicalShipKey + error summary`；最终 `FAILED_SHIPS=n`，task 不得 COMPLETE。
- `selfPairEvents > 0` 继续硬失败。
- 每个 RUNNING task 启动 STS 专用 daemon heartbeat，每 5 分钟执行只更新 RUNNING 行的 `updated_at`；`finally` 必须 shutdown。DB heartbeat failure 仅 WARN 并计入 `STS_FLEET_BACKFILL_SUMMARY.heartbeatFailures`，不立即杀 task。
- stale timeout 保持 180 分钟，仍用于进程崩溃恢复；heartbeat 更新过的活任务不满足 stale SQL，真正 stale 的 task 仍可 reclaim。
- 远端已存在 `ship_behavior_region_batch_status`、`ship_behavior_region_slice_status`、`ship_behavior_region_candidate`。Fleet discovery 因而启用 `resume=true,persist=true`；缺任一表时只禁用 resume/persist，不由 Fleet 路径自动建表。
- discovery identity 由 `STS_PRESELECT + regionSetFingerprint + sliceFrom + sliceTo + regionCode + regionVersion + targetMmsiFingerprint` 哈希得到，不使用 `shipOffset`。额外 target fingerprint 是 target-aware 持久化的正确性边界，防止不同 fleet chunk 错误复用只含上一 chunk target 的候选快照。
- target-aware accumulation 只将 target MMSI 构造成/持久化为 candidate；Replay 请求、truncation、parse/completeness 判定不变。大量 unrelated MMSI 不再消耗 `MAX_DISCOVERY_CANDIDATES=20000`，但 terminal truncation 仍失败。
- 相同 Region Set/time 对不同 target chunk 仍会重复 discovery：`CROSS_FLEET_DISCOVERY_DUPLICATION=YES`。Phase1.4.2 可设计保存完整 Region discovery snapshot 的跨 chunk cache；本轮不新增 cache 表。

## 13. Remote smoke matrix (manual only)

当前 DB 没有 single-Riau 或 single-Augusta set。执行 Smoke 前由人工审核并执行以下最小数据配置；本轮代码交付没有执行这些 SQL：

```sql
INSERT IGNORE INTO dataanalysis.ship_sts_region_set_item
  (region_set_code,region_code,region_version,priority,enabled)
VALUES
  ('RIAU_EOPL_NIPAH_STS_SMOKE_V1','RIAU_EOPL_NIPAH_STS_V2','20260903-v2',10,1),
  ('AUGUSTA_SICILY_STS_SMOKE_V1','AUGUSTA_SICILY_STS_V2','20260903-v2',10,1);
```

四条新的单行 XXL 参数，必须按 A→B→C→D 顺序且每次只运行一个；A/B/C 全部稳定后才允许 D：

```text
A: {"targetListBatchNo":"merchant-50k-2026h1-v1","expectedTargetCount":44760,"shipOffset":0,"shipCount":100,"regionSetCode":"RIAU_EOPL_NIPAH_STS_SMOKE_V1","from":"2024-01-01 00:00:00","to":"2024-01-02 00:00:00","sliceDays":1,"batchNo":"sts-p141-riau-100-1d-v1"}
B: {"targetListBatchNo":"merchant-50k-2026h1-v1","expectedTargetCount":44760,"shipOffset":0,"shipCount":100,"regionSetCode":"AUGUSTA_SICILY_STS_SMOKE_V1","from":"2024-01-01 00:00:00","to":"2024-01-02 00:00:00","sliceDays":1,"batchNo":"sts-p141-control-100-1d-v1"}
C: {"targetListBatchNo":"merchant-50k-2026h1-v1","expectedTargetCount":44760,"shipOffset":0,"shipCount":100,"regionSetCode":"STS_HOTSPOT_HIGH_YIELD_V2","from":"2024-01-01 00:00:00","to":"2024-01-02 00:00:00","sliceDays":1,"batchNo":"sts-p141-hotspot-100-1d-v1"}
D: {"targetListBatchNo":"merchant-50k-2026h1-v1","expectedTargetCount":44760,"shipOffset":0,"shipCount":100,"regionSetCode":"STS_HOTSPOT_HIGH_YIELD_V2","from":"2024-01-01 00:00:00","to":"2024-01-08 00:00:00","sliceDays":7,"batchNo":"sts-p141-hotspot-100-7d-v1"}
```

每次记录 `wallClockMs`、`initialPlanSlices`、`actualQuerySlices`、`queryExpansionRatio`、`truncatedQueries`、`rawPoints`、`uniqueRegionMmsis`、`targetMatchedMmsis`、`candidatePhysicalShips`、`failedShips`、`selfPairEvents`、`heartbeatFailures`。验收门禁为：failedShips=0、selfPairEvents=0、discovery complete、无 terminal truncation、无 silent COMPLETE、heartbeat active、A/B/C 成功且 query count 无无界递归。满足后 `GO_TO_7_DAY_SMOKE`，否则 `NO_GO`。

人工后续动作严格为：上传新 JAR → restart → 确认 `shipStsHistoricalFleetBackfillJobHandler` → A → B → C → 比较 query/wall-clock → 再决定 D。禁止直接进入 1 month、500×3 months、5000×1 year 或 Strict STS Phase2。

## 14. Production safety

Phase1.4.1 不修改 `ShipAlongsideDetector`、`ShipBehaviorAnalysisService`、生产 Alongside rule/threshold、Replay API 协议或并发语义。`ship_behavior_target_ship` 仍只读；正式 `ship_behavior_event` / `ship_behavior_event_detail` 写路径为 0，`FORMAL_EVENT_WRITE_PATHS=NONE`。只允许现有 shadow event 写入。

## 15. P0 state consistency fix

Parent batch progress 现在只从 `ship_sts_backfill_task` 聚合，不再由 runner 对 completed/failed 和事件指标做增量更新。普通 FAILED task 可在下一次 invocation 重试；`retry_count` 表示已经发生的 retry 次数，初次执行为 0，最多允许 3 次 retry。可重试 FAILED 仍使 Parent 保持 RUNNING，只有 retry 耗尽后才进入 PARTIAL_FAILED。

旧行为：单 Task 完成后，DB 最终为 COMPLETE，但 XXL Summary 可能显示 `RUNNING 1/1`。

新预期：Task terminal transition 后先刷新 Parent、再重新读取 Parent，单 Task Smoke 的 DB 与 XXL Summary 均显示 `COMPLETE 1/1`。
