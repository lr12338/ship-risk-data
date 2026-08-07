-- Replace :batch_no after manually running one of the dry-run templates.
SELECT status, COUNT(*) AS windows FROM dataanalysis.ship_behavior_loitering_batch_status WHERE batch_no=:batch_no GROUP BY status;
SELECT COUNT(DISTINCT mmsi) AS shipsDone FROM dataanalysis.ship_behavior_loitering_batch_status WHERE batch_no=:batch_no AND status='DONE';
SELECT COUNT(DISTINCT mmsi) AS shipsFailed FROM dataanalysis.ship_behavior_loitering_batch_status WHERE batch_no=:batch_no AND status='FAILED';
SELECT COUNT(*) AS windowsDone FROM dataanalysis.ship_behavior_loitering_batch_status WHERE batch_no=:batch_no AND status='DONE';
SELECT COUNT(*) AS windowsFailed FROM dataanalysis.ship_behavior_loitering_batch_status WHERE batch_no=:batch_no AND status='FAILED';

-- Formal-event verification only (dry runs intentionally write no events).
SELECT event_sub_type, COUNT(*) AS candidateEvents,
       SUM(JSON_EXTRACT(score_detail_json,'$.loiteringSceneDecision')='PASS') AS scenePassCount,
       SUM(JSON_EXTRACT(score_detail_json,'$.loiteringSceneDecision')='REJECTED') AS sceneRejectedCount,
       AVG(JSON_EXTRACT(score_detail_json,'$.minSog')) AS avgMinSog,
       AVG(JSON_EXTRACT(score_detail_json,'$.avgSog')) AS avgSog,
       AVG(JSON_EXTRACT(score_detail_json,'$.medianSog')) AS avgMedianSog,
       AVG(JSON_EXTRACT(score_detail_json,'$.distanceToNearestPortNm')) AS avgDistanceToPortNm
FROM dataanalysis.ship_behavior_event
WHERE batch_no=:batch_no AND behavior_type='LOITERING' AND rule_version='LOITERING_V2_PORT_APPROACH_V1'
GROUP BY event_sub_type;

SELECT CASE
         WHEN JSON_EXTRACT(score_detail_json,'$.distanceToNearestPortNm') < 2 THEN '<2nm'
         WHEN JSON_EXTRACT(score_detail_json,'$.distanceToNearestPortNm') < 8 THEN '2-8nm'
         WHEN JSON_EXTRACT(score_detail_json,'$.distanceToNearestPortNm') <= 10 THEN '8-10nm'
         ELSE '>10nm' END AS distanceBucket, COUNT(*) AS events
FROM dataanalysis.ship_behavior_event
WHERE batch_no=:batch_no AND behavior_type='LOITERING' AND rule_version='LOITERING_V2_PORT_APPROACH_V1'
GROUP BY distanceBucket;

-- Dry-run counters are emitted in XXL logs as LOITERING_PORT_SCENE audit:
-- rawCandidateCount, scenePassCount, sceneRejectedCount, belowMinSpeedSplitCount,
-- portOperationalRejectedCount, anchorRejectedCount, tooFarFromPortRejectedCount.
-- WORLD_SEA pass/rejected counters are in ship_behavior_world_sea_audit:
SELECT SUM(raw_event_count) rawCandidateCount, SUM(world_sea_pass_event_count) worldSeaPassCount,
       SUM(world_sea_rejected_event_count) worldSeaRejectedCount
FROM dataanalysis.ship_behavior_world_sea_audit WHERE batch_no=:batch_no AND behavior_type='LOITERING';
