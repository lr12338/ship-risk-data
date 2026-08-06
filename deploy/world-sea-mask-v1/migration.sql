-- WORLD_SEA_MASK-only migration. Run independently before any release-state command.
-- This file does not publish a geometry table and does not create ACTIVE metadata.
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
  PRIMARY KEY (dataset_version, geometry_fingerprint),
  KEY idx_world_sea_meta_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Upgrade the earlier draft's single-column meta primary key so retired fingerprints remain recoverable.
SET @ws_meta_pk = (SELECT GROUP_CONCAT(column_name ORDER BY ordinal_position SEPARATOR ',')
  FROM information_schema.key_column_usage
  WHERE table_schema='dataanalysis' AND table_name='ship_behavior_world_sea_meta'
    AND constraint_name='PRIMARY');
SET @ws_sql = IF(@ws_meta_pk='dataset_version',
  'ALTER TABLE dataanalysis.ship_behavior_world_sea_meta DROP PRIMARY KEY, ADD PRIMARY KEY (dataset_version,geometry_fingerprint)',
  'SELECT 1');
PREPARE ws_stmt FROM @ws_sql; EXECUTE ws_stmt; DEALLOCATE PREPARE ws_stmt;

SET @ws_meta_status_idx = (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema='dataanalysis' AND table_name='ship_behavior_world_sea_meta'
    AND index_name='idx_world_sea_meta_status');
SET @ws_sql = IF(@ws_meta_status_idx=0,
  'ALTER TABLE dataanalysis.ship_behavior_world_sea_meta ADD KEY idx_world_sea_meta_status (status)',
  'SELECT 1');
PREPARE ws_stmt FROM @ws_sql; EXECUTE ws_stmt; DEALLOCATE PREPARE ws_stmt;

CREATE TABLE IF NOT EXISTS dataanalysis.ship_behavior_world_sea_release (
  release_id VARCHAR(96) NOT NULL,
  dataset_version VARCHAR(64) NOT NULL,
  parent_dataset_version VARCHAR(64) DEFAULT NULL,
  source_file_sha256 VARCHAR(64) NOT NULL,
  original_stage_fingerprint VARCHAR(64) DEFAULT NULL,
  geometry_fingerprint VARCHAR(64) NOT NULL,
  repair_tool_git_sha VARCHAR(40) DEFAULT NULL,
  repair_release_id VARCHAR(96) DEFAULT NULL,
  review_status VARCHAR(32) DEFAULT NULL,
  stage_table_name VARCHAR(128) NOT NULL,
  formal_table_name VARCHAR(128) NOT NULL,
  backup_table_name VARCHAR(128) DEFAULT NULL,
  failed_table_name VARCHAR(128) DEFAULT NULL,
  publish_mode VARCHAR(32) DEFAULT NULL,
  previous_active_dataset_version VARCHAR(64) DEFAULT NULL,
  previous_active_fingerprint VARCHAR(64) DEFAULT NULL,
  feature_count INT NOT NULL,
  polygon_count INT NOT NULL,
  multipolygon_count INT NOT NULL,
  vertex_count BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  last_completed_step VARCHAR(32) DEFAULT NULL,
  error_message VARCHAR(1000) DEFAULT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  published_at DATETIME DEFAULT NULL,
  activated_at DATETIME DEFAULT NULL,
  rolled_back_at DATETIME DEFAULT NULL,
  PRIMARY KEY (release_id),
  UNIQUE KEY uk_world_sea_release_stage (stage_table_name),
  KEY idx_world_sea_release_status (status,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @ws_release_publish_mode = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema='dataanalysis' AND table_name='ship_behavior_world_sea_release'
    AND column_name='publish_mode');
SET @ws_sql = IF(@ws_release_publish_mode=0,
  'ALTER TABLE dataanalysis.ship_behavior_world_sea_release ADD COLUMN publish_mode VARCHAR(32) DEFAULT NULL AFTER failed_table_name',
  'SELECT 1');
PREPARE ws_stmt FROM @ws_sql; EXECUTE ws_stmt; DEALLOCATE PREPARE ws_stmt;

-- V1R1 release identity columns. Compatible with a V1 migration that was already applied.
SET @ws_schema = 'dataanalysis';
SET @ws_table = 'ship_behavior_world_sea_release';

SET @ws_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=@ws_schema AND table_name=@ws_table AND column_name='parent_dataset_version');
SET @ws_sql = IF(@ws_exists=0,
  'ALTER TABLE dataanalysis.ship_behavior_world_sea_release ADD COLUMN parent_dataset_version VARCHAR(64) DEFAULT NULL AFTER dataset_version',
  'SELECT 1');
PREPARE ws_stmt FROM @ws_sql; EXECUTE ws_stmt; DEALLOCATE PREPARE ws_stmt;

SET @ws_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=@ws_schema AND table_name=@ws_table AND column_name='original_stage_fingerprint');
SET @ws_sql = IF(@ws_exists=0,
  'ALTER TABLE dataanalysis.ship_behavior_world_sea_release ADD COLUMN original_stage_fingerprint VARCHAR(64) DEFAULT NULL AFTER source_file_sha256',
  'SELECT 1');
PREPARE ws_stmt FROM @ws_sql; EXECUTE ws_stmt; DEALLOCATE PREPARE ws_stmt;

SET @ws_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=@ws_schema AND table_name=@ws_table AND column_name='repair_tool_git_sha');
SET @ws_sql = IF(@ws_exists=0,
  'ALTER TABLE dataanalysis.ship_behavior_world_sea_release ADD COLUMN repair_tool_git_sha VARCHAR(40) DEFAULT NULL AFTER geometry_fingerprint',
  'SELECT 1');
PREPARE ws_stmt FROM @ws_sql; EXECUTE ws_stmt; DEALLOCATE PREPARE ws_stmt;

SET @ws_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=@ws_schema AND table_name=@ws_table AND column_name='repair_release_id');
SET @ws_sql = IF(@ws_exists=0,
  'ALTER TABLE dataanalysis.ship_behavior_world_sea_release ADD COLUMN repair_release_id VARCHAR(96) DEFAULT NULL AFTER repair_tool_git_sha',
  'SELECT 1');
PREPARE ws_stmt FROM @ws_sql; EXECUTE ws_stmt; DEALLOCATE PREPARE ws_stmt;

SET @ws_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=@ws_schema AND table_name=@ws_table AND column_name='review_status');
SET @ws_sql = IF(@ws_exists=0,
  'ALTER TABLE dataanalysis.ship_behavior_world_sea_release ADD COLUMN review_status VARCHAR(32) DEFAULT NULL AFTER repair_release_id',
  'SELECT 1');
PREPARE ws_stmt FROM @ws_sql; EXECUTE ws_stmt; DEALLOCATE PREPARE ws_stmt;

CREATE TABLE IF NOT EXISTS dataanalysis.ship_behavior_world_sea_repair_audit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  repair_release_id VARCHAR(96) NOT NULL,
  dataset_version_before VARCHAR(64) NOT NULL,
  dataset_version_after VARCHAR(64) NOT NULL,
  original_stage_fingerprint VARCHAR(64) NOT NULL,
  repaired_geometry_fingerprint VARCHAR(64) NOT NULL,
  feature_id INT NOT NULL,
  feature_name VARCHAR(255) DEFAULT NULL,
  feature_cn_name VARCHAR(100) DEFAULT NULL,
  original_geometry_type VARCHAR(32) NOT NULL,
  repaired_geometry_type VARCHAR(32) NOT NULL,
  original_validity_error VARCHAR(1000) NOT NULL,
  repaired_valid TINYINT(1) NOT NULL,
  original_wkb_sha256 VARCHAR(64) NOT NULL,
  repaired_wkb_sha256 VARCHAR(64) NOT NULL,
  original_vertex_count INT NOT NULL,
  repaired_vertex_count INT NOT NULL,
  original_component_count INT NOT NULL,
  repaired_component_count INT NOT NULL,
  original_area_degree2 DOUBLE NOT NULL,
  repaired_area_degree2 DOUBLE NOT NULL,
  relative_area_delta DOUBLE NOT NULL,
  original_envelope VARCHAR(255) NOT NULL,
  repaired_envelope VARCHAR(255) NOT NULL,
  collapsed_component_count INT NOT NULL DEFAULT 0,
  collapsed_component_type VARCHAR(255) DEFAULT NULL,
  collapsed_component_length DOUBLE NOT NULL DEFAULT 0,
  collapsed_component_decision VARCHAR(64) NOT NULL,
  polygon_component_areas TEXT NOT NULL,
  minimum_polygon_area DOUBLE NOT NULL,
  main_polygon_area DOUBLE NOT NULL,
  minimum_polygon_ratio DOUBLE NOT NULL,
  repair_method VARCHAR(64) NOT NULL,
  repair_tool_git_sha VARCHAR(40) NOT NULL,
  review_status VARCHAR(32) NOT NULL,
  review_comment VARCHAR(2000) DEFAULT NULL,
  reviewed_at DATETIME DEFAULT NULL,
  reviewed_by VARCHAR(128) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_world_sea_repair_feature (repair_release_id,feature_id),
  KEY idx_world_sea_repair_dataset (dataset_version_after,review_status)
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
  baseline_planned_replay_query_count INT NOT NULL DEFAULT 0,
  filtered_planned_replay_query_count INT NOT NULL DEFAULT 0,
  saved_replay_query_count INT NOT NULL DEFAULT 0,
  attempted_replay_query_count INT NOT NULL DEFAULT 0,
  successful_replay_query_count INT NOT NULL DEFAULT 0,
  failed_replay_query_count INT NOT NULL DEFAULT 0,
  truncated_replay_query_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_world_sea_batch_ship_window
    (batch_no,mmsi,behavior_type,window_start_time,window_end_time),
  KEY idx_world_sea_audit_batch (batch_no,behavior_type),
  KEY idx_world_sea_audit_ship_time (mmsi,window_start_time,window_end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Compatibility upgrades for an audit table created by an earlier V1 draft.
SET @ws_schema = 'dataanalysis';
SET @ws_table = 'ship_behavior_world_sea_audit';

SET @ws_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=@ws_schema AND table_name=@ws_table AND column_name='baseline_planned_replay_query_count');
SET @ws_sql = IF(@ws_exists=0,
  'ALTER TABLE dataanalysis.ship_behavior_world_sea_audit ADD COLUMN baseline_planned_replay_query_count INT NOT NULL DEFAULT 0 AFTER world_sea_rejected_window_count',
  'SELECT 1');
PREPARE ws_stmt FROM @ws_sql; EXECUTE ws_stmt; DEALLOCATE PREPARE ws_stmt;

SET @ws_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=@ws_schema AND table_name=@ws_table AND column_name='filtered_planned_replay_query_count');
SET @ws_sql = IF(@ws_exists=0,
  'ALTER TABLE dataanalysis.ship_behavior_world_sea_audit ADD COLUMN filtered_planned_replay_query_count INT NOT NULL DEFAULT 0 AFTER baseline_planned_replay_query_count',
  'SELECT 1');
PREPARE ws_stmt FROM @ws_sql; EXECUTE ws_stmt; DEALLOCATE PREPARE ws_stmt;

SET @ws_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=@ws_schema AND table_name=@ws_table AND column_name='attempted_replay_query_count');
SET @ws_sql = IF(@ws_exists=0,
  'ALTER TABLE dataanalysis.ship_behavior_world_sea_audit ADD COLUMN attempted_replay_query_count INT NOT NULL DEFAULT 0 AFTER saved_replay_query_count',
  'SELECT 1');
PREPARE ws_stmt FROM @ws_sql; EXECUTE ws_stmt; DEALLOCATE PREPARE ws_stmt;

SET @ws_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=@ws_schema AND table_name=@ws_table AND column_name='successful_replay_query_count');
SET @ws_sql = IF(@ws_exists=0,
  'ALTER TABLE dataanalysis.ship_behavior_world_sea_audit ADD COLUMN successful_replay_query_count INT NOT NULL DEFAULT 0 AFTER attempted_replay_query_count',
  'SELECT 1');
PREPARE ws_stmt FROM @ws_sql; EXECUTE ws_stmt; DEALLOCATE PREPARE ws_stmt;

SET @ws_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=@ws_schema AND table_name=@ws_table AND column_name='failed_replay_query_count');
SET @ws_sql = IF(@ws_exists=0,
  'ALTER TABLE dataanalysis.ship_behavior_world_sea_audit ADD COLUMN failed_replay_query_count INT NOT NULL DEFAULT 0 AFTER successful_replay_query_count',
  'SELECT 1');
PREPARE ws_stmt FROM @ws_sql; EXECUTE ws_stmt; DEALLOCATE PREPARE ws_stmt;

SET @ws_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=@ws_schema AND table_name=@ws_table AND column_name='truncated_replay_query_count');
SET @ws_sql = IF(@ws_exists=0,
  'ALTER TABLE dataanalysis.ship_behavior_world_sea_audit ADD COLUMN truncated_replay_query_count INT NOT NULL DEFAULT 0 AFTER failed_replay_query_count',
  'SELECT 1');
PREPARE ws_stmt FROM @ws_sql; EXECUTE ws_stmt; DEALLOCATE PREPARE ws_stmt;
