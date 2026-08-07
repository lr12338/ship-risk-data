-- Replace :batch_no with one of the new dry-run batch names. This is read-only.
SELECT behavior_type, JSON_UNQUOTE(JSON_EXTRACT(score_detail_json,'$.sceneType')) AS scene_type,
       COUNT(*) AS event_count
FROM dataanalysis.ship_behavior_event
WHERE batch_no=:batch_no AND behavior_type IN ('LOITERING','ALONGSIDE')
GROUP BY behavior_type, JSON_UNQUOTE(JSON_EXTRACT(score_detail_json,'$.sceneType'));

SELECT behavior_type, JSON_UNQUOTE(JSON_EXTRACT(score_detail_json,'$.distanceBucket')) AS distance_bucket,
       COUNT(*) AS event_count
FROM dataanalysis.ship_behavior_event
WHERE batch_no=:batch_no AND behavior_type IN ('LOITERING','ALONGSIDE')
GROUP BY behavior_type, JSON_UNQUOTE(JSON_EXTRACT(score_detail_json,'$.distanceBucket'));

SELECT behavior_type, event_sub_type, exclusion_flag, COUNT(*) AS event_count
FROM dataanalysis.ship_behavior_event
WHERE batch_no=:batch_no AND behavior_type IN ('LOITERING','ALONGSIDE')
GROUP BY behavior_type, event_sub_type, exclusion_flag;
