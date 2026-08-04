from __future__ import annotations

import base64
from functools import lru_cache
import json
import math
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterator


GEOMETRY_STATUSES = {
    "VALID_ORIGINAL",
    "VALID_REPAIRED",
    "QUARANTINED",
    "REJECTED_EMPTY",
    "REJECTED_UNPARSEABLE",
    "REJECTED_WRONG_DIMENSION",
}


@dataclass
class NormalizedGeometry:
    geometry: Any | None
    status: str
    reason: str | None = None


def require_geospatial_dependencies() -> None:
    try:
        import fiona  # noqa: F401
        import pyproj  # noqa: F401
        import shapely  # noqa: F401
    except ImportError as exc:
        raise RuntimeError(
            "Fiona, PyProj, and Shapely are required; run "
            "`make -C tools/maritime-scene-etl venv`"
        ) from exc


def extract_zip(archive: Path, destination: Path, force: bool = False) -> Path:
    if destination.exists() and any(destination.iterdir()) and not force:
        return destination
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as handle:
        root = destination.resolve()
        for member in handle.infolist():
            target = (destination / member.filename).resolve()
            if root not in target.parents and target != root:
                raise ValueError(f"unsafe ZIP member path: {member.filename}")
        handle.extractall(destination)
    return destination


def vector_paths(staging: Path) -> list[Path]:
    return sorted(staging.rglob("*.shp"))


def select_vector_path(paths: list[Path], expected_kind: str | None = None) -> Path:
    if not paths:
        raise ValueError("no vector paths available")
    if expected_kind != "polygon" or len(paths) == 1:
        return paths[0]
    require_geospatial_dependencies()
    import fiona

    for path in paths:
        with fiona.open(path) as source:
            geometry_type = str(source.schema.get("geometry", "")).upper()
            if "POLYGON" in geometry_type:
                return path
    return paths[0]


def iter_vector_features(
    path: Path,
    bbox: tuple[float, float, float, float] | None = None,
) -> Iterator[tuple[str, str, dict[str, Any]]]:
    require_geospatial_dependencies()
    import fiona

    open_kwargs = {"bbox": bbox} if bbox is not None else {}
    with fiona.open(path, **open_kwargs) as source:
        source_crs = source.crs_wkt or str(source.crs) or "UNKNOWN"
        layer = path.stem
        for feature in source:
            yield source_crs, layer, feature


def feature_properties(feature: dict[str, Any]) -> dict[str, Any]:
    raw = dict(feature.get("properties") or {})
    result: dict[str, Any] = {}
    for key, value in raw.items():
        if value is None or isinstance(value, (str, int, float, bool)):
            result[str(key)] = value
        else:
            result[str(key)] = str(value)
    return result


def normalize_geometry(
    raw_geometry: dict[str, Any] | None,
    source_crs: Any,
    expected_dimension: int | None = None,
    require_kind: str | None = None,
) -> NormalizedGeometry:
    require_geospatial_dependencies()
    from shapely.geometry import shape
    from shapely.ops import transform
    from shapely.validation import explain_validity
    from shapely import get_dimensions, make_valid

    if not raw_geometry:
        return NormalizedGeometry(None, "REJECTED_EMPTY", "missing geometry")
    try:
        geometry = shape(raw_geometry)
    except Exception as exc:
        return NormalizedGeometry(None, "REJECTED_UNPARSEABLE", str(exc))
    if geometry.is_empty:
        return NormalizedGeometry(None, "REJECTED_EMPTY", "empty geometry")
    actual_dimension = get_dimensions(geometry)
    if expected_dimension is not None and actual_dimension != expected_dimension:
        return NormalizedGeometry(
            None,
            "REJECTED_WRONG_DIMENSION",
            f"expected dimension {expected_dimension}, actual {actual_dimension}",
        )
    if require_kind and not kind_matches(geometry, require_kind):
        return NormalizedGeometry(
            None, "REJECTED_WRONG_DIMENSION", f"expected geometry kind {require_kind}"
        )
    status = "VALID_ORIGINAL"
    if not geometry.is_valid:
        try:
            fixed = make_valid(geometry)
        except Exception as exc:
            return NormalizedGeometry(None, "QUARANTINED", f"make_valid failed: {exc}")
        if fixed.is_empty:
            return NormalizedGeometry(None, "REJECTED_EMPTY", "make_valid produced empty")
        geometry = fixed
        status = "VALID_REPAIRED" if geometry.is_valid else "QUARANTINED"
        if status == "QUARANTINED":
            return NormalizedGeometry(
                geometry.envelope, status, explain_validity(geometry)
            )
    try:
        geometry = _transform_to_wgs84(geometry, source_crs, transform)
        min_x, _, max_x, _ = geometry.bounds
        if min_x < -180.0 or max_x > 180.0:
            geometry = normalize_longitudes(geometry)
        if _crosses_dateline(geometry):
            geometry = split_dateline(geometry)
            status = "VALID_REPAIRED"
        if not geometry.is_valid:
            fixed = make_valid(geometry)
            if fixed.is_empty:
                return NormalizedGeometry(None, "REJECTED_EMPTY", "longitude normalization empty")
            geometry = fixed
            status = "VALID_REPAIRED"
        if require_kind == "polygon" and not kind_matches(geometry, require_kind):
            geometry = polygonal_only(geometry)
        elif require_kind == "line" and not kind_matches(geometry, require_kind):
            geometry = lineal_only(geometry)
        if geometry is None or geometry.is_empty:
            return NormalizedGeometry(None, "REJECTED_EMPTY", "dimension extraction empty")
        if require_kind and not kind_matches(geometry, require_kind):
            return NormalizedGeometry(
                geometry.envelope,
                "QUARANTINED",
                f"normalized geometry is not {require_kind}",
            )
        return NormalizedGeometry(geometry, status)
    except Exception as exc:
        return NormalizedGeometry(None, "REJECTED_UNPARSEABLE", str(exc))


def _transform_to_wgs84(geometry, source_crs, transform):
    if source_crs in (None, "", "{}", "UNKNOWN", "UNKNOWN_UNTIL_INSPECT"):
        return geometry
    try:
        transformer = _cached_wgs84_transformer(str(source_crs))
    except Exception:
        return geometry
    if transformer is None:
        return geometry
    return transform(transformer.transform, geometry)


@lru_cache(maxsize=8)
def _cached_wgs84_transformer(source_crs: str):
    from pyproj import CRS, Transformer

    source = CRS.from_user_input(source_crs)
    target = CRS.from_epsg(4326)
    if source.equals(target):
        return None
    return Transformer.from_crs(source, target, always_xy=True)


def normalize_longitudes(geometry):
    from shapely.ops import transform

    def wrap(x, y, z=None):
        wrapped_x = ((x + 180.0) % 360.0) - 180.0
        if z is None:
            return wrapped_x, y
        return wrapped_x, y, z

    return transform(wrap, geometry)


def split_dateline(geometry):
    from shapely import make_valid
    from shapely.geometry import GeometryCollection, MultiLineString, MultiPolygon, box
    from shapely.ops import transform, unary_union

    min_x, min_y, max_x, max_y = geometry.bounds
    if min_x <= -180.0 and max_x >= 180.0 \
            and (min_y <= -90.0 or max_y >= 90.0):
        return geometry

    if geometry.geom_type == "MultiPolygon":
        polygons = []
        for item in geometry.geoms:
            split = split_dateline(item) if _crosses_dateline(item) else item
            split = polygonal_only(split)
            if split is None or split.is_empty:
                continue
            polygons.extend(split.geoms if split.geom_type == "MultiPolygon" else [split])
        return MultiPolygon(polygons)
    if geometry.geom_type == "MultiLineString":
        lines = []
        for item in geometry.geoms:
            split = split_dateline(item) if _crosses_dateline(item) else item
            lines.extend(split.geoms if split.geom_type == "MultiLineString" else [split])
        return MultiLineString(lines)
    if geometry.geom_type == "GeometryCollection":
        return GeometryCollection([
            split_dateline(item) if _crosses_dateline(item) else item
            for item in geometry.geoms
        ])

    shifted = transform(
        lambda x, y, z=None: (x % 360.0, y)
        if z is None else (x % 360.0, y, z),
        geometry,
    )
    if not shifted.is_valid:
        shifted = make_valid(shifted)
    parts = []
    west = shifted.intersection(box(0, -90, 180, 90))
    east = shifted.intersection(box(180, -90, 360, 90))
    if not west.is_empty:
        parts.append(west)
    if not east.is_empty:
        parts.append(transform(
            lambda x, y, z=None: (x - 360.0, y)
            if z is None else (x - 360.0, y, z), east))
    return unary_union(parts)


def _crosses_dateline(geometry) -> bool:
    for sequence in _coordinate_sequences(geometry):
        for first, second in zip(sequence, sequence[1:]):
            if abs(first[0] - second[0]) > 180.0:
                return True
    return False


def _coordinate_sequences(geometry):
    if geometry.geom_type == "Polygon":
        yield list(geometry.exterior.coords)
        for ring in geometry.interiors:
            yield list(ring.coords)
        return
    if geometry.geom_type in {"LineString", "LinearRing"}:
        yield list(geometry.coords)
        return
    if geometry.geom_type == "Point":
        yield [geometry.coords[0]]
        return
    for item in getattr(geometry, "geoms", []):
        yield from _coordinate_sequences(item)


def kind_matches(geometry, expected: str) -> bool:
    expected = expected.lower()
    if expected == "polygon":
        return geometry.geom_type in {"Polygon", "MultiPolygon"}
    if expected == "line":
        return geometry.geom_type in {"LineString", "MultiLineString"}
    if expected == "point":
        return geometry.geom_type in {"Point", "MultiPoint"}
    return True


def polygonal_only(geometry):
    from shapely.geometry import GeometryCollection, MultiPolygon, Polygon

    if geometry is None or geometry.is_empty:
        return None
    if isinstance(geometry, (Polygon, MultiPolygon)):
        return geometry
    if isinstance(geometry, GeometryCollection):
        polygons = [polygonal_only(item) for item in geometry.geoms]
        polygons = [item for item in polygons if item is not None and not item.is_empty]
        if not polygons:
            return None
        from shapely.ops import unary_union

        return unary_union(polygons)
    return None


def lineal_only(geometry):
    from shapely.geometry import GeometryCollection, LineString, MultiLineString

    if geometry is None or geometry.is_empty:
        return None
    if isinstance(geometry, (LineString, MultiLineString)):
        return geometry
    if isinstance(geometry, GeometryCollection):
        lines = [lineal_only(item) for item in geometry.geoms]
        lines = [item for item in lines if item is not None and not item.is_empty]
        if not lines:
            return None
        from shapely.ops import linemerge

        return linemerge(lines)
    return None


def metric_buffer(geometry, meters: float):
    """Buffer a local geometry in metres using a local azimuthal projection."""
    require_geospatial_dependencies()
    from pyproj import CRS, Transformer
    from shapely.ops import transform

    centroid = geometry.centroid
    utm_epsg = _utm_epsg(centroid.x, centroid.y)
    if utm_epsg is not None:
        forward, backward = _cached_metric_transformers(utm_epsg)
    else:
        local = CRS.from_proj4(
            f"+proj=aeqd +lat_0={centroid.y} +lon_0={centroid.x} "
            "+datum=WGS84 +units=m +no_defs"
        )
        forward = Transformer.from_crs("EPSG:4326", local, always_xy=True)
        backward = Transformer.from_crs(local, "EPSG:4326", always_xy=True)
    projected = transform(forward.transform, geometry)
    return transform(backward.transform, projected.buffer(meters))


def _utm_epsg(longitude: float, latitude: float) -> int | None:
    if latitude < -80 or latitude > 84:
        return None
    zone = min(60, max(1, int((longitude + 180) // 6) + 1))
    return (32600 if latitude >= 0 else 32700) + zone


@lru_cache(maxsize=120)
def _cached_metric_transformers(epsg: int):
    from pyproj import Transformer

    forward = Transformer.from_crs("EPSG:4326", f"EPSG:{epsg}", always_xy=True)
    backward = Transformer.from_crs(f"EPSG:{epsg}", "EPSG:4326", always_xy=True)
    return forward, backward


def geodesic_area_km2(geometry) -> float:
    require_geospatial_dependencies()
    from pyproj import Geod

    geod = Geod(ellps="WGS84")

    def area(value) -> float:
        if value.geom_type == "Polygon":
            result, _ = geod.geometry_area_perimeter(value)
            return abs(result)
        if value.geom_type == "MultiPolygon":
            return sum(area(item) for item in value.geoms)
        return 0.0

    return area(geometry) / 1_000_000.0


def geometry_wkb_hex(geometry) -> str:
    return geometry.wkb_hex


def geometry_wkb_base64(geometry) -> str:
    return base64.b64encode(geometry.wkb).decode("ascii")


def geometry_from_wkb_hex(value: str):
    require_geospatial_dependencies()
    from shapely import from_wkb

    return from_wkb(bytes.fromhex(value))


def safe_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), default=str)


def coordinate_is_valid(geometry) -> bool:
    for x, y in _coordinates(geometry):
        if not (math.isfinite(x) and math.isfinite(y)):
            return False
        if x < -180.0 or x > 180.0 or y < -90.0 or y > 90.0:
            return False
    return True


def _coordinates(geometry):
    if hasattr(geometry, "coords"):
        yield from geometry.coords
        return
    for item in getattr(geometry, "geoms", []):
        yield from _coordinates(item)
