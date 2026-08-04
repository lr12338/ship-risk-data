from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from .config import load_yaml
from .geometry import geometry_from_wkb_hex
from .manifest import utc_now, write_json
from .tiling import tile_id


def validate_release(
    release_dir: Path,
    config_dir: Path,
    dataset_statuses: list[dict[str, Any]] | None = None,
    write_report: bool = True,
) -> dict[str, Any]:
    tiles = sorted((release_dir / "tiles").glob("*.ndjson.gz"))
    sizes = [path.stat().st_size for path in tiles]
    report: dict[str, Any] = {
        "schemaVersion": 1,
        "validatedAt": utc_now(),
        "datasetStatuses": dataset_statuses or [],
        "tileCount": len(tiles),
        "maxTileBytes": max(sizes) if sizes else 0,
        "averageTileBytes": sum(sizes) / len(sizes) if sizes else 0,
        "outputBytes": sum(sizes),
        "goldenPoints": _validate_golden_points(release_dir, config_dir),
    }
    report["manualSpotCheck"] = {
        "status": report["goldenPoints"].get("status", "NOT_PERFORMED"),
        "blocking": False,
        "configured": report["goldenPoints"].get("configured", 0),
        "enabled": report["goldenPoints"].get("enabled", 0),
    }
    automatic = _read_automatic_validation(release_dir)
    if automatic is not None:
        report["automaticValidation"] = automatic
    if any(status.get("status") in {"MISSING", "INVALID", "FAILED"} for status in report["datasetStatuses"]):
        report["status"] = "FAILED_DATASET"
    elif any(status.get("status") == "BLOCKED_MANUAL" for status in report["datasetStatuses"]):
        report["status"] = "PARTIAL_MANUAL_ACTION_REQUIRED"
    elif automatic is not None and automatic.get("status") in {"FAILED", "FAIL"}:
        report["status"] = "FAILED_AUTOMATIC_VALIDATION"
    elif automatic is not None and automatic.get("status") in {"PASS", "WARN"}:
        report["status"] = automatic.get("releaseStatus", "AUTO_CROSS_VALIDATED")
    else:
        report["status"] = "GEOMETRY_VALIDATED"
    existing_status = _existing_release_status(release_dir)
    if report["status"] not in {
        "FAILED_DATASET", "PARTIAL_MANUAL_ACTION_REQUIRED", "FAILED_AUTOMATIC_VALIDATION"
    } and _status_rank(existing_status) > _status_rank(report["status"]):
        report["status"] = existing_status
    report["releaseStatus"] = report["status"]
    if write_report:
        write_json(release_dir / "validation-report.json", report)
    return report


def _validate_golden_points(release_dir: Path, config_dir: Path) -> dict[str, Any]:
    config = load_yaml(config_dir / "golden-points.yaml")
    points = config.get("points", [])
    enabled = [point for point in points if point.get("enabled")]
    optional = config.get("blocking", True) is False or config.get(
        "validation_role"
    ) == "OPTIONAL_MANUAL_REFERENCE"
    if not enabled:
        return {
            "status": "NOT_PERFORMED",
            "blocking": False,
            "configured": len(points),
            "enabled": 0,
            "results": [],
        }
    results = []
    for point in enabled:
        results.append(_validate_point(release_dir, point))
    if optional:
        status = "OPTIONAL_REVIEW"
    elif any(not item["passed"] for item in results):
        status = "FAIL"
    elif len(enabled) < len(points):
        status = "PENDING_MANUAL_COORDINATE_REVIEW"
    else:
        status = "PASS"
    return {
        "status": status,
        "blocking": not optional,
        "configured": len(points),
        "enabled": len(enabled),
        "results": results,
    }


def _read_automatic_validation(release_dir: Path) -> dict[str, Any] | None:
    path = release_dir / "validation" / "automatic-validation.json"
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def _existing_release_status(release_dir: Path) -> str | None:
    path = release_dir / "validation-report.json"
    if not path.exists():
        return None
    report = json.loads(path.read_text(encoding="utf-8"))
    return report.get("releaseStatus") or report.get("status")


def _status_rank(status: str | None) -> int:
    return {
        "RAW_VERIFIED": 0,
        "GEOMETRY_VALIDATED": 1,
        "GLOBAL_CORE_VALIDATED": 2,
        "AUTO_CROSS_VALIDATED": 3,
        "SHADOW_READY": 4,
        "ENFORCE_CANDIDATE": 5,
        "ENFORCE_READY": 6,
    }.get(status or "", -1)


def _validate_point(release_dir: Path, point: dict[str, Any]) -> dict[str, Any]:
    point_tile = tile_id(float(point["longitude"]), float(point["latitude"]))
    path = release_dir / "tiles" / f"{point_tile}.scene.ndjson.gz"
    hits: set[str] = set()
    if path.exists():
        import gzip
        from shapely.geometry import Point

        target = Point(float(point["longitude"]), float(point["latitude"]))
        with gzip.open(path, "rt", encoding="utf-8") as handle:
            for line in handle:
                record = json.loads(line)
                geometry = geometry_from_wkb_hex(record["geometryWkbHex"])
                if geometry.intersects(target):
                    hits.add(record["sceneType"])
    expected = set(point.get("expected_hits", []))
    allowed_alternatives = set(point.get("allowed_alternatives", []))
    forbidden = set(point.get("forbidden_hits", []))
    matched_expected = expected.intersection(hits)
    matched_alternatives = allowed_alternatives.intersection(hits)
    forbidden_hits = forbidden.intersection(hits)
    passed = (matched_expected or matched_alternatives) and not forbidden_hits
    return {
        "id": point["id"],
        "tile": point_tile,
        "hits": sorted(hits),
        "expected": sorted(expected),
        "allowedAlternatives": sorted(allowed_alternatives),
        "forbidden": sorted(forbidden),
        "matchedExpected": sorted(matched_expected),
        "matchedAlternatives": sorted(matched_alternatives),
        "forbiddenHits": sorted(forbidden_hits),
        "passed": bool(passed),
        "reason": point.get("reason"),
    }
