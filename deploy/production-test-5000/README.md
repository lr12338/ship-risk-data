# 5000 船与 Region 生产测试手工部署

## 1. 发布物校验

本地先执行 `mvn clean test` 和 `mvn clean package -DskipTests`，记录 Jar 的 SHA-256。发布包不含服务器密码或完整 `application.properties`。

```bash
shasum -a 256 target/ship-risk-data-0.0.1-SNAPSHOT.jar
scp target/ship-risk-data-0.0.1-SNAPSHOT.jar 242:/data/zhengyi/ship-risk-data-0.0.1-SNAPSHOT.jar.uploading
ssh 242 'sha256sum /data/zhengyi/ship-risk-data-0.0.1-SNAPSHOT.jar.uploading'
```

## 2. 数据库与配置预检

- 通过已批准的数据库客户端在 `dataanalysis` 执行 `migration.sql`；服务器本机没有 `mysql` 命令。
- 若四个 Region 字段或索引已存在，跳过对应 `ALTER` 子句；不要删除已有字段或表。
- 将 `application.properties.delta` 逐项合并到服务器现有配置，不要覆盖完整文件。
- 首次启动和 Region dry-run 期间保持 `ship.behavior.region.write-enabled=false`。

## 3. 备份、替换与启动

```bash
ssh 242
cd /data/zhengyi
release_stamp=$(date +%Y%m%d-%H%M%S)
mkdir -p "backups/$release_stamp"
cp ship-risk-data-0.0.1-SNAPSHOT.jar "backups/$release_stamp/"
cp application.properties start.sh stop.sh status.sh restart.sh "backups/$release_stamp/"
[ ! -f startup.log ] || mv startup.log "backups/$release_stamp/startup.log"
[ ! -f heapdump.hprof ] || mv heapdump.hprof "backups/$release_stamp/heapdump.hprof"
./stop.sh
mv ship-risk-data-0.0.1-SNAPSHOT.jar.uploading ship-risk-data-0.0.1-SNAPSHOT.jar
./start.sh
./status.sh
ss -lnt | grep -E ':8090|:30008'
tail -n 200 startup.log
```

确认无 Bean 初始化、DDL、XXL-JOB 注册或数据源错误后再继续。

## 4. XXL-JOB 执行顺序

1. 手动运行名单固化 handler，用 `query.sql` 确认 5000 条、5000 个唯一 MMSI、序号 1–5000。
2. 将船舶任务复制为独立 `-smoke-` 批次，改为 `shipLimit=20`、`enableInsert=false`，串行执行。
3. 确认 smoke 批次在 `ship_behavior_event` 中为 0 条后，再串行执行两个 5000 船正式批次。
4. Region 先将模板改为 `writeMode=DRY_RUN`、`dryRun=true`、`enableInsert=false`，运行 preflight 和 dry-run handler。
5. 审核报告后手工改为 `ship.behavior.region.write-enabled=true`，再用独立正式批次运行 `shipBehaviorRegionRunJobHandler`。

四个正式任务都设为手动触发、单机路由、串行阻塞，不得并发。中断后使用完全相同参数和原 `batchNo` 重新触发。

## 5. 回滚

```bash
cd /data/zhengyi
./stop.sh
cp "backups/REPLACE_RELEASE_STAMP/ship-risk-data-0.0.1-SNAPSHOT.jar" ./ship-risk-data-0.0.1-SNAPSHOT.jar
cp "backups/REPLACE_RELEASE_STAMP/application.properties" ./application.properties
./start.sh
./status.sh
```

新增字段和状态表为兼容性增量，回滚 Jar 时不删除。回滚不会删除已生成的独立批次事件。
