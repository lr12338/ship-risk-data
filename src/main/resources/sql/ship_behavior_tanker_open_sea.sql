CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_behavior_tanker_open_sea_target_ship` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `list_batch_no` VARCHAR(64) NOT NULL,
  `seq_no` INT NOT NULL,
  `mmsi` VARCHAR(20) NOT NULL,
  `imo` VARCHAR(32) DEFAULT NULL,
  `ship_name` VARCHAR(255) DEFAULT NULL,
  `raw_type` VARCHAR(64) DEFAULT NULL,
  `raw_minotype` VARCHAR(64) DEFAULT NULL,
  `ais_ship_type_code` INT DEFAULT NULL,
  `standard_ship_type` VARCHAR(32) NOT NULL,
  `length_meters` DOUBLE DEFAULT NULL,
  `dwt` BIGINT DEFAULT NULL,
  `metadata_updated_at` DATETIME DEFAULT NULL,
  `selection_reason` VARCHAR(255) NOT NULL,
  `metadata_quality` VARCHAR(20) NOT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tanker_list_mmsi` (`list_batch_no`, `mmsi`),
  UNIQUE KEY `uk_tanker_list_seq` (`list_batch_no`, `seq_no`),
  KEY `idx_tanker_list_seq` (`list_batch_no`, `seq_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='液货船与集装箱船隔离测试固定名单快照（保留历史表名）';

CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_behavior_event_degraded_candidate` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `candidate_key` CHAR(64) NOT NULL,
  `batch_no` VARCHAR(64) NOT NULL,
  `behavior_type` VARCHAR(32) NOT NULL,
  `mmsi` VARCHAR(20) NOT NULL,
  `target_mmsi` VARCHAR(20) DEFAULT NULL,
  `start_time` DATETIME NOT NULL,
  `end_time` DATETIME NOT NULL,
  `rule_version` VARCHAR(64) NOT NULL,
  `degradation_reason` VARCHAR(64) NOT NULL,
  `exclusion_fingerprint` CHAR(64) NOT NULL,
  `exclusion_health` VARCHAR(16) NOT NULL,
  `source_health_json` LONGTEXT NOT NULL,
  `candidate_payload_json` LONGTEXT NOT NULL,
  `review_status` ENUM('PENDING','APPROVED','REJECTED')
    NOT NULL DEFAULT 'PENDING',
  `reviewer` VARCHAR(128) DEFAULT NULL,
  `reviewed_at` DATETIME DEFAULT NULL,
  `review_note` VARCHAR(1000) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_degraded_candidate_key` (`candidate_key`),
  KEY `idx_degraded_candidate_batch` (`batch_no`, `behavior_type`, `mmsi`),
  KEY `idx_degraded_candidate_review` (`review_status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='空间排除数据异常时的独立人工复核候选，不参与正式事件统计';
