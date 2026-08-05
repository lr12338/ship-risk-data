-- Manual migration only. The application never executes this file at startup.
CREATE TABLE IF NOT EXISTS dataanalysis.ship_behavior_run (
    run_id VARCHAR(64) NOT NULL PRIMARY KEY,
    batch_no VARCHAR(128) NOT NULL,
    behavior_type VARCHAR(32) NOT NULL,
    profile_id VARCHAR(128) NOT NULL,
    profile_hash CHAR(64) NOT NULL,
    resolved_profile_json LONGTEXT NOT NULL,
    rule_set_id VARCHAR(128) NOT NULL,
    rule_set_version INT NOT NULL,
    rule_set_hash CHAR(64) NOT NULL,
    scene_mode VARCHAR(16) NOT NULL,
    scene_snapshot_version VARCHAR(128),
    scene_fingerprint CHAR(64),
    runtime_policy_version VARCHAR(128),
    snapshot_policy_version VARCHAR(128),
    write_mode_requested VARCHAR(16) NOT NULL,
    write_mode_effective VARCHAR(16) NOT NULL,
    git_commit VARCHAR(64) NOT NULL,
    jar_sha256 CHAR(64) NOT NULL,
    list_batch_no VARCHAR(128) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    manifest_json LONGTEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_behavior_run_batch_profile (batch_no, profile_id, profile_hash),
    KEY idx_behavior_run_list (list_batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dataanalysis.ship_behavior_shadow_result (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_no VARCHAR(128) NOT NULL,
    profile_id VARCHAR(128) NOT NULL,
    profile_hash CHAR(64) NOT NULL,
    rule_set_hash CHAR(64) NOT NULL,
    comparison_key CHAR(64) NOT NULL,
    mmsi VARCHAR(32) NOT NULL,
    target_mmsi VARCHAR(32),
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    control_accepted TINYINT(1) NOT NULL,
    scene_decision VARCHAR(64) NOT NULL,
    filtered_disposition VARCHAR(16) NOT NULL,
    evidence_json LONGTEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_shadow_batch_comparison (batch_no, comparison_key),
    KEY idx_shadow_batch_disposition (batch_no, filtered_disposition)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dataanalysis.ship_behavior_rule_decision_evidence (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_no VARCHAR(128) NOT NULL,
    comparison_key CHAR(64) NOT NULL,
    rule_id VARCHAR(128) NOT NULL,
    rule_version VARCHAR(64) NOT NULL,
    passed TINYINT(1) NOT NULL,
    observed_value VARCHAR(512),
    threshold_value VARCHAR(512),
    unit VARCHAR(32),
    reason_code VARCHAR(128),
    evidence_json LONGTEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_rule_evidence (batch_no, comparison_key, rule_id, rule_version),
    KEY idx_rule_evidence_batch_passed (batch_no, passed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Batch cleanup (execute manually and in this order):
-- DELETE FROM dataanalysis.ship_behavior_rule_decision_evidence WHERE batch_no = ?;
-- DELETE FROM dataanalysis.ship_behavior_shadow_result WHERE batch_no = ?;
-- DELETE FROM dataanalysis.ship_behavior_run WHERE batch_no = ?;
