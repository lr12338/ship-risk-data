-- Read-only review package for invalid LOITERING port-scene geometries detected on 2026-08-07.
-- Export the WKT from these queries for visual review. Do not run a bulk UPDATE or GeometryFixer write-back.

SELECT id, name, zones_type, ST_SRID(bound) AS srid, ST_AsText(bound) AS geometry_wkt
FROM dataanalysis.zones_with_port_id
WHERE id IN (3112,4512,4518,5715,5726,9486,9846,9978,10070,10197,10474,10502,10703,11087,12884,16132)
ORDER BY id;

SELECT oid, name, ST_SRID(bound) AS srid, ST_AsText(bound) AS geometry_wkt
FROM ais41.alertzones
WHERE oid IN (6168,125932,191447,199272,226001,227249,727332,775218,1040261,1040262,1040263,
              1148447,1148449,1409950,1483268,1679861,1769422,2024513,2121419,2123416,2264371,
              2299766,2328861,2328862,2351465,2432144,2433415,2450597,2459165,2461185,2469327,
              2472782,2479485,2481054,2483097,2487890,2494451,2525290,2544218)
ORDER BY oid;
