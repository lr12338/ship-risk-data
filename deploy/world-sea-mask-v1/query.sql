SELECT release_id,dataset_version,geometry_fingerprint,stage_table_name,formal_table_name,
       backup_table_name,failed_table_name,publish_mode,status,last_completed_step,error_message,
       created_at,updated_at,published_at,activated_at,rolled_back_at
FROM dataanalysis.ship_behavior_world_sea_release
ORDER BY created_at DESC;

SELECT dataset_version,geometry_fingerprint,status,imported_at
FROM dataanalysis.ship_behavior_world_sea_meta
ORDER BY imported_at DESC;

SELECT batch_no,mmsi,behavior_type,window_start_time,window_end_time,
       raw_event_count,world_sea_pass_event_count,world_sea_rejected_event_count,
       world_sea_checked_point_count,world_sea_rejected_window_count,
       baseline_planned_replay_query_count,filtered_planned_replay_query_count,
       saved_replay_query_count,attempted_replay_query_count,
       successful_replay_query_count,failed_replay_query_count,truncated_replay_query_count
FROM dataanalysis.ship_behavior_world_sea_audit
WHERE batch_no=? AND mmsi=? AND behavior_type=?
ORDER BY window_start_time;

SELECT behavior_type,batch_no,COUNT(*) AS final_event_count
FROM dataanalysis.ship_behavior_event
WHERE batch_no IN ('loitering-merchant20-2025-worldsea-smoke-v1',
                   'alongside-merchant20-2025-worldsea-smoke-v1')
GROUP BY behavior_type,batch_no;
