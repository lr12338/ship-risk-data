CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_behavior_run_batch` (
  `batch_no` VARCHAR(64) NOT NULL,
  `behavior_type` VARCHAR(32) NOT NULL,
  `list_batch_no` VARCHAR(64) NOT NULL,
  `start_time` DATETIME NOT NULL,
  `end_time` DATETIME NOT NULL,
  `rule_version` VARCHAR(64) NOT NULL,
  `write_mode` VARCHAR(16) NOT NULL,
  `config_hash` VARCHAR(64) NOT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='船舶行为生产测试批次清单';
