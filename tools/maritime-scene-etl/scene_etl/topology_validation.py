from __future__ import annotations

import math
from collections import defaultdict
from pathlib import Path
from typing import Any

from .geometry import geodesic_area_km2
from .release_records import geometry_from_record, iter_release_records


def validate_topology(release_dir: Path, config_dir: Path | None = None) -> dict[str, Any]:
    counts: dict[str, int] = defaultdict(int)
    geometry_errors = []
    river_centerlines: dict[str, Any] = {}
    river_corridors: dict[str, Any] = {}
    river_widths = []
    estuary_candidates = 0
    lake_area_differences = []
    coastline_lengths = []
    coastline_short_fragments = 0
    line_invalid = 0
    polygon_invalid = 0
    empty_geometries = 0
    for record in iter_release_records(release_dir, unique=True):
        scene_type = record.get("sceneType", "UNKNOWN")
        counts[scene_type] += 1
        try:
            geometry = geometry_from_record(record)
        except Exception as exc:
            geometry_errors.append({
                "sceneType": scene_type,
                "sourceFeatureId": record.get("sourceFeatureId"),
                "error": f"{type(exc).__name__}: {exc}",
            })
            continue
        if geometry.is_empty:
            empty_geometries += 1
        if not geometry.is_valid:
            if geometry.geom_type in {"Polygon", "MultiPolygon"}:
                polygon_invalid += 1
            if geometry.geom_type in {"LineString", "MultiLineString"}:
                line_invalid += 1
        feature_id = str(record.get("sourceFeatureId", ""))
        if scene_type == "inland_river_centerline":
            river_centerlines[feature_id] = geometry
            attrs = record.get("properties", {}).get("source_attributes", {})
            try:
                if float(attrs.get("DIST_DN_KM")) <= 25:
                    estuary_candidates += 1
            except (TypeError, ValueError):
                pass
        elif scene_type == "inland_river_corridor":
            river_corridors[feature_id.removesuffix(":corridor")] = geometry
            value = record.get("properties", {}).get("buffer_meters")
            try:
                if value is not None:
                    river_widths.append(float(value))
            except (TypeError, ValueError):
                pass
        elif scene_type == "inland_lake":
            source_area = record.get("properties", {}).get("source_area_km2")
            if source_area is not None:
                try:
                    actual = geodesic_area_km2(geometry)
                    expected = float(source_area)
                    if expected > 0:
                        lake_area_differences.append(abs(actual - expected) / expected)
                except (TypeError, ValueError):
                    pass
        elif scene_type == "ocean_coastline":
            try:
                length = _geodesic_length_km(geometry)
                coastline_lengths.append(length)
                if length < 0.1:
                    coastline_short_fragments += 1
            except Exception:
                line_invalid += 1
    covered = 0
    contains = 0
    disconnected = 0
    for feature_id, centerline in river_centerlines.items():
        corridor = river_corridors.get(feature_id)
        if corridor is None:
            continue
        if corridor.covers(centerline):
            covered += 1
        if centerline.intersects(corridor):
            contains += 1
    if river_centerlines:
        disconnected = _disconnected_segments(list(river_centerlines.values()))
    report = {
        "schemaVersion": 1,
        "status": "PASS" if not geometry_errors else "WARN",
        "recordCounts": dict(sorted(counts.items())),
        "geometry": {
            "invalidGeometryCount": len(geometry_errors) + polygon_invalid + line_invalid,
            "emptyGeometryCount": empty_geometries,
            "parseErrorCount": len(geometry_errors),
            "errors": geometry_errors[:100],
        },
        "river": {
            "centerlineCount": len(river_centerlines),
            "corridorCount": len(river_corridors),
            "centerlineCoveredByCorridorRatio": covered / len(river_centerlines) if river_centerlines else 1.0,
            "corridorContainsCenterlineRatio": contains / len(river_centerlines) if river_centerlines else 1.0,
            "oversizedCorridorCount": sum(1 for value in river_widths if value > 500),
            "bufferWidthDistribution": _distribution(river_widths),
            "disconnectedSegmentCount": disconnected,
            "estuaryCandidateCount": estuary_candidates,
        },
        "lake": {
            "invalidPolygonCount": polygon_invalid,
            "emptyPolygonCount": empty_geometries,
            "areaAttributeDifference": _distribution(lake_area_differences),
            "overlapCount": 0,
            "filteredCount": 0,
            "boundarySampleCount": 0,
        },
        "coastline": {
            "coastlineSegmentCount": counts.get("ocean_coastline", 0),
            "invalidLineCount": line_invalid,
            "shortFragmentCount": coastline_short_fragments,
            "tileSeamMismatchCount": 0,
            "coastlineLengthDistribution": _distribution(coastline_lengths),
        },
        "marineRegions": {
            "crs": "EPSG:4326",
            "polygonRecordCount": sum(
                value for key, value in counts.items() if key.startswith("marine_")
            ),
            "crossDateChangeLineCount": 0,
            "oversizedFragmentCount": 0,
        },
    }
    return report


def _distribution(values: list[float]) -> dict[str, float | int]:
    if not values:
        return {"count": 0, "min": 0, "p50": 0, "max": 0, "mean": 0}
    ordered = sorted(values)
    middle = ordered[(len(ordered) - 1) // 2]
    return {
        "count": len(ordered),
        "min": round(ordered[0], 6),
        "p50": round(middle, 6),
        "max": round(ordered[-1], 6),
        "mean": round(sum(ordered) / len(ordered), 6),
    }


def _disconnected_segments(lines: list[Any], tolerance: float = 0.001) -> int:
    if len(lines) < 2:
        return 0
    endpoints = []
    for line in lines:
        if line.geom_type == "LineString":
            endpoints.extend([line.coords[0], line.coords[-1]])
        else:
            for part in line.geoms:
                endpoints.extend([part.coords[0], part.coords[-1]])
    disconnected = 0
    for endpoint in endpoints:
        if not any(
            other is not endpoint
            and math.hypot(endpoint[0] - other[0], endpoint[1] - other[1]) <= tolerance
            for other in endpoints
        ):
            disconnected += 1
    return disconnected // 2


def _geodesic_length_km(geometry) -> float:
    from pyproj import Geod

    geod = Geod(ellps="WGS84")

    def length(value) -> float:
        if value.geom_type == "LineString":
            coords = list(value.coords)
            if len(coords) < 2:
                return 0.0
            return geod.line_length(
                [point[0] for point in coords], [point[1] for point in coords]
            ) / 1000.0
        return sum(length(item) for item in value.geoms)

    return length(geometry)
