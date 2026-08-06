-- WORLD_SEA_MASK-only compatible migration. Does not touch ais41 or existing events.
CREATE TABLE IF NOT EXISTS dataanalysis.ship_behavior_world_sea_meta (
  dataset_version VARCHAR(64) NOT NULL,
  source_file_name VARCHAR(255) DEFAULT NULL,
  source_file_sha256 VARCHAR(64) NOT NULL,
  geometry_fingerprint VARCHAR(64) NOT NULL,
  feature_count INT NOT NULL,
  polygon_count INT NOT NULL,
  multipolygon_count INT NOT NULL,
  vertex_count BIGINT NOT NULL,
  coordinate_assumption VARCHAR(128) NOT NULL,
  imported_at DATETIME NOT NULL,
  imported_by VARCHAR(128) DEFAULT NULL,
  status VARCHAR(20) NOT NULL,
  PRIMARY KEY (dataset_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dataanalysis.ship_behavior_world_sea_audit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  batch_no VARCHAR(64) NOT NULL,
  mmsi VARCHAR(20) NOT NULL,
  behavior_type VARCHAR(32) NOT NULL,
  window_start_time DATETIME NOT NULL,
  window_end_time DATETIME NOT NULL,
  raw_event_count INT NOT NULL DEFAULT 0,
  world_sea_pass_event_count INT NOT NULL DEFAULT 0,
  world_sea_rejected_event_count INT NOT NULL DEFAULT 0,
  world_sea_checked_point_count INT NOT NULL DEFAULT 0,
  world_sea_rejected_window_count INT NOT NULL DEFAULT 0,
  saved_replay_query_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_world_sea_batch_ship_window
    (batch_no,mmsi,behavior_type,window_start_time,window_end_time),
  KEY idx_world_sea_audit_batch (batch_no,behavior_type),
  KEY idx_world_sea_audit_ship_time (mmsi,window_start_time,window_end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
