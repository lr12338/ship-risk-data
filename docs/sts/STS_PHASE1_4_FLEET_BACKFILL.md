# STS Phase 1.4 — Fleet Historical Backfill

## Overview

Resumable, sharded, region-first STS historical backfill runner.

Source: `dataanalysis.ship_behavior_target_ship` (READ-ONLY)
Output: `dataanalysis.ship_sts_shadow_event` (shadow only, no production writes)
Orchestration: `dataanalysis.ship_sts_backfill_batch` + `dataanalysis.ship_sts_backfill_task`

## Target Table Semantics

**Source table**: `ship_behavior_target_ship` — existing target ship list with version management via `list_batch_no`.

**READ-ONLY CONTRACT**: The backfill runner never issues INSERT/UPDATE/DELETE/DDL against this table. All access is SELECT only.

**Batches found**:
- `merchant-50k-2026h1-v1` — 50,000 rows, 44,760 physical ships
- `liquid-container-5000-2025h2-2026h1-v1` — 5,000 rows, 4,677 physical ships

## Physical Ship Definition

A "physical ship" is identified by:

- **IMO grouping** (preferred): If IMO is a valid 7-digit number, `physicalShipKey = IMO:{imo}`. All MMSIs in the same `list_batch_no` sharing that IMO are treated as aliases of the same physical ship.
- **MMSI fallback**: If IMO is missing or invalid (not 7 digits), `physicalShipKey = MMSI:{mmsi}`. Each MMSI is its own physical ship.

**Known aliases only**: `ship_behavior_target_ship` has no `first_seen`/`last_seen`, so we know *which* MMSIs belong to a ship but not *when* each was active. During a time slice, we attempt trajectory loading for all known MMSIs; those with no data are naturally skipped.

## Multi-MMSI Semantics

- One physical ship can have multiple MMSI aliases (MMSI changes over time).
- `primaryMmsi` is for display/logging only — it never affects identity safety.
- Region-first discovery: if any alias MMSI is seen in a region, the entire physical ship enters STS processing.
- Self-pair protection: all alias MMSIs of a physical OWN ship are excluded from target candidates.

## Identity Conflict Safety

- A MMSI mapping to multiple different IMOs within the same batch is an `AMBIGUOUS_MMSI_IDENTITY` conflict.
- Due to `UNIQUE(list_batch_no, mmsi)` on the source table, within-batch conflicts cannot occur.
- The runner still validates at batch init and fails closed if conflicts are found.

## Region-First Architecture

**Goal**: avoid loading HBase trajectories for 5000+ ships that never entered a risk region.

```
Selected Fleet (MMSI set)
        +
DB Region Set (analysis geometries)
        +
7-day time slice
        ↓
Spatial AIS replay → MMSIs seen in region
        ↓
Intersect with target fleet MMSIs
        ↓
Map back to physical ships
        ↓
Only these ships → full STS pipeline
```

**Fail-closed**: if any region's discovery is incomplete (truncated, timeout, error), the entire task is FAILED. We never silently produce results from partial discovery.

**One load per ship**: if a ship is in multiple regions, its trajectory is loaded exactly once per time slice.

## Backfill Tables

### `ship_sts_backfill_batch`

Parent batch record. One row = one fleet offset × one region set × one multi-year range.

Key fields:
- `batch_no` — PK, human-readable identifier
- `config_fingerprint` — SHA-256 of all input params (resume guard)
- `target_list_fingerprint` — SHA-256 of selected fleet identities (resume guard)
- `region_set_fingerprint` — SHA-256 of region definitions (resume guard)
- `expected_target_count` / `actual_target_count` — safety lock
- `status` — PENDING / RUNNING / PARTIAL_FAILED / COMPLETE / BLOCKED
- Progress counters: total/completed/failed tasks, cumulative events

### `ship_sts_backfill_task`

One row = one time-slice task (1–7 days).

Key fields:
- `id` — auto-increment PK
- `batch_no` — FK to parent
- `slice_seq` — 0-based sequence within batch
- `slice_from` / `slice_to` — [inclusive, exclusive) time range
- `status` — PENDING / RUNNING / FAILED / COMPLETE
- `retry_count` — max 3 retries
- Per-task statistics: candidate ships, processed ships, events, shadow counts

UNIQUE keys: `(batch_no, slice_seq)`, `(batch_no, slice_from, slice_to)`.

**NOT auto-created at startup.** Apply DDL manually before first run.

## Resume Behavior

### First invocation

1. Validates params, loads target fleet and region set
2. Computes config / target / region fingerprints
3. Creates parent batch row
4. Generates all slice task rows (PENDING)
5. Claims and runs one task

### Resume invocation

1. Same params → recomputes all fingerprints
2. If fingerprints match existing batch → RESUME
3. Claims next PENDING task (or stale RUNNING task older than 180 min)
4. Runs one task, updates progress

### Fail-closed on change

If any fingerprint differs from the stored batch:
- `TARGET_LIST_CHANGED_AFTER_BATCH_INIT` — target list was modified
- `REGION_SET_CHANGED_AFTER_BATCH_INIT` — region definitions changed
- `BATCH_CONFIG_CONFLICT` — other input params changed

### Task claim safety

- Uses transaction with `SELECT ... FOR UPDATE` + `UPDATE status='RUNNING'`
- Stale RUNNING tasks (>180 min) are reclaimed first
- Max 3 retries per task

## XXL Handler

**Name**: `shipStsHistoricalFleetBackfillJobHandler`

**One invocation = one slice task.** Never runs multiple slices in a single call.

### Parameters (single-line JSON)

| Field | Type | Description |
|---|---|---|
| `targetListBatchNo` | string | list_batch_no from ship_behavior_target_ship |
| `expectedTargetCount` | int | expected physical ship count (safety lock) |
| `shipOffset` | int | physical ship offset (0-based) |
| `shipCount` | int | physical ship count per job (max 5000) |
| `regionSetCode` | string | region set code from DB |
| `from` | string | start time "yyyy-MM-dd HH:mm:ss" |
| `to` | string | end time "yyyy-MM-dd HH:mm:ss" |
| `sliceDays` | int | slice size in days (1–7, recommended 7) |
| `batchNo` | string | parent batch identifier |

**Hardcoded**: SHADOW_ONLY, serial execution, ALONGSIDE_V3_BASELINE rule.

## Smoke Strategy

Start small, scale up.

**A. 100 ships × 1 month** — validate pipeline end-to-end
**B. 500 ships × 3 months** — validate scaling and resume
**C. 5000 ships × 1 year** — validate fleet-scale performance
**D. 5000 ships × multi-year** — full backfill

Never start with 5000×multi-year as the first run.

## Database Check SQL

### 1. Target list audit

```sql
SELECT list_batch_no, COUNT(*) AS rows,
  SUM(CASE WHEN enabled=1 THEN 1 ELSE 0 END) AS enabled_rows,
  COUNT(DISTINCT CASE WHEN imo REGEXP '^[0-9]{7}$' THEN CONCAT('IMO:',imo)
       WHEN mmsi IS NOT NULL AND mmsi != '' THEN CONCAT('MMSI:',mmsi) END) AS physical_ships
FROM dataanalysis.ship_behavior_target_ship
GROUP BY list_batch_no
ORDER BY rows DESC;
```

### 2. Backfill parent batch

```sql
SELECT * FROM dataanalysis.ship_sts_backfill_batch
WHERE batch_no = 'YOUR_BATCH_NO';
```

### 3. Task progress by status

```sql
SELECT status, COUNT(*) AS task_count
FROM dataanalysis.ship_sts_backfill_task
WHERE batch_no = 'YOUR_BATCH_NO'
GROUP BY status
ORDER BY status;
```

### 4. Timeline progress

```sql
SELECT slice_seq, slice_from, slice_to, status,
  candidate_ship_count, processed_ship_count,
  baseline_events, shadow_inserted, last_error
FROM dataanalysis.ship_sts_backfill_task
WHERE batch_no = 'YOUR_BATCH_NO'
ORDER BY slice_seq;
```

### 5. Shadow result count

```sql
SELECT COUNT(*) AS total_shadow_events
FROM dataanalysis.ship_sts_shadow_event
WHERE batch_no = 'YOUR_BATCH_NO';
```

### 6. Region distribution

```sql
SELECT region_id, COUNT(*) AS event_count
FROM dataanalysis.ship_sts_shadow_event
WHERE batch_no = 'YOUR_BATCH_NO'
GROUP BY region_id
ORDER BY event_count DESC;
```

### 7. Self MMSI check (should be 0)

```sql
SELECT COUNT(*) AS self_mmsi_events
FROM dataanalysis.ship_sts_shadow_event
WHERE batch_no = 'YOUR_BATCH_NO' AND own_mmsi = target_mmsi;
```

### 8. Self IMO check (should be 0)

```sql
SELECT COUNT(*) AS self_imo_events
FROM dataanalysis.ship_sts_shadow_event
WHERE batch_no = 'YOUR_BATCH_NO' AND own_imo = target_imo AND own_imo IS NOT NULL;
```

### 9. Duplicate event key check (should be 0)

```sql
SELECT event_key, COUNT(*) AS cnt
FROM dataanalysis.ship_sts_shadow_event
WHERE batch_no = 'YOUR_BATCH_NO'
GROUP BY event_key
HAVING cnt > 1
LIMIT 10;
```

## Known Limitations

1. **No historical MMSI validity**: `ship_behavior_target_ship` lacks first_seen/last_seen per MMSI. We know known aliases but not exact time ranges each was active. This may cause some missed windows if a ship's MMSI was re-assigned to another vessel within our fleet.

2. **Alongside V3 baseline only**: This is STS Phase 1 baseline (ALONGSIDE_V3_BASELINE), not strict STS. No distance-to-berth, tug filtering, or strict geometry rules.

3. **Shadow-only output**: All results go to `ship_sts_shadow_event`. No production event tables are written.

4. **Single-threaded per invocation**: Each XXL invocation processes one task sequentially. Parallelism is achieved via multiple XXL triggers.

5. **Max 5000 ships per job**: A single backfill job is limited to 5000 physical ships. Full fleet requires multiple offset jobs.

6. **Max 5 years / 261 slices**: Parent batch time range capped at 5 years or 261 slices (whichever is stricter).

7. **Region discovery completeness**: Discovery must be complete for all regions; any truncation/timeout fails the task. Small regions with very high vessel density may need time-split refinement.

8. **No cross-batch identity merging**: Each list_batch_no is treated independently. Ships that appear in multiple batches are not deduplicated across batches.
