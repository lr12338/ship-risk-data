#!/usr/bin/env python3
"""
Generate INSERT SQL for dataanalysis.ship_sts_seed_identity from normalized CSV.

Usage:
    python3 scripts/sts/generate_seed_import_sql.py \
        var/sts/sts-crude-identity-20260902.csv \
        sts-crude-identity-20260902-v1 \
        > var/sts/seed_identity_import_sts-crude-identity-20260902-v1.sql

Does NOT connect to any database. Writes SQL to stdout.
"""
import csv
import sys


def main():
    if len(sys.argv) < 3:
        print("Usage: generate_seed_import_sql.py <csv_path> <seed_batch_no>", file=sys.stderr)
        sys.exit(1)

    csv_path = sys.argv[1]
    seed_batch_no = sys.argv[2]

    rows_by_imo = {}  # imo -> list of rows (preserving CSV order)
    imos_ordered = []

    with open(csv_path, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            imo = row['imo'].strip()
            if not imo:
                continue
            if imo not in rows_by_imo:
                rows_by_imo[imo] = []
                imos_ordered.append(imo)
            rows_by_imo[imo].append(row)

    # Assign ship_ordinal: deterministic per-IMO ordering
    # same ordinal for all MMSIs of the same IMO
    imo_to_ordinal = {}
    for idx, imo in enumerate(imos_ordered):
        imo_to_ordinal[imo] = idx + 1  # 1-based

    total_identity_rows = sum(len(v) for v in rows_by_imo.values())
    distinct_imos = len(imos_ordered)

    print("-- =============================================================")
    print("-- STS Phase 1.3 — Seed Identity Import SQL")
    print("--")
    print(f"-- Source CSV:       {csv_path}")
    print(f"-- seed_batch_no:    {seed_batch_no}")
    print(f"-- Distinct IMOs:    {distinct_imos}")
    print(f"-- Identity rows:    {total_identity_rows}")
    print("--")
    print("-- Execute manually against dataanalysis database.")
    print("-- Does NOT drop or truncate — INSERT IGNORE for idempotency.")
    print("-- =============================================================")
    print()
    print("SET NAMES utf8mb4;")
    print()

    print("-- Insert identity rows (INSERT IGNORE for idempotency)")
    print(f"-- {distinct_imos} distinct IMOs, {total_identity_rows} identity rows")
    print()

    # Batch INSERT statements for readability
    BATCH_SIZE = 50
    all_rows = []
    for imo in imos_ordered:
        ordinal = imo_to_ordinal[imo]
        for row in rows_by_imo[imo]:
            all_rows.append((ordinal, row))

    for i in range(0, len(all_rows), BATCH_SIZE):
        batch = all_rows[i:i + BATCH_SIZE]
        print("INSERT IGNORE INTO dataanalysis.ship_sts_seed_identity (")
        print("    seed_batch_no, ship_ordinal,")
        print("    imo, mmsi, first_seen, last_seen, identity_mode,")
        print("    name, callsign, length, width, type, minotype,")
        print("    enabled")
        print(") VALUES")
        for j, (ordinal, row) in enumerate(batch):
            values = [
                f"'{seed_batch_no}'",
                str(ordinal),
                f"'{_esc(row['imo'])}'",
                f"'{_esc(row['mmsi'])}'",
                _ts_or_null(row.get('first_seen', '')),
                _ts_or_null(row.get('last_seen', '')),
                _str_or_null(row.get('identity_mode', '')),
                _str_or_null(row.get('name', '')),
                _str_or_null(row.get('callsign', '')),
                _int_or_null(row.get('length', '')),
                _int_or_null(row.get('width', '')),
                _str_or_null(row.get('type', '')),
                _str_or_null(row.get('minotype', '')),
                "1",  # enabled
            ]
            comma = "," if j < len(batch) - 1 else ";"
            print(f"    ({', '.join(values)}){comma}")
        print()

    print("-- Verification queries")
    print(f"SELECT COUNT(DISTINCT imo) AS distinct_imos FROM dataanalysis.ship_sts_seed_identity")
    print(f"  WHERE seed_batch_no = '{seed_batch_no}' AND enabled = 1;")
    print(f"SELECT COUNT(*) AS identity_rows FROM dataanalysis.ship_sts_seed_identity")
    print(f"  WHERE seed_batch_no = '{seed_batch_no}' AND enabled = 1;")
    print()
    print("-- Expected:")
    print(f"--   distinct_imos = {distinct_imos}")
    print(f"--   identity_rows = {total_identity_rows}")


def _esc(s):
    return s.replace("'", "''").replace("\\", "\\\\")


def _str_or_null(s):
    s = (s or '').strip()
    if not s:
        return "NULL"
    return f"'{_esc(s)}'"


def _int_or_null(s):
    s = (s or '').strip()
    if not s:
        return "NULL"
    try:
        return str(int(s))
    except ValueError:
        return "NULL"


def _ts_or_null(s):
    s = (s or '').strip()
    if not s:
        return "NULL"
    return f"'{_esc(s)}'"


if __name__ == '__main__':
    main()
