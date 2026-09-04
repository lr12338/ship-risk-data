-- STS meeting-priority region set. Reuses existing versioned polygons only.
-- Execute manually after verifying every referenced region exists and is enabled.
-- The preflight query must return resolved_region_count=9 before running the INSERT.
SELECT COUNT(*) AS resolved_region_count
FROM dataanalysis.ship_sts_risk_region r
WHERE r.enabled = 1
  AND (r.region_code, r.region_version) IN (
    ('PERSIAN_GULF_TEST_V1',        '20260902-v1'),
    ('GULF_OMAN_FUJAIRAH_STS_V2',  '20260903-v2'),
    ('SOHAR_STS_V2',                '20260903-v2'),
    ('SINGAPORE_MALACCA_TEST_V1',  '20260903-v1'),
    ('VENEZUELA_JOSE_TEST_V1',     '20260903-v1'),
    ('VENEZUELA_PARAGUANA_TEST_V1','20260903-v1'),
    ('RUSSIA_BALTIC_TEST_V1',      '20260903-v1'),
    ('RUSSIA_BLACK_SEA_TEST_V1',   '20260903-v1'),
    ('RUSSIA_FAR_EAST_TEST_V1',    '20260903-v1')
  );

INSERT IGNORE INTO dataanalysis.ship_sts_region_set_item
  (region_set_code, region_code, region_version, priority, enabled)
VALUES
  ('STS_MEETING_PRIORITY_REGION_SET_V1', 'PERSIAN_GULF_TEST_V1',       '20260902-v1', 10, 1),
  ('STS_MEETING_PRIORITY_REGION_SET_V1', 'GULF_OMAN_FUJAIRAH_STS_V2',  '20260903-v2', 20, 1),
  ('STS_MEETING_PRIORITY_REGION_SET_V1', 'SOHAR_STS_V2',                '20260903-v2', 30, 1),
  ('STS_MEETING_PRIORITY_REGION_SET_V1', 'SINGAPORE_MALACCA_TEST_V1',  '20260903-v1', 40, 1),
  ('STS_MEETING_PRIORITY_REGION_SET_V1', 'VENEZUELA_JOSE_TEST_V1',     '20260903-v1', 50, 1),
  ('STS_MEETING_PRIORITY_REGION_SET_V1', 'VENEZUELA_PARAGUANA_TEST_V1','20260903-v1', 60, 1),
  ('STS_MEETING_PRIORITY_REGION_SET_V1', 'RUSSIA_BALTIC_TEST_V1',      '20260903-v1', 70, 1),
  ('STS_MEETING_PRIORITY_REGION_SET_V1', 'RUSSIA_BLACK_SEA_TEST_V1',   '20260903-v1', 80, 1),
  ('STS_MEETING_PRIORITY_REGION_SET_V1', 'RUSSIA_FAR_EAST_TEST_V1',    '20260903-v1', 90, 1);

-- Postflight must return exactly 9 enabled rows, all joined to enabled canonical geometry.
SELECT si.region_set_code, si.region_code, si.region_version, si.priority,
       r.enabled AS canonical_region_enabled
FROM dataanalysis.ship_sts_region_set_item si
JOIN dataanalysis.ship_sts_risk_region r
  ON r.region_code = si.region_code AND r.region_version = si.region_version
WHERE si.region_set_code = 'STS_MEETING_PRIORITY_REGION_SET_V1'
  AND si.enabled = 1
  AND r.enabled = 1
ORDER BY si.priority, si.region_code;
