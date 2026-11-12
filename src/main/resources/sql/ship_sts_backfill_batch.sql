-- STS Phase 1.4 — Fleet Backfill Batch Table
--
-- Parent batch for multi-year STS historical fleet backfill.
-- One batch = one fleet offset × one region set × one multi-year time range.
--
-- READ-ONLY source: dataanalysis.ship_behavior_target_ship
-- Shadow output:     dataanalysis.ship_sts_shadow_event (via batch_no)
--
-- NOT auto-created at startup. Apply manually before first run.

CREATE TABLE IF NOT EXISTS dataanalysis.ship_sts_backfill_batch (
  batch_no                  VARCHAR(128)  NOT NULL COMMENT 'Parent backfill batch identifier',
  runner_version            VARCHAR(64)   NOT NULL COMMENT 'Algorithm / runner version tag',
  config_fingerprint        VARCHAR(64)   NOT NULL COMMENT 'SHA-256 of all input params (resume guard)',

  target_list_batch_no      VARCHAR(64)   NOT NULL COMMENT 'source list_batch_no from ship_behavior_target_ship',
  target_list_fingerprint   VARCHAR(64)   NOT NULL COMMENT 'SHA-256 of selected fleet identities (resume guard)',
  expected_target_count     INT           NOT NULL COMMENT 'expected physical ship count (safety lock)',
  actual_target_count       INT           NOT NULL COMMENT 'actual physical ship count at batch init',

  ship_offset               INT           NOT NULL DEFAULT 0 COMMENT 'physical ship offset within list_batch_no',
  ship_count                INT           NOT NULL COMMENT 'physical ship count for this job (max 5000)',
  selected_ship_count       INT           NOT NULL COMMENT 'actual selected ship count (offset+limit clip)',

  region_set_code           VARCHAR(64)   NOT NULL COMMENT 'region set code from ship_sts_region_set_item',
  region_set_fingerprint    VARCHAR(64)   NOT NULL COMMENT 'SHA-256 of region definitions (resume guard)',
  resolved_region_count     INT           NOT NULL COMMENT 'number of enabled regions resolved',

  from_time                 DATETIME      NOT NULL COMMENT 'parent backfill start time (inclusive)',
  to_time                   DATETIME      NOT NULL COMMENT 'parent backfill end time (exclusive)',
  slice_days                INT           NOT NULL COMMENT 'task slice size in days (1-7)',

  status                    VARCHAR(32)   NOT NULL DEFAULT 'PENDING'
                              COMMENT 'PENDING / RUNNING / PARTIAL_FAILED / COMPLETE / BLOCKED',

  total_tasks               INT           NOT NULL DEFAULT 0 COMMENT 'total slice tasks planned',
  completed_tasks           INT           NOT NULL DEFAULT 0 COMMENT 'completed slice tasks',
  failed_tasks              INT           NOT NULL DEFAULT 0 COMMENT 'failed slice tasks',

  total_candidate_ships     INT           NOT NULL DEFAULT 0 COMMENT 'cumulative region-preselected ships',
  total_processed_ships     INT           NOT NULL DEFAULT 0 COMMENT 'cumulative processed ships',
  total_baseline_events     INT           NOT NULL DEFAULT 0 COMMENT 'cumulative baseline events',
  total_shadow_inserted     INT           NOT NULL DEFAULT 0 COMMENT 'cumulative shadow events inserted',
  total_duplicate_skipped   INT           NOT NULL DEFAULT 0 COMMENT 'cumulative duplicate events skipped',

  last_error                VARCHAR(1024) DEFAULT NULL COMMENT 'most recent error message',

  created_at                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  started_at                DATETIME      DEFAULT NULL,
  updated_at                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  finished_at               DATETIME      DEFAULT NULL,

  PRIMARY KEY (batch_no)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='STS fleet backfill parent batch (Phase 1.4)';
