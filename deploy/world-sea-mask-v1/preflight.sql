-- Run after WORLD_SEA_102_V1R1 is formally published and activated.
SELECT COUNT(*) AS formal_table_exists
FROM information_schema.tables
WHERE table_schema='dataanalysis' AND table_name='ship_behavior_world_sea';

SELECT COUNT(*) AS feature_count,COUNT(DISTINCT ID) AS distinct_id,
       SUM(the_geom IS NULL) AS null_geometry,
       SUM(SRID(the_geom)=101) AS srid_101_count
FROM dataanalysis.ship_behavior_world_sea;

SELECT GeometryType(the_geom) AS geometry_type,COUNT(*) AS feature_count
FROM dataanalysis.ship_behavior_world_sea
GROUP BY GeometryType(the_geom);

SELECT dataset_version,geometry_fingerprint,feature_count,status
FROM dataanalysis.ship_behavior_world_sea_meta
WHERE status='ACTIVE';

SELECT release_id,dataset_version,geometry_fingerprint,status,last_completed_step,
       review_status,authorization_mode,authorization_ref,authorized_at,
       stage_table_name,formal_table_name
FROM dataanalysis.ship_behavior_world_sea_release
WHERE dataset_version='WORLD_SEA_102_V1R1'
ORDER BY created_at DESC;

SELECT COUNT(*) AS active_count
FROM dataanalysis.ship_behavior_world_sea_meta
WHERE status='ACTIVE';
