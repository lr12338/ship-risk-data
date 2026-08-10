# 测试服手工部署（242 /data/zhengyi）

本手册只提供人工操作步骤；不要在本地或服务器上自动触发 XXL-Job。

## 1. 本地构建与身份记录

在 `codex/behavior-insurance-rules-v1` 工作树执行：

```bash
git rev-parse HEAD
mvn clean test
mvn clean package -DskipTests
shasum -a 256 target/ship-risk-data-0.0.1-SNAPSHOT.jar
date '+%Y-%m-%d %H:%M:%S %Z'
```

记录 Git SHA、SHA-256 和构建时间。JAR 与本目录材料必须来自同一 Git SHA；不要把完整服务器配置或密码复制进仓库。

## 2. 上传与远端校验

本次必须上传或准备的关键文件：

| 文件 | 服务器位置 | 用途 |
| --- | --- | --- |
| `target/ship-risk-data-0.0.1-SNAPSHOT.jar` | `/data/zhengyi/ship-risk-data-0.0.1-SNAPSHOT.jar.uploading` | 待替换应用 JAR。 |
| `application.properties.delta` | `/data/zhengyi/import/behavior-insurance-rules-v1/` | 仅供逐项合并，禁止覆盖完整配置。 |
| `geometry-repair.md` | 同上 | 26条几何 PREVIEW/APPLY 手册。 |
| `geometry-repair-rollback.sql` | 同上 | 几何写回回滚。 |
| `query.sql` | 同上 | 场景、批次、状态和事件验收。 |
| `xxl-job-guide.md`、`xxl-job-params.json` | 同上 | XXL-Job 人工配置和单行参数。 |

```bash
scp target/ship-risk-data-0.0.1-SNAPSHOT.jar 242:/data/zhengyi/ship-risk-data-0.0.1-SNAPSHOT.jar.uploading
ssh 242 'mkdir -p /data/zhengyi/import/behavior-insurance-rules-v1'
scp deploy/behavior-insurance-rules-v1/application.properties.delta deploy/behavior-insurance-rules-v1/geometry-repair.md deploy/behavior-insurance-rules-v1/geometry-repair-rollback.sql deploy/behavior-insurance-rules-v1/query.sql deploy/behavior-insurance-rules-v1/xxl-job-guide.md deploy/behavior-insurance-rules-v1/xxl-job-params.json 242:/data/zhengyi/import/behavior-insurance-rules-v1/
ssh 242 'sha256sum /data/zhengyi/ship-risk-data-0.0.1-SNAPSHOT.jar.uploading'
```

远端输出必须与本地 SHA-256 完全一致；不一致时删除 `.uploading` 文件并重新上传，不要替换正在运行的 JAR。

## 3. 合并配置

将 [application.properties.delta](application.properties.delta) 的键逐项合并到 `/data/zhengyi/application.properties`：

- 不覆盖完整配置；必须保留现有数据源、WORLD_SEA、XXL-Job 地址、端口和服务器专有参数。
- `ship.behavior.scene.port-approach-distance-nm=10.0` 为 Port Approach 场景阈值；正式距离字段使用港口 Polygon 边界。
- Loitering 使用 `LOITERING_V2_INSURANCE_SCENE_V1`，`min-speed=1.0`、低速 hard split、港区/锚地排除、远海启用、weak 不写库、场景数据 fail closed。
- Alongside 使用 `ALONGSIDE_V3_INSURANCE_SCENE_V2`；不要恢复 target merchant hard filter，也不要添加“距港口 10nm”存在性 Gate。
- 港区、锚地和 WORLD_SEA 已完成预检后，将 `ship.behavior.loitering.scene-data.available`、`ship.behavior.alongside.scene-data.available` 设为 `true`，否则风险解释仍按“场景数据不可用”降级。
- 本轮 Loitering 参数固定 `engines=["V2"]`，因此保留 `ship.behavior.loitering.production.scene-ready=false`；只有另行验收 V3 后才允许改为 true。

合并后只核对键和值，不输出数据源密码或 XXL access token：

```bash
ssh 242 "grep -E '^(ship.behavior.scene.port-approach-distance-nm|ship.behavior.loitering.(rule-version|min-speed|split-below-min-speed|port-approach.required|offshore.enabled|weak.store-enabled|scene.fail-closed|scene-data.available)|ship.behavior.alongside.(rule-version|scene-data.available))=' /data/zhengyi/application.properties"
```

## 4. 备份、替换、启动

```bash
ssh 242
cd /data/zhengyi
release_stamp=$(date +%Y%m%d-%H%M%S)
mkdir -p "backups/$release_stamp"
cp ship-risk-data-0.0.1-SNAPSHOT.jar application.properties start.sh stop.sh status.sh restart.sh "backups/$release_stamp/"
[ ! -f logs/startup.log ] || cp logs/startup.log "backups/$release_stamp/startup.log"
./stop.sh
mv ship-risk-data-0.0.1-SNAPSHOT.jar.uploading ship-risk-data-0.0.1-SNAPSHOT.jar
./start.sh
./status.sh
tail -n 200 logs/startup.log
```

启动后确认无 Bean 初始化、数据源、WORLD_SEA、港区/锚地 scene、XXL-Job handler 注册错误。仅启动成功不代表规则已验收；不要因此触发任何任务。

港口参考集合必须加载 9,251 条，且不得包含 `zones_type=1` 或名称含 `anchor/锚地/锚泊` 的记录。代码部署验收通过后，再按 [geometry-repair.md](geometry-repair.md) 使用同一 JAR 执行 PREVIEW 和 APPLY；不要自动触发 XXL-Job。

如果你选择先只验证代码查询修复，可以完成 PREVIEW 后暂不执行 APPLY；但进入20船测试前必须明确记录是否已写回26条，以及备份表/回滚状态。

## 5. 回滚

```bash
ssh 242
cd /data/zhengyi
./stop.sh
cp "backups/REPLACE_RELEASE_STAMP/ship-risk-data-0.0.1-SNAPSHOT.jar" ./ship-risk-data-0.0.1-SNAPSHOT.jar
cp "backups/REPLACE_RELEASE_STAMP/application.properties" ./application.properties
./start.sh
./status.sh
```

回滚 JAR 不删除批次清单、窗口状态、WORLD_SEA 审计或既有事件；这些数据用于追溯，不得手工清理以掩盖失败。

如果已经执行港口锚地几何 APPLY，完整回滚还必须执行 [geometry-repair-rollback.sql](geometry-repair-rollback.sql) 恢复26条原 WKB，并核对恢复哈希。
