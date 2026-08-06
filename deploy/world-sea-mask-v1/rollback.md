# WORLD_SEA_MASK V1R1 回滚

先停止新的 WORLD_SEA 批次调度并运行只读 `STATE`。不要手工 DROP 或 RENAME formal、stage、backup、failed 表；数据库恢复只使用 `ROLLBACK_RELEASE`。

```bash
stage=ship_behavior_world_sea_stage_20260806_175527
java -Dloader.main=com.hifleet.worldsea.WorldSeaImportTool \
  -cp /data/zhengyi/ship-risk-data-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.PropertiesLauncher STATE "$stage"

java -Dloader.main=com.hifleet.worldsea.WorldSeaImportTool \
  -cp /data/zhengyi/ship-risk-data-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.PropertiesLauncher ROLLBACK_RELEASE "$stage"
```

- 有 backup：原子 RENAME 隔离当前 formal 并恢复 backup。
- 首次发布无 backup：formal 改名为 failed，不删除；应用继续 fail-closed。
- RENAME 后中断：再次 `STATE`，再重跑 `ROLLBACK_RELEASE` 补齐元数据。

JAR/配置回滚必须人工执行：

```bash
cd /data/zhengyi
./stop.sh
cp "backups/<release_stamp>/ship-risk-data-0.0.1-SNAPSHOT.jar" ./ship-risk-data-0.0.1-SNAPSHOT.jar
cp "backups/<release_stamp>/application.properties" ./application.properties
./start.sh
./status.sh
```

回滚后重新运行 WORLD_SEA preflight。旧 JAR `9e2f865d…` 标记为 `SUPERSEDED_NOT_FOR_WORLD_SEA_102_V1R1`，不得用于本数据版本。
