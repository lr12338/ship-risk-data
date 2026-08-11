-- 清理 ship_zone_crossing_event 中已存在的重复事件（保留 id 最小的一条）
-- 重复定义：mmsi + zone_id + event_category + event_type + event_time 完全相同
-- 执行前建议先备份或在从库验证

DELETE e1 FROM dataanalysis.ship_zone_crossing_event e1
INNER JOIN dataanalysis.ship_zone_crossing_event e2
  ON e1.mmsi = e2.mmsi
 AND e1.zone_id = e2.zone_id
 AND e1.event_category = e2.event_category
 AND e1.event_type = e2.event_type
 AND e1.event_time = e2.event_time
 AND e1.id > e2.id;
