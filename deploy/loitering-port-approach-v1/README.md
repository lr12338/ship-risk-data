# Loitering Port Approach V1 manual test materials

`xxl-job-params.json` contains preflight, 20/100/500-ship dry-run payloads, and a `formal5000` payload for the 5,000-ship list. Paste a single payload into XXL-JOB manually; this repository provides no trigger command. All dry-run payloads use `parallelism=1` and `enableInsert=false`; `formal5000` uses `enableInsert=true` and `storeDetails=true` and writes to the database.

Run `scene-preflight.sql` in an approved database client before every retry to verify the candidate scope. The data sources run MySQL 5.7 and 5.6 respectively, so the service's JTS validation is authoritative; do not retry after it reports an invalid row until the authoritative geometry is repaired or explicitly excluded.

`scene-repair-candidates.sql` contains the 55 rows detected on 2026-08-07 for business-owner review. It is read-only; source-data repair must be approved and applied by the data owner.

The 55 source geometries were repaired on 2026-08-07 after review: 54 self-intersecting geometries were fixed with JTS `GeometryFixer`, and alert zone `2459165` was reconstructed from its published center and 0.3-nautical-mile radius. Original WKB is recoverable from `dataanalysis.loitering_scene_geometry_backup_20260807` and `ais41.loitering_scene_geometry_backup_20260807`.

The same production check also found port-zone `dataanalysis.zones_with_port_id.id=15713` with a valid polygon but an out-of-range centroid longitude (`-256.537263`). Its coordinates were normalized by adding 360 degrees to longitude; the original WKB was added to `dataanalysis.loitering_scene_geometry_backup_20260807` before the single-row update. Recheck port centroids before retrying if `LOITERING_PORT_SCENE_UNAVAILABLE` is raised from the port list.

Use `query.sql` after the manual run. It separates run-window counts, WORLD_SEA audit totals, event subtype, SOG, and distance-to-port distributions. Port-scene dry-run counters are deliberately emitted in the XXL execution log because dry runs do not persist formal events.

Expected version: `LOITERING_V2_PORT_APPROACH_V1`. A successful service restart only means the code is deployable; it does not validate the rule against production trajectories.
