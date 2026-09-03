-- =====================================================================
-- STS Phase 1.3 — Seed Identity Table
--
-- Purpose:  Normalized seed IMO/MMSI identity catalog for batch runs.
--           Each row = one seed batch + one IMO + one MMSI observation.
--           One IMO may have multiple MMSI rows (dual identity).
--
-- Safety:   This is a reference/seed table. It is NOT a production
--           event table. No auto-creation on Spring startup.
--           Execute manually before running region-set shadow jobs.
--
-- Uniqueness: UNIQUE(seed_batch_no, imo, mmsi)
-- =====================================================================

CREATE TABLE IF NOT EXISTS dataanalysis.ship_sts_seed_identity (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    seed_batch_no   VARCHAR(128) NOT NULL COMMENT 'e.g. sts-crude-identity-20260902-v1',
    ship_ordinal    INT NOT NULL COMMENT 'Deterministic per-IMO ordering within batch; same for all MMSIs of same IMO',

    imo             VARCHAR(16) NOT NULL,
    mmsi            VARCHAR(16) NOT NULL,

    first_seen      DATETIME,
    last_seen       DATETIME,

    identity_mode   VARCHAR(32) COMMENT 'SINGLE_OBSERVED, DUAL_MMSI_OBSERVED, etc.',

    name            VARCHAR(128),
    callsign        VARCHAR(32),

    length          INT COMMENT 'meters',
    width           INT COMMENT 'meters',

    type            VARCHAR(64),
    minotype        VARCHAR(64),

    enabled         TINYINT(1) NOT NULL DEFAULT 1,

    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_seed_imo_mmsi (seed_batch_no, imo, mmsi),
    KEY idx_seed_ordinal (seed_batch_no, ship_ordinal),
    KEY idx_seed_imo (seed_batch_no, imo),
    KEY idx_mmsi (mmsi)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='STS Phase1.3 seed identity catalog — one row per IMO/MMSI pair';
