-- Read-only candidate inventory for LOITERING_V2_PORT_APPROACH_V1.
-- dataanalysis is MySQL 5.7 and ais41 is MySQL 5.6, so their spatial validity functions are incompatible.
-- The service's JTS validation is authoritative and reports source/id/name for any invalid row.

-- Port operational zones: the predicate matches LoiteringPortSceneLoader exactly.
SELECT ST_GeometryType(bound) AS geometry_type, ST_SRID(bound) AS srid, COUNT(*) AS rows
FROM dataanalysis.zones_with_port_id
WHERE is_delete = 0
  AND bound IS NOT NULL
  AND zones_type IN (2, 3)
  AND LOWER(name) NOT LIKE '%anchor%'
  AND name NOT LIKE '%锚地%'
  AND name NOT LIKE '%锚泊%'
GROUP BY ST_GeometryType(bound), ST_SRID(bound)
ORDER BY geometry_type, srid;

-- Full candidate inventory for review after JTS reports an invalid row.
SELECT id, name, zones_type, ST_GeometryType(bound) AS geometry_type,
       ST_SRID(bound) AS srid, ST_AsText(bound) AS geometry_wkt
FROM dataanalysis.zones_with_port_id
WHERE is_delete = 0
  AND bound IS NOT NULL
  AND zones_type IN (2, 3)
  AND LOWER(name) NOT LIKE '%anchor%'
  AND name NOT LIKE '%锚地%'
  AND name NOT LIKE '%锚泊%'
ORDER BY id;

-- Anchorage zones: the predicate matches LoiteringPortSceneLoader exactly.
SELECT ST_GeometryType(bound) AS geometry_type, ST_SRID(bound) AS srid, COUNT(*) AS rows
FROM ais41.alertzones
WHERE bound IS NOT NULL
  AND ST_GeometryType(bound) IN ('POLYGON', 'MULTIPOLYGON')
  AND (LOWER(name) LIKE '%anchor%' OR name LIKE '%锚地%' OR name LIKE '%锚泊%')
GROUP BY ST_GeometryType(bound), ST_SRID(bound)
ORDER BY geometry_type, srid;

-- Full candidate inventory for review after JTS reports an invalid row.
SELECT oid, name, ST_GeometryType(bound) AS geometry_type,
       ST_SRID(bound) AS srid, ST_AsText(bound) AS geometry_wkt
FROM ais41.alertzones
WHERE bound IS NOT NULL
  AND ST_GeometryType(bound) IN ('POLYGON', 'MULTIPOLYGON')
  AND (LOWER(name) LIKE '%anchor%' OR name LIKE '%锚地%' OR name LIKE '%锚泊%')
ORDER BY oid;
