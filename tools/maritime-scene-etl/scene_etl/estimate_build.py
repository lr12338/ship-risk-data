from __future__ import annotations

import json
import shutil
from pathlib import Path
from typing import Any

from .config import load_datasets


def estimate_build(
    data_root: Path,
    config_dir: Path,
    scope: str,
    bbox: tuple[float, float, float, float] | None = None,
    release: str = "phase1-global-base-v1",
) -> dict[str, Any]:
    if scope not in {"global", "regional", "partition"}:
        raise ValueError("scope must be global, regional, or partition")
    datasets = [item for item in load_datasets(config_dir) if item["enabled"]]
    raw_bytes = 0
    inspected = _inspected_counts(data_root)
    policy_counts = _policy_counts(data_root)
    feature_estimates = []
    for dataset in datasets:
        path = data_root / "raw" / dataset["raw_target_path"]
        size = path.stat().st_size if path.exists() else int(dataset.get("expected_size_bytes", 0) or 0)
        raw_bytes += size
        stats_path = data_root / "staging" / "normalized" / f"{dataset['dataset_id']}.ndjson.stats.json"
        source_count = inspected.get(dataset["dataset_id"])
        if stats_path.exists():
            stats = json.loads(stats_path.read_text(encoding="utf-8"))
            is_global = stats.get("bbox") is None
            filtered = stats.get("outputCount") if is_global else policy_counts.get(dataset["dataset_id"])
            feature_estimates.append({
                "datasetId": dataset["dataset_id"],
                "sourceFeatures": stats.get("rawCount", source_count) if is_global else source_count,
                "filteredFeatures": filtered,
                "estimatedFeatures": filtered if filtered is not None else source_count,
                "source": "global-normalized-stats" if is_global else (
                    "policy-statistics" if filtered is not None else "inspect-metadata"),
            })
        else:
            feature_estimates.append({
                "datasetId": dataset["dataset_id"],
                "sourceFeatures": source_count,
                "filteredFeatures": None,
                "estimatedFeatures": source_count,
                "source": "inspect-metadata" if source_count is not None else "not-inspected",
            })
    scope_factor = 1.0 if scope == "global" else 0.15
    staging = int(raw_bytes * 1.35 * scope_factor)
    release_bytes = int(raw_bytes * 0.85 * scope_factor)
    disk_path = data_root
    while not disk_path.exists() and disk_path != disk_path.parent:
        disk_path = disk_path.parent
    usage = shutil.disk_usage(disk_path)
    required = staging + release_bytes
    tile_count = 64_800 if scope == "global" else max(1, int(64_800 * scope_factor))
    partitions = [
        "marine", "land-coast", "asia", "europe", "africa",
        "north-america", "south-america", "oceania",
    ]
    safe_margin = max(10 * 1024**3, int(usage.free * 0.2))
    one_pass = required + safe_margin <= usage.free
    return {
        "schemaVersion": 2,
        "release": release,
        "scope": scope,
        "bbox": list(bbox) if bbox else None,
        "datasetCount": len(datasets),
        "estimatedRawBytes": raw_bytes,
        "estimatedFeatureCounts": feature_estimates,
        "estimatedReadFeatures": sum(
            int(item["sourceFeatures"] or 0) for item in feature_estimates
        ),
        "estimatedTileCount": tile_count,
        "estimatedSceneFileCount": tile_count,
        "estimatedCoastFileCount": tile_count,
        "estimatedStagingBytes": staging,
        "estimatedReleaseBytes": release_bytes,
        "estimatedPeakMemoryBytes": 768 * 1024 * 1024,
        "estimatedSeconds": max(
            60,
            int((staging + release_bytes) / 20_000_000),
            int(sum(int(item["sourceFeatures"] or 0) for item in feature_estimates) / 5_000),
        ),
        "diskAvailableBytes": usage.free,
        "safeMarginBytes": safe_margin,
        "safe": one_pass,
        "onePassBuildSuitable": one_pass,
        "recommendedBuildMode": "PARTITIONED_STREAMING" if scope == "global" else "REGIONAL_STREAMING",
        "recommendedPartitions": partitions if scope == "global" else ["requested-bbox"],
        "constraints": [
            "Do not load global HydroRIVERS/HydroLAKES into memory.",
            "Do not union global polygons or vectorize global JRC rasters.",
            "Use deterministic tile ordering and immutable release names.",
        ],
    }


def _inspected_counts(data_root: Path) -> dict[str, int]:
    path = data_root / "reports" / "inspect.json"
    if not path.exists():
        return {}
    value = json.loads(path.read_text(encoding="utf-8"))
    rows = value.get("result", value) if isinstance(value, dict) else value
    return {
        str(item["datasetId"]): int(item["featureCount"])
        for item in rows
        if isinstance(item, dict) and item.get("featureCount") is not None
    }


def _policy_counts(data_root: Path) -> dict[str, int]:
    path = data_root / "reports" / "global-source-statistics.json"
    if not path.exists():
        return {}
    value = json.loads(path.read_text(encoding="utf-8"))
    return {
        "hydrolakes_v1": int(value.get("lake", {}).get("selectedAt50Km2", 0)),
        "hydrorivers_v1": int(value.get("river", {}).get("selected", 0)),
    }
