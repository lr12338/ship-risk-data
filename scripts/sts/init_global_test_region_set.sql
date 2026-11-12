-- =====================================================================
-- STS Phase 1.3 — Global Test Region Set Initialization
--
-- Contains:
--   12 canonical test regions (status=TEST, source_type=TEST_ONLY_BBOX)
--   4 region sets:
--     PERSIAN_GULF_STS_TEST_V1     (1 region)
--     RUSSIA_STS_TEST_V1           (3 regions: Baltic + Black Sea + Far East)
--     VENEZUELA_STS_TEST_V1        (2 regions: Jose + Paraguana)
--     GLOBAL_STS_TEST_REGION_SET_V1 (all 12 regions)
--
-- All regions are TEST_ONLY_BBOX. NOT authoritative maritime boundaries.
-- Execute manually against dataanalysis database.
-- =====================================================================

SET NAMES utf8mb4;

-- ============================================================
-- 1. Canonical regions
-- ============================================================

INSERT IGNORE INTO dataanalysis.ship_sts_risk_region
  (region_code, region_version, region_name, geometry_wkt, analysis_buffer_nm,
   priority, status, enabled, geometry_fingerprint, source_type, source_note)
VALUES
(
  'PERSIAN_GULF_TEST_V1',
  '20260902-v1',
  'Persian Gulf (Test)',
  'POLYGON((47.5 23.5,56.5 23.5,56.5 30.5,47.5 30.5,47.5 23.5))',
  10.0,
  100,
  'TEST',
  1,
  NULL,
  'TEST_ONLY_BBOX',
  'NOT_AUTHORITATIVE_MARITIME_BOUNDARY; covers Ras Tanura, Juaymah, Mina al-Ahmadi, Basra, Kharg, Ruwais'
),
(
  'GULF_OF_OMAN_FUJAIRAH_TEST_V1',
  '20260903-v1',
  'Gulf of Oman / Fujairah (Test)',
  'POLYGON((54.0 22.5,59.5 22.5,59.5 27.0,54.0 27.0,54.0 22.5))',
  10.0,
  110,
  'TEST',
  1,
  NULL,
  'TEST_ONLY_BBOX',
  'NOT_AUTHORITATIVE_MARITIME_BOUNDARY; covers Fujairah, Khor Fakkan, Sohar offshore approaches'
),
(
  'SINGAPORE_MALACCA_TEST_V1',
  '20260903-v1',
  'Singapore / Malacca Strait (Test)',
  'POLYGON((98.0 0.0,105.5 0.0,105.5 6.5,98.0 6.5,98.0 0.0))',
  10.0,
  120,
  'TEST',
  1,
  NULL,
  'TEST_ONLY_BBOX',
  'NOT_AUTHORITATIVE_MARITIME_BOUNDARY; wide strait and offshore coverage'
),
(
  'VENEZUELA_JOSE_TEST_V1',
  '20260903-v1',
  'Venezuela Jose (Test)',
  'POLYGON((-66.5 8.0,-60.0 8.0,-60.0 12.5,-66.5 12.5,-66.5 8.0))',
  10.0,
  130,
  'TEST',
  1,
  NULL,
  'TEST_ONLY_BBOX',
  'NOT_AUTHORITATIVE_MARITIME_BOUNDARY; covers Jose Terminal, Puerto La Cruz, Paria approaches'
),
(
  'VENEZUELA_PARAGUANA_TEST_V1',
  '20260903-v1',
  'Venezuela Paraguana (Test)',
  'POLYGON((-72.5 10.0,-68.0 10.0,-68.0 13.5,-72.5 13.5,-72.5 10.0))',
  10.0,
  135,
  'TEST',
  1,
  NULL,
  'TEST_ONLY_BBOX',
  'NOT_AUTHORITATIVE_MARITIME_BOUNDARY; covers Amuay, Cardon, Paraguana approaches'
),
(
  'RUSSIA_BALTIC_TEST_V1',
  '20260903-v1',
  'Russia Baltic (Test)',
  'POLYGON((26.0 58.5,31.5 58.5,31.5 61.5,26.0 61.5,26.0 58.5))',
  10.0,
  140,
  'TEST',
  1,
  NULL,
  'TEST_ONLY_BBOX',
  'NOT_AUTHORITATIVE_MARITIME_BOUNDARY; covers Primorsk, Ust-Luga, Gulf of Finland'
),
(
  'RUSSIA_BLACK_SEA_TEST_V1',
  '20260903-v1',
  'Russia Black Sea (Test)',
  'POLYGON((35.0 42.0,41.0 42.0,41.0 46.5,35.0 46.5,35.0 42.0))',
  10.0,
  145,
  'TEST',
  1,
  NULL,
  'TEST_ONLY_BBOX',
  'NOT_AUTHORITATIVE_MARITIME_BOUNDARY; covers Novorossiysk, Tuapse, NE Black Sea'
),
(
  'RUSSIA_FAR_EAST_TEST_V1',
  '20260903-v1',
  'Russia Far East (Test)',
  'POLYGON((131.0 40.0,136.5 40.0,136.5 44.5,131.0 44.5,131.0 40.0))',
  10.0,
  150,
  'TEST',
  1,
  NULL,
  'TEST_ONLY_BBOX',
  'NOT_AUTHORITATIVE_MARITIME_BOUNDARY; covers Kozmino, Nakhodka, Sea of Japan approaches'
),
(
  'WEST_AFRICA_LOME_TEST_V1',
  '20260903-v1',
  'West Africa Lome (Test)',
  'POLYGON((-1.5 4.0,2.5 4.0,2.5 7.5,-1.5 7.5,-1.5 4.0))',
  10.0,
  160,
  'TEST',
  1,
  NULL,
  'TEST_ONLY_BBOX',
  'NOT_AUTHORITATIVE_MARITIME_BOUNDARY; covers Togo/Lome offshore STS area'
),
(
  'GULF_OF_GUINEA_TEST_V1',
  '20260903-v1',
  'Gulf of Guinea (Test)',
  'POLYGON((-10.0 -6.0,14.0 -6.0,14.0 7.5,-10.0 7.5,-10.0 -6.0))',
  10.0,
  170,
  'TEST',
  1,
  NULL,
  'TEST_ONLY_BBOX',
  'NOT_AUTHORITATIVE_MARITIME_BOUNDARY; wide Gulf of Guinea coverage — split before production use'
),
(
  'MEDITERRANEAN_SICILY_MALTA_TEST_V1',
  '20260903-v1',
  'Mediterranean Sicily / Malta (Test)',
  'POLYGON((11.0 34.0,17.0 34.0,17.0 39.0,11.0 39.0,11.0 34.0))',
  10.0,
  180,
  'TEST',
  1,
  NULL,
  'TEST_ONLY_BBOX',
  'NOT_AUTHORITATIVE_MARITIME_BOUNDARY; covers Sicily Channel and Malta approaches'
),
(
  'MALAYSIA_INDONESIA_RIAU_TEST_V1',
  '20260903-v1',
  'Malaysia / Indonesia Riau (Test)',
  'POLYGON((102.0 0.0,106.0 0.0,106.0 3.0,102.0 3.0,102.0 0.0))',
  10.0,
  190,
  'TEST',
  1,
  NULL,
  'TEST_ONLY_BBOX',
  'NOT_AUTHORITATIVE_MARITIME_BOUNDARY; covers Nipah/Riau and Singapore eastern approaches'
);

-- ============================================================
-- 2. Region Sets
-- ============================================================

-- PERSIAN_GULF_STS_TEST_V1 — single region
INSERT IGNORE INTO dataanalysis.ship_sts_region_set_item
  (region_set_code, region_code, region_version, priority, enabled)
VALUES
  ('PERSIAN_GULF_STS_TEST_V1', 'PERSIAN_GULF_TEST_V1', '20260902-v1', 100, 1);

-- RUSSIA_STS_TEST_V1 — 3 regions
INSERT IGNORE INTO dataanalysis.ship_sts_region_set_item
  (region_set_code, region_code, region_version, priority, enabled)
VALUES
  ('RUSSIA_STS_TEST_V1', 'RUSSIA_BALTIC_TEST_V1',   '20260903-v1', 100, 1),
  ('RUSSIA_STS_TEST_V1', 'RUSSIA_BLACK_SEA_TEST_V1', '20260903-v1', 110, 1),
  ('RUSSIA_STS_TEST_V1', 'RUSSIA_FAR_EAST_TEST_V1',  '20260903-v1', 120, 1);

-- VENEZUELA_STS_TEST_V1 — 2 regions
INSERT IGNORE INTO dataanalysis.ship_sts_region_set_item
  (region_set_code, region_code, region_version, priority, enabled)
VALUES
  ('VENEZUELA_STS_TEST_V1', 'VENEZUELA_JOSE_TEST_V1',      '20260903-v1', 100, 1),
  ('VENEZUELA_STS_TEST_V1', 'VENEZUELA_PARAGUANA_TEST_V1', '20260903-v1', 110, 1);

-- GLOBAL_STS_TEST_REGION_SET_V1 — all 12 regions
INSERT IGNORE INTO dataanalysis.ship_sts_region_set_item
  (region_set_code, region_code, region_version, priority, enabled)
VALUES
  ('GLOBAL_STS_TEST_REGION_SET_V1', 'PERSIAN_GULF_TEST_V1',              '20260902-v1', 100, 1),
  ('GLOBAL_STS_TEST_REGION_SET_V1', 'GULF_OF_OMAN_FUJAIRAH_TEST_V1',     '20260903-v1', 110, 1),
  ('GLOBAL_STS_TEST_REGION_SET_V1', 'SINGAPORE_MALACCA_TEST_V1',         '20260903-v1', 120, 1),
  ('GLOBAL_STS_TEST_REGION_SET_V1', 'VENEZUELA_JOSE_TEST_V1',            '20260903-v1', 130, 1),
  ('GLOBAL_STS_TEST_REGION_SET_V1', 'VENEZUELA_PARAGUANA_TEST_V1',       '20260903-v1', 135, 1),
  ('GLOBAL_STS_TEST_REGION_SET_V1', 'RUSSIA_BALTIC_TEST_V1',             '20260903-v1', 140, 1),
  ('GLOBAL_STS_TEST_REGION_SET_V1', 'RUSSIA_BLACK_SEA_TEST_V1',          '20260903-v1', 145, 1),
  ('GLOBAL_STS_TEST_REGION_SET_V1', 'RUSSIA_FAR_EAST_TEST_V1',           '20260903-v1', 150, 1),
  ('GLOBAL_STS_TEST_REGION_SET_V1', 'WEST_AFRICA_LOME_TEST_V1',          '20260903-v1', 160, 1),
  ('GLOBAL_STS_TEST_REGION_SET_V1', 'GULF_OF_GUINEA_TEST_V1',            '20260903-v1', 170, 1),
  ('GLOBAL_STS_TEST_REGION_SET_V1', 'MEDITERRANEAN_SICILY_MALTA_TEST_V1','20260903-v1', 180, 1),
  ('GLOBAL_STS_TEST_REGION_SET_V1', 'MALAYSIA_INDONESIA_RIAU_TEST_V1',   '20260903-v1', 190, 1);

-- ============================================================
-- 3. Verification queries
-- ============================================================

-- Check region count
SELECT COUNT(*) AS total_regions FROM dataanalysis.ship_sts_risk_region
  WHERE status IN ('TEST', 'APPROVED') AND enabled = 1;
-- Expected: 12

-- Check each region set
SELECT region_set_code, COUNT(*) AS region_count
FROM dataanalysis.ship_sts_region_set_item
WHERE enabled = 1
GROUP BY region_set_code
ORDER BY region_set_code;
-- Expected:
--   GLOBAL_STS_TEST_REGION_SET_V1     12
--   PERSIAN_GULF_STS_TEST_V1           1
--   RUSSIA_STS_TEST_V1                 3
--   VENEZUELA_STS_TEST_V1              2
