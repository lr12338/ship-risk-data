-- =====================================================================
-- STS Phase 1.3 — V2 Region Expansion (2026-09-03)
--
-- Purpose:  Expand STS test region catalog from 12 V1 bboxes to
--           29 V2 regions covering high-yield STS hotspots and
--           major crude export port approaches.
--
-- Safety:   INSERT NEW VERSION ONLY.
--           Does NOT modify, delete, or truncate any V1 data.
--           All V2 rows: status=TEST, source_type=TEST_ONLY_BBOX.
--           NOT authoritative maritime / legal / sanctions boundaries.
--
-- Idempotency: Uses INSERT IGNORE for same code+version+fingerprint.
--              If same code+version exists with DIFFERENT geometry,
--              that is a CONFLICT and must be investigated manually.
--
-- New regions: 29
-- New region sets: 3
--   STS_HOTSPOT_HIGH_YIELD_V2     (14 regions)
--   CRUDE_EXPORT_APPROACH_V2     (15 regions)
--   GLOBAL_STS_EXPANDED_V2       (29 regions = both of the above)
-- =====================================================================

SET NAMES utf8mb4;

-- ============================================================
-- Part 1: Canonical regions (29 V2 regions)
-- ============================================================

-- --------------------------------------------------------
-- Group A: STS Hotspot / High-Yield Test Areas (14 regions)
-- --------------------------------------------------------

INSERT IGNORE INTO dataanalysis.ship_sts_risk_region
  (region_code, region_version, region_name, geometry_wkt, analysis_buffer_nm,
   priority, status, enabled, geometry_fingerprint, source_type, source_note)
VALUES
(
  'RIAU_EOPL_NIPAH_STS_V2',
  '20260903-v2',
  'Riau / Malaysia EOPL / Nipah STS (Test V2)',
  'POLYGON((102.6 0.2,105.4 0.2,105.4 2.5,102.6 2.5,102.6 0.2))',
  10.0, 10, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'LINGGI_MALACCA_STS_V2',
  '20260903-v2',
  'Linggi / Southern Malacca STS (Test V2)',
  'POLYGON((101.2 1.3,103.3 1.3,103.3 3.6,101.2 3.6,101.2 1.3))',
  10.0, 20, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'LOME_OFFSHORE_STS_V2',
  '20260903-v2',
  'Lome Offshore STS (Test V2)',
  'POLYGON((-0.8 4.2,1.8 4.2,1.8 7.0,-0.8 7.0,-0.8 4.2))',
  10.0, 30, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'PORT_SAID_EASTMED_STS_V2',
  '20260903-v2',
  'Port Said / East Med STS (Test V2)',
  'POLYGON((31.0 30.6,33.8 30.6,33.8 32.8,31.0 32.8,31.0 30.6))',
  10.0, 40, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'CYPRUS_OFFSHORE_STS_V2',
  '20260903-v2',
  'Cyprus Offshore STS (Test V2)',
  'POLYGON((31.5 33.2,35.5 33.2,35.5 35.8,31.5 35.8,31.5 33.2))',
  10.0, 50, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'MALTA_HURDS_BANK_STS_V2',
  '20260903-v2',
  'Malta / Hurds Bank STS (Test V2)',
  'POLYGON((13.5 34.8,15.8 34.8,15.8 36.8,13.5 36.8,13.5 34.8))',
  10.0, 60, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'AUGUSTA_SICILY_STS_V2',
  '20260903-v2',
  'Augusta / Sicily STS (Test V2)',
  'POLYGON((14.2 36.5,16.2 36.5,16.2 38.2,14.2 38.2,14.2 36.5))',
  10.0, 70, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'LACONIAN_KALAMATA_STS_V2',
  '20260903-v2',
  'Laconian Gulf / Kalamata STS (Test V2)',
  'POLYGON((21.3 35.4,23.4 35.4,23.4 37.2,21.3 37.2,21.3 35.4))',
  10.0, 80, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'CEUTA_GIBRALTAR_STS_V2',
  '20260903-v2',
  'Ceuta / Gibraltar STS (Test V2)',
  'POLYGON((-6.4 35.0,-4.4 35.0,-4.4 36.7,-6.4 36.7,-6.4 35.0))',
  10.0, 90, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'GULF_OMAN_FUJAIRAH_STS_V2',
  '20260903-v2',
  'Gulf of Oman / Fujairah STS (Test V2)',
  'POLYGON((54.8 23.2,58.8 23.2,58.8 26.4,54.8 26.4,54.8 23.2))',
  10.0, 100, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'SOHAR_STS_V2',
  '20260903-v2',
  'Sohar STS (Test V2)',
  'POLYGON((56.3 23.2,58.2 23.2,58.2 24.9,56.3 24.9,56.3 23.2))',
  10.0, 110, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'NAKHODKA_KOZMINO_STS_V2',
  '20260903-v2',
  'Nakhodka / Kozmino STS (Test V2)',
  'POLYGON((131.2 41.0,134.6 41.0,134.6 43.6,131.2 43.6,131.2 41.0))',
  10.0, 120, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'HONG_KONG_OFFSHORE_STS_V2',
  '20260903-v2',
  'Hong Kong Offshore STS (Test V2)',
  'POLYGON((112.8 20.8,115.5 20.8,115.5 23.3,112.8 23.3,112.8 20.8))',
  10.0, 130, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'ARUBA_CURACAO_STS_V2',
  '20260903-v2',
  'Aruba / Curacao STS (Test V2)',
  'POLYGON((-71.6 11.0,-68.0 11.0,-68.0 13.6,-71.6 13.6,-71.6 11.0))',
  10.0, 140, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
);

-- --------------------------------------------------------
-- Group B: Crude Export Port Approaches (15 regions)
-- --------------------------------------------------------

INSERT IGNORE INTO dataanalysis.ship_sts_risk_region
  (region_code, region_version, region_name, geometry_wkt, analysis_buffer_nm,
   priority, status, enabled, geometry_fingerprint, source_type, source_note)
VALUES
(
  'NORTH_GULF_BASRA_KUWAIT_KHARG_V2',
  '20260903-v2',
  'North Gulf — Basra / Kuwait / Kharg (Test V2)',
  'POLYGON((47.5 27.2,52.5 27.2,52.5 30.5,47.5 30.5,47.5 27.2))',
  10.0, 210, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'CENTRAL_GULF_RAS_TANURA_JUAYMAH_V2',
  '20260903-v2',
  'Central Gulf — Ras Tanura / Juaymah (Test V2)',
  'POLYGON((49.0 24.0,52.8 24.0,52.8 27.3,49.0 27.3,49.0 24.0))',
  10.0, 220, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'SOUTH_GULF_RUWAIS_JEBEL_DHANNA_V2',
  '20260903-v2',
  'South Gulf — Ruwais / Jebel Dhanna (Test V2)',
  'POLYGON((52.0 23.0,55.0 23.0,55.0 25.5,52.0 25.5,52.0 23.0))',
  10.0, 230, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'SAUDI_YANBU_RED_SEA_V2',
  '20260903-v2',
  'Saudi Yanbu Red Sea (Test V2)',
  'POLYGON((37.0 22.2,40.5 22.2,40.5 25.8,37.0 25.8,37.0 22.2))',
  10.0, 240, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'VENEZUELA_JOSE_PARIA_V2',
  '20260903-v2',
  'Venezuela Jose / Paria (Test V2)',
  'POLYGON((-65.8 9.0,-61.0 9.0,-61.0 12.0,-65.8 12.0,-65.8 9.0))',
  10.0, 250, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'VENEZUELA_PARAGUANA_V2',
  '20260903-v2',
  'Venezuela Paraguana (Test V2)',
  'POLYGON((-72.0 10.5,-68.5 10.5,-68.5 13.2,-72.0 13.2,-72.0 10.5))',
  10.0, 260, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'RUSSIA_PRIMORSK_USTLUGA_V2',
  '20260903-v2',
  'Russia Primorsk / Ust-Luga (Test V2)',
  'POLYGON((26.0 58.3,30.8 58.3,30.8 61.2,26.0 61.2,26.0 58.3))',
  10.0, 270, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'RUSSIA_NOVOROSSIYSK_TUAPSE_V2',
  '20260903-v2',
  'Russia Novorossiysk / Tuapse (Test V2)',
  'POLYGON((36.2 42.8,40.0 42.8,40.0 45.5,36.2 45.5,36.2 42.8))',
  10.0, 280, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'RUSSIA_TAMAN_KAVKAZ_V2',
  '20260903-v2',
  'Russia Taman / Kavkaz (Test V2)',
  'POLYGON((35.0 44.0,37.8 44.0,37.8 46.2,35.0 46.2,35.0 44.0))',
  10.0, 290, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'NIGERIA_EAST_BONNY_QUAIBOE_V2',
  '20260903-v2',
  'Nigeria East — Bonny / Qua Iboe (Test V2)',
  'POLYGON((5.8 3.0,9.2 3.0,9.2 5.5,5.8 5.5,5.8 3.0))',
  10.0, 300, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'NIGERIA_WEST_FORCADOS_ESCRAVOS_V2',
  '20260903-v2',
  'Nigeria West — Forcados / Escravos (Test V2)',
  'POLYGON((3.8 3.5,6.5 3.5,6.5 5.8,3.8 5.8,3.8 3.5))',
  10.0, 310, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'ANGOLA_LUANDA_STS_V2',
  '20260903-v2',
  'Angola Luanda STS (Test V2)',
  'POLYGON((11.5 -10.5,14.2 -10.5,14.2 -7.5,11.5 -7.5,11.5 -10.5))',
  10.0, 320, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'ANGOLA_CABINDA_STS_V2',
  '20260903-v2',
  'Angola Cabinda STS (Test V2)',
  'POLYGON((10.5 -6.5,13.5 -6.5,13.5 -4.2,10.5 -4.2,10.5 -6.5))',
  10.0, 330, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'LIBYA_SIDRA_RASLANUF_V2',
  '20260903-v2',
  'Libya Sidra / Ras Lanuf (Test V2)',
  'POLYGON((16.5 29.2,20.2 29.2,20.2 32.2,16.5 32.2,16.5 29.2))',
  10.0, 340, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
),
(
  'CEYHAN_EASTMED_EXPORT_V2',
  '20260903-v2',
  'Ceyhan East Med Export (Test V2)',
  'POLYGON((34.5 35.0,37.0 35.0,37.0 37.5,34.5 37.5,34.5 35.0))',
  10.0, 350, 'TEST', 1, NULL,
  'TEST_ONLY_BBOX',
  'Engineering test bbox for STS historical calibration. Not an authoritative maritime, legal, sanctions, port or STS boundary.'
);

-- ============================================================
-- Part 2: Region Sets
-- ============================================================

-- Set 1: STS_HOTSPOT_HIGH_YIELD_V2 (14 regions)
INSERT IGNORE INTO dataanalysis.ship_sts_region_set_item
  (region_set_code, region_code, region_version, priority, enabled)
VALUES
  ('STS_HOTSPOT_HIGH_YIELD_V2', 'RIAU_EOPL_NIPAH_STS_V2',         '20260903-v2', 10,  1),
  ('STS_HOTSPOT_HIGH_YIELD_V2', 'LINGGI_MALACCA_STS_V2',          '20260903-v2', 20,  1),
  ('STS_HOTSPOT_HIGH_YIELD_V2', 'LOME_OFFSHORE_STS_V2',           '20260903-v2', 30,  1),
  ('STS_HOTSPOT_HIGH_YIELD_V2', 'PORT_SAID_EASTMED_STS_V2',       '20260903-v2', 40,  1),
  ('STS_HOTSPOT_HIGH_YIELD_V2', 'CYPRUS_OFFSHORE_STS_V2',         '20260903-v2', 50,  1),
  ('STS_HOTSPOT_HIGH_YIELD_V2', 'MALTA_HURDS_BANK_STS_V2',        '20260903-v2', 60,  1),
  ('STS_HOTSPOT_HIGH_YIELD_V2', 'AUGUSTA_SICILY_STS_V2',          '20260903-v2', 70,  1),
  ('STS_HOTSPOT_HIGH_YIELD_V2', 'LACONIAN_KALAMATA_STS_V2',       '20260903-v2', 80,  1),
  ('STS_HOTSPOT_HIGH_YIELD_V2', 'CEUTA_GIBRALTAR_STS_V2',         '20260903-v2', 90,  1),
  ('STS_HOTSPOT_HIGH_YIELD_V2', 'GULF_OMAN_FUJAIRAH_STS_V2',      '20260903-v2', 100, 1),
  ('STS_HOTSPOT_HIGH_YIELD_V2', 'SOHAR_STS_V2',                   '20260903-v2', 110, 1),
  ('STS_HOTSPOT_HIGH_YIELD_V2', 'NAKHODKA_KOZMINO_STS_V2',        '20260903-v2', 120, 1),
  ('STS_HOTSPOT_HIGH_YIELD_V2', 'HONG_KONG_OFFSHORE_STS_V2',      '20260903-v2', 130, 1),
  ('STS_HOTSPOT_HIGH_YIELD_V2', 'ARUBA_CURACAO_STS_V2',           '20260903-v2', 140, 1);

-- Set 2: CRUDE_EXPORT_APPROACH_V2 (15 regions)
INSERT IGNORE INTO dataanalysis.ship_sts_region_set_item
  (region_set_code, region_code, region_version, priority, enabled)
VALUES
  ('CRUDE_EXPORT_APPROACH_V2', 'NORTH_GULF_BASRA_KUWAIT_KHARG_V2',    '20260903-v2', 210, 1),
  ('CRUDE_EXPORT_APPROACH_V2', 'CENTRAL_GULF_RAS_TANURA_JUAYMAH_V2', '20260903-v2', 220, 1),
  ('CRUDE_EXPORT_APPROACH_V2', 'SOUTH_GULF_RUWAIS_JEBEL_DHANNA_V2',  '20260903-v2', 230, 1),
  ('CRUDE_EXPORT_APPROACH_V2', 'SAUDI_YANBU_RED_SEA_V2',             '20260903-v2', 240, 1),
  ('CRUDE_EXPORT_APPROACH_V2', 'VENEZUELA_JOSE_PARIA_V2',            '20260903-v2', 250, 1),
  ('CRUDE_EXPORT_APPROACH_V2', 'VENEZUELA_PARAGUANA_V2',             '20260903-v2', 260, 1),
  ('CRUDE_EXPORT_APPROACH_V2', 'RUSSIA_PRIMORSK_USTLUGA_V2',         '20260903-v2', 270, 1),
  ('CRUDE_EXPORT_APPROACH_V2', 'RUSSIA_NOVOROSSIYSK_TUAPSE_V2',      '20260903-v2', 280, 1),
  ('CRUDE_EXPORT_APPROACH_V2', 'RUSSIA_TAMAN_KAVKAZ_V2',             '20260903-v2', 290, 1),
  ('CRUDE_EXPORT_APPROACH_V2', 'NIGERIA_EAST_BONNY_QUAIBOE_V2',      '20260903-v2', 300, 1),
  ('CRUDE_EXPORT_APPROACH_V2', 'NIGERIA_WEST_FORCADOS_ESCRAVOS_V2',  '20260903-v2', 310, 1),
  ('CRUDE_EXPORT_APPROACH_V2', 'ANGOLA_LUANDA_STS_V2',               '20260903-v2', 320, 1),
  ('CRUDE_EXPORT_APPROACH_V2', 'ANGOLA_CABINDA_STS_V2',              '20260903-v2', 330, 1),
  ('CRUDE_EXPORT_APPROACH_V2', 'LIBYA_SIDRA_RASLANUF_V2',            '20260903-v2', 340, 1),
  ('CRUDE_EXPORT_APPROACH_V2', 'CEYHAN_EASTMED_EXPORT_V2',           '20260903-v2', 350, 1);

-- Set 3: GLOBAL_STS_EXPANDED_V2 (29 regions = both sets combined)
INSERT IGNORE INTO dataanalysis.ship_sts_region_set_item
  (region_set_code, region_code, region_version, priority, enabled)
VALUES
  ('GLOBAL_STS_EXPANDED_V2', 'RIAU_EOPL_NIPAH_STS_V2',              '20260903-v2', 10,  1),
  ('GLOBAL_STS_EXPANDED_V2', 'LINGGI_MALACCA_STS_V2',               '20260903-v2', 20,  1),
  ('GLOBAL_STS_EXPANDED_V2', 'LOME_OFFSHORE_STS_V2',                '20260903-v2', 30,  1),
  ('GLOBAL_STS_EXPANDED_V2', 'PORT_SAID_EASTMED_STS_V2',            '20260903-v2', 40,  1),
  ('GLOBAL_STS_EXPANDED_V2', 'CYPRUS_OFFSHORE_STS_V2',              '20260903-v2', 50,  1),
  ('GLOBAL_STS_EXPANDED_V2', 'MALTA_HURDS_BANK_STS_V2',             '20260903-v2', 60,  1),
  ('GLOBAL_STS_EXPANDED_V2', 'AUGUSTA_SICILY_STS_V2',               '20260903-v2', 70,  1),
  ('GLOBAL_STS_EXPANDED_V2', 'LACONIAN_KALAMATA_STS_V2',            '20260903-v2', 80,  1),
  ('GLOBAL_STS_EXPANDED_V2', 'CEUTA_GIBRALTAR_STS_V2',              '20260903-v2', 90,  1),
  ('GLOBAL_STS_EXPANDED_V2', 'GULF_OMAN_FUJAIRAH_STS_V2',           '20260903-v2', 100, 1),
  ('GLOBAL_STS_EXPANDED_V2', 'SOHAR_STS_V2',                        '20260903-v2', 110, 1),
  ('GLOBAL_STS_EXPANDED_V2', 'NAKHODKA_KOZMINO_STS_V2',             '20260903-v2', 120, 1),
  ('GLOBAL_STS_EXPANDED_V2', 'HONG_KONG_OFFSHORE_STS_V2',           '20260903-v2', 130, 1),
  ('GLOBAL_STS_EXPANDED_V2', 'ARUBA_CURACAO_STS_V2',                '20260903-v2', 140, 1),
  ('GLOBAL_STS_EXPANDED_V2', 'NORTH_GULF_BASRA_KUWAIT_KHARG_V2',    '20260903-v2', 210, 1),
  ('GLOBAL_STS_EXPANDED_V2', 'CENTRAL_GULF_RAS_TANURA_JUAYMAH_V2', '20260903-v2', 220, 1),
  ('GLOBAL_STS_EXPANDED_V2', 'SOUTH_GULF_RUWAIS_JEBEL_DHANNA_V2',   '20260903-v2', 230, 1),
  ('GLOBAL_STS_EXPANDED_V2', 'SAUDI_YANBU_RED_SEA_V2',              '20260903-v2', 240, 1),
  ('GLOBAL_STS_EXPANDED_V2', 'VENEZUELA_JOSE_PARIA_V2',             '20260903-v2', 250, 1),
  ('GLOBAL_STS_EXPANDED_V2', 'VENEZUELA_PARAGUANA_V2',              '20260903-v2', 260, 1),
  ('GLOBAL_STS_EXPANDED_V2', 'RUSSIA_PRIMORSK_USTLUGA_V2',          '20260903-v2', 270, 1),
  ('GLOBAL_STS_EXPANDED_V2', 'RUSSIA_NOVOROSSIYSK_TUAPSE_V2',       '20260903-v2', 280, 1),
  ('GLOBAL_STS_EXPANDED_V2', 'RUSSIA_TAMAN_KAVKAZ_V2',              '20260903-v2', 290, 1),
  ('GLOBAL_STS_EXPANDED_V2', 'NIGERIA_EAST_BONNY_QUAIBOE_V2',       '20260903-v2', 300, 1),
  ('GLOBAL_STS_EXPANDED_V2', 'NIGERIA_WEST_FORCADOS_ESCRAVOS_V2',   '20260903-v2', 310, 1),
  ('GLOBAL_STS_EXPANDED_V2', 'ANGOLA_LUANDA_STS_V2',                '20260903-v2', 320, 1),
  ('GLOBAL_STS_EXPANDED_V2', 'ANGOLA_CABINDA_STS_V2',               '20260903-v2', 330, 1),
  ('GLOBAL_STS_EXPANDED_V2', 'LIBYA_SIDRA_RASLANUF_V2',             '20260903-v2', 340, 1),
  ('GLOBAL_STS_EXPANDED_V2', 'CEYHAN_EASTMED_EXPORT_V2',            '20260903-v2', 350, 1);

-- ============================================================
-- Part 3: Post-insert verification
-- ============================================================

-- V2 region count (expect 29)
SELECT COUNT(*) AS v2_region_count
FROM dataanalysis.ship_sts_risk_region
WHERE region_code LIKE '%_V2';

-- V2 set counts
SELECT region_set_code, COUNT(*) AS region_count
FROM dataanalysis.ship_sts_region_set_item
WHERE region_set_code IN (
  'STS_HOTSPOT_HIGH_YIELD_V2',
  'CRUDE_EXPORT_APPROACH_V2',
  'GLOBAL_STS_EXPANDED_V2'
)
AND enabled = 1
GROUP BY region_set_code
ORDER BY region_set_code;

-- V1 untouched sanity check (expect 12 regions, 4 sets)
SELECT COUNT(*) AS v1_region_count
FROM dataanalysis.ship_sts_risk_region
WHERE region_code LIKE '%_V1';
