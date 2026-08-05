-- 0731 液货船/集装箱船5,000艘年度范围任务只读验收 SQL，适用于 MySQL 5.7。
SET @fingerprint=
  '7470be70e955bc79064bc402586c592a8a8da7a241e00c61997b1fa1bc0df50f';
SET @alongside_pattern='alongside-lc-s5000-%-v3';
SET @loitering_pattern='loitering-lc-s5000-%-v3';

-- 1. 搭靠月度进度：年度结束时不应出现 FAILED。
SELECT batch_no,status,
       COUNT(*) windows,
       COUNT(DISTINCT mmsi) ships,
       COALESCE(SUM(candidate_target_count),0) candidates,
       COALESCE(SUM(event_count),0) status_events,
       MAX(updated_at) last_update
FROM dataanalysis.ship_behavior_alongside_batch_status
WHERE batch_no LIKE @alongside_pattern
GROUP BY batch_no,status
ORDER BY batch_no,status;

-- 2. 徘徊月度进度。
SELECT batch_no,status,result_code,
       COUNT(*) windows,
       COUNT(DISTINCT mmsi) ships,
       COALESCE(SUM(candidate_count),0) candidates,
       COALESCE(SUM(valid_count),0) detected,
       COALESCE(SUM(stored_event_count),0) status_events,
       MAX(updated_at) last_update
FROM dataanalysis.ship_behavior_loitering_batch_status
WHERE batch_no LIKE @loitering_pattern
GROUP BY batch_no,status,result_code
ORDER BY batch_no,status,result_code;

-- 当前失败窗口详情。任务运行中可用于判断是否需要原参数续跑。
SELECT batch_no,mmsi,window_start_time,window_end_time,
       error_message,updated_at
FROM dataanalysis.ship_behavior_alongside_batch_status
WHERE batch_no LIKE @alongside_pattern
  AND status='FAILED'
ORDER BY updated_at DESC
LIMIT 100;

SELECT batch_no,mmsi,window_start_time,window_end_time,
       result_code,error_message,updated_at
FROM dataanalysis.ship_behavior_loitering_batch_status
WHERE batch_no LIKE @loitering_pattern
  AND status='FAILED'
ORDER BY updated_at DESC
LIMIT 100;

-- 3. 正式事件分布。
SELECT batch_no,behavior_type,rule_version,
       COUNT(*) event_rows,
       COUNT(DISTINCT mmsi) ships,
       MIN(start_time) first_event,
       MAX(end_time) last_event
FROM dataanalysis.ship_behavior_event
WHERE batch_no LIKE @alongside_pattern
   OR batch_no LIKE @loitering_pattern
GROUP BY batch_no,behavior_type,rule_version
ORDER BY batch_no;

-- 4. 正式事件硬门禁：必须为0。
SELECT COUNT(*) invalid_formal_events
FROM dataanalysis.ship_behavior_event
WHERE (
    batch_no LIKE @alongside_pattern
    OR batch_no LIKE @loitering_pattern
  )
  AND (
    behavior_type NOT IN ('ALONGSIDE','LOITERING')
    OR rule_version NOT IN (
      'ALONGSIDE_LIQUID_CONTAINER_V2',
      'LOITERING_LIQUID_CONTAINER_V2'
    )
    OR exclusion_flag<>'VALID_RISK_EVENT'
    OR exclusion_reason<>'OUTSIDE_PORT_ANCHORAGE_BERTH'
    OR own_ship_type NOT IN (
      'TANKER_OIL','TANKER_CHEMICAL','TANKER_GAS',
      'TANKER_UNSPECIFIED','CONTAINER'
    )
    OR near_port IS NULL OR near_port<>0
    OR near_anchor IS NULL OR near_anchor<>0
    OR open_sea IS NOT NULL
    OR JSON_VALID(score_detail_json)=0
    OR (
      behavior_type='ALONGSIDE'
      AND (
        target_mmsi IS NULL
        OR target_ship_type NOT IN (
          'TANKER_OIL','TANKER_CHEMICAL','TANKER_GAS',
          'TANKER_UNSPECIFIED','CONTAINER'
        )
      )
    )
    OR (
      behavior_type='LOITERING'
      AND (target_mmsi IS NOT NULL OR target_ship_type IS NOT NULL)
    )
    OR COALESCE(JSON_UNQUOTE(JSON_EXTRACT(
       score_detail_json,
       '$.ruleFeatures.exclusionEvidence.portHit')),'')<>'false'
    OR COALESCE(JSON_UNQUOTE(JSON_EXTRACT(
       score_detail_json,
       '$.ruleFeatures.exclusionEvidence.anchorageHit')),'')<>'false'
    OR COALESCE(JSON_UNQUOTE(JSON_EXTRACT(
       score_detail_json,
       '$.ruleFeatures.exclusionEvidence.berthHit')),'')<>'false'
    OR COALESCE(JSON_UNQUOTE(JSON_EXTRACT(
       score_detail_json,
       '$.ruleFeatures.exclusionEvidence.fingerprint')),'')<>@fingerprint
  );

-- 5. 状态表与事件表逐批次对账。
SELECT s.batch_no,s.status_events,COALESCE(e.event_rows,0) event_rows
FROM (
  SELECT batch_no,COALESCE(SUM(event_count),0) status_events
  FROM dataanalysis.ship_behavior_alongside_batch_status
  WHERE batch_no LIKE @alongside_pattern
    AND status='DONE'
  GROUP BY batch_no
) s
LEFT JOIN (
  SELECT batch_no,COUNT(*) event_rows
  FROM dataanalysis.ship_behavior_event
  WHERE behavior_type='ALONGSIDE'
    AND batch_no LIKE @alongside_pattern
  GROUP BY batch_no
) e ON e.batch_no=s.batch_no
WHERE s.status_events<>COALESCE(e.event_rows,0);

SELECT s.batch_no,s.status_events,COALESCE(e.event_rows,0) event_rows
FROM (
  SELECT batch_no,COALESCE(SUM(stored_event_count),0) status_events
  FROM dataanalysis.ship_behavior_loitering_batch_status
  WHERE batch_no LIKE @loitering_pattern
    AND status='DONE'
  GROUP BY batch_no
) s
LEFT JOIN (
  SELECT batch_no,COUNT(*) event_rows
  FROM dataanalysis.ship_behavior_event
  WHERE behavior_type='LOITERING'
    AND batch_no LIKE @loitering_pattern
  GROUP BY batch_no
) e ON e.batch_no=s.batch_no
WHERE s.status_events<>COALESCE(e.event_rows,0);

-- 6. 降级候选：AVAILABLE 且未命中隔离包络时应为空。
SELECT batch_no,behavior_type,exclusion_health,degradation_reason,
       review_status,COUNT(*) candidate_rows
FROM dataanalysis.ship_behavior_event_degraded_candidate
WHERE batch_no LIKE @alongside_pattern
   OR batch_no LIKE @loitering_pattern
GROUP BY batch_no,behavior_type,exclusion_health,
         degradation_reason,review_status
ORDER BY batch_no;

-- 7. 正式事件幂等：结果必须为空。
SELECT batch_no,rule_version,behavior_type,mmsi,
       COALESCE(target_mmsi,'') target_mmsi,
       start_time,end_time,COUNT(*) duplicates
FROM dataanalysis.ship_behavior_event
WHERE batch_no LIKE @alongside_pattern
   OR batch_no LIKE @loitering_pattern
GROUP BY batch_no,rule_version,behavior_type,mmsi,
         COALESCE(target_mmsi,''),start_time,end_time
HAVING COUNT(*)>1;

-- 8. 复核候选幂等：结果必须为空。
SELECT candidate_key,COUNT(*) duplicates
FROM dataanalysis.ship_behavior_event_degraded_candidate
WHERE batch_no LIKE @alongside_pattern
   OR batch_no LIKE @loitering_pattern
GROUP BY candidate_key
HAVING COUNT(*)>1;

-- 9. 按事件排查；替换批次和 MMSI。
SET @inspect_batch='loitering-lc-s5000-202507010000-202508010000-v3';
SET @inspect_mmsi='<MMSI>';

SELECT id,batch_no,behavior_type,rule_version,mmsi,target_mmsi,
       start_time,end_time,own_ship_type,target_ship_type,
       exclusion_flag,exclusion_reason,near_port,near_anchor,open_sea,
       score_detail_json
FROM dataanalysis.ship_behavior_event
WHERE batch_no=@inspect_batch
  AND mmsi=@inspect_mmsi
ORDER BY start_time;

SELECT batch_no,mmsi,window_start_time,window_end_time,status,
       point_count,candidate_count,valid_count,stored_event_count,
       result_code,error_message
FROM dataanalysis.ship_behavior_loitering_batch_status
WHERE batch_no=@inspect_batch
  AND mmsi=@inspect_mmsi
ORDER BY window_start_time;
