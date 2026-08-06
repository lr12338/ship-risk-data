SELECT list_batch_no, COUNT(*) ships, COUNT(DISTINCT mmsi) distinct_mmsi,
       MIN(seq_no) min_seq, MAX(seq_no) max_seq
FROM dataanalysis.ship_behavior_target_ship
WHERE list_batch_no='liquid-container-5000-2025h2-2026h1-v1'
GROUP BY list_batch_no;

SELECT batch_no, behavior_type, COUNT(*) event_count, COUNT(DISTINCT mmsi) ship_count,
       MIN(start_time) first_event, MAX(end_time) last_event
FROM dataanalysis.ship_behavior_event
WHERE batch_no IN ('loitering-merchant5000-2025-v1','alongside-merchant5000-2025-v1')
GROUP BY batch_no, behavior_type;

SELECT batch_no, status, COUNT(*) windows
FROM dataanalysis.ship_behavior_loitering_batch_status
WHERE batch_no='loitering-merchant5000-2025-v1' GROUP BY batch_no,status;

SELECT batch_no, status, COUNT(*) windows
FROM dataanalysis.ship_behavior_alongside_batch_status
WHERE batch_no='alongside-merchant5000-2025-v1' GROUP BY batch_no,status;

SELECT batch_no,region_id,behavior_type,status,start_time,end_time,updated_at
FROM dataanalysis.ship_behavior_region_batch_status
WHERE batch_no='REPLACE_REGION_BATCH';

SELECT batch_no,region_id,behavior_type,COUNT(*) event_count,COUNT(DISTINCT mmsi) ship_count
FROM dataanalysis.ship_behavior_event
WHERE batch_no='REPLACE_REGION_BATCH' AND event_source='REGION'
GROUP BY batch_no,region_id,behavior_type;
