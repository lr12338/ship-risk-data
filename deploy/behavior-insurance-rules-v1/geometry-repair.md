# 港口锚地自相交几何修复

本流程只处理 `dataanalysis.zones_with_port_id` 中已审计的 26 条 `zones_type=1` 锚地记录。应用加载港口参考边界时会另行排除锚地；源数据修复用于恢复空间数据质量，不得扩大 ID 范围。

## 1. 前置条件

1. 暂停 Loitering、Alongside 的 XXL-Job 调度，确认没有正在执行的窗口。
2. 使用本次构建并校验过 SHA-256 的同一 JAR 执行 PREVIEW 和 APPLY。
3. `/data/zhengyi/application.properties` 必须保留 `spring.datasource.newdataanalysis.*`；命令和日志不得显示密码。
4. 当前预期：MySQL `5.7.44-log`、26 条均为 `zones_type=1`、SRID 101、Polygon，错误为 Self-intersection/Ring Self-intersection。

固定 ID：

```text
312,393,513,709,844,857,1650,1872,1885,1941,2373,2392,2397,2868,3011,3108,3298,3300,3303,3537,8861,10892,12711,12718,12719,13316
```

## 2. 只读 PREVIEW

在 `/data/zhengyi` 执行一整行命令：

```bash
java -Dloader.main=com.hifleet.loitering.LoiteringPortGeometryRepairTool -cp ship-risk-data-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.PropertiesLauncher --mode=preview --config=/data/zhengyi/application.properties | tee logs/port-anchor-repair-preview-20260810.log
```

必须看到：

- 26 行 `PORT_ANCHOR_REPAIR id=...`；
- 每条 repaired geometry 有效、包络差异不超过 `1e-12`；
- `3298、3300、3303` 为 `LARGE_AREA_DELTA_ACCEPTED`；
- 结尾为 `PORT_ANCHOR_REPAIR PREVIEW PASS rows=26 ... databaseWrites=0`。

从最后一行复制 `sourceFingerprint`。PREVIEW 不创建备份表、不更新源表。

## 3. 受控 APPLY

将 `<PREVIEW_SOURCE_FINGERPRINT>` 替换为刚才得到的 64 位小写 SHA-256，并保持命令为一整行：

```bash
java -Dloader.main=com.hifleet.loitering.LoiteringPortGeometryRepairTool -cp ship-risk-data-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.PropertiesLauncher --mode=apply --config=/data/zhengyi/application.properties --expected-source-fingerprint=<PREVIEW_SOURCE_FINGERPRINT> --confirm=PORT_ANCHOR_20260810_V1 | tee logs/port-anchor-repair-apply-20260810.log
```

APPLY 会执行以下保护：

- 建立 `dataanalysis.loitering_scene_geometry_backup_20260810_port_anchor_v1`，保存完整原记录、原 WKB SHA-256、时间、方法和 run ID；
- `SELECT ... FOR UPDATE` 后再次校验 26 条范围和 source fingerprint；
- 使用 JTS `GeometryFixer 1.18.2` 和 SRID 101 写回；
- 回读比较精确 WKB、有效性及 repaired fingerprint；
- 只有恰好 26 条全部通过才提交，否则整批回滚。

成功结尾必须为 `PORT_ANCHOR_REPAIR APPLY PASS rows=26 ... databaseWrites=26`。不要在失败后改 fingerprint 或 ID 重试。

## 4. 验收与回滚

执行 [query.sql](query.sql) 的“场景与几何修复验收”部分。预期备份 26 条、源表 26 条 SHA 均已变化、港口参考集合 9,251 条且锚地混入为 0。

如修复影响其他系统，按 [geometry-repair-rollback.sql](geometry-repair-rollback.sql) 在同一数据库会话恢复原 WKB。完整发布回滚必须同时恢复旧 JAR；回滚后旧版本仍会重新遇到这 26 条无效锚地，因此不得继续触发行为任务。
