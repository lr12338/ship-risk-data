# WORLD_SEA_MASK V1 发布加固说明

## 当前状态：BLOCKED_INVALID_GEOMETRY

`WORLD_SEA_102_V1` 的暂存表仍包含 7 条 JTS 拓扑无效几何：

```text
11, 26, 85, 91, 92, 99, 24556
```

因此当前 Mask 不得部署，不得运行应用预检或任何行为任务。本轮产物只完成代码和发布流程加固；下一阶段必须获得修订后的有效数据版本或逐要素修复的独立授权。

旧 JAR `63d4ad5ff02389c5a32ab81daed4dee3359a5afce9f45758111ff420b172b330` 是从包含未提交用户修改的工作区构建，已废弃，不得部署。

## 安全边界

- 禁止直接执行原始 `world_sea.sql`。
- 禁止修改当前 `ship_behavior_world_sea_stage_20260806_155556`。
- 禁止对 7 条无效 Geometry 使用 `buffer(0)`、`GeometryFixer`、删除或排除后发布。
- `migration.sql` 必须独立人工执行，导入工具不会自动建表。
- `PUBLISH_STAGE` 只发布为 `PUBLISHED_INACTIVE`，不会写 ACTIVE。
- `ACTIVATE_DATASET` 会重新严格校验正式表，之后才在事务中切换唯一 ACTIVE 元数据。
- 当前数据未修复前，不得执行 migration、IMPORT、REGISTER、PUBLISH、ACTIVATE 或 ROLLBACK。

## 后续获批后的发布顺序

先人工执行 [migration.sql](migration.sql)，再以固定服务器配置运行：

```bash
java -Dloader.main=com.hifleet.worldsea.WorldSeaImportTool \
  -cp ship-risk-data-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.PropertiesLauncher \
  DRY_RUN ship_behavior_world_sea_stage_<timestamp>

java -Dloader.main=com.hifleet.worldsea.WorldSeaImportTool \
  -cp ship-risk-data-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.PropertiesLauncher \
  IMPORT_STAGE ship_behavior_world_sea_stage_<timestamp>

# 后续依次使用 VALIDATE_STAGE、REGISTER_STAGE、PUBLISH_STAGE。
# PUBLISH_STAGE 后必须先检查 STATE，再由独立人工批准执行 ACTIVATE_DATASET。
```

任一步中断后先执行只读 `STATE`，再执行 `RESUME_RELEASE`。详细状态转换见 [release-state-machine.md](release-state-machine.md)，故障处置见 [failure-recovery-matrix.md](failure-recovery-matrix.md)。

## 干净发布构建

发布提交推送后，只能从与远端 SHA 一致的 clean source worktree 运行：

```bash
WORLD_SEA_RELEASE_OUTPUT_DIR=/absolute/output/path \
  scripts/build_world_sea_release.sh
```

脚本会在新的 detached clean worktree 中再次执行 `mvn clean test` 和 `mvn clean package -DskipTests`。任何 dirty tree、远端 SHA 不一致、测试失败、JAR 缺失、manifest 不完整或构建后源码变化都会停止发布包生成。
