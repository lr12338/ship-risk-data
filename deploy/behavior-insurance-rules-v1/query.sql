-- 行为保险规则 V1 只读验收 SQL。
-- 每次只修改 @batch_no；不要修改已经创建的批次不可变参数。
SET @batch_no = 'loitering-merchant20-2025-insurance-scene-v1';

-- 固定名单预检：预期 total_rows=5000、enabled_rows=5000、distinct_mmsi=5000。
SELECT COUNT(*) AS total_rows,
       SUM(enabled=1) AS enabled_rows,
       COUNT(DISTINCT CASE WHEN enabled=1 THEN mmsi END) AS distinct_mmsi
FROM dataanalysis.ship_behavior_target_ship
WHERE list_batch_no='liquid-container-5000-2025h2-2026h1-v1';

-- 批次不可变清单：20/100/500 必须是 DRY_RUN；5000 必须是 FORMAL_EVENT。
SELECT batch_no,behavior_type,list_batch_no,start_time,end_time,rule_version,write_mode,config_hash,created_at,updated_at
FROM dataanalysis.ship_behavior_run_batch
WHERE batch_no=@batch_no;

-- 徘徊窗口进度：dry-run 候选数来自 event_count；failed_windows 必须为 0。
SELECT COUNT(DISTINCT mmsi) AS distinct_ships,COUNT(*) AS windows,
       SUM(status='DONE') AS done_windows,SUM(status='FAILED') AS failed_windows,
       COALESCE(SUM(point_count),0) AS point_count,COALESCE(SUM(event_count),0) AS candidate_event_count,
       MIN(window_start_time) AS first_window,MAX(window_end_time) AS last_window,MAX(updated_at) AS last_update
FROM dataanalysis.ship_behavior_loitering_batch_status
WHERE batch_no=@batch_no;

-- 徘徊失败窗口：无结果即通过；error_message 用于定位可恢复失败。
SELECT mmsi,window_start_time,window_end_time,error_message,updated_at
FROM dataanalysis.ship_behavior_loitering_batch_status
WHERE batch_no=@batch_no AND status='FAILED'
ORDER BY updated_at DESC;

-- 搭靠窗口进度：replay_failures 与 failed_windows 必须均为 0。
SELECT COUNT(DISTINCT mmsi) AS distinct_ships,COUNT(*) AS windows,
       SUM(status='DONE') AS done_windows,SUM(status='FAILED') AS failed_windows,
       COALESCE(SUM(point_count),0) AS point_count,COALESCE(SUM(candidate_target_count),0) AS candidate_targets,
       COALESCE(SUM(replay_query_count),0) AS replay_queries,COALESCE(SUM(replay_failure_count),0) AS replay_failures,
       COALESCE(SUM(event_count),0) AS candidate_event_count,MAX(updated_at) AS last_update
FROM dataanalysis.ship_behavior_alongside_batch_status
WHERE batch_no=@batch_no;

-- 搭靠失败窗口：无结果即通过。
SELECT mmsi,window_start_time,window_end_time,error_message,updated_at
FROM dataanalysis.ship_behavior_alongside_batch_status
WHERE batch_no=@batch_no AND status='FAILED'
ORDER BY updated_at DESC;

-- 正式事件数：20/100/500 dry-run 必须为 0；5000 正式批次记录实际写入事件。
SELECT @batch_no AS batch_no,COUNT(*) AS formal_event_count
FROM dataanalysis.ship_behavior_event
WHERE batch_no=@batch_no;

-- WORLD_SEA 审计：failed/truncated replay 必须为 0；用于确认 pair/event 未被非法降级。
SELECT behavior_type,COUNT(DISTINCT mmsi) AS audited_ships,COUNT(*) AS audited_windows,
       SUM(raw_event_count) AS raw_events,SUM(world_sea_pass_event_count) AS strict_pass_events,
       SUM(world_sea_rejected_event_count) AS rejected_events,SUM(failed_replay_query_count) AS failed_replay,
       SUM(truncated_replay_query_count) AS truncated_replay,MAX(updated_at) AS last_update
FROM dataanalysis.ship_behavior_world_sea_audit
WHERE batch_no=@batch_no
GROUP BY behavior_type;

-- 正式 5000 事件的场景分布：PORT_APPROACH 与 OFFSHORE 都应可见；港区/锚地搭靠应为排除。
SELECT behavior_type,JSON_UNQUOTE(JSON_EXTRACT(score_detail_json,'$.sceneType')) AS scene_type,
       exclusion_flag,COUNT(*) AS event_count
FROM dataanalysis.ship_behavior_event
WHERE batch_no=@batch_no AND behavior_type IN ('LOITERING','ALONGSIDE')
GROUP BY behavior_type,JSON_UNQUOTE(JSON_EXTRACT(score_detail_json,'$.sceneType')),exclusion_flag;

-- 正式 5000 事件的距离桶和最近港口边界距离：用于检查 Port Approach 与 Offshore 分类。
SELECT behavior_type,JSON_UNQUOTE(JSON_EXTRACT(score_detail_json,'$.distanceBucket')) AS distance_bucket,
       COUNT(*) AS event_count,AVG(JSON_EXTRACT(score_detail_json,'$.distanceToNearestPortNm')) AS avg_boundary_distance_nm
FROM dataanalysis.ship_behavior_event
WHERE batch_no=@batch_no AND behavior_type IN ('LOITERING','ALONGSIDE')
GROUP BY behavior_type,JSON_UNQUOTE(JSON_EXTRACT(score_detail_json,'$.distanceBucket'));

-- Loitering 行为模式与低速证据：正式事件 below_min_speed_evidence_count 必须为 0。
SELECT JSON_UNQUOTE(JSON_EXTRACT(score_detail_json,'$.behaviorPattern')) AS behavior_pattern,
       JSON_UNQUOTE(JSON_EXTRACT(score_detail_json,'$.sceneType')) AS scene_type,
       SUM(JSON_EXTRACT(score_detail_json,'$.belowMinSpeedEvidenceCount')) AS below_min_speed_evidence_count,
       COUNT(*) AS event_count
FROM dataanalysis.ship_behavior_event
WHERE batch_no=@batch_no AND behavior_type='LOITERING'
GROUP BY JSON_UNQUOTE(JSON_EXTRACT(score_detail_json,'$.behaviorPattern')),
         JSON_UNQUOTE(JSON_EXTRACT(score_detail_json,'$.sceneType'));

-- Alongside 目标角色、行为模式与风险解释：仅 review/indicator，不代表违法确认。
SELECT JSON_UNQUOTE(JSON_EXTRACT(score_detail_json,'$.targetVesselRole')) AS target_role,
       JSON_UNQUOTE(JSON_EXTRACT(score_detail_json,'$.behaviorMode')) AS behavior_mode,
       JSON_UNQUOTE(JSON_EXTRACT(score_detail_json,'$.riskInterpretation')) AS risk_interpretation,
       COUNT(*) AS event_count
FROM dataanalysis.ship_behavior_event
WHERE batch_no=@batch_no AND behavior_type='ALONGSIDE'
GROUP BY JSON_UNQUOTE(JSON_EXTRACT(score_detail_json,'$.targetVesselRole')),
         JSON_UNQUOTE(JSON_EXTRACT(score_detail_json,'$.behaviorMode')),
         JSON_UNQUOTE(JSON_EXTRACT(score_detail_json,'$.riskInterpretation'));
