# 5000 船与 Region 生产测试手工部署

搭靠生产测试的完整参数定义、自定义时间、断点续跑和运维 SQL 见
[`docs/alongside-production-test-operations.md`](../../docs/alongside-production-test-operations.md)。

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
[ ! -f logs/startup.log ] || cp logs/startup.log "backups/$release_stamp/startup.log"
[ ! -f logs/heapdump.hprof ] || mv logs/heapdump.hprof "backups/$release_stamp/heapdump.hprof"
./stop.sh
mv ship-risk-data-0.0.1-SNAPSHOT.jar.uploading ship-risk-data-0.0.1-SNAPSHOT.jar
./start.sh
./status.sh
ss -lnt | grep -E ':8090|:30008'
tail -n 200 logs/startup.log
```

确认无 Bean 初始化、DDL、XXL-JOB 注册或数据源错误后再继续。

## 4. XXL-JOB 执行顺序

1. 手动运行名单固化 handler，用 `query.sql` 确认 5000 条、5000 个唯一 MMSI、序号 1–5000。
2. 将船舶任务复制为独立 `-smoke-` 批次，改为 `shipLimit=20`、`enableInsert=false`，串行执行。
3. 确认 smoke 批次在 `ship_behavior_event` 中为 0 条后，再串行执行两个 5000 船正式批次。
4. Region 先将模板改为 `writeMode=DRY_RUN`、`dryRun=true`、`enableInsert=false`，运行 preflight 和 dry-run handler。
5. 审核报告后手工改为 `ship.behavior.region.write-enabled=true`，再用独立正式批次运行 `shipBehaviorRegionRunJobHandler`。

四个正式任务都设为手动触发、单机路由、串行阻塞，不得并发。搭靠自定义时间参数已放在
`xxl-job-params.json` 的 `alongsideSmokeCustomRange` 和 `alongsideCustomRange`。

中断后只能使用完全相同的原参数和原 `batchNo` 续跑。只要时间、名单、规则版本、写入模式、窗口参数或世界海域掩膜身份发生变化，必须使用新 `batchNo` 从头执行。

### 4.1 搭靠自定义时间验收

`2025-01-01 00:00:00` 至 `2026-08-01 00:00:00` 共拆分为 20 个 30 天窗口：

- 20 船 smoke：预期 `windows=20`、`windowsDone=400`、`windowsFailed=0`，事件表中该 dry-run 批次为 0 条。
- 5000 船正式任务：预期总窗口数 100000，任务结束时 `shipsSeen=5000`、`windowsFailed=0`。
- `own_ship_ineligible` 是按窗口的船型资格过滤日志，不等于任务失败；以 `FAILED`、`windowsFailed` 和状态表为准。

### 4.2 中断和版本更新

- 部署前记录 XXL-Job 日志的最后一条 Summary，并查询 `DONE/FAILED` 窗口数。
- 计划续跑时，不得修改任何原参数；`resume=true` 只跳过同批次的 `DONE` 窗口。
- 出现 `batchNo already exists with different immutable parameters` 时不要改库表或绕过校验；使用新 `batchNo` 重跑。
- 新老版本的事件对比必须限定同一船舶集合和重叠时间范围，不直接比较不同进度批次的总数。

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
