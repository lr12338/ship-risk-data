-- =====================================================================
-- STS Phase 1.3 — Region Set Membership Table
--
-- Purpose:  Groups regions into named sets for batch runs.
--           A region set is identified by region_set_code.
--           No separate metadata table for Phase 1.3 — the set code
--           itself is the identifier.
--
-- Uniqueness: UNIQUE(region_set_code, region_code, region_version)
-- =====================================================================

CREATE TABLE IF NOT EXISTS dataanalysis.ship_sts_region_set_item (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    region_set_code VARCHAR(64) NOT NULL COMMENT 'e.g. GLOBAL_STS_TEST_REGION_SET_V1',

    region_code     VARCHAR(64) NOT NULL,
    region_version  VARCHAR(32) NOT NULL,

    priority        INT NOT NULL DEFAULT 100 COMMENT 'Set-level priority override; lower = higher priority for overlap',
    enabled         TINYINT(1)  NOT NULL DEFAULT 1,

    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_set_region (region_set_code, region_code, region_version),
    KEY idx_set_code (region_set_code, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='STS Phase1.3 region set membership — groups regions for batch runs';
