CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_behavior_region_candidate` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `batch_no` VARCHAR(64) NOT NULL,
  `mmsi` VARCHAR(32) NOT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_region_batch_mmsi` (`batch_no`,`mmsi`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='区域行为已发现候选船';
