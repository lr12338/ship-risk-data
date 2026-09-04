# STS Phase 1.4 — Fleet Backfill Smoke Test Status & Handoff

**当前状态**: SMOKE_TEST_IN_PROGRESS — 第一个 7-day slice 正在运行，Region Discovery 阶段耗时超出预期
**分支**: `codex/sts-phase1-4-fleet-backfill-v1`
**最新 commit**: `b4793c6` (fix: use RegionAisDiscoveryService for fleet preselector)

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

## 5. 已知性能问题（需要排查优化）

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

**可优化方向**:
- 增大 `devSize`（每个 slice 的 max points），减少切分次数
- 减少初始 time slice（比如从 30 分钟改为 6 小时）
- 只对 STS 高风险区域做 discovery，跳过小区域（RIAU 等极小区域 STS 概率极低）
- 并行查询 replay（当前是串行）
- 用 RegionQueryPlanner 生成初始 plan（多个空间 cell），而不是从单个 full-region slice 开始

### 问题 2: 单个 task 可能跑超过 XXL 超时

如果一个 7-day task 跑 30+ 分钟，可能超过 XXL-Job 的执行超时或触发重复调度。

**建议**: 确认 XXL-Job executor 的超时配置是否足够（建议 ≥ 1 小时）。

### 问题 3: 100 ships smoke 不代表真实性能

Region discovery 的开销基本与 target fleet 大小无关（它是查整个区域的 AIS），而与区域大小和密度相关。5000 ships 的 STS pipeline 阶段才是主要耗时。

## 6. 下一步建议

**短期（验证功能正确性）**:
1. 等当前 slice 0 跑完，看是否成功、candidate 数量、shadow 事件数
2. 如果 slice 0 成功，手动触发 4-5 次跑完整个 month
3. 验证：self-pair=0，shadow 事件按 region 分布合理，batch 进度正确

**中期（性能优化）**:
1. 调大 devSize 或调整 RegionBehaviorConfig 的 discovery 参数
2. 考虑跳过极小区域（STS 概率低但 discovery 成本高）
3. 评估 replay 查询并行化收益

**长期（全量 backfill）**:
1. 500 ships × 3 months smoke
2. 5000 ships × 1 year smoke
3. 全量 fleet × 多年 backfill

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
