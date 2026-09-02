#!/usr/bin/env python3
"""Normalize STS crude-oil-tanker seed data from 0902 XLSX sources into a single CSV.

Input:
  0902/crude-oil-tanker-1imo-vs-1mmsi-20260902.xlsx
  0902/crude-oil-tanker-1imo-vs-2mmsi-时间连续性强-20260902.xlsx

Output:
  var/sts/sts-crude-identity-20260902.csv

Identity modes:
  SINGLE_OBSERVED   — 1 IMO → 1 MMSI
  DUAL_NON_OVERLAP  — 1 IMO → 2 MMSI, observed intervals do not overlap
  DUAL_OVERLAP      — 1 IMO → 2 MMSI, observed intervals overlap
"""

import argparse
import csv
import os
import sys

import pandas as pd


SINGLE_FILE = "crude-oil-tanker-1imo-vs-1mmsi-20260902.xlsx"
DUAL_FILE = "crude-oil-tanker-1imo-vs-2mmsi-时间连续性强-20260902.xlsx"
TOTAL_LIST_FILE = "crude-oil-tanker-list-20260902(总名单).csv"

CSV_COLUMNS = [
    "imo", "mmsi", "first_seen", "last_seen",
    "name", "callsign", "length", "width", "type", "minotype",
    "identity_mode",
]


def _norm_ts(val):
    if pd.isna(val):
        return ""
    ts = pd.Timestamp(val)
    return ts.strftime("%Y-%m-%d %H:%M:%S")


def _s(val):
    if pd.isna(val):
        return ""
    return str(val)


def _i(val):
    if pd.isna(val):
        return ""
    return str(int(val))


def load_single(path):
    df = pd.read_excel(path)
    records = []
    for _, row in df.iterrows():
        records.append({
            "imo": _i(row["imo"]),
            "mmsi": _i(row["mmsi"]),
            "first_seen": _norm_ts(row["firstseetime"]),
            "last_seen": _norm_ts(row["updatetime"]),
            "name": _s(row.get("name", "")),
            "callsign": _s(row.get("callsign", "")),
            "length": _i(row.get("length", "")),
            "width": _i(row.get("width", "")),
            "type": _s(row.get("type", "")),
            "minotype": _s(row.get("minotype", "")),
            "identity_mode": "SINGLE_OBSERVED",
        })
    return records


def load_dual(path):
    df = pd.read_excel(path)
    records = []
    overlap_imos = []
    non_overlap_imos = []

    for _, row in df.iterrows():
        imo = _i(row["imo"])
        first_1 = pd.Timestamp(row["firstseetime_1"]) if not pd.isna(row["firstseetime_1"]) else None
        last_1 = pd.Timestamp(row["updatetime_1"]) if not pd.isna(row["updatetime_1"]) else None
        first_2 = pd.Timestamp(row["firstseetime_2"]) if not pd.isna(row["firstseetime_2"]) else None
        last_2 = pd.Timestamp(row["updatetime_2"]) if not pd.isna(row["updatetime_2"]) else None

        if first_1 and first_2 and last_1 and last_2:
            overlaps = first_2 <= last_1 and first_1 <= last_2
        else:
            overlaps = False

        mode = "DUAL_OVERLAP" if overlaps else "DUAL_NON_OVERLAP"
        if overlaps:
            overlap_imos.append(imo)
        else:
            non_overlap_imos.append(imo)

        for suffix in ("_1", "_2"):
            records.append({
                "imo": imo,
                "mmsi": _i(row[f"mmsi{suffix}"]),
                "first_seen": _norm_ts(row[f"firstseetime{suffix}"]),
                "last_seen": _norm_ts(row[f"updatetime{suffix}"]),
                "name": _s(row.get(f"name{suffix}", "")),
                "callsign": _s(row.get(f"callsign{suffix}", "")),
                "length": _i(row.get(f"length{suffix}", "")),
                "width": _i(row.get(f"width{suffix}", "")),
                "type": _s(row.get(f"type{suffix}", "")),
                "minotype": _s(row.get(f"minotype{suffix}", "")),
                "identity_mode": mode,
            })

    return records, overlap_imos, non_overlap_imos


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-dir", default="0902", help="directory with source XLSX files")
    parser.add_argument("--output", default="var/sts/sts-crude-identity-20260902.csv", help="output CSV path")
    args = parser.parse_args()

    single_path = os.path.join(args.input_dir, SINGLE_FILE)
    dual_path = os.path.join(args.input_dir, DUAL_FILE)
    total_list_path = os.path.join(args.input_dir, TOTAL_LIST_FILE)

    for p in (single_path, dual_path):
        if not os.path.exists(p):
            print(f"ERROR: missing input file: {p}", file=sys.stderr)
            sys.exit(1)

    single_records = load_single(single_path)
    dual_records, overlap_imos, non_overlap_imos = load_dual(dual_path)

    all_records = single_records + dual_records

    imos = sorted(set(r["imo"] for r in all_records))
    mmsis = sorted(set(r["mmsi"] for r in all_records if r["mmsi"]))
    single_imos = [r["imo"] for r in single_records]
    dual_imos = sorted(set(r["imo"] for r in dual_records))

    os.makedirs(os.path.dirname(args.output), exist_ok=True)
    with open(args.output, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=CSV_COLUMNS)
        writer.writeheader()
        for r in all_records:
            writer.writerow(r)

    total_list_count = None
    if os.path.exists(total_list_path):
        try:
            df_total = pd.read_csv(total_list_path)
            total_list_count = len(df_total)
        except Exception as e:
            print(f"WARN: could not read total list: {e}", file=sys.stderr)

    print("=" * 60)
    print("STS SEED NORMALIZATION — SUMMARY")
    print("=" * 60)
    print(f"  output:           {args.output}")
    print(f"  total IMOs:       {len(imos)}")
    print(f"  total MMSIs:      {len(mmsis)}")
    print(f"  total rows:       {len(all_records)}")
    print(f"  single-MMSI IMOs: {len(single_imos)}")
    print(f"  dual-MMSI IMOs:   {len(dual_imos)}")
    print(f"    DUAL_OVERLAP:     {len(overlap_imos)}")
    print(f"    DUAL_NON_OVERLAP: {len(non_overlap_imos)}")
    if total_list_count is not None:
        print(f"  total list IMOs:  {total_list_count}  (reference only)")
        print(f"  SEED_COUNT_DISCREPANCY_786_VS_787: seed={len(imos)}, total_list={total_list_count}")
    print("=" * 60)


if __name__ == "__main__":
    main()
