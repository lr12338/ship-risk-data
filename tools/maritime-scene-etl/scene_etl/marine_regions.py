from __future__ import annotations

from pathlib import Path
from typing import Any, Iterator

from .geometry import (
    extract_zip,
    feature_properties,
    iter_vector_features,
    normalize_geometry,
    polygonal_only,
    select_vector_path,
)
from .records import quarantined_record, scene_record


SCENE_TYPES = {
    "marine_eez_v12": "marine_eez",
    "marine_territorial_sea_v4": "marine_territorial_sea",
    "marine_internal_waters_v4": "marine_internal_water",
    "marine_archipelagic_waters_v4": "marine_archipelagic_water",
    "marine_high_seas_v2": "marine_high_seas",
}


def iter_records(
    repo_root: Path,
    data_root: Path,
    dataset: dict[str, Any],
    stats: dict[str, Any],
    force: bool = False,
    bbox: tuple[float, float, float, float] | None = None,
) -> Iterator[dict[str, Any]]:
    raw = data_root / "raw" / dataset["raw_target_path"]
    staging = extract_zip(raw, data_root / "staging" / dataset["dataset_id"], force=force)
    paths = sorted(staging.rglob("*.shp"))
    if not paths:
        raise ValueError(f"Marine Regions archive has no shapefile: {raw}")
    source_path = select_vector_path(paths, expected_kind="polygon")
    stats["sourceLayer"] = source_path.stem
    scene_type = SCENE_TYPES[dataset["dataset_id"]]
    for source_crs, layer, feature in iter_vector_features(source_path, bbox=bbox):
        stats["rawCount"] = stats.get("rawCount", 0) + 1
        properties = feature_properties(feature)
        feature_id = _feature_id(feature, properties)
        normalized = normalize_geometry(
            feature.get("geometry"), source_crs, expected_dimension=2, require_kind="polygon"
        )
        if normalized.geometry is None:
            stats[normalized.status] = stats.get(normalized.status, 0) + 1
            continue
        polygon = polygonal_only(normalized.geometry)
        if polygon is None or polygon.is_empty:
            stats["REJECTED_WRONG_DIMENSION"] = stats.get("REJECTED_WRONG_DIMENSION", 0) + 1
            continue
        stats[normalized.status] = stats.get(normalized.status, 0) + 1
        if normalized.status == "QUARANTINED":
            yield quarantined_record(
                dataset["source_name"], dataset["source_version"], feature_id,
                polygon, {"original_scene_type": scene_type, "reason": normalized.reason, **properties}
            )
        else:
            parts = list(polygon.geoms) if polygon.geom_type == "MultiPolygon" else [polygon]
            stats["splitPartCount"] = stats.get("splitPartCount", 0) + len(parts)
            for index, part in enumerate(parts):
                part_id = f"{feature_id}:part-{index:04d}" if len(parts) > 1 else feature_id
                yield scene_record(
                    scene_type,
                    dataset["source_name"],
                    dataset["source_version"],
                    part_id,
                    part,
                    normalized.status,
                    "LEGAL_BOUNDARY_DATASET",
                    "SOURCE_PUBLISHED",
                    {"source_layer": layer, "source_parent_feature_id": feature_id,
                     "source_attributes": properties},
                )


def _feature_id(feature: dict[str, Any], properties: dict[str, Any]) -> str:
    for key in ("MRGID", "MRG_ID", "mrgid", "ID", "id"):
        if properties.get(key) is not None:
            return str(properties[key])
    return str(feature.get("id", "UNKNOWN"))
