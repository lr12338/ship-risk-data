from __future__ import annotations

import gzip
import io
import json
from contextlib import contextmanager
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable

from .geometry import geometry_from_wkb_hex, safe_json
from .manifest import canonical_json
from .tiling import tile_path, tiles_for_bounds


@contextmanager
def deterministic_gzip_text(path: Path):
    raw = path.open("wb")
    try:
        compressed = gzip.GzipFile(fileobj=raw, mode="wb", mtime=0)
        text = io.TextIOWrapper(compressed, encoding="utf-8", newline="\n")
        try:
            yield text
        finally:
            text.close()
    finally:
        raw.close()


def record_sort_key(record: dict[str, Any]) -> tuple[str, ...]:
    return (
        str(record.get("sceneType", "")),
        str(record.get("sourceName", "")),
        str(record.get("sourceVersion", "")),
        str(record.get("sourceFeatureId", "")),
        str(record.get("geometryStatus", "")),
        str(record.get("geometryWkbHex", "")),
        safe_json(record.get("properties", {})),
    )


def write_normalized_records(
    path: Path, records: Iterable[dict[str, Any]], stats: dict[str, Any]
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".part")
    with deterministic_gzip_text(temporary) as handle:
        for record in records:
            handle.write(canonical_json(record) + "\n")
            stats["outputCount"] = stats.get("outputCount", 0) + 1
    temporary.replace(path)


def iter_normalized_records(path: Path):
    with gzip.open(path, "rt", encoding="utf-8") as handle:
        for line in handle:
            if line.strip():
                yield json.loads(line)


def write_qa_geopackage(path: Path, grouped_records: dict[str, Iterable[dict[str, Any]]]) -> None:
    try:
        import fiona
        from fiona.crs import CRS
    except ImportError as exc:
        raise RuntimeError("Fiona is required for QA GeoPackage output") from exc

    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        path.unlink()
    schema = {
        "geometry": "Unknown",
        "properties": {
            "scene_type": "str:64",
            "source_name": "str:128",
            "source_version": "str:64",
            "source_id": "str:128",
            "authority": "str:32",
            "confidence": "str:32",
            "geometry_status": "str:32",
            "source_attributes": "str",
        },
    }
    for layer_name, records in grouped_records.items():
        with fiona.open(
            path,
            "w",
            driver="GPKG",
            layer=layer_name,
            schema=schema,
            crs=CRS.from_epsg(4326),
        ) as layer:
            for record in records:
                geometry = geometry_from_wkb_hex(record["geometryWkbHex"])
                properties = record.get("properties", {})
                layer.write(
                    {
                        "geometry": geometry.__geo_interface__,
                        "properties": {
                            "scene_type": record.get("sceneType"),
                            "source_name": record.get("sourceName"),
                            "source_version": record.get("sourceVersion"),
                            "source_id": record.get("sourceFeatureId"),
                            "authority": record.get("authorityLevel"),
                            "confidence": record.get("confidence"),
                            "geometry_status": record.get("geometryStatus"),
                            "source_attributes": safe_json(properties.get("source_attributes", properties)),
                        },
                    }
                )


def tile_records(
    records: Iterable[dict[str, Any]],
    tile_root: Path,
    tile_size: int = 1,
    kind: str = "scene",
) -> dict[str, int]:
    """Spool records to intersecting tiles, then finalize each tile deterministically."""
    spool_root = tile_root / ".spool"
    spool_root.mkdir(parents=True, exist_ok=True)
    counts: dict[str, int] = defaultdict(int)
    for record in records:
        geometry = geometry_from_wkb_hex(record["geometryWkbHex"])
        for tile in tiles_for_bounds(geometry.bounds, tile_size):
            tile_record = dict(record)
            tile_record["tileId"] = tile
            spool = spool_root / tile_path(tile, kind).removesuffix(".gz")
            spool.parent.mkdir(parents=True, exist_ok=True)
            with spool.open("a", encoding="utf-8", newline="\n") as handle:
                handle.write(canonical_json(tile_record) + "\n")
            counts[tile] += 1
    for spool in sorted(spool_root.glob("*.ndjson")):
        rows = [json.loads(line) for line in spool.read_text(encoding="utf-8").splitlines() if line]
        rows.sort(key=record_sort_key)
        final = tile_root / (spool.name + ".gz")
        final.parent.mkdir(parents=True, exist_ok=True)
        temporary = final.with_name(final.name + ".part")
        with deterministic_gzip_text(temporary) as handle:
            for row in rows:
                handle.write(canonical_json(row) + "\n")
        temporary.replace(final)
        spool.unlink()
    if spool_root.exists() and not any(spool_root.iterdir()):
        spool_root.rmdir()
    return dict(counts)


def release_tile_paths(tile_root: Path) -> list[Path]:
    return sorted(tile_root.glob("*.ndjson.gz"))
