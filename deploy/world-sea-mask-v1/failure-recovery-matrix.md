# WORLD_SEA_MASK 故障恢复矩阵

| 故障点 | 数据库现实 | STATE/下一动作 | 可重试 | 数据丢失 |
|---|---|---|---|---|
| stage 导入一半 | stage 为受控数据子集 | `IMPORT_STAGE` 补齐缺失 ID | 是 | 否 |
| stage 已存在且相同 | stage 指纹等于受控文件 | `IMPORT_STAGE` 幂等登记 | 是 | 否 |
| stage 已存在但不同 | 任一 ID/WKB/名称不同 | 拒绝，人工核验 | 否 | 否 |
| validate 失败 | `FAILED`，stage 保留 | 修订数据后 `VALIDATE_STAGE` | 是 | 否 |
| REGISTER 后中断 | `STAGED` | `PUBLISH_STAGE` | 是 | 否 |
| PUBLISH 前中断 | `STAGED` 或 stage 仍存在的 `PUBLISHING` | `RESUME_RELEASE` | 是 | 否 |
| RENAME 后、状态更新前中断 | stage 消失、formal 指纹匹配、`PUBLISHING` | `RESUME_RELEASE`→`PUBLISHED_INACTIVE` | 是 | 否 |
| formal 已切换、ACTIVE 未写 | `PUBLISHED_INACTIVE` | 人工批准后 `ACTIVATE_DATASET` | 是 | 否 |
| ACTIVE 写入失败 | DML 事务回滚，仍 `PUBLISHED_INACTIVE` | `ACTIVATE_DATASET` | 是 | 否 |
| 新旧 formal 指纹相同 | stage 保留、`NOOP_IDENTICAL` | 已有 ACTIVE 同指纹则结束，否则激活 | 是 | 否 |
| backup 计划表已存在 | RENAME 前拒绝 | 人工处理命名冲突 | 否 | 否 |
| rollback RENAME 后中断 | failed 已存在，backup 已恢复或 formal 已隔离 | 重跑 `ROLLBACK_RELEASE` 只补元数据 | 是 | 否 |
| 多次 RESUME | 根据数据库现实重新推导 | 当前唯一合法下一步或 `NONE` | 是 | 否 |
| formal 不匹配且 backup 存在 | `ROLLBACK_REQUIRED` | `ROLLBACK_RELEASE` | 是 | 否 |

当前 7 条无效几何对应 `VALIDATE_STAGE` 失败场景，必须继续保持 stage，不得进入 `STAGED`。
