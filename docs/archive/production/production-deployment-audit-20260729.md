# 5 万船生产测试部署与任务调度审计记录

> 不可变历史审计：本文记录 2026-07-29 的部署与当时快照，不代表滚动任务的
> 当前进度。当前状态、参数和下一步见
> [0731液货船/集装箱船测试维护手册](../../liquid-container-behavior-test-20260731.md)。

执行日期：2026-07-29

时区：Asia/Shanghai

目标服务器：SSH 别名 `242`，部署目录 `/data/zhengyi`

本地 Git 基线：`0b3f93a`，部署包包含未提交的徘徊生产过滤实现

执行人：Codex（按用户授权通过 SSH 执行）

## 1. 本次结论

- 已将线上 JAR 从 SHA-256 `043c3f2dcbab8417508db0423090cc8f0a97ebd826af5f3cd7be455c4370b4f7`
  更新为 `7e93c169c1b44815e56ec629e44a16ce139caf3ed6a687be641be7ba1c401112`。
- 未覆盖或修改线上 `application.properties`。部署前后配置文件 SHA-256 均为
  `2ae3d33e34f7e0944ee1443fbc6a2009b9a729df40960a5b09550cb8a795fd73`。
- 已建立生产补跑组合索引
  `idx_batch_behavior_mmsi_rule_time(batch_no, behavior_type, mmsi, rule_version, start_time)`。
- 新 JAR 启动时已自动为徘徊状态表补齐
  `candidate_count、weak_count、downgraded_count、valid_count、stored_event_count、result_code、elapsed_ms`。
- 服务已启动，HTTP `8090` 和 XXL-JOB 执行器 `30008` 均监听；生产搭靠、徘徊 JobHandler 均注册成功。
- 固定名单 `merchant-50k-2026h1-v1` 为 50,000 条启用记录，MMSI 无非法值和重复，`seq_no=1..50000`。
- 新徘徊批次 `loiter-prod-2026h1-50k-v2-valid-v1` 当前 status/event/detail 均为 0，可开始分段执行。
- 被中断的搭靠批次可以原批次续跑。必须保持原参数和配置不变，并设置 `resume=true`。

## 2. 操作时间线与证据

### 2.1 部署前检查

2026-07-29 14:33 CST：

- 旧服务 PID：`619668`。
- 旧服务已连续运行约 23 小时，仍在执行搭靠任务。
- 旧 JAR：
  - 大小：87,453,559 bytes。
  - SHA-256：`043c3f2dcbab8417508db0423090cc8f0a97ebd826af5f3cd7be455c4370b4f7`。
- 新 JAR：
  - 大小：87,460,848 bytes。
  - SHA-256：`7e93c169c1b44815e56ec629e44a16ce139caf3ed6a687be641be7ba1c401112`。
- 本地 Maven 测试：133 tests，0 failure，0 error，0 skipped。
- 配置保护项：

```properties
ship.behavior.loitering.production.max-points-per-ship=200000
ship.behavior.loitering.production.scene-ready=false
ship.behavior.trace.replay.enabled=false
ship.behavior.trace.replay.max-days=7
ship.behavior.trace.replay.max-points=100000
ship.behavior.trace.replay.concurrent-requests=2
```

轨迹回溯接口保持关闭；本轮 XXL-JOB 计算不依赖该接口。若后续做精确回溯，应另行生成专用令牌并通过 SSH 隧道访问，令牌不得写入日志或本审计文档。

### 2.2 搭靠中断前数据库快照

批次：`alongside-prod-2026h1-50k-v1`

| 指标 | 值 |
|---|---:|
| 已产生状态的船 | 5,371 |
| DONE 窗口 | 36,479 |
| FAILED 窗口 | 1,110 |
| 状态表事件合计 | 199 |
| event 表 ALONGSIDE | 199 |
| detail 表 ALONGSIDE | 0 |

event 表与 DONE 状态的 `SUM(event_count)` 完全一致。

### 2.3 上传、停止和备份

上传时先使用临时文件名，校验成功后再切换：

```bash
scp dist/ship-risk-data-0.0.1-SNAPSHOT.jar \
  242:/data/zhengyi/ship-risk-data-0.0.1-SNAPSHOT.jar.upload-20260729
scp dist/ship_behavior_event_production_index.sql \
  242:/data/zhengyi/ship_behavior_event_production_index.sql.upload-20260729
```

临时文件校验：

```text
7e93c169c1b44815e56ec629e44a16ce139caf3ed6a687be641be7ba1c401112  JAR
dd2cfdefbeaed7dd744a627d7c5723075df1d6ba56ad55c0a14d6fe32af17be3  SQL
```

2026-07-29 14:37:17 CST 向旧 PID 发送 `SIGTERM`。进程 60 秒后仍未退出，且搭靠工作线程仍在写入状态，因此于 14:38:41 CST 在校验 PID 命令行后发送 `SIGKILL`。这与现有 `stop.sh` 的最终停止语义一致。

已创建备份：

```text
/data/zhengyi/ship-risk-data-0.0.1-SNAPSHOT.jar.bak-20260729-143841
/data/zhengyi/application.properties.bak-20260729-143841
/data/zhengyi/ship_behavior_event_production_index.sql.bak-20260729-143841
```

正在执行但未写 DONE 的搭靠窗口会在 `resume=true` 时重算。已提交的 DONE 窗口不会重算；事件替换按批次、船舶和窗口幂等执行。

### 2.4 数据库优化

部署前确认 event 表不存在等价组合索引，随后在应用停止期间执行：

```sql
ALTER TABLE dataanalysis.ship_behavior_event
ADD INDEX idx_batch_behavior_mmsi_rule_time
(batch_no, behavior_type, mmsi, rule_version, start_time);
```

执行耗时约 0.354 秒。执行后索引为：

```text
idx_batch_behavior_mmsi_rule_time =
batch_no,behavior_type,mmsi,rule_version,start_time
```

### 2.5 启动验证

新服务：

- PID：`686901`。
- Spring Boot 启动完成：2026-07-29 14:39:38 CST。
- 启动耗时：13.089 秒。
- HTTP `8090`：监听正常；根路径返回 404 是应用无首页的预期行为。
- XXL-JOB `30008`：监听正常。
- 已注册：
  - `shipAlongsideProductionTestJobHandler`
  - `shipLoiteringProductionTestJobHandler`
  - `shipBehaviorTargetShipListJobHandler`
- JAR SHA-256 再校验为
  `7e93c169c1b44815e56ec629e44a16ce139caf3ed6a687be641be7ba1c401112`。
- 服务重启后没有自动启动搭靠或徘徊生产任务。
- 启动迁移日志显示 7 个徘徊状态字段全部 `column ready`。

### 2.6 搭靠中断后最终快照

批次：`alongside-prod-2026h1-50k-v1`

| 指标 | 值 |
|---|---:|
| 已产生状态的船 | 5,375 |
| 七个窗口全部 DONE 的船 | 4,732 |
| 包含 FAILED 窗口的船 | 643 |
| DONE 窗口 | 36,507 |
| FAILED 窗口 | 1,114 |
| 状态表事件合计 | 199 |
| event 表 ALONGSIDE | 199 |
| detail 表 ALONGSIDE | 0 |
| 最大已处理 `seq_no` | 5,375 |

失败原因：

| 原因 | 窗口数 |
|---|---:|
| 区域回放结果截断 | 1,090 |
| 单窗口回放查询数超过 300 | 24 |

这些失败在中断前已持续出现，不是部署引入。不能在原批次中途调整回放阈值、bbox、窗口或规则版本，否则已 DONE 数据和新数据会混合口径。原参数续跑可继续处理剩余船舶，但上述确定性失败可能仍会失败；全量扫描后应先修复回放截断策略，再决定用新批次重算还是对 FAILED 窗口做同口径补跑。

### 2.7 搭靠续跑验证

2026-07-29 14:53:36 CST 在 XXL-JOB 使用原始参数重新触发
`alongside-prod-2026h1-50k-v1`，其中 `resume=true`。

截至 14:57:28 CST：

- 续跑仅更新了 5 艘船的 10 个 FAILED 窗口。
- 原有 36,507 个 DONE 窗口均未被更新。
- 状态表 DONE 事件合计仍为 199，event 表仍为 199，detail 仍为 0。
- 证明任务正在按原批次跳过 DONE 窗口，只补跑 FAILED/未完成窗口。

样例 `MMSI=477158700`、`seq_no=9`：

| 窗口 | 状态 | `updated_at` |
|---|---|---|
| 2026-01-01～2026-01-31 | DONE | 2026-07-28 15:24:56 |
| 2026-01-31～2026-03-02 | DONE | 2026-07-28 15:25:00 |
| 2026-03-02～2026-04-01 | DONE | 2026-07-28 15:25:09 |
| 2026-04-01～2026-05-01 | DONE | 2026-07-28 15:25:15 |
| 2026-05-01～2026-05-31 | DONE | 2026-07-28 15:25:16 |
| 2026-05-31～2026-06-30 | FAILED，续跑 | 2026-07-29 14:54:00 |
| 2026-06-30～2026-07-01 | DONE | 2026-07-28 15:25:37 |

该样例显示，同一艘船的 6 个 DONE 窗口被跳过，仅原 FAILED 窗口被重新执行。

## 3. XXL-JOB 统一配置

两个任务都使用：

- 运行模式：`BEAN`
- 路由策略：`FIRST`
- 阻塞处理策略：`单机串行`
- 超时时间：`0`
- 失败重试次数：`0`
- Cron：停用
- 仅人工触发
- 同一时间只允许运行搭靠或徘徊中的一个

当前优先级是徘徊，因此先保持搭靠任务停用。徘徊完成并验收后，再恢复搭靠。

## 4. 徘徊任务配置

JobHandler：

```text
shipLoiteringProductionTestJobHandler
```

V3 场景尚未验收，线上保持
`ship.behavior.loitering.production.scene-ready=false`，本轮只能使用 `engines:["V2"]`。

### 4.1 第一段：同批次前 1,000 艘

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

通过数据库门禁后再进入 5,000 艘阶段。预期历史数据未变化时，前 1,000 艘约：

- candidates：24,356；
- valid：3,716；
- storedEvents：1,541；
- NO_DATA：25；
- detail：0。

### 4.2 第二段：同批次续跑至 5,000 艘

复制第一段完整参数，只修改：

```json
{
  "shipLimit": 5000,
  "parallelism": 2
}
```

必须保留同一个 `batchNo`。前 1,000 艘 DONE/NO_DATA 将跳过。

### 4.3 第三段：同批次续跑至 50,000 艘

复制第二段完整参数，只修改：

```json
{
  "shipLimit": 50000
}
```

必须保留同一个 `batchNo`。前 5,000 艘 DONE/NO_DATA 将跳过。

### 4.4 每段完成后的数据库门禁

```sql
SELECT
    COUNT(*) AS status_rows,
    SUM(status = 'DONE') AS done_ships,
    SUM(status = 'FAILED') AS failed_ships,
    SUM(result_code = 'NO_DATA') AS no_data_ships,
    SUM(point_count) AS points,
    SUM(candidate_count) AS candidates,
    SUM(weak_count) AS weak_candidates,
    SUM(downgraded_count) AS downgraded_candidates,
    SUM(valid_count) AS valid_candidates,
    SUM(stored_event_count) AS stored_events
FROM dataanalysis.ship_behavior_loitering_batch_status
WHERE batch_no = 'loiter-prod-2026h1-50k-v2-valid-v1';
```

```sql
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
      OR score_detail_json NOT LIKE
          '%"persistPolicy":"VALID_NON_WEAK"%'
  );

SELECT COUNT(*) AS event_rows
FROM dataanalysis.ship_behavior_event
WHERE batch_no = 'loiter-prod-2026h1-50k-v2-valid-v1'
  AND behavior_type = 'LOITERING';

SELECT COUNT(*) AS detail_rows
FROM dataanalysis.ship_behavior_event_detail
WHERE batch_no = 'loiter-prod-2026h1-50k-v2-valid-v1'
  AND behavior_type = 'LOITERING';
```

进入下一段前必须满足：

- `failed_ships=0`；
- `invalid_events=0`；
- `missing_policy=0`；
- `detail_rows=0`；
- `event_rows=SUM(stored_event_count)`；
- NO_DATA 比例不高于 3%；
- 5,000 船阶段平均正式事件数约 1.23–1.85 条/船；
- 无持续 HBase 超时、OOM 或数据库事务错误。

## 5. 搭靠任务恢复配置

JobHandler：

```text
shipAlongsideProductionTestJobHandler
```

徘徊任务完全停止后，使用中断前的原始完整参数人工触发：

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

不得修改：

- `batchNo`；
- 名单批次；
- 起止时间；
- `windowDays`；
- `enableInsert`；
- `ALONGSIDE_V3_INSURANCE` 规则及相关回放/识别配置。

恢复后的首批日志应出现 `windowsSkipped>0`，表示 DONE 窗口被跳过。FAILED 和无状态窗口会重算。

搭靠核验 SQL：

```sql
SELECT
    status,
    COUNT(*) AS windows,
    COUNT(DISTINCT mmsi) AS ships,
    COALESCE(SUM(event_count), 0) AS events
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

## 6. 监控与停止规则

运行期间：

```bash
ssh 242
cd /data/zhengyi
./status.sh
tail -f logs/log.log
```

XXL-JOB 任务日志在：

```text
/data/zhengyi/logs/job/jobhandler/YYYY-MM-DD/<log-id>.log
```

遇到以下情况应停止扩量：

- FAILED 持续增加且同口径重试不收敛；
- HBase/空间回放持续超时；
- JVM 接近 2 GB 堆上限或产生 OOM；
- event 与状态表事件数不一致；
- 徘徊出现弱候选、降级候选或缺少 `persistPolicy` 的落库事件；
- detail 表出现本批次新增数据。

停止生产任务时优先在 XXL-JOB 调度中心执行“终止任务”并等待当前窗口结束。服务器现有 `stop.sh` 最终使用 `kill -9`，会使正在计算但未写 DONE 的窗口在下次触发时重算。

## 7. 回滚

仅在新版本出现启动或运行阻断问题时执行：

```bash
ssh 242
cd /data/zhengyi
./stop.sh
cp -p ship-risk-data-0.0.1-SNAPSHOT.jar.bak-20260729-143841 \
  ship-risk-data-0.0.1-SNAPSHOT.jar
cp -p application.properties.bak-20260729-143841 \
  application.properties
./start.sh
./status.sh
```

新增索引可与旧 JAR 兼容，不需要在普通回滚中删除。不要删除搭靠或徘徊的 event/status 数据。
