CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_behavior_region_slice_status` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `batch_no` VARCHAR(64) NOT NULL,
  `phase` VARCHAR(16) NOT NULL,
  `slice_key` VARCHAR(96) NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `error_message` VARCHAR(1024) DEFAULT NULL,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_region_batch_phase_slice` (`batch_no`,`phase`,`slice_key`),
  KEY `idx_region_batch_status` (`batch_no`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='区域行为发现与分析进度';
