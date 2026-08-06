SELECT batch_no,mmsi,behavior_type,window_start_time,window_end_time,
       raw_event_count,world_sea_pass_event_count,world_sea_rejected_event_count,
       world_sea_checked_point_count,world_sea_rejected_window_count,saved_replay_query_count
FROM dataanalysis.ship_behavior_world_sea_audit
WHERE batch_no=? AND mmsi=? AND behavior_type=?
ORDER BY window_start_time;

SELECT behavior_type,batch_no,COUNT(*) AS final_event_count
FROM dataanalysis.ship_behavior_event
WHERE batch_no IN ('loitering-merchant20-2025-worldsea-smoke-v1',
                   'alongside-merchant20-2025-worldsea-smoke-v1')
GROUP BY behavior_type,batch_no;

SELECT ID,NAME,CNNAME,GeometryType(the_geom) AS geometry_type
FROM dataanalysis.ship_behavior_world_sea ORDER BY ID;
