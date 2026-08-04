# Region Behavior V1 部署说明

Region Behavior V1 是独立、只读、dry-run-only 的区域历史行为扫描。它不写正式事件表、不写候选船表、不写原有 batch status。

## 启用

1. 把 `region-behavior.properties.example` 中的配置合并到部署环境的外部配置，不要将凭据写入 XXL-JOB 参数。
2. 先保持 `ship.behavior.region.enabled=false` 检查部署包。
3. 确认输出目录可写、AIS41 为只读账号、HBase 和区域 replay 端点可达后，在外部配置中显式设为 `true`。

## XXL-JOB

- `shipBehaviorRegionPreflightJobHandler`：使用 `jobs/preflight-example.json`，只做参数、几何、资源和 Bean 检查，不调用 AIS replay。
- `shipBehaviorRegionDryRunJobHandler`：只接受 `dryRun=true` 且 `storeCandidates=false`。完整查询返回 `REGION_RUN_COMPLETED`；任意 slice 不完整或分析失败返回 `REGION_RUN_PARTIAL`，XXL-JOB 状态为失败。

每个 `batchNo` 只允许写一次。目标目录已存在时任务 fail-closed，避免覆盖调查证据。

## Smoke 顺序

仅使用小 Polygon 和少量候选船，依次执行 2 小时、6 小时、24 小时。首次不得扫描大区域或多日。记录 API/HBase 耗时、点数、船数、事件数、timeout/truncated、峰值内存及 `checksums.sha256`。
