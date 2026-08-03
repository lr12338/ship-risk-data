from __future__ import annotations

import gzip
import json
from pathlib import Path
from typing import Iterable, Iterator

from .tiling import tile_id


def iter_release_records(
    release_dir: Path,
    record_kind: str | None = None,
    scene_types: set[str] | None = None,
    unique: bool = True,
) -> Iterator[dict]:
    """Stream records from a release without loading the global snapshot."""
    seen: set[tuple[str, str, str, str]] = set()
    for path in sorted((release_dir / "tiles").glob("*.ndjson.gz")):
        with gzip.open(path, "rt", encoding="utf-8") as handle:
            for line in handle:
                if not line.strip():
                    continue
                record = json.loads(line)
                if record_kind and record.get("recordKind", "scene") != record_kind:
                    continue
                if scene_types and record.get("sceneType") not in scene_types:
                    continue
                if unique:
                    key = (
                        record.get("recordKind", "scene"),
                        str(record.get("sourceName", "")),
                        str(record.get("sourceFeatureId", "")),
                        str(record.get("geometryWkbHex", "")),
                    )
                    if key in seen:
                        continue
                    seen.add(key)
                yield record


def iter_records_for_point(
    release_dir: Path,
    longitude: float,
    latitude: float,
    record_kind: str | None = None,
    scene_types: set[str] | None = None,
    radius_degrees: int = 1,
) -> Iterator[dict]:
    """Read the point tile and neighbouring tiles for boundary-safe lookup."""
    center_lat = int(latitude // 1)
    center_lon = int(longitude // 1)
    tile_names = {
        tile_id(center_lon + dx, center_lat + dy)
        for dx in range(-radius_degrees, radius_degrees + 1)
        for dy in range(-radius_degrees, radius_degrees + 1)
        if -180 <= center_lon + dx <= 180 and -90 <= center_lat + dy <= 90
    }
    seen: set[tuple[str, str, str]] = set()
    for name in sorted(tile_names):
        path = release_dir / "tiles" / f"{name}.{record_kind or 'scene'}.ndjson.gz"
        if not path.exists():
            continue
        with gzip.open(path, "rt", encoding="utf-8") as handle:
            for line in handle:
                if not line.strip():
                    continue
                record = json.loads(line)
                if scene_types and record.get("sceneType") not in scene_types:
                    continue
                key = (
                    str(record.get("sourceName", "")),
                    str(record.get("sourceFeatureId", "")),
                    str(record.get("geometryWkbHex", "")),
                )
                if key in seen:
                    continue
                seen.add(key)
                yield record


def geometry_from_record(record: dict):
    from .geometry import geometry_from_wkb_hex

    return geometry_from_wkb_hex(record["geometryWkbHex"])


def point_hits(
    release_dir: Path,
    longitude: float,
    latitude: float,
    scene_types: set[str] | None = None,
) -> list[dict]:
    from shapely.geometry import Point

    point = Point(longitude, latitude)
    hits = []
    for record in iter_records_for_point(
        release_dir, longitude, latitude, scene_types=scene_types
    ):
        geometry = geometry_from_record(record)
        if geometry.intersects(point):
            hits.append(record)
    return hits
