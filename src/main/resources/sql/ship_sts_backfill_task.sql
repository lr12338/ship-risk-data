-- STS Phase 1.4 — Fleet Backfill Task Table
--
-- One row = one time-slice task (1-7 days) within a parent backfill batch.
-- Tasks are claimed atomically by workers, retried on staleness, and
-- track detailed per-slice statistics.
--
-- NOT auto-created at startup. Apply manually before first run.

CREATE TABLE IF NOT EXISTS dataanalysis.ship_sts_backfill_task (
  id                          BIGINT        NOT NULL AUTO_INCREMENT,
  batch_no                    VARCHAR(128)  NOT NULL COMMENT 'parent batch_no FK to ship_sts_backfill_batch',
  slice_seq                   INT           NOT NULL COMMENT 'slice sequence number (0-based) within batch',
  slice_from                  DATETIME      NOT NULL COMMENT 'slice start time (inclusive)',
  slice_to                    DATETIME      NOT NULL COMMENT 'slice end time (exclusive)',

  status                      VARCHAR(32)   NOT NULL DEFAULT 'PENDING'
                                COMMENT 'PENDING / RUNNING / FAILED / COMPLETE',
  retry_count                 INT           NOT NULL DEFAULT 0 COMMENT 'number of retries (max 3)',

  candidate_ship_count        INT           NOT NULL DEFAULT 0 COMMENT 'region-preselected physical ship count',
  processed_ship_count        INT           NOT NULL DEFAULT 0 COMMENT 'fully processed ship count',
  trajectory_loaded_ship_count INT          NOT NULL DEFAULT 0 COMMENT 'ships with trajectory loaded',

  activity_windows            INT           NOT NULL DEFAULT 0 COMMENT 'total low-speed activity windows',
  region_activity_windows     INT           NOT NULL DEFAULT 0 COMMENT 'windows inside analysis region',

  replay_queries              INT           NOT NULL DEFAULT 0 COMMENT 'spatial replay query count',
  replay_failures             INT           NOT NULL DEFAULT 0 COMMENT 'replay query failures',
  replay_truncated            INT           NOT NULL DEFAULT 0 COMMENT 'truncated replay queries',

  baseline_events             INT           NOT NULL DEFAULT 0 COMMENT 'baseline alongside events (pre-region gate)',
  region_accepted_events      INT           NOT NULL DEFAULT 0 COMMENT 'events with region attribution',

  shadow_inserted             INT           NOT NULL DEFAULT 0 COMMENT 'shadow events newly inserted',
  shadow_duplicate_skipped    INT           NOT NULL DEFAULT 0 COMMENT 'shadow events skipped (duplicate key)',

  self_pair_events            INT           NOT NULL DEFAULT 0 COMMENT 'self-pair events (should always be 0)',

  last_error                  VARCHAR(1024) DEFAULT NULL COMMENT 'most recent error message',

  started_at                  DATETIME      DEFAULT NULL,
  updated_at                  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  finished_at                 DATETIME      DEFAULT NULL,

  PRIMARY KEY (id),
  UNIQUE KEY uk_batch_slice (batch_no, slice_seq),
  UNIQUE KEY uk_batch_time (batch_no, slice_from, slice_to),
  KEY idx_batch_status (batch_no, status)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='STS fleet backfill slice tasks (Phase 1.4)';
