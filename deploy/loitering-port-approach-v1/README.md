# Loitering Port Approach V1 manual test materials

`xxl-job-params.json` contains preflight, 20/100/500-ship dry-run payloads, and a `formal5000` payload for the 5,000-ship list. Paste a single payload into XXL-JOB manually; this repository provides no trigger command. All dry-run payloads use `parallelism=1` and `enableInsert=false`; `formal5000` uses `enableInsert=true` and `storeDetails=true` and writes to the database.

Use `query.sql` after the manual run. It separates run-window counts, WORLD_SEA audit totals, event subtype, SOG, and distance-to-port distributions. Port-scene dry-run counters are deliberately emitted in the XXL execution log because dry runs do not persist formal events.

Expected version: `LOITERING_V2_PORT_APPROACH_V1`. A successful service restart only means the code is deployable; it does not validate the rule against production trajectories.
