from __future__ import annotations

from pathlib import Path
from typing import Any, Iterator

from .config import load_yaml
from .geometry import extract_zip, feature_properties, iter_vector_features, lineal_only, metric_buffer, normalize_geometry
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
    policy = load_yaml(config_dir / "river-policy.yaml")
    stats["selectionMode"] = policy.get("selection_mode", "UNKNOWN")
    stats["selectionFilters"] = policy.get("filters", [])
    stats["candidateConfidence"] = "LOW"
    stats["selectedByOrder"] = {}
    stats["selectedByContinent"] = {}
    stats["selectedCenterlineLengthKm"] = 0.0
    stats["estimatedCorridorAreaKm2"] = 0.0
    raw = data_root / "raw" / dataset["raw_target_path"]
    staging = extract_zip(raw, data_root / "staging" / dataset["dataset_id"], force=force)
    paths = sorted(staging.rglob("*.shp"))
    if not paths:
        raise ValueError(f"HydroRIVERS archive has no shapefile: {raw}")
    field_resolution: dict[str, str | None] | None = None
    candidates = policy.get("field_candidates", {})
    filters = policy.get("filters", [])
    requested_bbox = None
    if bbox is not None:
        from shapely.geometry import box

        requested_bbox = box(*bbox)
    for source_crs, layer, feature in iter_vector_features(paths[0], bbox=bbox):
        stats["rawCount"] = stats.get("rawCount", 0) + 1
        properties = feature_properties(feature)
        if field_resolution is None:
            field_resolution = {
                role: _resolve_field(properties, names)
                for role, names in candidates.items()
            }
            stats["fieldResolution"] = field_resolution
        if not _keep(properties, filters, field_resolution, policy, stats):
            stats["filteredCount"] = stats.get("filteredCount", 0) + 1
            continue
        normalized = normalize_geometry(feature.get("geometry"), source_crs, expected_dimension=1, require_kind="line")
        if normalized.geometry is None:
            stats[normalized.status] = stats.get(normalized.status, 0) + 1
            continue
        line = lineal_only(normalized.geometry)
        if line is None or line.is_empty:
            stats["REJECTED_WRONG_DIMENSION"] = stats.get("REJECTED_WRONG_DIMENSION", 0) + 1
            continue
        if requested_bbox is not None:
            if not line.intersects(requested_bbox):
                stats["requestedBboxRejected"] = stats.get("requestedBboxRejected", 0) + 1
                continue
            line = lineal_only(line.intersection(requested_bbox))
            if line is None or line.is_empty:
                continue
        order = _number(properties, field_resolution.get("river_order") if field_resolution else None)
        distance_to_ocean = _number(properties, field_resolution.get("distance_to_ocean_km") if field_resolution else None)
        buffer_meters = _buffer_meters(order, policy)
        confidence = _confidence(distance_to_ocean, policy)
        feature_id = _feature_id(feature, properties)
        stats[normalized.status] = stats.get(normalized.status, 0) + 1
        if normalized.status == "QUARANTINED":
            yield quarantined_record(
                dataset["source_name"], dataset["source_version"], feature_id,
                line.envelope, {"original_scene_type": "inland_river_centerline", "reason": normalized.reason, **properties}
            )
            continue
        order_key = str(int(order)) if order is not None else "UNKNOWN"
        stats["selectedByOrder"][order_key] = stats["selectedByOrder"].get(order_key, 0) + 1
        continent = _continent(line.centroid.x, line.centroid.y)
        stats["selectedByContinent"][continent] = stats["selectedByContinent"].get(continent, 0) + 1
        length_km = float(properties.get("LENGTH_KM") or 0.0)
        stats["selectedCenterlineLengthKm"] += length_km
        stats["estimatedCorridorAreaKm2"] += length_km * buffer_meters * 2.0 / 1000.0
        base_properties = {
            "source_layer": layer,
            "source_attributes": properties,
            "river_class": str(order) if order is not None else "UNKNOWN",
        }
        yield scene_record(
            "inland_river_centerline", dataset["source_name"], dataset["source_version"],
            feature_id, line, normalized.status, "HYDROGRAPHY_CANDIDATE", confidence, base_properties
        )
        corridor = metric_buffer(line, buffer_meters)
        yield scene_record(
            "inland_river_corridor", dataset["source_name"], dataset["source_version"],
            feature_id + ":corridor", corridor, normalized.status, "HYDROGRAPHY_CANDIDATE", confidence,
            {**base_properties, "buffer_meters": buffer_meters, "confidence": confidence}
        )


def _continent(lon: float, lat: float) -> str:
    if lat < -60:
        return "ANTARCTICA"
    if -25 <= lon <= 60 and lat >= -35:
        return "EUROPE" if lat >= 35 else "AFRICA"
    if 25 <= lon <= 180 and lat >= -10:
        return "ASIA"
    if 110 <= lon <= 180 and lat < -10:
        return "OCEANIA"
    if -170 <= lon < -25:
        return "NORTH_AMERICA" if lat >= 8 else "SOUTH_AMERICA"
    return "OCEANIA"


def _resolve_field(properties: dict[str, Any], candidates: list[str]) -> str | None:
    upper = {key.upper(): key for key in properties}
    for candidate in candidates:
        if candidate.upper() in upper:
            return upper[candidate.upper()]
    return None


def _keep(properties, filters, resolution, policy, stats) -> bool:
    available = []
    matches = []
    for rule in filters:
        role = rule.get("field_role")
        field = resolution.get(role) if resolution else None
        if not field or properties.get(field) is None:
            continue
        available.append(role)
        number = _number(properties, field)
        if number is None:
            continue
        operator = rule.get("operator")
        target = float(rule.get("value"))
        matches.append(_compare(number, operator, target))
    if not available:
        stats["policyStatus"] = "UNKNOWN_FIELDS_KEEP_ALL"
        return True
    stats["policyStatus"] = "APPLIED"
    return any(matches) if policy.get("selection_mode") == "ANY_AVAILABLE_RULE" else all(matches)


def _compare(value: float, operator: str, target: float) -> bool:
    return {
        "ge": value >= target,
        "gt": value > target,
        "le": value <= target,
        "lt": value < target,
        "eq": value == target,
    }.get(operator, False)


def _number(properties, field) -> float | None:
    if not field:
        return None
    value = properties.get(field)
    try:
        return float(value) if value is not None else None
    except (TypeError, ValueError):
        return None


def _buffer_meters(order: float | None, policy: dict[str, Any]) -> float:
    values = policy.get("buffer_meters_by_order", {})
    if order is None:
        return float(values.get("default", 80))
    return float(values.get(str(int(order)), values.get("default", 80)))


def _confidence(distance_to_ocean: float | None, policy: dict[str, Any]) -> str:
    if distance_to_ocean is not None and distance_to_ocean <= float(policy.get("estuary_low_confidence_distance_km", 25)):
        return "LOW"
    return str(policy.get("fallback_confidence", "MEDIUM"))


def _feature_id(feature, properties) -> str:
    for key in ("HYRIV_ID", "RIV_ID", "MAIN_RIV", "ID", "id"):
        if properties.get(key) is not None:
            return str(properties[key])
    return str(feature.get("id", "UNKNOWN"))
