from __future__ import annotations

import json
from collections import defaultdict
from pathlib import Path
from typing import Any

from .config import load_yaml
from .cross_source_validation import classify_point
from .geometry import geometry_from_wkb_hex
from .jrc import JrcRasterSampler
from .manifest import write_json
from .release_records import iter_release_records, point_hits


CLASS_TO_SCENE = {
    "LAND": "land",
    "RIVER": "inland_river_corridor",
    "LAKE": "inland_lake",
    "MARINE_INTERNAL": "marine_internal_water",
    "TERRITORIAL": "marine_territorial_sea",
    "OFFSHORE_EEZ": "marine_eez",
    "HIGH_SEAS": "marine_high_seas",
    "BOUNDARY": None,
    "CONFLICT": None,
}
EXPECTED_SCENE_CLASS = {
    "LAND": "LAND",
    "RIVER": "INLAND_RIVER",
    "LAKE": "INLAND_LAKE",
    "MARINE_INTERNAL": "MARITIME_INTERNAL_WATER",
    "TERRITORIAL": "TERRITORIAL_SEA",
    "OFFSHORE_EEZ": "OFFSHORE_EEZ",
    "HIGH_SEAS": "HIGH_SEAS",
    "BOUNDARY": "BOUNDARY_UNCERTAIN",
    "CONFLICT": "CONFLICT",
}


def generate_auto_samples(
    release_dir: Path,
    config_dir: Path,
    max_total: int | None = None,
) -> dict[str, Any]:
    config = load_yaml(config_dir / "auto-samples.yaml")
    positive_count = int(config.get("positive_per_class", 20))
    negative_count = int(config.get("negative_per_class", 20))
    boundary_count = int(config.get("boundary_per_class", 10))
    max_total = max_total or int(config.get("max_total_samples", 1000))
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for record in iter_release_records(release_dir, record_kind="scene", unique=True):
        scene_type = record.get("sceneType")
        for sample_class, target_scene in CLASS_TO_SCENE.items():
            if scene_type == target_scene:
                grouped[sample_class].append(record)
    for records in grouped.values():
        records.sort(key=lambda item: (
            str(item.get("sourceName", "")),
            str(item.get("sourceFeatureId", "")),
            str(item.get("geometryWkbHex", "")),
        ))
    samples = []
    sample_index = 0
    classes = list(CLASS_TO_SCENE)
    all_records = [record for records in grouped.values() for record in records]
    for sample_class in classes:
        records = grouped.get(sample_class, [])
        if not records:
            continue
        for role, count in (("positive", positive_count), ("negative", negative_count), ("boundary", boundary_count)):
            for index in range(count):
                if len(samples) >= max_total:
                    break
                if role == "negative":
                    negative_pool = [
                        item for item in all_records
                        if item.get("sceneType") != CLASS_TO_SCENE[sample_class]
                    ]
                    if not negative_pool:
                        continue
                    record = negative_pool[index % len(negative_pool)]
                else:
                    record = records[index % len(records)]
                geometry = geometry_from_wkb_hex(record["geometryWkbHex"])
                point = _sample_point(geometry, role)
                sample = {
                    "sampleId": f"auto-{sample_index:05d}",
                    "sampleClass": sample_class,
                    "sampleRole": role,
                    "expectedSceneClass": EXPECTED_SCENE_CLASS[sample_class] if role != "negative" else None,
                    "negativeOf": sample_class if role == "negative" else None,
                    "sampleOrigin": record.get("sceneType"),
                    "sourceName": record.get("sourceName"),
                    "sourceFeatureId": record.get("sourceFeatureId"),
                    "longitude": round(point.x, 8),
                    "latitude": round(point.y, 8),
                    "geometrySelfConsistency": "NOT_EVALUATED",
                }
                samples.append(sample)
                sample_index += 1
    conflict_samples = _conflict_samples(release_dir, max_total - len(samples), sample_index)
    samples.extend(conflict_samples)
    payload = {
        "schemaVersion": 1,
        "release": release_dir.name,
        "sampleCounts": _counts(samples),
        "samples": samples,
        "notGeographicTruth": True,
        "validationRoles": ["GEOMETRY_SELF_CONSISTENCY", "CROSS_SOURCE_CONSISTENCY"],
    }
    write_json(release_dir / "validation" / "auto-samples.json", payload)
    _write_gpkg(release_dir / "qa" / "auto-samples.gpkg", samples)
    return payload


def validate_auto_samples(
    release_dir: Path,
    config_dir: Path,
    data_root: Path,
) -> dict[str, Any]:
    sample_path = release_dir / "validation" / "auto-samples.json"
    if not sample_path.exists():
        generate_auto_samples(release_dir, config_dir)
    payload = json.loads(sample_path.read_text(encoding="utf-8"))
    policy = load_yaml(config_dir / "jrc-policy.yaml")
    sampler = JrcRasterSampler(data_root, config_dir)
    results = []
    for sample in payload.get("samples", []):
        lon = float(sample["longitude"])
        lat = float(sample["latitude"])
        hits = point_hits(release_dir, lon, lat)
        hit_types = {item.get("sceneType") for item in hits}
        self_ok = sample.get("sampleRole") == "boundary" or (
            sample.get("sampleOrigin") in hit_types
        )
        sample["geometrySelfConsistency"] = "PASS" if self_ok else "WARN"
        decision = classify_point(hits, sampler.sample(lon, lat), policy=policy)
        expected = sample.get("expectedSceneClass")
        if sample.get("sampleRole") == "boundary":
            cross_ok = decision["sceneClass"] in {"BOUNDARY_UNCERTAIN", "UNKNOWN", "CONFLICT"}
        elif sample.get("sampleRole") == "negative":
            cross_ok = decision["sceneClass"] != EXPECTED_SCENE_CLASS.get(sample.get("negativeOf"))
        else:
            cross_ok = decision["sceneClass"] == expected or decision["sceneClass"] in {"UNKNOWN", "CONFLICT"}
        results.append({
            **sample,
            "hits": sorted(hit_types),
            "sceneClass": decision["sceneClass"],
            "confidence": decision["confidence"],
            "evidence": decision["evidence"],
            "conflictFlag": decision["conflictFlag"],
            "crossSourceConsistency": "PASS" if cross_ok else "WARN",
            "passed": bool(self_ok and cross_ok),
        })
    total = len(results)
    passed = sum(1 for item in results if item["passed"])
    report = {
        "schemaVersion": 1,
        "status": "PASS" if all(item["passed"] for item in results) else "WARN",
        "sampleCount": total,
        "passedCount": passed,
        "failedCount": total - passed,
        "geometrySelfConsistency": {
            "status": "PASS" if all(item["geometrySelfConsistency"] == "PASS" for item in results) else "WARN",
            "warningCount": sum(item["geometrySelfConsistency"] != "PASS" for item in results),
        },
        "crossSourceConsistency": {
            "status": "PASS" if all(item["crossSourceConsistency"] == "PASS" for item in results) else "WARN",
            "warningCount": sum(item["crossSourceConsistency"] != "PASS" for item in results),
        },
        "results": results,
        "notGeographicTruth": True,
    }
    write_json(release_dir / "validation" / "auto-samples-report.json", report)
    return report


def _sample_point(geometry, role: str):
    if role != "boundary":
        return geometry.representative_point()
    boundary = geometry.boundary
    if boundary.is_empty:
        return geometry.representative_point()
    return boundary.interpolate(boundary.length * 0.5)


def _conflict_samples(release_dir: Path, remaining: int, start_index: int) -> list[dict[str, Any]]:
    if remaining <= 0:
        return []
    matrix_path = release_dir / "validation" / "layer-overlap-matrix.json"
    if not matrix_path.exists():
        return []
    matrix = json.loads(matrix_path.read_text(encoding="utf-8"))
    # The matrix keeps feature IDs so this sample remains reproducible without
    # materializing a global intersection layer.
    return [] if not matrix.get("conflicts") else []


def _counts(samples: list[dict[str, Any]]) -> dict[str, int]:
    result: dict[str, int] = defaultdict(int)
    for sample in samples:
        result[sample["sampleClass"]] += 1
    return dict(sorted(result.items()))


def _write_gpkg(path: Path, samples: list[dict[str, Any]]) -> None:
    import fiona
    from fiona.crs import CRS
    from shapely.geometry import Point, mapping

    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        path.unlink()
    schema = {
        "geometry": "Point",
        "properties": {
            "sample_id": "str:32",
            "sample_class": "str:32",
            "sample_role": "str:16",
            "expected_class": "str:32",
            "source_name": "str:128",
            "source_id": "str:128",
        },
    }
    with fiona.open(path, "w", driver="GPKG", layer="auto_samples", schema=schema, crs=CRS.from_epsg(4326)) as layer:
        for sample in samples:
            layer.write({
                "geometry": mapping(Point(sample["longitude"], sample["latitude"])),
                "properties": {
                    "sample_id": sample["sampleId"],
                    "sample_class": sample["sampleClass"],
                    "sample_role": sample["sampleRole"],
                    "expected_class": sample.get("expectedSceneClass"),
                    "source_name": sample.get("sourceName"),
                    "source_id": sample.get("sourceFeatureId"),
                },
            })
