-- 港口锚地几何修复回滚。必须在同一数据库会话逐段执行。
-- 仅当备份数量、run ID 和哈希均通过时继续；任何结果不符立即执行 ROLLBACK。
START TRANSACTION;

-- 预期 backup_rows=26、unique_ids=26、hash_mismatch=0。
SELECT COUNT(*) AS backup_rows,COUNT(DISTINCT id) AS unique_ids,
       SUM(original_wkb_sha256<>SHA2(AsBinary(bound),256)) AS hash_mismatch
FROM dataanalysis.loitering_scene_geometry_backup_20260810_port_anchor_v1
WHERE repair_run_id='PORT_ANCHOR_20260810_V1'
FOR UPDATE;

-- 恢复26条原始 WKB。
UPDATE dataanalysis.zones_with_port_id source
JOIN dataanalysis.loitering_scene_geometry_backup_20260810_port_anchor_v1 backup ON backup.id=source.id
SET source.bound=backup.bound
WHERE backup.repair_run_id='PORT_ANCHOR_20260810_V1';

-- 预期 restored_rows=26；不是26则立即 ROLLBACK。
SELECT ROW_COUNT() AS restored_rows;

-- 预期 restored_hash_mismatch=0；不是0则立即 ROLLBACK。
SELECT SUM(backup.original_wkb_sha256<>SHA2(AsBinary(source.bound),256)) AS restored_hash_mismatch
FROM dataanalysis.zones_with_port_id source
JOIN dataanalysis.loitering_scene_geometry_backup_20260810_port_anchor_v1 backup ON backup.id=source.id
WHERE backup.repair_run_id='PORT_ANCHOR_20260810_V1';

-- 人工确认以上三个预期值后，手工执行 COMMIT；否则手工执行 ROLLBACK。
-- COMMIT;
