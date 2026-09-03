-- =====================================================================
-- STS Phase 1.3 — Risk Region Canonical Table
--
-- Purpose:  Canonical region polygon definitions for STS analysis.
--           Each region is versioned and independently reviewable.
--
-- Safety:   TEST-only initial data. All initial rows have status=TEST
--           and source_type=TEST_ONLY_BBOX. These are NOT authoritative
--           maritime boundaries. Execute manually.
--
-- Uniqueness: UNIQUE(region_code, region_version)
-- =====================================================================

CREATE TABLE IF NOT EXISTS dataanalysis.ship_sts_risk_region (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    region_code             VARCHAR(64)  NOT NULL COMMENT 'e.g. PERSIAN_GULF_TEST_V1',
    region_version          VARCHAR(32)  NOT NULL COMMENT 'e.g. 20260902-v1',

    region_name             VARCHAR(128) NOT NULL COMMENT 'Display name, e.g. Persian Gulf (Test)',

    geometry_wkt            TEXT NOT NULL COMMENT 'Core region polygon WKT (EPSG:4326, lon first)',
    analysis_buffer_nm      DOUBLE NOT NULL DEFAULT 10.0 COMMENT 'Nautical miles added to core for replay gate',

    priority                INT NOT NULL DEFAULT 100 COMMENT 'Lower = higher priority for multi-region overlap',

    status                  VARCHAR(16)  NOT NULL DEFAULT 'TEST' COMMENT 'TEST, APPROVED, DISABLED',
    enabled                 TINYINT(1)   NOT NULL DEFAULT 1,

    geometry_fingerprint    VARCHAR(128) COMMENT 'SHA-256 of WKT + buffer for cache invalidation',

    source_type             VARCHAR(32)  NOT NULL DEFAULT 'TEST_ONLY_BBOX' COMMENT 'TEST_ONLY_BBOX, MARINE_REGIONS, INTERNAL_REVIEWED, etc.',
    source_note             VARCHAR(256),

    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_region_version (region_code, region_version),
    KEY idx_status (status, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='STS Phase1.3 risk region definitions — versioned canonical geometries';
