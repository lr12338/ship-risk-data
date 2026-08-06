# WORLD_SEA_MASK V1R1 可恢复发布状态机

| 状态/现实 | 已完成步骤 | 唯一正常下一步 |
|---|---|---|
| repaired stage + `PENDING_REVIEW` | 修复、审计、严格校验 | 人工审核 |
| repaired stage + matching `APPROVED` | 审批文件就绪 | `REGISTER_STAGE` |
| `STAGED` | 发布身份已登记 | `PUBLISH_STAGE` |
| `PUBLISHING` | 发布意图、backup 和旧 ACTIVE 已持久化 | `RESUME_RELEASE` |
| `PUBLISHED_INACTIVE` | formal 已切换，尚不可被应用加载 | `ACTIVATE_DATASET` |
| `NOOP_IDENTICAL` | formal 已是相同指纹 | `ACTIVATE_DATASET` 或幂等结束 |
| `ACTIVE` | formal 严格复核且唯一 ACTIVE 已事务提交 | 无 |
| `ROLLBACK_REQUIRED` | 数据库现实不一致 | `ROLLBACK_RELEASE` |
| `ROLLED_BACK` | 正式表和元数据已恢复/隔离 | 无 |

`APPLY`、原始 `IMPORT_STAGE` 和 `VALIDATE_STAGE` 在 V1R1 禁用。修复、REGISTER、PUBLISH、ACTIVATE 均是独立命令。

`REGISTER_STAGE` 强制校验 approval 状态、审核人、审核时间、7 个 ID、Baffin/Bo Hai 决策和 repaired 指纹，并把 7 条审计行事务性更新为 APPROVED。

`PUBLISH_STAGE` 再次读取审批文件和 repaired stage；RENAME 前持久化 `PUBLISHING`。若进程在 RENAME 后中断，`STATE` 根据 stage/formal/backup/指纹现实判断，`RESUME_RELEASE` 只恢复为 `PUBLISHED_INACTIVE`，不会自动激活。

`ACTIVATE_DATASET` 再次严格读取 formal 的 102 条真实 WKB和指纹，然后在事务中执行旧 ACTIVE→RETIRED、新版本→ACTIVE，并验证 ACTIVE 数量为 1。
