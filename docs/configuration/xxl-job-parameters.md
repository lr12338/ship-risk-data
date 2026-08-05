# XXL-JOB 参数

允许字段只有 `profile/profileId`、`list/listBatchNo`、`from/startTime`、`to/endTime`、`startSeq`、`limit/shipLimit`、`pageSize`、`parallelism`、`resume`、`dryRun`、`batch/batchNo`、`sceneModeOverride`、`writeModeOverride`。未知字段和截断 JSON 直接拒绝。

示例：

```json
{"profile":"alongside-scene-ab-v1","list":"liquid-container-5000-v1","from":"2025-07-01","to":"2026-01-01","limit":100,"batch":"as-ab-20260805-01"}
```

校验：`scripts/validate-xxl-job-param --file deploy/behavior-platform-v1/jobs/alongside-scene-ab-v1.json --field-length <实际长度>`。本地样例为 147–162 UTF-8 字节。当前应用服务器没有 XXL Admin 数据库配置或 mysql 客户端，实际 `executor_param` 长度尚未完成只读数据库核验；不得用 512 假设替代生产证据。
