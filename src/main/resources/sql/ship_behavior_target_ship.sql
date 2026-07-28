CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_behavior_target_ship` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `list_batch_no` VARCHAR(64) NOT NULL,
  `seq_no` INT NOT NULL,
  `mmsi` VARCHAR(20) NOT NULL,
  `imo` VARCHAR(32) DEFAULT NULL,
  `ship_name` VARCHAR(255) DEFAULT NULL,
  `ship_type` VARCHAR(64) DEFAULT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `source_updatetime` DATETIME DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_list_mmsi` (`list_batch_no`, `mmsi`),
  UNIQUE KEY `uk_list_seq` (`list_batch_no`, `seq_no`),
  KEY `idx_list_enabled_seq` (`list_batch_no`, `enabled`, `seq_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='船舶行为生产测试固定名单';
