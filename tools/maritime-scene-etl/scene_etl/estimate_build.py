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
) -> dict[str, Any]:
    if scope not in {"global", "regional", "partition"}:
        raise ValueError("scope must be global, regional, or partition")
    datasets = [item for item in load_datasets(config_dir) if item["enabled"]]
    raw_bytes = 0
    feature_estimates = []
    for dataset in datasets:
        path = data_root / "raw" / dataset["raw_target_path"]
        size = path.stat().st_size if path.exists() else int(dataset.get("expected_size_bytes", 0) or 0)
        raw_bytes += size
        stats_path = data_root / "staging" / "normalized" / f"{dataset['dataset_id']}.ndjson.stats.json"
        if stats_path.exists():
            stats = json.loads(stats_path.read_text(encoding="utf-8"))
            feature_estimates.append({
                "datasetId": dataset["dataset_id"],
                "estimatedFeatures": stats.get("outputCount", 0),
                "source": "normalized-stats",
            })
        else:
            feature_estimates.append({
                "datasetId": dataset["dataset_id"],
                "estimatedFeatures": None,
                "source": "not-normalized",
            })
    scope_factor = 1.0 if scope == "global" else 0.15
    staging = int(raw_bytes * (0.15 if scope == "global" else 0.05) * scope_factor)
    release = int(raw_bytes * (0.08 if scope == "global" else 0.04) * scope_factor)
    usage = shutil.disk_usage(data_root if data_root.exists() else data_root.parent)
    required = staging + release
    return {
        "schemaVersion": 1,
        "scope": scope,
        "bbox": list(bbox) if bbox else None,
        "datasetCount": len(datasets),
        "estimatedRawBytes": raw_bytes,
        "estimatedFeatureCounts": feature_estimates,
        "estimatedReadFeatures": None if scope == "global" else "partitioned-stream",
        "estimatedTileCount": "global-grid" if scope == "global" else "bbox-dependent",
        "estimatedStagingBytes": staging,
        "estimatedReleaseBytes": release,
        "estimatedPeakMemoryBytes": 512 * 1024 * 1024 if scope != "global" else 2 * 1024 * 1024 * 1024,
        "estimatedSeconds": max(60, int((staging + release) / 50_000_000)),
        "diskAvailableBytes": usage.free,
        "safeMarginBytes": int(usage.free * 0.2),
        "safe": required + int(usage.free * 0.2) <= usage.free,
        "recommendedBuildMode": "PARTITIONED_STREAMING" if scope == "global" else "REGIONAL_STREAMING",
        "constraints": [
            "Do not load global HydroRIVERS/HydroLAKES into memory.",
            "Do not union global polygons or vectorize global JRC rasters.",
            "Use deterministic tile ordering and immutable release names.",
        ],
    }
