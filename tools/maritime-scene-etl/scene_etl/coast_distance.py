from __future__ import annotations

import math
from pathlib import Path
from typing import Any

from .release_records import geometry_from_record, iter_records_for_point


NM_TO_METERS = 1852.0


def distance_to_coast(
    release_dir: Path,
    longitude: float,
    latitude: float,
    search_radius_nm: float = 60.0,
) -> dict[str, Any]:
    radius_degrees = max(1, int(math.ceil(search_radius_nm / 60.0)))
    best: tuple[float, dict[str, Any], Any] | None = None
    for record in iter_records_for_point(
        release_dir,
        longitude,
        latitude,
        record_kind="coast",
        radius_degrees=radius_degrees,
    ):
        geometry = geometry_from_record(record)
        distance_meters = _local_metric_distance(longitude, latitude, geometry)
        if distance_meters > search_radius_nm * NM_TO_METERS:
            continue
        if best is None or distance_meters < best[0]:
            best = (distance_meters, record, geometry)
    if best is None:
        return {
            "distanceNm": None,
            "nearestFeatureId": None,
            "nearestTileId": None,
            "searchRadiusNm": search_radius_nm,
            "available": False,
        }
    distance_meters, record, _ = best
    return {
        "distanceNm": round(distance_meters / NM_TO_METERS, 6),
        "nearestFeatureId": record.get("sourceFeatureId"),
        "nearestTileId": record.get("tileId"),
        "searchRadiusNm": search_radius_nm,
        "available": True,
        "sourceName": record.get("sourceName"),
        "sourceVersion": record.get("sourceVersion"),
    }


def classify_coast_distance(distance_nm: float | None, policy: dict[str, Any]) -> str:
    if distance_nm is None:
        return "UNKNOWN"
    thresholds = policy.get("thresholds", {})
    if distance_nm <= float(thresholds.get("very_near_coast_nm", 1)):
        return "VERY_NEAR_COAST"
    if distance_nm <= float(thresholds.get("coastal_nm", 12)):
        return "COASTAL"
    if distance_nm <= float(thresholds.get("offshore_nm", 60)):
        return "OFFSHORE"
    if distance_nm <= float(thresholds.get("far_offshore_nm", 200)):
        return "FAR_OFFSHORE"
    return "FAR_OFFSHORE"


def _local_metric_distance(longitude: float, latitude: float, geometry) -> float:
    """Distance in metres in a local azimuthal-equidistant projection."""
    from pyproj import CRS, Transformer
    from shapely.geometry import Point
    from shapely.ops import transform

    local = CRS.from_proj4(
        f"+proj=aeqd +lat_0={latitude} +lon_0={longitude} "
        "+datum=WGS84 +units=m +no_defs"
    )
    transformer = Transformer.from_crs("EPSG:4326", local, always_xy=True)
    projected_geometry = transform(transformer.transform, geometry)
    return Point(0.0, 0.0).distance(projected_geometry)
