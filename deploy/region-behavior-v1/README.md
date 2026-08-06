# Region Behavior V1 部署说明

Region Behavior V1 默认仍是独立、只读的 dry-run 区域历史行为扫描。只有参数显式选择
`writeMode=FORMAL_EVENT` 且服务器设置 `ship.behavior.region.write-enabled=true` 时，新的正式 handler
才会写入独立批次事件和续跑状态。

## 启用

1. 把 `region-behavior.properties.example` 中的配置合并到部署环境的外部配置，不要将凭据写入 XXL-JOB 参数。
2. 先保持 `ship.behavior.region.enabled=false` 检查部署包。
3. 确认输出目录可写、AIS41 为只读账号、HBase 和区域 replay 端点可达后，在外部配置中显式设为 `true`。

## XXL-JOB

- `shipBehaviorRegionPreflightJobHandler`：使用 `jobs/preflight-example.json`，只做参数、几何、资源和 Bean 检查，不调用 AIS replay。
- `shipBehaviorRegionDryRunJobHandler`：只接受 `dryRun=true` 且 `storeCandidates=false`。完整查询返回 `REGION_RUN_COMPLETED`；任意 slice 不完整或分析失败返回 `REGION_RUN_PARTIAL`，XXL-JOB 状态为失败。
- `shipBehaviorRegionRunJobHandler`：仅用于已批准的独立正式批次，每次只允许一个 behaviorType，支持原批次续跑。

dry-run 的每个 `batchNo` 只允许写一次报告。正式模式每次续跑写入独立 attempt 报告目录。

## Smoke 顺序

仅使用小 Polygon 和少量候选船，依次执行 2 小时、6 小时、24 小时。首次不得扫描大区域或多日。记录 API/HBase 耗时、点数、船数、事件数、timeout/truncated、峰值内存及 `checksums.sha256`。
