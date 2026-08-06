CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_behavior_region_batch_status` (
  `batch_no` VARCHAR(64) NOT NULL,
  `params_hash` VARCHAR(64) NOT NULL,
  `region_id` VARCHAR(128) NOT NULL,
  `region_name` VARCHAR(256) NOT NULL,
  `region_fingerprint` VARCHAR(64) NOT NULL,
  `polygon_wkt` TEXT NOT NULL,
  `behavior_type` VARCHAR(32) NOT NULL,
  `start_time` DATETIME NOT NULL,
  `end_time` DATETIME NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
  `summary_json` TEXT DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`batch_no`),
  KEY `idx_region_behavior_time` (`region_id`,`behavior_type`,`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='区域行为任务批次';
