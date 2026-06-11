CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_zone_passage_status` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `batch_month` VARCHAR(20) NOT NULL COMMENT '批次标识：年=yyyy-YEAR 月=yyyy-MM 日=yyyy-MM-dd 周=yyyy-MM-dd-W',
  `mmsi` VARCHAR(20) NOT NULL COMMENT '船舶MMSI',
  `start_time` DATETIME NOT NULL COMMENT '本次轨迹查询开始时间',
  `end_time` DATETIME NOT NULL COMMENT '本次轨迹查询结束时间',
  `status` VARCHAR(20) NOT NULL DEFAULT 'DONE' COMMENT 'DONE=已完成, FAILED=失败',
  `event_count` INT DEFAULT 0 COMMENT '本次产生的事件条数',
  `processed_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '处理完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_ship` (`batch_month`, `mmsi`, `start_time`, `end_time`),
  KEY `idx_batch_month` (`batch_month`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='区域通行分析任务进度（断点续跑）';
