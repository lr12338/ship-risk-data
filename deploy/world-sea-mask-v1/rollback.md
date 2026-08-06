# WORLD_SEA_MASK V1 回滚

不要手工 DROP 正式表、stage、backup 或 failed 表。先停止新的 WORLD_SEA 批次调度并运行只读 `STATE`。

获批后使用固定 stage 名运行 `ROLLBACK_RELEASE`。工具只允许 `PUBLISHED_INACTIVE`、`ACTIVE`、`ROLLBACK_REQUIRED`：

- 有 backup：一次原子 RENAME，把当前 formal 隔离为 failed，并恢复 backup；
- 首次发布无 backup：只把 formal 改名为 failed，不 DROP，应用继续 fail-closed；
- RENAME 后进程中断：重复执行会识别 failed/formal/backup 现实，只补齐元数据回滚。

JAR 回滚仍须人工执行：

```bash
cd /data/zhengyi
./stop.sh
cp "backups/<release_stamp>/ship-risk-data-0.0.1-SNAPSHOT.jar" ./ship-risk-data-0.0.1-SNAPSHOT.jar
cp "backups/<release_stamp>/application.properties" ./application.properties
./start.sh
./status.sh
```

旧 JAR 不得运行新的 WORLD_SEA batchNo。
