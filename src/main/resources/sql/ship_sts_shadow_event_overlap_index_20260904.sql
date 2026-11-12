-- Required before STS_ALONGSIDE_HISTORY_V1 execution.
-- Execute manually only after SHOW INDEX confirms these names are absent.
ALTER TABLE dataanalysis.ship_sts_shadow_event
  ADD KEY idx_sts_overlap_target_imo
    (batch_no, region_id, own_imo, target_imo, start_time, end_time),
  ADD KEY idx_sts_overlap_target_mmsi
    (batch_no, region_id, own_imo, target_mmsi, start_time, end_time);

SHOW INDEX FROM dataanalysis.ship_sts_shadow_event
WHERE Key_name IN ('idx_sts_overlap_target_imo', 'idx_sts_overlap_target_mmsi');
