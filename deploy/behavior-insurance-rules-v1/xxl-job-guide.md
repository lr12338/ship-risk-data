# XXL-Job 手工配置与运行

## 粘贴规则

在 XXL-Job 选择对应 Handler 后，将 `xxl-job-params.json` 中对象的 **`params` 值**复制到“任务参数”。每个 JSON 对象是一行；复制时不要插入换行、注释或额外字符，避免控制台参数长度/格式校验失败。

| 行为 | Handler | 20 / 100 / 500 批次 | 5000 批次 |
| --- | --- | --- | --- |
| 徘徊 | `shipLoiteringProductionTestJobHandler` | `loitering-merchant{N}-2025-insurance-scene-v1` | `loitering-merchant5000-2025-insurance-scene-v1` |
| 搭靠 | `shipAlongsideProductionTestJobHandler` | `alongside-merchant{N}-2025-insurance-scene-v1` | `alongside-merchant5000-2025-insurance-scene-v1` |

任务设置为手动触发、单机路由、串行阻塞。任何时间只运行一个行为任务。

## 参数定义

- `listBatchNo=liquid-container-5000-2025h2-2026h1-v1`：固定测试名单；运行前必须查询为 5000 条、5000 个唯一 MMSI。
- `expectedShipCount=5000`：校验名单身份，不是本次运行数量。`shipLimit` 才是 20、100、500 或 5000。
- `pageSize`：单页从名单读取的数量；20 用 20，其他用 50。`parallelism=1` 强制串行。
- `startTime`/`endTime`：AIS 分析时间范围；不得超过 366 天。`windowDays=30`，Loitering 的 `trajectorySliceHours=6` 固定。
- `resume=true`：同一批次按已完成窗口续跑；失败恢复时必须保持原参数与原 `batchNo`。
- `retryCount=2`、`logIntervalShips=10`：窗口重试次数和 XXL 日志频率。
- `engines=["V2"]`：Loitering 使用本次保险场景 V2 链路。Alongside 参数无需 `engines`。
- `storeDetails=false`：不额外写轨迹详情；Alongside 生产测试只能 event-only。
- `batchNo` 与名单、范围、规则版本、写入模式、配置 hash 一起构成不可变运行身份；同名不同参数会被拒绝。

## 分级执行

1. 依次运行 Loitering 20、Alongside 20；使用 `query.sql` 验证后，再依次运行 100 和 500。
2. 20/100/500 都是 dry-run：`enableInsert=false`。会写 `ship_behavior_run_batch` 和两个窗口状态表，**不会**写 `ship_behavior_event`；因此候选数以状态表 `event_count` 为准。
3. 5000 参数为正式受控任务：`enableInsert=true`。只有所有 dry-run 通过、人工审核 SQL 和轨迹样本、明确授权后才可粘贴。先完成 Loitering 5000，再运行 Alongside 5000。
4. 不得修改已运行批次的参数；失败时修复后以原单行 JSON、原 `batchNo`、`resume=true` 续跑。
