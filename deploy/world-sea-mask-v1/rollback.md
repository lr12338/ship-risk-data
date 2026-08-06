# WORLD_SEA_MASK V1 回滚

停止应用前确认没有 WORLD_SEA 新批次正在运行。旧 JAR 不得继续使用新批次号。

数据库有备份表时执行一次原子切换；将占位符替换为实际时间戳：

```sql
RENAME TABLE
dataanalysis.ship_behavior_world_sea TO dataanalysis.ship_behavior_world_sea_failed_<timestamp>,
dataanalysis.ship_behavior_world_sea_backup_<timestamp> TO dataanalysis.ship_behavior_world_sea;
```

不要删除 failed、backup 或 stage 表。JAR 回滚：

```bash
cd /data/zhengyi
./stop.sh
cp "backups/<release_stamp>/ship-risk-data-0.0.1-SNAPSHOT.jar" ./ship-risk-data-0.0.1-SNAPSHOT.jar
cp "backups/<release_stamp>/application.properties" ./application.properties
./start.sh
./status.sh
```
