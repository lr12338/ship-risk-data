from __future__ import annotations

import math
from typing import Iterable


def tile_origin(lon: float, lat: float, size: int = 1) -> tuple[int, int]:
    return math.floor(lat / size) * size, math.floor(lon / size) * size


def tile_id(lon: float, lat: float, size: int = 1) -> str:
    lat_origin, lon_origin = tile_origin(lon, lat, size)
    return f"{_lat_prefix(lat_origin)}{abs(lat_origin):02d}_{_lon_prefix(lon_origin)}{abs(lon_origin):03d}"


def _lat_prefix(value: int) -> str:
    return "N" if value >= 0 else "S"


def _lon_prefix(value: int) -> str:
    return "E" if value >= 0 else "W"


def tiles_for_bounds(bounds: Iterable[float], size: int = 1) -> list[str]:
    min_lon, min_lat, max_lon, max_lat = bounds
    min_lon = max(-180.0, min_lon)
    max_lon = min(180.0, max_lon)
    min_lat = max(-90.0, min_lat)
    max_lat = min(90.0, max_lat)
    start_lat, start_lon = tile_origin(min_lon, min_lat, size)
    end_lat, end_lon = tile_origin(
        min_lon if max_lon == 180 else max_lon,
        min_lat if max_lat == 90 else max_lat,
        size,
    )
    result = []
    for lat in range(start_lat, end_lat + size, size):
        for lon in range(start_lon, end_lon + size, size):
            result.append(tile_id(lon, lat, size))
    return sorted(set(result))


def tile_path(tile: str, kind: str) -> str:
    return f"{tile}.{kind}.ndjson.gz"


def jrc_tile_origins(
    bbox: tuple[float, float, float, float], size: int = 10
) -> list[tuple[int, int]]:
    """Return official JRC top-left tile origins covering a WGS84 bbox.

    JRC names a tile by its western longitude and northern latitude, for
    example ``120E_40N`` covers 120–130E, 30–40N.
    """
    min_lon, min_lat, max_lon, max_lat = bbox
    epsilon = 1e-9
    first_lon = math.floor(min_lon / size) * size
    last_lon = math.floor((max_lon - epsilon) / size) * size
    first_top_lat = math.ceil(min_lat / size) * size
    last_top_lat = math.ceil(max_lat / size) * size
    origins = []
    for top_lat in range(first_top_lat, last_top_lat + 1, size):
        for left_lon in range(first_lon, last_lon + 1, size):
            origins.append((left_lon, top_lat))
    return origins


def jrc_tile_id(left_lon: int, top_lat: int) -> str:
    lon_suffix = "E" if left_lon >= 0 else "W"
    lat_suffix = "N" if top_lat >= 0 else "S"
    return f"{abs(left_lon)}{lon_suffix}_{abs(top_lat)}{lat_suffix}"
