-- =====================================================================
-- STS Phase 1.2 — Shadow Event Table (EXPERIMENTAL)
--
-- Purpose:  Persian Gulf region STS baseline candidate events
--           for manual review. NOT a production event table.
--
-- Safety:   This table is standalone. It does NOT link to
--           ship_behavior_event or ship_behavior_event_detail.
--           No auto-creation on Spring startup.
--           Execute manually before running region shadow jobs.
--
-- Behavior: INSERT IGNORE on (batch_no, event_key) — re-running
--           the same batch is safe and idempotent.
-- =====================================================================

CREATE TABLE IF NOT EXISTS dataanalysis.ship_sts_shadow_event (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    batch_no            VARCHAR(128) NOT NULL,
    event_key           VARCHAR(128) NOT NULL,

    algorithm_version   VARCHAR(64)  NOT NULL,

    region_id           VARCHAR(64)  NOT NULL,
    region_version      VARCHAR(64)  NOT NULL,

    own_imo             VARCHAR(16),
    own_mmsi            VARCHAR(16),

    target_imo          VARCHAR(16),
    target_mmsi         VARCHAR(16),

    identity_mode       VARCHAR(32),

    start_time          DATETIME,
    end_time            DATETIME,
    duration_minutes    INT,

    center_lon          DOUBLE,
    center_lat          DOUBLE,

    behavior_type       VARCHAR(64)  NOT NULL,
    baseline_rule       VARCHAR(64),

    min_distance_nm     DOUBLE,
    avg_distance_nm     DOUBLE,

    own_avg_speed_kn    DOUBLE,
    target_avg_speed_kn DOUBLE,

    behavior_score      DOUBLE,

    target_role         VARCHAR(32),

    review_status       VARCHAR(32)  NOT NULL DEFAULT 'PENDING',

    score_detail_json   TEXT,

    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_batch_event (batch_no, event_key),
    KEY idx_batch_no (batch_no),
    KEY idx_region (region_id, region_version),
    KEY idx_own_imo (own_imo),
    KEY idx_target_imo (target_imo),
    KEY idx_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='STS Phase1.2 region shadow baseline candidates — EXPERIMENTAL';
