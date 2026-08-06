# WORLD_SEA_MASK V1 部署手册

WORLD_SEA_MASK 仅表示全球命名海洋水域掩膜，不表示法律公海、EEZ、领海或主权结论。

## 数据导入

原始 `world_sea.sql` 禁止直接执行。服务器固定目录为 `/data/zhengyi/import/world-sea-mask-v1/`。先核对 SHA-256，再用发布 JAR 中的 `WorldSeaImportTool` 对受控 SQL 执行 `DRY_RUN`，确认无 `CREATE DATABASE`、`USE` 或越界 `DROP TABLE` 后执行 `APPLY`。工具只读取服务器外置配置，不打印凭据；只允许 `dataanalysis` 和固定 stage 前缀。

```bash
java -Dloader.main=com.hifleet.worldsea.WorldSeaImportTool \
  -cp /data/zhengyi/import/world-sea-mask-v1/ship-risk-data-import-tool.jar \
  org.springframework.boot.loader.PropertiesLauncher DRY_RUN <stage_table>

java -Dloader.main=com.hifleet.worldsea.WorldSeaImportTool \
  -cp /data/zhengyi/import/world-sea-mask-v1/ship-risk-data-import-tool.jar \
  org.springframework.boot.loader.PropertiesLauncher APPLY <stage_table>
```

## JAR 手工部署（本任务不自动执行）

```bash
scp dist/world-sea-mask-v1/ship-risk-data-0.0.1-SNAPSHOT.jar \
  242:/data/zhengyi/ship-risk-data-0.0.1-SNAPSHOT.jar.uploading
ssh 242
cd /data/zhengyi
sha256sum ship-risk-data-0.0.1-SNAPSHOT.jar.uploading
release_stamp=$(date +%Y%m%d-%H%M%S)
mkdir -p "backups/$release_stamp"
cp ship-risk-data-0.0.1-SNAPSHOT.jar application.properties start.sh stop.sh status.sh restart.sh "backups/$release_stamp/"
[ ! -f startup.log ] || mv startup.log "backups/$release_stamp/startup.log"
[ ! -f heapdump.hprof ] || mv heapdump.hprof "backups/$release_stamp/heapdump.hprof"
# 手工合并 application.properties.delta，并设置最终 ship.behavior.code-commit-sha
./stop.sh
mv ship-risk-data-0.0.1-SNAPSHOT.jar.uploading ship-risk-data-0.0.1-SNAPSHOT.jar
./start.sh
./status.sh
ss -lnt | grep -E ':8090|:30008'
tail -n 300 startup.log
```

检查 Bean、数据源、WORLD_SEA_MASK、XXL-JOB Handler、8090/30008、OOM 和 DDL 错误。任务依次执行 mask preflight、20 船徘徊 dry-run、20 船搭靠 dry-run；通过后才人工扩大到 100、500、5000，始终单机路由、串行阻塞、`parallelism=1`。
