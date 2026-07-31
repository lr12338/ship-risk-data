# 已归档：搭靠与徘徊 5 万船生产测试状态跟踪

> 本文档记录截至2026-07-29的旧5万商船测试，仅供追溯。当前液货船/集装箱船
> 测试入口为
> [0731液货船/集装箱船搭靠与徘徊测试维护手册](../../liquid-container-behavior-test-20260731.md)。
> 若内容与当前代码或当前手册冲突，以当前手册和代码为准。

最后更新：2026-07-29 15:00 CST

状态来源：XXL-JOB 日志、生产数据库、服务器部署核验

测试区间：`[2026-01-01 00:00:00, 2026-07-01 00:00:00)`

固定名单：`merchant-50k-2026h1-v1`，50,000 艘有效商船

## 1. 使用规则

本文档是搭靠与徘徊生产测试的唯一状态入口。后续每次启动、暂停、补跑、
扩量或验收，都先更新本文档；设计细节查对应开发文档，部署原始证据查归档审计。

统一约束：

- 时间和任务日志统一使用 Asia/Shanghai。
- 搭靠与徘徊生产任务不得同时运行。
- XXL-JOB 使用 `FIRST`、单机串行、超时 `0`、调度重试 `0`、关闭 Cron。
- 只允许人工触发。
- 同一批次续跑不得修改规则、时间窗、名单、写入模式或数据源参数。
- `resume=true` 只跳过状态表中的 DONE；FAILED 和无状态窗口会重算。
- 所有生产测试保持 event-only，detail 增量必须为 0。

## 2. 当前环境

| 项目 | 当前值 |
|---|---|
| 服务器 | SSH `242`，`/data/zhengyi` |
| 运行 JAR SHA-256 | `7e93c169c1b44815e56ec629e44a16ce139caf3ed6a687be641be7ba1c401112` |
| HTTP / XXL 端口 | `8090 / 30008` |
| Java 堆 | `-Xms512m -Xmx2g` |
| 徘徊 V3 门禁 | `ship.behavior.loitering.production.scene-ready=false` |
| 精确回溯接口 | `ship.behavior.trace.replay.enabled=false` |
| 生产组合索引 | 已建立 |
| 徘徊状态统计字段 | 已完成启动迁移 |
| 本地测试基线 | 133 tests，0 failure，0 error，0 skipped |

部署与迁移证据见
[2026-07-29 部署审计](production-deployment-audit-20260729.md)。

## 3. 当前任务总览

| 行为 | 批次 | 状态 | 当前阶段 | 下一动作 |
|---|---|---|---|---|
| 搭靠 | `alongside-prod-2026h1-50k-v1` | RUNNING | 中断后原批次续跑 | 完成全名单扫描并统计确定性失败 |
| 徘徊 | `loiter-prod-2026h1-50k-v2-valid-v1` | WAITING | 尚未启动正式过滤批次 | 搭靠停止后先跑同批次 1,000 艘门禁 |
| 徘徊旧 smoke | `loiter-prod-2026h1-1k-v2-smoke-v1` | COMPLETED | 候选全集基线 | 只保留作对照，不扩量 |
| 徘徊 V3 | `LOITERING_V3_PROD_TEST_2026H1` | BLOCKED | 场景能力未验收 | 不得打开 `scene-ready` 绕过门禁 |

## 4. 搭靠生产测试

### 4.1 任务口径

- JobHandler：`shipAlongsideProductionTestJobHandler`
- 规则版本：`ALONGSIDE_V3_INSURANCE`
- 30 天切窗，每艘 7 个窗口。
- 仅保存非 `WEAK_ALONGSIDE_CANDIDATE` 且
  `exclusion_flag=VALID_RISK_EVENT` 的事件。
- `enableInsert=true`，完全不写 detail。
- 续跑粒度是 `batchNo + MMSI + window_start + window_end`。

### 4.2 当前进度快照

部署中断后、续跑前快照：

| 指标 | 数量 |
|---|---:|
| 已进入状态表的船 | 5,375 |
| 七个窗口全部 DONE 的船 | 4,732 |
| 含 FAILED 窗口的船 | 643 |
| DONE 窗口 | 36,507 |
| FAILED 窗口 | 1,114 |
| 状态表 DONE 事件合计 | 199 |
| event 表事件 | 199 |
| detail 表记录 | 0 |
| 最大已处理 `seq_no` | 5,375 |

续跑于 2026-07-29 14:53:36 启动。第一组 50 艘汇总：

```text
shipsSeen=50 shipsDone=0 shipsFailed=5 shipsSkipped=45
windowsDone=0 windowsFailed=12 windowsSkipped=338 events=0
```

`shipsSkipped=45`、`windowsSkipped=338` 证明旧 DONE 窗口被跳过；
只补跑原 FAILED 窗口。样例 `MMSI=477158700` 的 6 个 DONE 窗口仍保留
2026-07-28 的更新时间，仅原 FAILED 窗口在续跑后更新。

### 4.3 已知问题

续跑前 1,114 个失败窗口的原因：

| 原因 | 窗口数 |
|---|---:|
| 区域回放结果截断 | 1,090 |
| 单窗口回放查询数超过 300 | 24 |

这些是原任务已有问题，不是部署中断导致。当前批次不得中途调整 bbox、回放窗口、
`devSize`、最大查询数、检测阈值或规则版本，否则会形成混合口径。

处理原则：

1. 允许当前同口径任务继续扫描未处理船舶。
2. 记录最终 DONE、FAILED 和失败原因分布。
3. 不要求确定性截断失败通过重复触发自动消失。
4. 若要修复回放策略，先完成代码和对照测试，再使用新批次重新验证。

### 4.4 当前运行参数

```json
{
  "mode": "production_test",
  "listBatchNo": "merchant-50k-2026h1-v1",
  "expectedShipCount": 50000,
  "shipLimit": 50000,
  "pageSize": 200,
  "parallelism": 2,
  "startTime": "2026-01-01 00:00:00",
  "endTime": "2026-07-01 00:00:00",
  "windowDays": 30,
  "enableInsert": true,
  "resume": true,
  "retryCount": 2,
  "logIntervalShips": 50,
  "batchNo": "alongside-prod-2026h1-50k-v1"
}
```

### 4.5 搭靠跟踪 SQL

```sql
SELECT status,
       COUNT(*) AS windows,
       COUNT(DISTINCT mmsi) AS ships,
       COALESCE(SUM(event_count), 0) AS events,
       MAX(updated_at) AS last_update
FROM dataanalysis.ship_behavior_alongside_batch_status
WHERE batch_no = 'alongside-prod-2026h1-50k-v1'
GROUP BY status;

SELECT
  (SELECT COALESCE(SUM(event_count), 0)
   FROM dataanalysis.ship_behavior_alongside_batch_status
   WHERE batch_no = 'alongside-prod-2026h1-50k-v1'
     AND status = 'DONE') AS status_events,
  (SELECT COUNT(*)
   FROM dataanalysis.ship_behavior_event
   WHERE batch_no = 'alongside-prod-2026h1-50k-v1'
     AND behavior_type = 'ALONGSIDE') AS event_rows,
  (SELECT COUNT(*)
   FROM dataanalysis.ship_behavior_event_detail
   WHERE batch_no = 'alongside-prod-2026h1-50k-v1'
     AND behavior_type = 'ALONGSIDE') AS detail_rows;
```

## 5. 徘徊生产测试

### 5.1 旧 smoke 基线

`loiter-prod-2026h1-1k-v2-smoke-v1` 已完成：

| 指标 | 数量 |
|---|---:|
| 船舶 DONE / FAILED | 1,000 / 0 |
| 轨迹点 | 8,595,703 |
| 全部候选事件 | 24,356 |
| VALID 候选 | 3,716 |
| VALID 且非弱候选 | 1,541 |
| detail 增量 | 0 |

旧批次保存的是候选全集，不能扩至 50,000，也不能与正式过滤批次混用。

### 5.2 正式 V2 批次口径

- JobHandler：`shipLoiteringProductionTestJobHandler`
- 批次：`loiter-prod-2026h1-50k-v2-valid-v1`
- 规则：`LOITERING_V2_INSURANCE`
- `eventFilterMode=VALID_NON_WEAK`
- 只保存 `VALID_RISK_EVENT` 且非 `WEAK_LOITERING_CANDIDATE`。
- `traceRecipe.persistPolicy=VALID_NON_WEAK`。
- `storeDetails=false`。
- 采用同批次 `1,000 → 5,000 → 50,000` 分段续跑。
- 当前 status/event/detail 均为 0，尚未启动。

第一阶段参数：

```json
{
  "mode": "production_test",
  "listBatchNo": "merchant-50k-2026h1-v1",
  "expectedShipCount": 50000,
  "shipLimit": 1000,
  "startTime": "2026-01-01 00:00:00",
  "endTime": "2026-07-01 00:00:00",
  "engines": ["V2"],
  "pageSize": 200,
  "parallelism": 1,
  "trajectorySliceHours": 6,
  "eventFilterMode": "VALID_NON_WEAK",
  "storeDetails": false,
  "resume": true,
  "retryCount": 2,
  "logIntervalShips": 50,
  "batchNo": "loiter-prod-2026h1-50k-v2-valid-v1"
}
```

1,000 艘门禁通过后，仅修改为：

```json
{"shipLimit": 5000, "parallelism": 2}
```

5,000 艘门禁通过后，仅修改为：

```json
{"shipLimit": 50000, "parallelism": 2}
```

三个阶段必须使用同一个 `batchNo`。

### 5.3 徘徊阶段门禁

- FAILED 必须先补跑或解释。
- detail 增量为 0。
- NO_DATA 比例不高于 3%。
- event 表数量等于 `SUM(stored_event_count)`。
- 所有 event 都是 VALID 且非弱候选。
- 所有 event 都有 `persistPolicy=VALID_NON_WEAK`。
- 5,000 艘阶段平均正式事件数应在 1.23–1.85 条/船附近。
- 无持续 HBase 超时、OOM 或数据库事务错误。
- 1,000 和 5,000 阶段分别人工复核 100 个事件后才扩量。

### 5.4 徘徊跟踪 SQL

```sql
SELECT status, result_code, COUNT(*) AS ships,
       SUM(point_count) AS points,
       SUM(candidate_count) AS candidates,
       SUM(weak_count) AS weak_candidates,
       SUM(downgraded_count) AS downgraded_candidates,
       SUM(valid_count) AS valid_candidates,
       SUM(stored_event_count) AS stored_events,
       ROUND(SUM(elapsed_ms) / 1000, 0) AS ship_elapsed_seconds
FROM dataanalysis.ship_behavior_loitering_batch_status
WHERE batch_no = 'loiter-prod-2026h1-50k-v2-valid-v1'
GROUP BY status, result_code;

SELECT COUNT(*) AS invalid_events
FROM dataanalysis.ship_behavior_event
WHERE batch_no = 'loiter-prod-2026h1-50k-v2-valid-v1'
  AND behavior_type = 'LOITERING'
  AND (
    exclusion_flag IS NULL
    OR exclusion_flag <> 'VALID_RISK_EVENT'
    OR event_sub_type = 'WEAK_LOITERING_CANDIDATE'
  );

SELECT COUNT(*) AS missing_policy
FROM dataanalysis.ship_behavior_event
WHERE batch_no = 'loiter-prod-2026h1-50k-v2-valid-v1'
  AND behavior_type = 'LOITERING'
  AND (
    score_detail_json IS NULL
    OR score_detail_json NOT LIKE '%"persistPolicy":"VALID_NON_WEAK"%'
  );
```

## 6. V3 门禁

V3 仍缺少真实验收完成的港区外扩 10 海里、锚地/泊位排除、正常等待、
STS 场景和数据完整性门禁。当前不得：

- 把 `scene-ready` 改为 `true`；
- 在生产参数中加入 `V3`；
- 把 V2 正式候选称为最终真实可靠事件。

V3 完成后应使用新批次做 100 船 A/B、1,000 船双引擎和精确回溯验证，
不能复用当前 V2-only 批次。

## 7. 状态更新模板

每次状态变化在“更新记录”增加一行，并同步修改任务总览和对应进度快照。
不得覆盖历史行。

记录至少包含：

- 时间；
- 行为和批次；
- 操作：启动、暂停、重启、补跑、扩量或验收；
- 参数变化；
- DONE / FAILED / NO_DATA；
- candidate / stored event；
- detail 数；
- 主要错误；
- 门禁结论；
- 下一动作。

## 8. 更新记录

| 时间 | 行为/批次 | 操作与结果 | 结论/下一步 |
|---|---|---|---|
| 2026-07-28 15:25～18:51 | 徘徊 smoke | 1,000 艘 V2，DONE=1,000，events=24,356 | 候选过多，只保留为候选全集基线 |
| 2026-07-28 15:23～2026-07-29 14:38 | 搭靠 50k | 处理至 seq=5,375，DONE 窗口 36,507，FAILED 1,114 | 部署中断，状态与 event 对账一致 |
| 2026-07-29 14:39 | 服务部署 | 更新 JAR、建索引、迁移状态字段并启动 | 新版本可运行 |
| 2026-07-29 14:53 | 搭靠 50k | 原批次 `resume=true` 续跑；首 50 艘跳过 338 个 DONE 窗口 | 续跑语义确认，继续扫描 |
