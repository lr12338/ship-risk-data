# GitHub / GitLab 版本同步与手工部署（2026-08-12）

本发布分支以个人 GitHub `codex/behavior-insurance-rules-v1` 的已验证版本为基线，合入公司 GitLab `master` 的 `ef84505`（船舶基础数据日任务与补算截止日调整）。当前服务器有任务运行，**本次只更新 Git 版本、文档和本地发布物，不执行服务器部署、重启或 XXL-Job**。

## 1. 同步结果与问题根因

- GitHub 行为保险基线：`6f98979`，包含 WORLD_SEA、港区/锚地几何修复、保险场景规则、搭靠截断递归拆分与 1nm BBOX。
- GitLab 同事提交：`ef84505`，增加 `zonePassageDayJobHandler` 漏跑补算，将 `zone.passage.backfill.to-date` 调整为 `2026-08-10`。
- `ef84505` 附带的 `src/main/resources/sql/ship_zone_crossing_event_dedup.sql` 是人工去重 SQL，代码没有自动调用它；本次同步、构建和后续 JAR 部署都不得执行该 DELETE。
- 2026-08-12 服务器 JAR SHA-256 为 `7bb226ae2d9f6f797428a9a13c788c342db1857c464c8a9c553ed6c91afc2cee`，它来自 GitLab `master` 路线，仅允许行为测试范围 `[2026-01-01, 2026-07-01)`，因此无法续跑已建立的2025批次。
- 同步后的 `ProductionTestParams` 保留通用规则：时间跨度不超过366天，不硬编2026范围；原 `batchNo`、参数和 `resume=true` 可继续使用。

## 2. 关键配置

只逐项合并 [application.properties.delta](application.properties.delta)，禁止用仓库配置覆盖 `/data/zhengyi/application.properties`。必须保留服务器现有数据源、HBase、XXL-Job、WORLD_SEA、港区/锚地、场景快照路径及密码/token。

部署前只读核对（不输出密码）：

```bash
ssh 242 "grep -E '^(zone.passage.(parallelism|progress-interval|status.batch-size|trajectory.simplify-zoom|backfill.from-date|backfill.to-date)|ship.behavior.alongside.(scene-data.available|replay.read-timeout-ms|replay.bbox-buffer-nm|candidate.min-speed|candidate.max-speed))=' /data/zhengyi/application.properties"
```

`ship.behavior.build.git-commit`、`ship.behavior.code-commit-sha` 和 `ship.behavior.build.jar-sha256` 必须在手工部署时更新为本次实际 Git SHA/JAR SHA；不将旧值或占位符写入 delta。

## 3. 当前不部署

只要服务器仍有运行中任务，不得上传并替换正式 JAR、不得修改正式配置、不得停止/重启 Java 进程。可以预先在本地生成 JAR 并记录 SHA-256。

## 4. 后续手工部署

### 4.1 确认窗口

1. XXL-Job 禁用相关自动调度，确认调度日志无“执行中”实例。
2. 保留所有批次、窗口状态和事件；不通过删除数据解决续跑问题。
3. 记录当前 JAR SHA、进程启动时间和当前配置备份。

### 4.2 上传与校验

```bash
cd /path/to/ship-risk-data-gitlab-sync-20260812
git rev-parse HEAD
shasum -a 256 target/ship-risk-data-0.0.1-SNAPSHOT.jar
scp target/ship-risk-data-0.0.1-SNAPSHOT.jar 242:/data/zhengyi/ship-risk-data-0.0.1-SNAPSHOT.jar.uploading
ssh 242 'sha256sum /data/zhengyi/ship-risk-data-0.0.1-SNAPSHOT.jar.uploading'
```

本地与远端 SHA-256 必须完全一致；不一致时不允许替换。

### 4.3 备份、合并配置和替换

```bash
ssh 242
cd /data/zhengyi
release_stamp=$(date +%Y%m%d-%H%M%S)
mkdir -p "backups/$release_stamp"
cp ship-risk-data-0.0.1-SNAPSHOT.jar application.properties start.sh stop.sh status.sh restart.sh "backups/$release_stamp/"
```

使用编辑器逐项合并 delta，并填写实际构建身份。完成后再执行：

```bash
./stop.sh
mv ship-risk-data-0.0.1-SNAPSHOT.jar.uploading ship-risk-data-0.0.1-SNAPSHOT.jar
sha256sum ship-risk-data-0.0.1-SNAPSHOT.jar
./start.sh
./status.sh
tail -n 200 logs/startup.log
```

验收启动日志中的数据源、WORLD_SEA、港区/锚地、场景快照与 XXL-Job handler 注册；不自动触发任何任务。

### 4.4 回滚

```bash
ssh 242
cd /data/zhengyi
./stop.sh
cp "backups/REPLACE_RELEASE_STAMP/ship-risk-data-0.0.1-SNAPSHOT.jar" ./ship-risk-data-0.0.1-SNAPSHOT.jar
cp "backups/REPLACE_RELEASE_STAMP/application.properties" ./application.properties
./start.sh
./status.sh
```

回滚不删除已有批次、状态、审计或事件数据。
