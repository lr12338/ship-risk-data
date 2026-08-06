-- BLOCKED_INVALID_GEOMETRY: do not run until a revised dataset passes VALIDATE_STAGE
-- and has been explicitly activated.
SELECT COUNT(*) AS table_exists
FROM information_schema.tables
WHERE table_schema='dataanalysis' AND table_name='ship_behavior_world_sea';

SELECT COUNT(*) AS feature_count, COUNT(DISTINCT ID) AS distinct_id,
       SUM(the_geom IS NULL) AS null_geometry
FROM dataanalysis.ship_behavior_world_sea;

SELECT GeometryType(the_geom) AS geometry_type, COUNT(*) AS feature_count
FROM dataanalysis.ship_behavior_world_sea
GROUP BY GeometryType(the_geom);

SELECT dataset_version, source_file_name, source_file_sha256, geometry_fingerprint,
       feature_count, polygon_count, multipolygon_count, vertex_count,
       coordinate_assumption, imported_at, imported_by, status
FROM dataanalysis.ship_behavior_world_sea_meta
WHERE dataset_version='WORLD_SEA_102_V1';
