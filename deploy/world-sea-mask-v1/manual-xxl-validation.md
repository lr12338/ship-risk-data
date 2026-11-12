# WORLD_SEA_MASK XXL-JOB 人工执行与验收

本文只指导人工操作，不包含 XXL 管理中心密码、executor token 或数据库凭据。凭据继续由服务器 `/data/zhengyi/application.properties` 提供，禁止复制到命令、日志或发布材料。

## 固定执行规则

- 执行器选择 `ship-risk-data`，路由策略选择单机路由（推荐 `FIRST`）。
- 阻塞处理策略选择 `SERIAL_EXECUTION`，任务参数固定 `parallelism=1`。
- 仅手动触发；徘徊和搭靠禁止并发。
- 下一任务必须等上一任务日志出现 `job execute end(finish)`，且数据库验收全部通过。
- 20/100/500 船必须 `enableInsert=false`；5000 船只有在分级 dry-run 和人工抽检通过后才可将 `enableInsert` 设为 `true`。
- 失败续跑必须保持原 `batchNo`、原参数和 `resume=true`，不得换批次掩盖失败窗口。
- 当前 XXL 管理中心账号不可用：`XXL_ADMIN_AUTH_UNAVAILABLE`。由有权限的操作员在管理页面粘贴 `xxl-job-params.json` 中对应参数。

## 1. 每次启动前

在服务器检查应用和监听端口：

```bash
ssh 242
cd /data/zhengyi
./status.sh
ss -lntp | grep -E ':(8090|30008)[[:space:]]'
```

先在 XXL 管理页面手动运行：

```text
Handler: shipBehaviorWorldSeaMaskPreflightJobHandler
参数: {"mode":"WORLD_SEA_MASK_PREFLIGHT"}
```

只接受下列结果：

```text
status=PASS
featureCount=102
invalidGeometryCount=0
datasetVersion=WORLD_SEA_102_V1R1
geometryFingerprint=31dd89bd4968f8f436135b57cdb81e870ac7b827d4dc2c086282e7dfe91ab9c6
activeDatasetCount=1
indexReady=true
databaseWrites=0
```

任一项不符时停止，不运行搭靠或徘徊，不关闭 fail-closed，不修改 expected-feature-count。

## 2. 手动触发顺序

参数以 `xxl-job-params.json` 为唯一来源，顺序固定为：

1. 20 船徘徊（已 PASS）
2. 20 船搭靠（已 PASS）
3. 100 船徘徊（截至 2026-08-07 10:22:14 +08:00，50/100，人工监控中）
4. 100 船搭靠
5. 500 船徘徊
6. 500 船搭靠
7. 重点海区人工抽检
8. 5000 船徘徊正式任务
9. 5000 船搭靠正式任务

禁止跳级，也禁止同时运行序列中的两个任务。100 船徘徊完成前不要触发 100 船搭靠。

## 3. 日志检查

当前人工直连 executor 的 100 船徘徊日志为：

```bash
ssh 242
tail -n 200 -f /data/zhengyi/logs/job/jobhandler/2026-08-07/18080704.log
```

后续由管理中心生成的日志编号会变化，可先定位最近文件：

```bash
ssh 242
find /data/zhengyi/logs/job/jobhandler/$(date +%F) -type f -mmin -10 -print
```

同时观察应用日志和 JVM：

```bash
ssh 242
cd /data/zhengyi
tail -n 300 startup.log | grep -E 'WORLD_SEA|OutOfMemoryError|Full GC|HBase|timeout|truncat|ERROR'
pid=$(pgrep -fo 'ship-risk-data-0.0.1-SNAPSHOT.jar')
jcmd "$pid" GC.heap_info
ps -o pid,rss,vsz,etime,command -p "$pid"
```

通过日志必须同时看到：任务 finish、成功船数达到 shipLimit、失败窗口为 0。下列任一情况立即停止后续任务：

- `WORLD_SEA_MASK_UNAVAILABLE`
- `OutOfMemoryError` 或持续 Full GC
- 持续 HBase timeout
- Replay `failed > 0` 或 `truncated > 0`
- 作业进程重启、任务异常结束或失败窗口非 0

## 4. 数据库验收

使用已有安全数据源执行 `query.sql`。先把 SQL 中 `@batch_no` 改成刚完成的批次号，再依次检查：

- run manifest 的 `write_mode`、`config_hash` 与固定参数一致；
- 状态表 `distinct_ships=shipLimit`、`failed_windows=0`；
- 20/100/500 dry-run 的正式事件数严格为 0；
- 审计表 raw/pass/rejected 可查询；
- 搭靠的 baseline/filtered/saved 可查询，且 attempted=successful、failed=0、truncated=0；
- 日志没有 Replay 截断、WORLD_SEA 不可用或持续 HBase 超时。

当前审计表只持久化 strict 结果，没有独立的 Shadow 95% pass 字段；`storeDetails=false` 时也不能从事件详情重算。因此 Shadow 95% 必须标记为“尚无独立落库证据”，不得用 strict pass 替代。若 5000 正式任务验收要求该指标，必须先补充可审计的采集方式并重新走测试。

## 5. 当前已验证基线

```text
loitering 20: ships=20/20, windows=260, failed=0, candidate=197, formal=0
alongside 20: ships=20/20, windows=260, failed=0, candidate=5, formal=0
alongside Replay: baseline=1177, filtered=1165, saved=12,
                  attempted=1165, successful=1165, failed=0, truncated=0
```

100/500/5000 的结果必须在各自完成后用相同 SQL 留存，不能以本文件中的参数计划代替执行证据。
