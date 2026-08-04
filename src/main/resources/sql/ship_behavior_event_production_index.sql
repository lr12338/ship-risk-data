-- 全量任务前在低峰期人工执行。先检查是否已有等价前缀索引，避免重复建索引。
SELECT index_name,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS indexed_columns
FROM information_schema.statistics
WHERE table_schema = 'dataanalysis'
  AND table_name = 'ship_behavior_event'
GROUP BY index_name;

-- 仅在不存在 batch_no,behavior_type,mmsi,rule_version,start_time 等价索引时执行。
ALTER TABLE dataanalysis.ship_behavior_event
  ADD INDEX idx_batch_behavior_mmsi_rule_time
  (batch_no, behavior_type, mmsi, rule_version, start_time);
