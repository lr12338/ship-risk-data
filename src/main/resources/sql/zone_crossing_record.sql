CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_zone_crossing_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `mmsi` VARCHAR(20) NOT NULL COMMENT '船舶MMSI',
  `zone_id` INT NOT NULL COMMENT '区域ID',
  `zone_name` VARCHAR(255) DEFAULT NULL COMMENT '区域名称',
  `event_category` VARCHAR(20) NOT NULL COMMENT 'STORM_WAVE/STORM_WIND/STRAIT/SEA_AREA',
  `event_type` VARCHAR(10) NOT NULL COMMENT 'ENTER进入 / EXIT离开',
  `event_time` DATETIME NOT NULL COMMENT '事件发生时间',
  `lon` DOUBLE DEFAULT NULL COMMENT '事件位置经度',
  `lat` DOUBLE DEFAULT NULL COMMENT '事件位置纬度',
  `port_id` INT DEFAULT NULL COMMENT '港口ID',
  `zone_type` INT DEFAULT NULL COMMENT '区域类型',
  `zone_source` VARCHAR(64) DEFAULT NULL COMMENT '区域来源表',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_mmsi_zone_time` (`mmsi`, `zone_id`, `event_time`),
  KEY `idx_event_type` (`event_type`),
  KEY `idx_event_time` (`event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='船舶区域进出事件（进入/离开各一条）';
