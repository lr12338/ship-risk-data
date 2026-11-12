# 搭靠生产测试参数与系统维护

## 1. 适用范围

本文档适用于 XXL-Job Handler `shipAlongsideProductionTestJobHandler`，对已固化的目标船名单执行 Alongside V3 历史生产测试。任务入口为 `production_test`，不是 `shipAlongsideV3TestJobHandler` 的 `history_test`，两者参数不通用。

当前版本允许自定义 `startTime/endTime` 且不限制总天数，但必须满足固定格式且开始时间早于结束时间。长时间范围会按 30 天拆成独立窗口。

## 2. XXL-Job 参数定义

| 参数 | 必填/默认值 | 约束与说明 |
| --- | --- | --- |
| `mode` | 必填 | 固定为 `production_test`。 |
| `listBatchNo` | 必填 | 目标船名单批次，最长 64 字符。 |
| `expectedShipCount` | 默认 50000 | 名单应有船数；启动时校验数量和唯一性。5000 船任务建议显式传 5000。 |
| `shipLimit` | 默认等于 `expectedShipCount` | 本次处理船数，必须大于 0 且不超过 `expectedShipCount`。 |
| `pageSize` | 默认 200 | 名单分页大小，1～1000；50 适合当前 5000 船任务。 |
| `parallelism` | 默认 1 | 并行船数，最大 2；生产建议 1。 |
| `startTime` | 必填 | 格式 `yyyy-MM-dd HH:mm:ss`，包含边界。 |
| `endTime` | 必填 | 同上，必须晚于 `startTime`，作为不包含的结束边界。 |
| `windowDays` | 默认 30 | 搭靠生产测试强制为 30。窗口数为 `ceil(总天数/30)`。 |
| `trajectorySliceHours` | 默认 6 | 保留在运行配置哈希中；建议显式传 6，续跑时不可改。 |
| `engines` | 未传时 `["V2","V3"]` | 搭靠生产参数建议显式传 `["V3"]`；该值进入配置哈希。 |
| `resume` | 默认 `true` | 为 `true` 时跳过同批次中状态为 `DONE` 的船舶窗口。 |
| `retryCount` | 默认 2 | 单窗口额外重试次数，必须大于等于 0。 |
| `logIntervalShips` | 默认 50 | 页内每处理 N 艘船输出进度摘要。 |
| `batchNo` | 必填 | 事件、状态和运行清单的唯一批次号，最长 64 字符。 |
| `enableInsert` | 默认 `false` | `false` 为 dry-run，仍写状态表，不写正式事件；`true` 按窗口幂等替换事件。 |
| `storeDetails` | 默认 `false` | 生产测试强制为 `false`，只写事件，不写轨迹明细。 |

`pageSize`、`parallelism`、`retryCount`、`enableInsert`、`storeDetails`、`windowDays`、`trajectorySliceHours`、`engines` 均进入批次配置哈希。同批次续跑时不得把它们当作“调优参数”修改。

## 3. 可直接使用的参数

20 船 dry-run：

```json
{"mode":"production_test","listBatchNo":"liquid-container-5000-2025h2-2026h1-v1","expectedShipCount":5000,"shipLimit":20,"pageSize":20,"parallelism":1,"startTime":"2025-01-01 00:00:00","endTime":"2026-08-01 00:00:00","windowDays":30,"trajectorySliceHours":6,"engines":["V3"],"resume":true,"retryCount":2,"logIntervalShips":10,"batchNo":"alongside-merchant20-202501-202607-smoke-v3","enableInsert":false,"storeDetails":false}
```

5000 船正式写入：

```json
{"mode":"production_test","listBatchNo":"liquid-container-5000-2025h2-2026h1-v1","expectedShipCount":5000,"shipLimit":5000,"pageSize":50,"parallelism":1,"startTime":"2025-01-01 00:00:00","endTime":"2026-08-01 00:00:00","windowDays":30,"trajectorySliceHours":6,"engines":["V3"],"resume":true,"retryCount":2,"logIntervalShips":10,"batchNo":"alongside-merchant5000-202501-202607-v3","enableInsert":true,"storeDetails":false}
```

两个批次必须不同。dry-run 状态不能直接作为正式写入的续跑状态。

## 4. 运行和验收

XXL-Job 配置使用手动触发、单机路由、单机串行阻塞，禁止两个船舶生产任务并发。先执行 smoke，再执行正式批次。

| 任务 | 预期船数 | 每船窗口 | 预期总窗口 | 事件写入 |
| --- | ---: | ---: | ---: | --- |
| smoke | 20 | 20 | 400 | 0 条 |
| formal | 5000 | 20 | 100000 | 按实际检出写入 |

关键日志：

```text
Alongside Production Start batchNo=... shipLimit=... windows=20 parallelism=1 enableInsert=...
Alongside Production Summary batchNo=... shipsSeen=... shipsDone=... shipsFailed=... windowsDone=... windowsFailed=... events=...
Alongside Production FAILED batchNo=... mmsi=... startTime=... endTime=... error=...
```

每页会输出一条 Summary，不是最终完成标志。只有 XXL-Job `execute end(finish)` 且最终 Summary 达到目标船数才表示整批完成。

## 5. 断点续跑与不可变批次

`ship_behavior_run_batch` 保存批次清单，`ship_behavior_alongside_batch_status` 保存每船每窗口状态。正式写入时，窗口事件替换和 `DONE` 状态在同一事务中提交。

允许续跑的条件：

- 使用原 `batchNo`、原时间边界和原始完整 JSON 参数。
- 当前规则版本、写入模式和世界海域掩膜身份与批次创建时一致。
- `resume=true`，程序跳过 `DONE`，重算 `FAILED` 和未建状态的窗口。

以下情况必须新建 `batchNo` 从头运行：

- 修改任何哈希参数、时间范围、名单、规则版本或写入模式。
- 更新世界海域数据集、几何指纹或严格过滤配置。
- 出现 `batchNo already exists with different immutable parameters`。

不要手工删除批次清单、修改 `config_hash` 或把不同版本结果混入原批次。

## 6. 系统维护

部署或重启前：

1. 确认当前是否有任务运行，记录最后 Summary 和 XXL-Job 日志文件。
2. 查询批次 `DONE/FAILED` 窗口数、事件数和失败原因。
3. 如果必须中断，保留事件表、状态表和运行清单，不清库。

部署后：

1. 检查进程、8090/30008 端口、数据源、XXL-Job 注册和世界海域掩膜加载日志。
2. 先执行新 `batchNo` 的 20 船 dry-run，通过后再运行正式批次。
3. 若版本更新不确定是否改变计算依赖，使用新批次，在同船、重叠时间上比较事件键和业务字段。

查询 SQL 统一维护在 [`deploy/production-test-5000/query.sql`](../deploy/production-test-5000/query.sql)。

## 7. 常见错误

| 错误/现象 | 原因 | 处理 |
| --- | --- | --- |
| `production test range must not exceed 366 days` | 服务器仍是旧 Jar | 校验 Jar SHA-256 并重启，不要改短业务时间。 |
| `startTime must be before endTime` | 时间边界相等或倒序 | 修正时间，并使用新 `batchNo`。 |
| `batchNo already exists with different immutable parameters` | 批次已被其他参数、规则或掩膜占用 | 新建 `batchNo` 从头执行。 |
| Summary 只有 10/20/50 船 | 这是页内进度日志，或任务被中断 | 查找最终 Summary 和 `execute end(finish)`，再查状态表。 |
| 大量 `own_ship_ineligible` | 船型资格在每个窗口被过滤 | 不按错误处理；核对 `FAILED/windowsFailed`。 |
| `replayFailures>0` 或 `truncated>0` | 候选回放不完整 | 窗口会标记 `FAILED`；修复依赖后用原参数续跑。 |
