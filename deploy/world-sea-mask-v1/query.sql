SELECT release_id,dataset_version,parent_dataset_version,source_file_sha256,
       original_stage_fingerprint,geometry_fingerprint,repair_tool_git_sha,repair_release_id,
       review_status,authorization_mode,authorization_ref,authorized_at,
       stage_table_name,formal_table_name,backup_table_name,failed_table_name,
       publish_mode,status,last_completed_step,error_message,created_at,updated_at,
       published_at,activated_at,rolled_back_at
FROM dataanalysis.ship_behavior_world_sea_release
ORDER BY created_at DESC;

SELECT dataset_version,geometry_fingerprint,status,imported_at
FROM dataanalysis.ship_behavior_world_sea_meta
ORDER BY imported_at DESC;

SELECT repair_release_id,repaired_stage_table,feature_id,feature_name,feature_cn_name,original_geometry_type,
       repaired_geometry_type,original_vertex_count,repaired_vertex_count,
       original_component_count,repaired_component_count,relative_area_delta,
       collapsed_component_count,collapsed_component_type,collapsed_component_length,
       collapsed_component_decision,polygon_component_areas,minimum_polygon_area,
       main_polygon_area,minimum_polygon_ratio,repair_method,repair_tool_git_sha,
       review_status,review_comment,reviewed_at,reviewed_by,
       authorization_mode,authorization_ref,authorized_at
FROM dataanalysis.ship_behavior_world_sea_repair_audit
WHERE dataset_version_after='WORLD_SEA_102_V1R1'
ORDER BY feature_id;

SELECT batch_no,behavior_type,SUM(raw_event_count) AS raw_events,
       SUM(world_sea_pass_event_count) AS pass_events,
       SUM(world_sea_rejected_event_count) AS rejected_events,
       SUM(baseline_planned_replay_query_count) AS baseline_replay,
       SUM(filtered_planned_replay_query_count) AS filtered_replay,
       SUM(saved_replay_query_count) AS saved_replay,
       SUM(truncated_replay_query_count) AS truncated_replay
FROM dataanalysis.ship_behavior_world_sea_audit
WHERE batch_no IN ('loitering-merchant20-2025-worldsea-v1r1-smoke',
                   'alongside-merchant20-2025-worldsea-v1r1-smoke')
GROUP BY batch_no,behavior_type;

SELECT behavior_type,batch_no,COUNT(*) AS formal_event_count
FROM dataanalysis.ship_behavior_event
WHERE batch_no IN ('loitering-merchant20-2025-worldsea-v1r1-smoke',
                   'alongside-merchant20-2025-worldsea-v1r1-smoke')
GROUP BY behavior_type,batch_no;
