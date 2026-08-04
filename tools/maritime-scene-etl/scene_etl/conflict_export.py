from __future__ import annotations

import gzip
import json
from pathlib import Path
from typing import Any

from .geometry import geometry_from_wkb_hex
from .manifest import write_json


def export_conflicts(release_dir: Path) -> dict[str, Any]:
    validation_dir = release_dir / "validation"
    matrix_path = validation_dir / "layer-overlap-matrix.json"
    cross_path = validation_dir / "cross-source-validation.json"
    matrix = json.loads(matrix_path.read_text(encoding="utf-8")) if matrix_path.exists() else {}
    cross = json.loads(cross_path.read_text(encoding="utf-8")) if cross_path.exists() else {}
    details: list[dict[str, Any]] = []
    for row in matrix.get("conflicts", []):
        details.append({"kind": "LAYER_OVERLAP", **row})
    for item in cross.get("results", []):
        if item.get("sceneClass") == "CONFLICT" or item.get("conflictFlag"):
            details.append({"kind": "POINT_CONFLICT", **item})
    blocking = bool(matrix.get("blockingConflictPairCount"))
    summary = {
        "schemaVersion": 1,
        "status": "WARN" if details else "PASS",
        "conflictCount": len(details),
        "byKind": _counts(details),
        "blocking": blocking,
        "details": details[:100],
        "rule": "Local conflict is isolated as CONFLICT; widespread overlap above the configured area threshold blocks promotion.",
    }
    write_json(validation_dir / "conflict-summary.json", summary)
    ndjson_path = validation_dir / "conflict-details.ndjson.gz"
    ndjson_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = ndjson_path.with_name(ndjson_path.name + ".part")
    with temporary.open("wb") as raw:
        with gzip.GzipFile(fileobj=raw, mode="wb", mtime=0) as compressed:
            for item in details:
                compressed.write(
                    (json.dumps(item, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")
                )
    temporary.replace(ndjson_path)
    _write_gpkg(release_dir / "qa" / "conflicts.gpkg", details)
    return summary


def _counts(details: list[dict[str, Any]]) -> dict[str, int]:
    result: dict[str, int] = {}
    for item in details:
        kind = str(item.get("kind", "UNKNOWN"))
        result[kind] = result.get(kind, 0) + 1
    return dict(sorted(result.items()))


def _write_gpkg(path: Path, details: list[dict[str, Any]]) -> None:
    import fiona
    from fiona.crs import CRS
    from shapely.geometry import Point, mapping

    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        path.unlink()
    schema = {
        "geometry": "Point",
        "properties": {
            "kind": "str:32",
            "left_layer": "str:64",
            "right_layer": "str:64",
            "scene_class": "str:32",
            "detail": "str",
        },
    }
    with fiona.open(path, "w", driver="GPKG", layer="conflicts", schema=schema, crs=CRS.from_epsg(4326)) as layer:
        for item in details:
            longitude, latitude = _point_for_detail(item)
            layer.write({
                "geometry": mapping(Point(longitude, latitude)),
                "properties": {
                    "kind": item.get("kind"),
                    "left_layer": item.get("leftLayer"),
                    "right_layer": item.get("rightLayer"),
                    "scene_class": item.get("sceneClass"),
                    "detail": json.dumps(item, ensure_ascii=False, sort_keys=True, default=str)[:4000],
                },
            })


def _point_for_detail(item: dict[str, Any]) -> tuple[float, float]:
    if item.get("longitude") is not None and item.get("latitude") is not None:
        return float(item["longitude"]), float(item["latitude"])
    return 0.0, 0.0
