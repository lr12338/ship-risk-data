from __future__ import annotations

from pathlib import Path
from typing import Any

from .config import load_yaml


def generate_candidate_report(
    release_dir: Path,
    config_dir: Path,
    limit: int = 3,
) -> dict[str, Any]:
    qa_path = release_dir / "qa" / "maritime-scene-canonical.gpkg"
    if not qa_path.exists():
        raise FileNotFoundError(f"QA GeoPackage not found: {qa_path}")
    try:
        import fiona
        from pyproj import Geod
        from shapely.geometry import Point, shape
        from shapely.ops import nearest_points
    except ImportError as exc:
        raise RuntimeError("Fiona, PyProj, and Shapely are required for golden candidates") from exc

    geod = Geod(ellps="WGS84")
    config = load_yaml(config_dir / "golden-points.yaml")
    available_layers = set(fiona.listlayers(qa_path))
    points = []
    for point in config.get("points", []):
        if point.get("enabled"):
            continue
        expected = sorted(set(point.get("expected_hits", [])))
        alternatives = sorted(set(point.get("allowed_alternatives", [])))
        accepted_layers = sorted(set(expected + alternatives))
        target = Point(float(point["longitude"]), float(point["latitude"]))
        candidates = []
        direct_hits = []
        present_layers = []
        for layer in accepted_layers:
            if layer not in available_layers:
                continue
            present_layers.append(layer)
            with fiona.open(qa_path, layer=layer) as source:
                for feature in source:
                    geometry = shape(feature["geometry"])
                    nearest = nearest_points(target, geometry)[1]
                    _, _, distance_meters = geod.inv(
                        target.x, target.y, nearest.x, nearest.y
                    )
                    properties = dict(feature["properties"])
                    source_id = str(properties.get("source_id", ""))
                    candidate = {
                        "layer": layer,
                        "sourceId": source_id,
                        "sourceName": properties.get("source_name"),
                        "sourceVersion": properties.get("source_version"),
                        "geometryStatus": properties.get("geometry_status"),
                        "confidence": properties.get("confidence"),
                        "distanceMeters": round(abs(distance_meters), 3),
                        "nearestPoint": [round(nearest.x, 7), round(nearest.y, 7)],
                    }
                    candidates.append(candidate)
                    if geometry.intersects(target):
                        direct_hits.append(candidate)
        candidates.sort(
            key=lambda item: (
                item["distanceMeters"],
                item["layer"],
                item["sourceId"],
                item["nearestPoint"],
            )
        )
        direct_hits.sort(
            key=lambda item: (item["layer"], item["sourceId"], item["nearestPoint"])
        )
        if direct_hits:
            status = "DIRECT_HIT"
        elif not present_layers:
            status = "NO_LAYER_IN_RELEASE"
        elif candidates:
            status = "NEAREST_CANDIDATE"
        else:
            status = "NO_CANDIDATE"
        points.append(
            {
                "id": point["id"],
                "configuredPoint": [float(point["longitude"]), float(point["latitude"])],
                "expectedHits": expected,
                "allowedAlternatives": alternatives,
                "forbiddenHits": sorted(set(point.get("forbidden_hits", []))),
                "enabled": bool(point.get("enabled")),
                "status": status,
                "directHits": direct_hits,
                "candidates": candidates[:limit],
                "manualReviewRequired": True,
                "reason": point.get("reason"),
            }
        )
    return {
        "schemaVersion": 1,
        "releaseVersion": release_dir.name,
        "qaPath": "qa/maritime-scene-canonical.gpkg",
        "candidateLimit": limit,
        "points": points,
    }
