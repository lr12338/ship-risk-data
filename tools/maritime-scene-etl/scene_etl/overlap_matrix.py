from __future__ import annotations

import csv
import io
from collections import defaultdict
from pathlib import Path
from typing import Any

from .geometry import geodesic_area_km2
from .manifest import write_json
from .release_records import geometry_from_record, iter_release_records


REQUIRED_PAIRS = [
    ("marine_eez", "marine_territorial_sea"),
    ("marine_eez", "marine_internal_water"),
    ("marine_eez", "marine_archipelagic_water"),
    ("marine_high_seas", "marine_territorial_sea"),
    ("marine_high_seas", "marine_internal_water"),
    ("marine_high_seas", "marine_archipelagic_water"),
    ("marine_high_seas", "land"),
    ("marine_territorial_sea", "land"),
    ("marine_internal_water", "land"),
    ("inland_river_corridor", "land"),
    ("inland_lake", "land"),
    ("major_canal", "land"),
    ("inland_river_corridor", "marine_high_seas"),
    ("inland_lake", "marine_high_seas"),
    ("major_canal", "marine_high_seas"),
]


def calculate_overlap_matrix(release_dir: Path) -> dict[str, Any]:
    grouped: dict[str, list[tuple[dict, Any]]] = defaultdict(list)
    for record in iter_release_records(release_dir, record_kind="scene", unique=True):
        grouped[record.get("sceneType", "UNKNOWN")].append(
            (record, geometry_from_record(record))
        )
    rows = []
    conflicts = []
    for left, right in REQUIRED_PAIRS:
        overlap_count = 0
        area_km2 = 0.0
        examples = []
        for left_record, left_geometry in grouped.get(left, []):
            for right_record, right_geometry in grouped.get(right, []):
                if not _bounds_intersect(left_geometry.bounds, right_geometry.bounds):
                    continue
                intersection = left_geometry.intersection(right_geometry)
                if intersection.is_empty:
                    continue
                if intersection.geom_type not in {"Point", "MultiPoint"}:
                    overlap_count += 1
                    try:
                        area_km2 += geodesic_area_km2(intersection)
                    except Exception:
                        pass
                if len(examples) < 10:
                    examples.append({
                        "leftFeatureId": left_record.get("sourceFeatureId"),
                        "rightFeatureId": right_record.get("sourceFeatureId"),
                    })
        semantic = _semantic(left, right, overlap_count)
        row = {
            "leftLayer": left,
            "rightLayer": right,
            "leftFeatureCount": len(grouped.get(left, [])),
            "rightFeatureCount": len(grouped.get(right, [])),
            "overlapCount": overlap_count,
            "overlapAreaKm2": round(area_km2, 6),
            "semantic": semantic,
            "examples": examples,
        }
        rows.append(row)
        if semantic == "CONFLICT" and overlap_count:
            conflicts.append(row)
    return {
        "schemaVersion": 1,
        "status": "PASS" if not conflicts else "WARN",
        "requiredPairCount": len(REQUIRED_PAIRS),
        "rows": rows,
        "conflictPairCount": len(conflicts),
        "conflicts": conflicts,
        "areaMethod": "WGS84 geodesic area; never EPSG:4326 planar area",
    }


def write_overlap_outputs(release_dir: Path, report: dict[str, Any]) -> None:
    validation_dir = release_dir / "validation"
    write_json(validation_dir / "layer-overlap-matrix.json", report)
    buffer = io.StringIO()
    writer = csv.writer(buffer, lineterminator="\n")
    writer.writerow([
        "left_layer", "right_layer", "left_feature_count", "right_feature_count",
        "overlap_count", "overlap_area_km2", "semantic",
    ])
    for row in report["rows"]:
        writer.writerow([
            row["leftLayer"], row["rightLayer"], row["leftFeatureCount"],
            row["rightFeatureCount"], row["overlapCount"], row["overlapAreaKm2"],
            row["semantic"],
        ])
    csv_path = validation_dir / "layer-overlap-matrix.csv"
    csv_path.parent.mkdir(parents=True, exist_ok=True)
    csv_path.write_text(buffer.getvalue(), encoding="utf-8")


def _semantic(left: str, right: str, overlap_count: int) -> str:
    pair = {left, right}
    expected = [
        {"marine_eez", "marine_territorial_sea"},
        {"marine_eez", "marine_internal_water"},
        {"marine_eez", "marine_archipelagic_water"},
        {"inland_river_corridor", "land"},
        {"inland_lake", "land"},
        {"major_canal", "land"},
    ]
    if pair in expected:
        return "EXPECTED_OVERLAP" if overlap_count else "NONE"
    if pair.intersection({"marine_high_seas"}) and pair.intersection({"land", "marine_territorial_sea", "marine_internal_water", "marine_archipelagic_water", "inland_river_corridor", "inland_lake", "major_canal"}):
        return "CONFLICT" if overlap_count else "NONE"
    if "land" in pair and pair.intersection({"marine_territorial_sea", "marine_internal_water"}):
        return "BOUNDARY_UNCERTAIN" if overlap_count else "NONE"
    return "OVERLAP_REVIEW" if overlap_count else "NONE"


def _bounds_intersect(left, right) -> bool:
    return not (
        left[2] < right[0]
        or right[2] < left[0]
        or left[3] < right[1]
        or right[3] < left[1]
    )
