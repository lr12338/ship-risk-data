-- 在事件数据库 dataanalysis 执行。先替换批次号，再运行各段查询。
SELECT DATABASE();
SET @shadow_batch_no = 'maritime-scene-shadow-REPLACE_ME';

-- 1. Shadow 总量
SELECT COUNT(*) AS shadow_count
FROM dataanalysis.ship_behavior_maritime_scene_audit
WHERE batch_no = @shadow_batch_no;

-- 2. 场景与置信度分布
SELECT dominant_scene, scene_confidence, COUNT(*) AS row_count
FROM dataanalysis.ship_behavior_maritime_scene_audit
WHERE batch_no = @shadow_batch_no
GROUP BY dominant_scene, scene_confidence
ORDER BY row_count DESC, dominant_scene, scene_confidence;

-- 3. Shadow 决策分布
SELECT shadow_decision, COUNT(*) AS row_count
FROM dataanalysis.ship_behavior_maritime_scene_audit
WHERE batch_no = @shadow_batch_no
GROUP BY shadow_decision
ORDER BY row_count DESC, shadow_decision;

-- 4. UNKNOWN / CONFLICT
SELECT AVG(unknown_point_ratio) AS avg_unknown_point_ratio,
       AVG(conflict_point_ratio) AS avg_conflict_point_ratio,
       SUM(CASE WHEN shadow_decision = 'SCENE_UNKNOWN' THEN 1 ELSE 0 END) AS scene_unknown_count,
       SUM(CASE WHEN shadow_decision = 'SCENE_CONFLICT' THEN 1 ELSE 0 END) AS scene_conflict_count
FROM dataanalysis.ship_behavior_maritime_scene_audit
WHERE batch_no = @shadow_batch_no;

-- 5. 海岸距离：min/p50/max 列概览、NULL 数和 p50 分布
SELECT MIN(coast_distance_min_nm) AS min_nm,
       AVG(coast_distance_p50_nm) AS avg_episode_p50_nm,
       MAX(coast_distance_max_nm) AS max_nm,
       SUM(coast_distance_min_nm IS NULL) AS min_null_count,
       SUM(coast_distance_p50_nm IS NULL) AS p50_null_count,
       SUM(coast_distance_max_nm IS NULL) AS max_null_count
FROM dataanalysis.ship_behavior_maritime_scene_audit
WHERE batch_no = @shadow_batch_no;

SELECT CASE
           WHEN coast_distance_p50_nm IS NULL THEN 'UNAVAILABLE'
           WHEN coast_distance_p50_nm < 1 THEN '[0,1)'
           WHEN coast_distance_p50_nm < 5 THEN '[1,5)'
           WHEN coast_distance_p50_nm < 12 THEN '[5,12)'
           WHEN coast_distance_p50_nm < 50 THEN '[12,50)'
           ELSE '[50,+inf)'
       END AS p50_nm_band,
       COUNT(*) AS row_count
FROM dataanalysis.ship_behavior_maritime_scene_audit
WHERE batch_no = @shadow_batch_no
GROUP BY p50_nm_band
ORDER BY MIN(COALESCE(coast_distance_p50_nm, 999999));

-- 6. 幂等：audit_key 是主键；结果必须为 0 行
SELECT audit_key, COUNT(*) AS duplicate_count
FROM dataanalysis.ship_behavior_maritime_scene_audit
WHERE batch_no = @shadow_batch_no
GROUP BY audit_key
HAVING COUNT(*) > 1;

-- 7. 正式事件零变化快照。Shadow 前后各执行一次并逐列比较。
SELECT COUNT(*) AS formal_event_count,
       MAX(id) AS formal_event_max_id,
       MAX(created_at) AS formal_event_max_created_at
FROM dataanalysis.ship_behavior_event;

SELECT COUNT(*) AS formal_detail_count,
       MAX(id) AS formal_detail_max_id,
       MAX(created_at) AS formal_detail_max_created_at
FROM dataanalysis.ship_behavior_event_detail;
