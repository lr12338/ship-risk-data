# WORLD_SEA_MASK V1R1 故障恢复矩阵

| 故障点 | 数据库现实 | 下一动作 | 数据丢失 |
|---|---|---|---|
| 无 approval / PENDING / 指纹或 ID 不匹配 | repaired stage 和 PENDING 审计保留 | 修正人工审批文件，再 `REGISTER_STAGE` | 否 |
| repaired stage 校验失败 | stage 保留供检查 | 不发布；修复工具新建另一 stage | 否 |
| REGISTER 事务失败 | 无 STAGED 或审批审计一起回滚 | `STATE` 后重试 REGISTER | 否 |
| REGISTER 后中断 | `STAGED` | `STATE`→`PUBLISH_STAGE` | 否 |
| PUBLISH 前中断 | `STAGED` 或 stage 仍存在的 `PUBLISHING` | `STATE`→`RESUME_RELEASE` | 否 |
| RENAME 后、状态更新前中断 | stage 消失、formal 指纹匹配、`PUBLISHING` | `STATE`→`RESUME_RELEASE`→`PUBLISHED_INACTIVE` | 否 |
| formal 已切换、ACTIVE 未写 | `PUBLISHED_INACTIVE` | `ACTIVATE_DATASET` | 否 |
| ACTIVE DML 失败 | 事务回滚，仍 `PUBLISHED_INACTIVE` | `STATE`→`ACTIVATE_DATASET` | 否 |
| 多个 ACTIVE 或 formal 指纹不同 | 激活拒绝 | `STATE`，必要时 `ROLLBACK_RELEASE` | 否 |
| backup 名冲突 | RENAME 前拒绝 | 人工核验冲突表，不手工重命名发布表 | 否 |
| rollback RENAME 后中断 | failed/formal/backup 现实可判定 | 重跑 `ROLLBACK_RELEASE` 补元数据 | 否 |

任何中断先 `STATE`，不得猜测、重新导入或手工 RENAME。
