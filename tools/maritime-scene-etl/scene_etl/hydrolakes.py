from __future__ import annotations

from pathlib import Path
from typing import Any, Iterator

from .config import load_yaml
from .geometry import extract_zip, feature_properties, geodesic_area_km2, iter_vector_features, normalize_geometry, polygonal_only
from .records import quarantined_record, scene_record


def iter_records(
    repo_root: Path,
    data_root: Path,
    config_dir: Path,
    dataset: dict[str, Any],
    stats: dict[str, Any],
    force: bool = False,
    bbox: tuple[float, float, float, float] | None = None,
) -> Iterator[dict[str, Any]]:
    policy = load_yaml(config_dir / "lake-policy.yaml")
    raw = data_root / "raw" / dataset["raw_target_path"]
    staging = extract_zip(raw, data_root / "staging" / dataset["dataset_id"], force=force)
    paths = sorted(staging.rglob("*.shp"))
    if not paths:
        raise ValueError(f"HydroLAKES archive has no shapefile: {raw}")
    area_field = None
    for source_crs, layer, feature in iter_vector_features(paths[0], bbox=bbox):
        stats["rawCount"] = stats.get("rawCount", 0) + 1
        properties = feature_properties(feature)
        if area_field is None:
            area_field = _resolve_field(properties, policy.get("area_field_candidates", []))
            stats["areaField"] = area_field
        normalized = normalize_geometry(feature.get("geometry"), source_crs, expected_dimension=2, require_kind="polygon")
        if normalized.geometry is None:
            stats[normalized.status] = stats.get(normalized.status, 0) + 1
            continue
        polygon = polygonal_only(normalized.geometry)
        if polygon is None or polygon.is_empty:
            stats["REJECTED_WRONG_DIMENSION"] = stats.get("REJECTED_WRONG_DIMENSION", 0) + 1
            continue
        area = _number(properties.get(area_field)) if area_field else None
        if area is None and policy.get("allow_geodesic_area_fallback", True):
            area = geodesic_area_km2(polygon)
            stats["areaFallbackCount"] = stats.get("areaFallbackCount", 0) + 1
        if area is not None and area < float(policy.get("minimum_area_km2", 1.0)):
            stats["filteredCount"] = stats.get("filteredCount", 0) + 1
            continue
        stats[normalized.status] = stats.get(normalized.status, 0) + 1
        feature_id = _feature_id(feature, properties)
        name = _first(properties, policy.get("name_field_candidates", []))
        lake_type = _first(properties, policy.get("type_field_candidates", []))
        base_properties = {
            "source_layer": layer,
            "source_attributes": properties,
            "source_area_km2": area,
            "lake_name": name,
            "lake_type": lake_type,
        }
        if normalized.status == "QUARANTINED":
            yield quarantined_record(
                dataset["source_name"], dataset["source_version"], feature_id,
                polygon.envelope, {"original_scene_type": "inland_lake", "reason": normalized.reason, **base_properties}
            )
        else:
            yield scene_record(
                "inland_lake", dataset["source_name"], dataset["source_version"], feature_id,
                polygon, normalized.status, "HYDROGRAPHY_CANDIDATE", "MEDIUM", base_properties
            )


def _resolve_field(properties: dict[str, Any], candidates: list[str]) -> str | None:
    upper = {key.upper(): key for key in properties}
    for candidate in candidates:
        if candidate.upper() in upper:
            return upper[candidate.upper()]
    return None


def _number(value: Any) -> float | None:
    try:
        return float(value) if value is not None else None
    except (TypeError, ValueError):
        return None


def _first(properties: dict[str, Any], candidates: list[str]) -> Any:
    field = _resolve_field(properties, candidates)
    return properties.get(field) if field else None


def _feature_id(feature, properties) -> str:
    for key in ("Hylak_id", "HYLAK_ID", "LAKE_ID", "ID", "id"):
        if properties.get(key) is not None:
            return str(properties[key])
    return str(feature.get("id", "UNKNOWN"))
