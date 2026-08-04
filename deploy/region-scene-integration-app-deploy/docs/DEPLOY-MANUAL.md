# 区域行为 + Maritime Scene 手工部署

本轮只生成手工部署材料，Agent 未连接服务器、未执行 SQL、未写正式事件。仓库实际部署方式为 `/data/zhengyi` 下外置 `application.properties` 和 `start.sh`/`stop.sh`/`status.sh`，HTTP 8090、XXL-JOB 30008。

## 1. 本地待上传与 scp 模板

- 每次应用更新上传 `region-scene-integration-app-deploy-<timestamp>.tar.gz` 及 `.sha256`。
- Scene 数据只在服务器没有该 fingerprint 或 fingerprint 变化时上传 `phase1-global-base-v1-runtime.tar.gz` 及 `.sha256`。

```bash
UPLOAD_HOST=user@<SERVER_HOST>
scp target/region-scene-integration-app-deploy-<timestamp>.tar.gz* "$UPLOAD_HOST:/tmp/ship-risk-upload/"
# 仅首次或 fingerprint 变化时：
scp target/phase1-global-base-v1-runtime.tar.gz* "$UPLOAD_HOST:/tmp/ship-risk-upload/"
```

## 2. 服务器环境与磁盘检查

```bash
APP_HOME=/data/zhengyi
SCENE_HOME=/data/zhengyi/maritime-scene
BACKUP_ROOT=/data/zhengyi/backups
UPLOAD_DIR=/tmp/ship-risk-upload
df -h "$APP_HOME" "$UPLOAD_DIR"
free -h
java -version
ps -ef | grep 'ship-risk-data-0.0.1-SNAPSHOT.jar'
ss -lntp | grep -E ':(8090|30008)'
```

应用包很小；Scene 运行时数据解压后约 1.9 GiB。必须为上传包、解压目录和备份保留充足空间。

## 3. 校验上传包并解压应用包

```bash
cd "$UPLOAD_DIR"
sha256sum -c region-scene-integration-app-deploy-<timestamp>.tar.gz.sha256
tar -xzf region-scene-integration-app-deploy-<timestamp>.tar.gz
cd region-scene-integration-app-deploy
./scripts/verify-app-package.sh
./scripts/verify-server-environment.sh
```

## 4. 备份当前应用

```bash
STAMP=$(date +%Y%m%d-%H%M%S)
mkdir -p "$BACKUP_ROOT/$STAMP"
cp "$APP_HOME/ship-risk-data-0.0.1-SNAPSHOT.jar" "$BACKUP_ROOT/$STAMP/"
cp "$APP_HOME/application.properties" "$BACKUP_ROOT/$STAMP/"
cp "$APP_HOME/start.sh" "$APP_HOME/stop.sh" "$APP_HOME/status.sh" "$BACKUP_ROOT/$STAMP/"
```

## 5. 首次或 fingerprint 变化时安装 Scene 数据

先比较服务器 Manifest；相同 fingerprint 不上传、不解压、不替换。

```bash
jq -r '.snapshotFingerprint' "$SCENE_HOME/releases/phase1-global-base-v1/manifest.json" 2>/dev/null || true
cd "$UPLOAD_DIR"
sha256sum -c phase1-global-base-v1-runtime.tar.gz.sha256
mkdir -p "$SCENE_HOME/releases"
tar -xzf phase1-global-base-v1-runtime.tar.gz -C "$SCENE_HOME/releases"
cd "$SCENE_HOME/releases/phase1-global-base-v1"
sha256sum -c checksums.sha256
jq -e '.validationSummary.releaseStatus == "SHADOW_READY"' manifest.json
jq -e '.snapshotFingerprint == "<SCENE_FINGERPRINT>"' manifest.json
test "$(find tiles -type f -name '*.ndjson.gz' | wc -l | tr -d ' ')" = "<SCENE_TILE_COUNT>"
```

数据包不含原始 ZIP、staging、Atlas/QA、QGIS 中间文件、ETL venv 或其他 Release。

## 6. 人工合并配置增量

```bash
cp "$APP_HOME/application.properties" "$APP_HOME/application.properties.backup-$STAMP"
grep -nE 'ship.behavior.(maritime-scene|region)' "$APP_HOME/application.properties" || true
# 人工逐项合并 config/application-properties.patch.txt，禁止整文件覆盖。
diff -u "$APP_HOME/application.properties.backup-$STAMP" "$APP_HOME/application.properties"
```

必须保持 Scene `mode=SHADOW`、`audit.store-enabled=false`，Region `dry-run-only=true`。不要修改数据源、XXL-JOB、端口、AIS/HBase 或其他任务配置；不要执行 SQL。

## 7. 替换 JAR、启动和注册检查

```bash
cp app/ship-risk-data-0.0.1-SNAPSHOT.jar "$APP_HOME/ship-risk-data-0.0.1-SNAPSHOT.jar.new"
cd "$APP_HOME"
./stop.sh
mv ship-risk-data-0.0.1-SNAPSHOT.jar "ship-risk-data-0.0.1-SNAPSHOT.jar.previous-$STAMP"
mv ship-risk-data-0.0.1-SNAPSHOT.jar.new ship-risk-data-0.0.1-SNAPSHOT.jar
./start.sh
./status.sh
tail -n 200 logs/startup.log
tail -n 200 logs/log.log
LOG_FILE="$APP_HOME/logs/log.log" "$UPLOAD_DIR/region-scene-integration-app-deploy/scripts/check-handler-registration.sh"
```

## 8. Preflight 与分阶段任务

先在 XXL-JOB 手工运行 `shipMaritimeScenePreflightJobHandler`，参数为 `jobs/scene-preflight.json`。只有返回 PASS 才按 `docs/TEST-STAGES.md` 继续 Region Preflight、2h LOITERING、6h ALONGSIDE、24h combined。

严禁运行 `shipMaritimeSceneShadowAuditJobHandler`；它当前应返回 `SHADOW_RUNNER_NOT_IMPLEMENTED` 并失败。不得执行 SQL、不得写正式事件。

## 9. 回滚

```bash
APP_HOME=/data/zhengyi BACKUP_ROOT=/data/zhengyi/backups \
  "$UPLOAD_DIR/region-scene-integration-app-deploy/scripts/rollback-app.sh" "$STAMP"
```

等价手工命令：停止应用，恢复 `$BACKUP_ROOT/$STAMP` 中的 JAR 和 `application.properties`，再启动并检查状态。Scene Release 默认保留，不删除；回滚不执行 SQL。
