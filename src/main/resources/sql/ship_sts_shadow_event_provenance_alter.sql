-- =====================================================================
-- STS Phase 1.3 — Shadow Event Provenance Columns
--
-- Purpose:  Record which seed batch and region set produced each
--           shadow event. Enables historical result traceability.
--
-- Safety:   Both columns are NULLABLE. Existing rows remain NULL.
--           New Phase 1.3 handler MUST populate both fields.
--
-- Execute:  Manually, before running region-set shadow jobs.
-- =====================================================================

ALTER TABLE dataanalysis.ship_sts_shadow_event
    ADD COLUMN seed_batch_no  VARCHAR(128) NULL COMMENT 'STS Phase1.3: seed batch that produced this event'
    AFTER algorithm_version,
    ADD COLUMN region_set_code VARCHAR(64) NULL COMMENT 'STS Phase1.3: region set that produced this event'
    AFTER seed_batch_no,
    ADD KEY idx_seed_batch_no (seed_batch_no),
    ADD KEY idx_region_set_code (region_set_code);
