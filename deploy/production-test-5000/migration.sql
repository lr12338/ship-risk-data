-- Apply once before starting the new jar. The application also creates the new status tables,
-- but the explicit migration makes the production change reviewable.
ALTER TABLE dataanalysis.ship_behavior_event
  MODIFY COLUMN rule_version VARCHAR(64) DEFAULT NULL COMMENT '规则版本',
  ADD COLUMN event_source VARCHAR(16) DEFAULT NULL COMMENT 'TARGET_LIST/REGION',
  ADD COLUMN region_id VARCHAR(128) DEFAULT NULL COMMENT '区域任务编码',
  ADD COLUMN region_fingerprint VARCHAR(64) DEFAULT NULL COMMENT '区域几何指纹',
  ADD COLUMN event_key VARCHAR(64) DEFAULT NULL COMMENT '批次内稳定事件键',
  ADD KEY idx_batch_region_behavior_time (batch_no,region_id,behavior_type,start_time),
  ADD UNIQUE KEY uk_batch_event_key (batch_no,event_key);

ALTER TABLE dataanalysis.ship_behavior_event_detail
  MODIFY COLUMN rule_version VARCHAR(64) DEFAULT NULL COMMENT '规则版本';

CREATE TABLE IF NOT EXISTS dataanalysis.ship_behavior_run_batch (
  batch_no VARCHAR(64) NOT NULL, behavior_type VARCHAR(32) NOT NULL,
  list_batch_no VARCHAR(64) NOT NULL, start_time DATETIME NOT NULL, end_time DATETIME NOT NULL,
  rule_version VARCHAR(64) NOT NULL, write_mode VARCHAR(16) NOT NULL, config_hash VARCHAR(64) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dataanalysis.ship_behavior_region_batch_status (
  batch_no VARCHAR(64) NOT NULL, params_hash VARCHAR(64) NOT NULL,
  region_id VARCHAR(128) NOT NULL, region_name VARCHAR(256) NOT NULL,
  region_fingerprint VARCHAR(64) NOT NULL, polygon_wkt TEXT NOT NULL,
  behavior_type VARCHAR(32) NOT NULL, start_time DATETIME NOT NULL, end_time DATETIME NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'RUNNING', summary_json TEXT DEFAULT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (batch_no), KEY idx_region_behavior_time (region_id,behavior_type,start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dataanalysis.ship_behavior_region_slice_status (
  id BIGINT NOT NULL AUTO_INCREMENT, batch_no VARCHAR(64) NOT NULL,
  phase VARCHAR(16) NOT NULL, slice_key VARCHAR(96) NOT NULL,
  status VARCHAR(20) NOT NULL, error_message VARCHAR(1024) DEFAULT NULL,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_region_batch_phase_slice (batch_no,phase,slice_key),
  KEY idx_region_batch_status (batch_no,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dataanalysis.ship_behavior_region_candidate (
  id BIGINT NOT NULL AUTO_INCREMENT, batch_no VARCHAR(64) NOT NULL, mmsi VARCHAR(32) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (id),
  UNIQUE KEY uk_region_batch_mmsi (batch_no,mmsi)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
