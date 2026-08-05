# 写入模式矩阵

| 模式 | 文件 | Shadow 表 | 正式事件表 |
|---|---|---|---|
| NONE | Run Manifest | 0 | 0 |
| FILE_AUDIT | Manifest + NDJSON + checksums | 0 | 0 |
| SHADOW_DB | Manifest | `ship_behavior_shadow_result` | 0 |
| FORMAL_DB | Manifest | 可选 | 仅全部门禁通过 |

`dryRun=true` 永远解析为 NONE。SHADOW writer 只持有 Shadow sink；正式 sink 不可达。FORMAL_DB 缺任一服务器开关、专用 Handler、生产批准 RuleSet 或 Preflight 时拒绝启动，不自动降级。
