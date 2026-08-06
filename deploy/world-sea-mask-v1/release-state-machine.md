# WORLD_SEA_MASK 可恢复发布状态机

| 状态 | 已完成步骤 | 唯一正常下一步 |
|---|---|---|
| `NEW` | 迁移基础表 | `IMPORT_STAGE` |
| `STAGE_IMPORTED` | 暂存数据完整导入并与受控文件指纹一致 | `VALIDATE_STAGE` |
| `STAGE_VALIDATED` | 102 条、类型、坐标、拓扑、指纹全部通过 | `REGISTER_STAGE` |
| `STAGED` | 发布信息登记完成 | `PUBLISH_STAGE` |
| `PUBLISHING` | backup 名称和旧 ACTIVE 已持久化，可能正在/已经 RENAME | `RESUME_RELEASE` |
| `PUBLISHED_INACTIVE` | 正式表已切换，但应用不可加载 | `ACTIVATE_DATASET` |
| `NOOP_IDENTICAL` | 正式表指纹与新数据相同，没有切表 | `ACTIVATE_DATASET` 或幂等结束 |
| `ACTIVE` | 正式表严格复核且唯一 ACTIVE 已事务提交 | 无 |
| `ROLLBACK_REQUIRED` | 数据库现实与发布记录不一致 | `ROLLBACK_RELEASE` |
| `ROLLED_BACK` | 正式表已恢复/隔离，元数据已恢复 | 无 |
| `FAILED` | 步骤失败并记录截断错误 | `STATE` 后 `RESUME_RELEASE` |

`APPLY` 已禁用。发布表切换与 ACTIVE 激活是两个独立步骤。

## RENAME 后恢复

`PUBLISH_STAGE` 在执行 RENAME 前先持久化 `PUBLISHING`、backup 表名和旧 ACTIVE 身份。若进程在 RENAME 后、状态更新前中断：

1. `STATE` 发现 stage 不存在、formal 指纹等于 release 指纹且状态仍为 `PUBLISHING`；
2. `RESUME_RELEASE` 将状态恢复为 `PUBLISHED_INACTIVE`；
3. 不会自动激活；仍需独立执行 `ACTIVATE_DATASET`。

`NOOP_IDENTICAL` 会持久化 `publish_mode=NOOP_IDENTICAL`。它没有切换正式表，因此回滚只恢复旧 ACTIVE 元数据，不会改名或隔离正式表。

## ACTIVE 激活

激活前重新读取正式表，执行 102 条、拓扑和 fingerprint 严格校验。随后在一个数据库事务中：旧 ACTIVE 设为 `RETIRED`、当前 meta 设为 `ACTIVE`、release 设为 `ACTIVE`。事务失败时保持 `PUBLISHED_INACTIVE`，应用继续 fail-closed。

## 幂等导入

`IMPORT_STAGE` 会把受控文件解析为 ID→WKB 清单：

- stage 不存在：创建并逐条导入；
- stage 部分导入：逐 ID 比对，补写缺失行；
- stage 已完整且指纹一致：零 INSERT 幂等成功；
- 任一已有 ID 内容不同、出现额外 ID：拒绝，不删除 stage。
