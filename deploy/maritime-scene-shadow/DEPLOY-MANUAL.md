# Maritime Scene Shadow 手工部署

本包只允许人工部署。当前服务器方式来自仓库 `DEPLOY.md` 与 `deploy/*.sh`：应用目录 `/data/zhengyi`，JAR `ship-risk-data-0.0.1-SNAPSHOT.jar`，HTTP 8090，XXL-Job 30008，使用同目录外置 `application.properties`。没有 systemd unit。

## 当前门禁

数据仅覆盖上海 `[120.5,30.5,122.5,32.0]`。Preflight 可以执行；当前 Shadow Audit Handler 只返回 READY，尚未遍历名单/AIS episode，因此 100/500/5,000 参数是已校验的分阶段模板，不是本轮可运行授权。全球 5,000 艘还需要覆盖真实轨迹范围的全球或分区 Release。

## 1. 上传前检查

```bash
APP_HOME=/data/zhengyi
SCENE_HOME=/data/zhengyi/maritime-scene
BACKUP_ROOT=/data/zhengyi/backups
df -h
free -h
java -version
ps -ef | grep 'ship-risk-data-0.0.1-SNAPSHOT.jar'
ss -lntp | grep -E ':(8090|30008)'
```

## 2. 本地待上传

- `app/ship-risk-data-0.0.1-SNAPSHOT.jar`
- `scene-release/phase1-smoke-shanghai-autocross-v1/`
- `config/application-properties.patch.txt`
- `sql/*.sql`
- `checksums.sha256`

## 3. 先备份，后替换

```bash
STAMP=$(date +%Y%m%d-%H%M%S)
mkdir -p "$BACKUP_ROOT/$STAMP"
cp "$APP_HOME/ship-risk-data-0.0.1-SNAPSHOT.jar" "$BACKUP_ROOT/$STAMP/"
cp "$APP_HOME/application.properties" "$BACKUP_ROOT/$STAMP/"
cp "$APP_HOME/start.sh" "$APP_HOME/stop.sh" "$BACKUP_ROOT/$STAMP/"
test ! -d "$SCENE_HOME/releases/phase1-smoke-shanghai-autocross-v1" || cp -a "$SCENE_HOME/releases/phase1-smoke-shanghai-autocross-v1" "$BACKUP_ROOT/$STAMP/"
```

## 4. 分离 JAR 与数据

```bash
mkdir -p "$SCENE_HOME/releases"
cp -a scene-release/phase1-smoke-shanghai-autocross-v1 "$SCENE_HOME/releases/"
cp app/ship-risk-data-0.0.1-SNAPSHOT.jar "$APP_HOME/ship-risk-data-0.0.1-SNAPSHOT.jar.new"
```

在解压后的部署包根目录运行 `scripts/verify-package.sh`。复制后运行 `scripts/verify-server-files.sh`。另核对：

```bash
sha256sum "$APP_HOME/ship-risk-data-0.0.1-SNAPSHOT.jar.new"
jq '.releaseVersion,.snapshotFingerprint,.validationSummary.releaseStatus' "$SCENE_HOME/releases/phase1-smoke-shanghai-autocross-v1/manifest.json"
find "$SCENE_HOME/releases/phase1-smoke-shanghai-autocross-v1/tiles" -name '*.ndjson.gz' | wc -l
```

## 5. 只追加配置

```bash
cp "$APP_HOME/application.properties" "$APP_HOME/application.properties.backup-$STAMP"
grep -n 'ship.behavior.maritime-scene' "$APP_HOME/application.properties"
# 人工把 config/application-properties.patch.txt 追加或逐项合并；不要覆盖数据源、XXL-JOB、端口配置。
diff -u "$APP_HOME/application.properties.backup-$STAMP" "$APP_HOME/application.properties"
```

首次必须保持 `mode=SHADOW`、`audit.store-enabled=false`。

## 6. 更新并检查服务

```bash
cd "$APP_HOME"
./stop.sh
mv ship-risk-data-0.0.1-SNAPSHOT.jar ship-risk-data-0.0.1-SNAPSHOT.jar.previous-$STAMP
mv ship-risk-data-0.0.1-SNAPSHOT.jar.new ship-risk-data-0.0.1-SNAPSHOT.jar
./start.sh
./status.sh
ps -ef | grep 'ship-risk-data-0.0.1-SNAPSHOT.jar'
ss -lntp | grep -E ':(8090|30008)'
tail -n 200 logs/startup.log
tail -n 200 logs/log.log | grep -E 'shipMaritimeScene|MaritimeScene|xxl-job'
```

XXL-JOB 应注册 `shipMaritimeScenePreflightJobHandler` 与 `shipMaritimeSceneShadowAuditJobHandler`。只手工运行 Preflight，参数为 `jobs/preflight-params.json`。

## 7. 数据库

首次 dry-run 不执行 DDL、不写数据库。未来通过 runner 门禁后，先在事件库执行：

```sql
SELECT DATABASE();
-- 必须返回 dataanalysis，再执行 ship_behavior_maritime_scene_audit.sql
```

不得在 AIS 库执行，不得修改 `ship_behavior_event` 或 `ship_behavior_event_detail`。

## 8. 回滚

```bash
# 先把 mode 改回 OFF、store-enabled 改回 false，并恢复备份配置
cd "$APP_HOME"
./stop.sh
mv ship-risk-data-0.0.1-SNAPSHOT.jar "ship-risk-data-0.0.1-SNAPSHOT.jar.failed-$STAMP"
cp "$BACKUP_ROOT/$STAMP/ship-risk-data-0.0.1-SNAPSHOT.jar" .
cp "$BACKUP_ROOT/$STAMP/application.properties" .
./start.sh
./status.sh
```

默认保留 Shadow 审计表。只有明确批准后才执行 rollback SQL。
