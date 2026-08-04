from __future__ import annotations

from pathlib import Path
from typing import Any, Iterator

from .geometry import extract_zip, feature_properties, iter_vector_features, normalize_geometry, polygonal_only
from .records import quarantined_record, scene_record


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
    paths = sorted(
        path for path in staging.rglob("*.shp")
        if path.parent.name.lower() == "h" and "l1" in path.stem.lower()
    )
    if not paths:
        raise ValueError(f"GSHHG archive has no high-resolution L1 shapefile: {raw}")
    for source_crs, layer, feature in iter_vector_features(paths[0], bbox=bbox):
        stats["rawCount"] = stats.get("rawCount", 0) + 1
        properties = feature_properties(feature)
        feature_id = str(properties.get("id", feature.get("id", "UNKNOWN")))
        normalized = normalize_geometry(feature.get("geometry"), source_crs, expected_dimension=2, require_kind="polygon")
        if normalized.geometry is None:
            stats[normalized.status] = stats.get(normalized.status, 0) + 1
            continue
        polygon = polygonal_only(normalized.geometry)
        if polygon is None or polygon.is_empty:
            stats["REJECTED_WRONG_DIMENSION"] = stats.get("REJECTED_WRONG_DIMENSION", 0) + 1
            continue
        stats[normalized.status] = stats.get(normalized.status, 0) + 1
        base = {
            "source_layer": layer,
            "source_attributes": properties,
            "resolution": "high",
            "gshhg_level": 1,
        }
        if normalized.status == "QUARANTINED":
            yield quarantined_record(
                dataset["source_name"], dataset["source_version"], feature_id,
                polygon.envelope, {"original_scene_type": "land", "reason": normalized.reason, **base}
            )
            continue
        yield scene_record(
            "land", dataset["source_name"], dataset["source_version"], feature_id,
            polygon, normalized.status, "GLOBAL_COASTLINE_DATASET", "SOURCE_PUBLISHED", base
        )
        yield scene_record(
            "ocean_coastline", dataset["source_name"], dataset["source_version"], feature_id,
            polygon.boundary, normalized.status, "GLOBAL_COASTLINE_DATASET", "SOURCE_PUBLISHED", base,
            record_kind="coast",
        )
