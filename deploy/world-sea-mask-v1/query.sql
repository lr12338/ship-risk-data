-- WORLD_SEA_MASK V1R1 发布身份。预期：formal=102，ACTIVE=1，fingerprint=31dd89bd...。
SELECT release_id,dataset_version,parent_dataset_version,geometry_fingerprint,
       review_status,authorization_mode,authorization_ref,status,last_completed_step,
       stage_table_name,formal_table_name,error_message,published_at,activated_at
FROM dataanalysis.ship_behavior_world_sea_release
WHERE dataset_version='WORLD_SEA_102_V1R1'
ORDER BY created_at DESC;

SELECT dataset_version,geometry_fingerprint,status,imported_at
FROM dataanalysis.ship_behavior_world_sea_meta
WHERE status='ACTIVE';

SELECT COUNT(*) AS formal_rows,
       COUNT(DISTINCT id) AS formal_distinct_ids,
       SUM(ST_IsValid(geometry)=0) AS invalid_geometry_count,
       SUM(ST_SRID(geometry)<>101) AS wrong_srid_count
FROM dataanalysis.ship_behavior_world_sea;

SELECT repair_release_id,feature_id,feature_name,feature_cn_name,
       original_geometry_type,repaired_geometry_type,
       original_vertex_count,repaired_vertex_count,
       original_component_count,repaired_component_count,relative_area_delta,
       collapsed_component_count,collapsed_component_type,collapsed_component_length,
       collapsed_component_decision,polygon_component_areas,minimum_polygon_area,
       main_polygon_area,minimum_polygon_ratio,repair_method,repair_tool_git_sha,
       review_status,review_comment,reviewed_at,reviewed_by,
       authorization_mode,authorization_ref,authorized_at
FROM dataanalysis.ship_behavior_world_sea_repair_audit
WHERE dataset_version_after='WORLD_SEA_102_V1R1'
ORDER BY feature_id;

-- 固定测试名单。预期 total=5000、enabled=5000、distinct_mmsi=5000。
SELECT COUNT(*) AS total_rows,
       SUM(enabled=1) AS enabled_rows,
       COUNT(DISTINCT CASE WHEN enabled=1 THEN mmsi END) AS distinct_mmsi
FROM dataanalysis.ship_behavior_target_ship
WHERE list_batch_no='liquid-container-5000-2025h2-2026h1-v1';

-- 每次验收只修改此变量，禁止修改已经运行批次的不可变参数。
SET @batch_no = 'loitering-merchant100-2025-worldsea-v1r1';

-- 批次不可变清单。20/100/500 应为 DRY_RUN；5000 正式任务才是 FORMAL_EVENT。
SELECT batch_no,behavior_type,list_batch_no,start_time,end_time,
       rule_version,write_mode,config_hash,created_at,updated_at
FROM dataanalysis.ship_behavior_run_batch
WHERE batch_no=@batch_no;

-- 徘徊进度：distinct_ships 必须等于 shipLimit，failed_windows 必须为 0。
SELECT COUNT(DISTINCT mmsi) AS distinct_ships,
       COUNT(*) AS windows,
       SUM(status='DONE') AS done_windows,
       SUM(status='FAILED') AS failed_windows,
       COALESCE(SUM(point_count),0) AS point_count,
       COALESCE(SUM(event_count),0) AS candidate_event_count,
       MIN(window_start_time) AS first_window,
       MAX(window_end_time) AS last_window,
       MAX(updated_at) AS last_update
FROM dataanalysis.ship_behavior_loitering_batch_status
WHERE batch_no=@batch_no;

-- 搭靠进度：distinct_ships 必须等于 shipLimit，failed_windows/replay_failures 必须为 0。
SELECT COUNT(DISTINCT mmsi) AS distinct_ships,
       COUNT(*) AS windows,
       SUM(status='DONE') AS done_windows,
       SUM(status='FAILED') AS failed_windows,
       COALESCE(SUM(point_count),0) AS point_count,
       COALESCE(SUM(candidate_target_count),0) AS candidate_targets,
       COALESCE(SUM(replay_query_count),0) AS replay_queries,
       COALESCE(SUM(replay_failure_count),0) AS replay_failures,
       COALESCE(SUM(event_count),0) AS candidate_event_count,
       MAX(updated_at) AS last_update
FROM dataanalysis.ship_behavior_alongside_batch_status
WHERE batch_no=@batch_no;

-- dry-run 必须为 0；正式 5000 批次记录实际写入数。
SELECT @batch_no AS batch_no,COUNT(*) AS formal_event_count
FROM dataanalysis.ship_behavior_event
WHERE batch_no=@batch_no;

-- WORLD_SEA strict 审计及 Replay 计划。failed/truncated 必须为 0。
SELECT batch_no,behavior_type,
       COUNT(DISTINCT mmsi) AS audited_ships,
       COUNT(*) AS audited_windows,
       SUM(raw_event_count) AS raw_events,
       SUM(world_sea_pass_event_count) AS strict_pass_events,
       SUM(world_sea_rejected_event_count) AS rejected_events,
       SUM(world_sea_checked_point_count) AS checked_points,
       SUM(world_sea_rejected_window_count) AS rejected_windows,
       SUM(baseline_planned_replay_query_count) AS baseline_replay,
       SUM(filtered_planned_replay_query_count) AS filtered_replay,
       SUM(saved_replay_query_count) AS saved_replay,
       SUM(attempted_replay_query_count) AS attempted_replay,
       SUM(successful_replay_query_count) AS successful_replay,
       SUM(failed_replay_query_count) AS failed_replay,
       SUM(truncated_replay_query_count) AS truncated_replay,
       MAX(updated_at) AS last_update
FROM dataanalysis.ship_behavior_world_sea_audit
WHERE batch_no=@batch_no
GROUP BY batch_no,behavior_type;

-- 已完成 20 船基线复核。
SELECT batch_no,behavior_type,
       SUM(raw_event_count) AS raw_events,
       SUM(world_sea_pass_event_count) AS strict_pass_events,
       SUM(world_sea_rejected_event_count) AS rejected_events,
       SUM(baseline_planned_replay_query_count) AS baseline_replay,
       SUM(filtered_planned_replay_query_count) AS filtered_replay,
       SUM(saved_replay_query_count) AS saved_replay,
       SUM(attempted_replay_query_count) AS attempted_replay,
       SUM(successful_replay_query_count) AS successful_replay,
       SUM(failed_replay_query_count) AS failed_replay,
       SUM(truncated_replay_query_count) AS truncated_replay
FROM dataanalysis.ship_behavior_world_sea_audit
WHERE batch_no IN ('loitering-merchant20-2025-worldsea-v1r1-smoke',
                   'alongside-merchant20-2025-worldsea-v1r1-smoke')
GROUP BY batch_no,behavior_type;

-- 注意：当前表没有独立 shadow 95% 字段，不能把 strict_pass_events 当作 shadow 指标。
