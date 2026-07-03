#!/usr/bin/env bash
set -euo pipefail

LOG_FILE="${1:-target/alongside-live-taskmanager.out}"
OUT_FILE="${2:-target/alongside-live-metrics.json}"

if [ ! -f "$LOG_FILE" ]; then
  echo "Missing log file: $LOG_FILE" >&2
  exit 1
fi

python3 - "$LOG_FILE" "$OUT_FILE" <<'PY'
import json
import re
import sys
from collections import Counter

log_file, out_file = sys.argv[1], sys.argv[2]
clean_re = re.compile(
    r"\[debug-clean-ais\] mmsi=(?P<mmsi>\d+), time=(?P<time>[^,]+), "
    r"lon=(?P<lon>[-0-9.]+), lat=(?P<lat>[-0-9.]+), speed=(?P<speed>[-0-9.]+), usable=(?P<usable>\w+)"
)
evidence_re = re.compile(
    r"\[debug-pair-evidence\] pair_id=(?P<pair_id>[^,]+), evidence_time=(?P<time>[^,]+), "
    r"point_a_time=(?P<point_a_time>[^,]+), point_b_time=(?P<point_b_time>[^,]+), "
    r"distance_nm=(?P<distance_nm>[-0-9.Ee]+), time_gap_seconds=(?P<time_gap_seconds>\d+)"
)

clean_count = 0
evidence_count = 0
event_count = 0
unique_mmsi = set()
pair_counts = Counter()
hour_counts = Counter()
min_time = None
max_time = None
min_lon = None
max_lon = None
min_lat = None
max_lat = None
speed_sum = 0.0
low_speed_count = 0
distance_sum = 0.0
time_gap_sum = 0.0

with open(log_file, "rb") as fh:
    for raw in fh:
        line = raw.replace(b"\x00", b"").decode("utf-8", "ignore").strip()
        if not line:
            continue
        match = clean_re.search(line)
        if match:
            clean_count += 1
            mmsi = match.group("mmsi")
            event_time = match.group("time")
            lon = float(match.group("lon"))
            lat = float(match.group("lat"))
            speed = float(match.group("speed"))
            unique_mmsi.add(mmsi)
            hour_counts[event_time[:13]] += 1
            min_time = event_time if min_time is None or event_time < min_time else min_time
            max_time = event_time if max_time is None or event_time > max_time else max_time
            min_lon = lon if min_lon is None else min(min_lon, lon)
            max_lon = lon if max_lon is None else max(max_lon, lon)
            min_lat = lat if min_lat is None else min(min_lat, lat)
            max_lat = lat if max_lat is None else max(max_lat, lat)
            speed_sum += speed
            if speed <= 4.0:
                low_speed_count += 1
            continue
        match = evidence_re.search(line)
        if match:
            evidence_count += 1
            pair_counts[match.group("pair_id")] += 1
            distance_sum += float(match.group("distance_nm"))
            time_gap_sum += float(match.group("time_gap_seconds"))
            continue
        if '"event_type":"ALONGSIDE_CANDIDATE"' in line:
            event_count += 1

metrics = {
    "log_file": log_file,
    "clean_ais_count": clean_count,
    "unique_mmsi_count": len(unique_mmsi),
    "pair_evidence_count": evidence_count,
    "alongside_event_count": event_count,
    "time_range": {
        "min_event_time": min_time,
        "max_event_time": max_time,
    },
    "bbox_observed": {
        "min_lon": min_lon,
        "max_lon": max_lon,
        "min_lat": min_lat,
        "max_lat": max_lat,
    },
    "avg_speed_knots": speed_sum / clean_count if clean_count else 0.0,
    "low_speed_ratio_le_4kn": low_speed_count / clean_count if clean_count else 0.0,
    "avg_pair_distance_nm": distance_sum / evidence_count if evidence_count else 0.0,
    "avg_pair_time_gap_seconds": time_gap_sum / evidence_count if evidence_count else 0.0,
    "top_pair_ids": [
        {"pair_id": pair_id, "evidence_count": count}
        for pair_id, count in pair_counts.most_common(10)
    ],
    "clean_ais_by_hour": dict(sorted(hour_counts.items())),
}

with open(out_file, "w", encoding="utf-8") as fh:
    json.dump(metrics, fh, ensure_ascii=False, indent=2)
    fh.write("\n")

print(json.dumps(metrics, ensure_ascii=False, indent=2))
PY
