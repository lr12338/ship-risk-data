# 数据库迁移

Forward：`src/main/resources/sql/migrations/V20260805__behavior_platform_v1.sql`。Rollback：`U20260805__behavior_platform_v1.sql`。应用不会执行这些文件；必须人工评审、备份并在授权窗口运行。

行为模块自动建表/补列默认关闭：`ship.behavior.schema-auto-migration-enabled=false`。缺表或缺列会 fail-closed。扩大 XXL 字段的 SQL 位于 `sql/optional/`，仅在查明实际字段后考虑，Profile 短参数仍是主方案。
