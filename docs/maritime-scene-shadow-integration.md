# 海域场景 Shadow 接入

本功能只提供独立的场景证据和审计，不改变正式搭靠、徘徊事件、severity、event_score、open_sea 或 AIS 回放超时/降级语义。默认配置是 `enabled=false`、`mode=OFF`；允许的运行模式只有 `OFF` 和 `SHADOW`，本轮不实现 `ENFORCE`。

## 快照门禁

Java 只接受至少为 `SHADOW_READY` 的 Release，并 fail-closed 校验：

- `manifest.json`、snapshot fingerprint 和 `checksums.sha256`；
- policy version `MARITIME_SCENE_SHADOW_V1`；
- `*.scene.ndjson.gz` 与 `*.coast.ndjson.gz` 瓦片格式；
- 1° 瓦片按需加载、JTS `STRtree`/`PreparedGeometry` 和线程安全 LRU；
- 场景文件缺失、指纹不符、checksum 失败或快照不可用时，Shadow 结果为 `SCENE_UNKNOWN`，正式链路继续原逻辑运行。

默认模板配置位于 `src/main/resources/application.properties`：

```properties
ship.behavior.maritime-scene.enabled=false
ship.behavior.maritime-scene.mode=OFF
ship.behavior.maritime-scene.snapshot-path=
ship.behavior.maritime-scene.expected-fingerprint=
ship.behavior.maritime-scene.policy-version=MARITIME_SCENE_SHADOW_V1
ship.behavior.maritime-scene.cache.max-tiles=64
ship.behavior.maritime-scene.boundary-tolerance-meters=1000
ship.behavior.maritime-scene.coast-search-radius-nm=60
ship.behavior.maritime-scene.offshore-min-coast-distance-nm=12
ship.behavior.maritime-scene.high-inland-point-ratio=0.90
ship.behavior.maritime-scene.min-inland-minutes=60
ship.behavior.maritime-scene.open-water-point-ratio=0.90
ship.behavior.maritime-scene.max-unknown-ratio=0.05
ship.behavior.maritime-scene.audit.store-enabled=false
```

线上启用 Shadow 时由运维外置配置注入实际路径和 fingerprint；本地默认模板不会启用它。回滚时切回 `mode=OFF`，不删除快照、不修改正式事件表。

## 点级与 episode 输出

点级结果包含 `sceneClass`、`confidence`、来源证据、海岸距离、边界距离、快照版本、fingerprint、policy version 和 `conflictFlag`。River/Lake/Canal 与 GSHHG `land` 重叠不自动视为冲突；River 无运行时水面支持时最多为 `LOW`。大型湖泊和已审核运河在几何内部可作 HIGH Shadow 证据。`UNKNOWN` 和 `CONFLICT` 不能被当成高置信开放水域。

episode 聚合包含各场景比例、dominant scene/confidence、转场次数、未知/冲突比例和海岸距离 min/p50/max。Shadow 决策只允许：

- `WOULD_REJECT_INLAND`：高置信内河比例至少 0.90 且连续内河时间至少 60 分钟；
- `OPEN_WATER_CONFIRMED`：High Seas+EEZ 至少 0.90、unknown ≤ 0.05、conflict=0 且 dominant confidence 为 HIGH；
- `COASTAL_REVIEW`、`SCENE_UNKNOWN`、`SCENE_CONFLICT`。

这些结果只写独立审计输出，不提前过滤搭靠 replay，不减少正式回放查询，也不改变超时跳过并继续计算的既有实现。

## 5,000 艘任务与 Handler

独立 Handler 为：

- `shipMaritimeScenePreflightJobHandler`
- `shipMaritimeSceneShadowAuditJobHandler`

当前只有 Preflight 和单 episode 的 `auditEpisode` 能力；尚无遍历目标名单、加载正式候选与 AIS
轨迹的 runner。`shipMaritimeSceneShadowAuditJobHandler` 会返回
`SHADOW_RUNNER_NOT_IMPLEMENTED` 并让 XXL-JOB 任务失败，防止把“预检通过”误报为 100/500/5,000
艘已执行。实现 runner、补齐 OFF/SHADOW 同轨迹验收并准备覆盖目标轨迹范围的 Release 前，只能运行
Preflight。

目标名单固定为 `liquid-container-5000-2025h2-2026h1-v1`，支持液货船和集装箱船对应的 `LOITERING`、`ALONGSIDE` Shadow 审计；单次 `shipLimit` 最大 5,000。Preflight 会检查路径、Release 状态、fingerprint、checksum、瓦片、Java 读取能力和 policy version。`dryRun=true` 时只输出独立 NDJSON/JSON 结果，不写数据库。

参数示例：

```json
{
  "listBatchNo": "liquid-container-5000-2025h2-2026h1-v1",
  "startSeq": 1,
  "shipLimit": 100,
  "startTime": "2025-07-01 00:00:00",
  "endTime": "2026-07-01 00:00:00",
  "batchNo": "maritime-scene-shadow-202608-v1",
  "sceneSnapshotVersion": "phase1-global-base-v1",
  "expectedSceneFingerprint": "<manifest.snapshotFingerprint>",
  "scenePolicyVersion": "MARITIME_SCENE_SHADOW_V1",
  "behaviorTypes": ["LOITERING", "ALONGSIDE"],
  "dryRun": true,
  "resume": true
}
```

当前实现提供独立 `auditEpisode` 输出和可审计的确定性 `audit_key`；正式任务仍先执行原有候选与 AIS replay，再旁路生成场景证据。正式事件字段、数量和 AIS 超时行为必须以 OFF/SHADOW 同轨迹回归结果为准。

## 审计、数据库与回滚

SQL 仅作为版本化脚本放在 `src/main/resources/sql/ship_behavior_maritime_scene_audit.sql` 及 rollback 文件中，本轮不执行生产 DDL。表属于 `dataanalysis.ship_behavior_maritime_scene_audit`，与正式事件表隔离；`store-enabled=false` 默认不写库，启用时使用确定性 key 和幂等 upsert，只允许 event/dataanalysis 数据库。

数据更新要生成新的 ETL Release，保留原 Release 的 manifest/checksum/fingerprint，完成 `AUTO_CROSS_VALIDATED` 后再提升到 `SHADOW_READY`。升级 Enforce 还需要独立的 OFF/SHADOW 同轨迹证明、未知/冲突门禁、正式事件字段完全一致、生产配置审批和明确的 `ENFORCE` 授权；本轮不得宣称 `ENFORCE_READY`。
