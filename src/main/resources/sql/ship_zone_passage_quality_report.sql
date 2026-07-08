-- =============================================================================
-- 区域通行数据质量评估表（zonePassageHistory2025JobHandler 产出数据评估）
-- 涉及源表：
--   ship_zone_passage_status  — 计算进度（DONE/FAILED + event_count）
--   ship_zone_crossing_event  — 各区域进出事件（ENTER/EXIT）
-- 区域类型：STRAIT/SEA_AREA/BRIDGE/WIND_FARM/STORM_WAVE/STORM_WIND
--           HIGH_RISK/ICE/MARINE_PROTECTED/AQUACULTURE/EMISSION_CONTROL/MARPOL/JWC
-- =============================================================================

-- 1. 批次评估总报告
CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_zone_passage_quality_report` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `batch_month` VARCHAR(20) NOT NULL COMMENT '批次标识，如 2025-01-01-W',
  `start_time` DATETIME NOT NULL COMMENT '批次开始时间',
  `end_time` DATETIME NOT NULL COMMENT '批次结束时间',
  `quality_status` VARCHAR(20) NOT NULL COMMENT 'PASS/WARN/FAIL',
  `quality_score` INT NOT NULL DEFAULT 0 COMMENT '质量得分 0-100',
  `total_ships_expected` INT DEFAULT 0 COMMENT '应计算船舶总数',
  `total_ships_done` INT DEFAULT 0 COMMENT 'DONE 船舶数',
  `total_ships_failed` INT DEFAULT 0 COMMENT 'FAILED 船舶数',
  `completion_rate` DECIMAL(5,2) DEFAULT 0 COMMENT '完成率 %',
  `total_events_in_status` INT DEFAULT 0 COMMENT 'status 表 event_count 合计',
  `total_events_actual` INT DEFAULT 0 COMMENT 'event 表实际事件数',
  `error_count` INT DEFAULT 0 COMMENT 'ERROR 级问题数',
  `warn_count` INT DEFAULT 0 COMMENT 'WARN 级问题数',
  `event_count_mismatch_ships` INT DEFAULT 0 COMMENT 'event_count 对账不一致船舶数',
  `unbalanced_pair_groups` INT DEFAULT 0 COMMENT 'ENTER/EXIT 不成对组合数',
  `null_field_events` INT DEFAULT 0 COMMENT '必填字段为空事件数',
  `out_of_range_events` INT DEFAULT 0 COMMENT '超批次时间范围事件数',
  `duplicate_event_groups` INT DEFAULT 0 COMMENT '重复事件组数',
  `enter_after_exit_pairs` INT DEFAULT 0 COMMENT 'EXIT 早于 ENTER 的通行对数',
  `invalid_category_events` INT DEFAULT 0 COMMENT '未知 event_category 事件数',
  `replay_sample_ships` INT DEFAULT 0 COMMENT '抽样回放船舶数',
  `replay_mismatch_ships` INT DEFAULT 0 COMMENT '回放结果不一致船舶数',
  `spatial_check_events` INT DEFAULT 0 COMMENT '空间抽检事件数',
  `spatial_mismatch_events` INT DEFAULT 0 COMMENT '坐标不在区域内事件数',
  `check_results_json` TEXT COMMENT '各检查项结果 JSON',
  `issue_summary` VARCHAR(2000) DEFAULT NULL COMMENT '问题摘要',
  `evaluated_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_range` (`batch_month`, `start_time`, `end_time`),
  KEY `idx_quality_status` (`quality_status`),
  KEY `idx_evaluated_at` (`evaluated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='区域通行数据质量评估总报告';

-- 2. 按区域类型（event_category）统计
CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_zone_passage_quality_category_stat` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `report_id` BIGINT NOT NULL,
  `event_category` VARCHAR(32) NOT NULL COMMENT 'STRAIT/SEA_AREA/STORM_WAVE 等',
  `event_count` INT DEFAULT 0,
  `ship_count` INT DEFAULT 0 COMMENT '涉及船舶数',
  `enter_count` INT DEFAULT 0,
  `exit_count` INT DEFAULT 0,
  `passage_pairs` INT DEFAULT 0 COMMENT '完整通行对数（ENTER+EXIT 均存在）',
  `unbalanced_groups` INT DEFAULT 0,
  `avg_dwell_minutes` DECIMAL(10,2) DEFAULT NULL COMMENT '平均停留时长（分钟）',
  PRIMARY KEY (`id`),
  KEY `idx_report_id` (`report_id`),
  KEY `idx_category` (`event_category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='区域通行质量评估-按类型统计';

-- 3. 按具体区域（zone_id）统计
CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_zone_passage_quality_zone_stat` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `report_id` BIGINT NOT NULL,
  `zone_id` INT NOT NULL,
  `zone_name` VARCHAR(255) DEFAULT NULL,
  `event_category` VARCHAR(32) NOT NULL,
  `event_count` INT DEFAULT 0,
  `ship_count` INT DEFAULT 0,
  `enter_count` INT DEFAULT 0,
  `exit_count` INT DEFAULT 0,
  `unbalanced_groups` INT DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_report_id` (`report_id`),
  KEY `idx_zone_category` (`zone_id`, `event_category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='区域通行质量评估-按区域统计';

-- 4. 各检查项执行结果
CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_zone_passage_quality_check` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `report_id` BIGINT NOT NULL,
  `check_code` VARCHAR(64) NOT NULL COMMENT '检查项编码',
  `check_name` VARCHAR(128) NOT NULL COMMENT '检查项名称',
  `severity` VARCHAR(10) NOT NULL COMMENT 'PASS/ERROR/WARN',
  `issue_count` INT DEFAULT 0 COMMENT '发现问题数',
  `detail` VARCHAR(1024) DEFAULT NULL COMMENT '检查详情',
  PRIMARY KEY (`id`),
  KEY `idx_report_id` (`report_id`),
  KEY `idx_check_code` (`check_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='区域通行质量评估-检查项明细';

-- 5. 问题明细（可追溯）
CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_zone_passage_quality_issue` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `report_id` BIGINT NOT NULL,
  `check_code` VARCHAR(64) DEFAULT NULL COMMENT '关联检查项',
  `issue_type` VARCHAR(64) NOT NULL,
  `severity` VARCHAR(10) NOT NULL COMMENT 'ERROR/WARN',
  `mmsi` VARCHAR(20) DEFAULT NULL,
  `zone_id` INT DEFAULT NULL,
  `zone_name` VARCHAR(255) DEFAULT NULL,
  `event_category` VARCHAR(32) DEFAULT NULL,
  `detail` VARCHAR(1024) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_report_id` (`report_id`),
  KEY `idx_issue_type` (`issue_type`),
  KEY `idx_severity` (`severity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='区域通行质量评估-问题明细';

-- =============================================================================
-- 若已执行过旧版建表，可手动执行以下 ALTER（按需）：
-- ALTER TABLE ship_zone_passage_quality_report
--   ADD COLUMN quality_score INT NOT NULL DEFAULT 0 AFTER quality_status,
--   ADD COLUMN total_ships_expected INT DEFAULT 0 AFTER quality_score,
--   ADD COLUMN completion_rate DECIMAL(5,2) DEFAULT 0 AFTER total_ships_failed,
--   ADD COLUMN error_count INT DEFAULT 0,
--   ADD COLUMN warn_count INT DEFAULT 0,
--   ADD COLUMN duplicate_event_groups INT DEFAULT 0,
--   ADD COLUMN enter_after_exit_pairs INT DEFAULT 0,
--   ADD COLUMN invalid_category_events INT DEFAULT 0,
--   ADD COLUMN replay_sample_ships INT DEFAULT 0,
--   ADD COLUMN replay_mismatch_ships INT DEFAULT 0,
--   ADD COLUMN spatial_check_events INT DEFAULT 0,
--   ADD COLUMN spatial_mismatch_events INT DEFAULT 0,
--   ADD COLUMN check_results_json TEXT,
--   DROP COLUMN zone_stats_json;
-- ALTER TABLE ship_zone_passage_quality_issue ADD COLUMN check_code VARCHAR(64) DEFAULT NULL AFTER report_id;
-- =============================================================================
