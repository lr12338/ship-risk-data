-- 上线前先执行：
-- SHOW COLUMNS FROM dataanalysis.ship_behavior_event;
-- 如果以下字段已经存在，请不要重复执行对应 ADD COLUMN。

ALTER TABLE dataanalysis.ship_behavior_event
  ADD COLUMN event_sub_type VARCHAR(32) DEFAULT NULL COMMENT '徘徊子类型',
  ADD COLUMN severity_level VARCHAR(8) DEFAULT NULL COMMENT 'L0-L4',
  ADD COLUMN event_score DOUBLE DEFAULT NULL COMMENT '单事件风险分，0-100',
  ADD COLUMN confidence_level VARCHAR(16) DEFAULT NULL COMMENT 'HIGH/MEDIUM/LOW',
  ADD COLUMN exclusion_flag VARCHAR(32) DEFAULT NULL COMMENT 'EXCLUDED_NORMAL/DOWNGRADED_NORMAL_LIKELY/VALID_RISK_EVENT',
  ADD COLUMN exclusion_reason VARCHAR(512) DEFAULT NULL COMMENT '正常场景排除原因',
  ADD COLUMN ais_gap_count INT DEFAULT NULL COMMENT 'AIS gap次数',
  ADD COLUMN max_ais_gap_minutes BIGINT DEFAULT NULL COMMENT '最大AIS gap分钟数',
  ADD COLUMN track_coverage_ratio DOUBLE DEFAULT NULL COMMENT '轨迹覆盖率',
  ADD COLUMN turn_count_per_hour DOUBLE DEFAULT NULL COMMENT '每小时转向次数',
  ADD COLUMN cog_change_per_hour DOUBLE DEFAULT NULL COMMENT '每小时累计航向变化',
  ADD COLUMN tortuosity DOUBLE DEFAULT NULL COMMENT '轨迹曲折系数',
  ADD COLUMN center_lon DOUBLE DEFAULT NULL COMMENT '事件中心经度',
  ADD COLUMN center_lat DOUBLE DEFAULT NULL COMMENT '事件中心纬度',
  ADD COLUMN radius_nm DOUBLE DEFAULT NULL COMMENT '事件活动半径，海里',
  ADD COLUMN score_detail_json TEXT DEFAULT NULL COMMENT '评分明细JSON';
