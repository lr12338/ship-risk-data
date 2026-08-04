from __future__ import annotations

import csv
import gzip
import json
from collections import defaultdict
from pathlib import Path
from typing import Any

from .manifest import write_json
from .tiling import tile_id


def write_global_coverage(
    release_dir: Path,
    scene_counts: dict[str, int],
    coast_counts: dict[str, int],
    global_coverage: bool,
) -> dict[str, Any]:
    covered = _global_tiles() if global_coverage else sorted(set(scene_counts) | set(coast_counts))
    feature_tiles = set(scene_counts) | set(coast_counts)
    layer_tiles: dict[str, set[str]] = defaultdict(set)
    for path in sorted((release_dir / "tiles").glob("*.scene.ndjson.gz")):
        tile = path.name.split(".scene.ndjson.gz", 1)[0]
        with gzip.open(path, "rt", encoding="utf-8") as handle:
            for line in handle:
                if line.strip():
                    layer_tiles[json.loads(line)["sceneType"]].add(tile)
    sizes = [path.stat().st_size for path in (release_dir / "tiles").glob("*.ndjson.gz")]
    empty = len(set(covered) - feature_tiles)
    report = {
        "schemaVersion": 1,
        "globalCoverage": global_coverage,
        "totalTileCount": len(covered),
        "coveredTileCount": len(covered),
        "coveredWithFeaturesTileCount": len(feature_tiles),
        "emptyCoveredTileCount": empty,
        "notCoveredTileCount": 0 if global_coverage else 64_800 - len(covered),
        "sceneTileCount": len(scene_counts),
        "coastTileCount": len(coast_counts),
        "layerCoverage": {key: len(value) for key, value in sorted(layer_tiles.items())},
        "maxTileBytes": max(sizes, default=0),
        "averageTileBytes": round(sum(sizes) / len(sizes), 2) if sizes else 0,
        "maxFeatureCount": max([*scene_counts.values(), *coast_counts.values()], default=0),
        "extent": [-180, -90, 180, 90] if global_coverage else None,
        "dateLineCovered": global_coverage or any("W180" in value or "E179" in value for value in covered),
        "polarCovered": global_coverage or any(value.startswith(("N89_", "S90_")) for value in covered),
    }
    validation = release_dir / "validation"
    write_json(validation / "global-coverage-report.json", report)
    csv_path = validation / "global-tile-coverage.csv"
    csv_path.parent.mkdir(parents=True, exist_ok=True)
    with csv_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(["tileId", "coverageStatus", "sceneFeatureCount", "coastFeatureCount"])
        for tile in covered:
            status = "COVERED_WITH_FEATURES" if tile in feature_tiles else "COVERED_EMPTY"
            writer.writerow([tile, status, scene_counts.get(tile, 0), coast_counts.get(tile, 0)])
    return report


def _global_tiles() -> list[str]:
    return [tile_id(lon, lat) for lat in range(-90, 90) for lon in range(-180, 180)]
