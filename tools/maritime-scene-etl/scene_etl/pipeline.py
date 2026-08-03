from __future__ import annotations

import json
import shutil
import time
from pathlib import Path
from typing import Any, Callable, Iterator

from .config import config_hash, load_datasets, load_yaml
from .export import (
    deterministic_gzip_text,
    iter_normalized_records,
    release_tile_paths,
    tile_records,
    write_normalized_records,
    write_qa_geopackage,
)
from .fingerprint import write_release_manifest
from .automatic_validation import run_automatic_validation
from .geometry import geometry_from_wkb_hex
from .manifest import utc_now, write_json
from .marine_regions import iter_records as marine_records
from .gshhg import iter_records as gshhg_records
from .hydrolakes import iter_records as hydrolake_records
from .hydrorivers import iter_records as hydroriver_records
from .osm_canals import iter_records as osm_records
from .validate import validate_release
from .verify import verify_dataset


HANDLERS: dict[str, Callable[..., Iterator[dict[str, Any]]]] = {
    "marine_regions": marine_records,
    "hydrorivers": hydroriver_records,
    "hydrolakes": hydrolake_records,
    "gshhg": gshhg_records,
    "osm_canals": osm_records,
}


def normalized_path(data_root: Path, dataset_id: str) -> Path:
    return data_root / "staging" / "normalized" / f"{dataset_id}.ndjson.gz"


def normalize_dataset(
    repo_root: Path,
    data_root: Path,
    config_dir: Path,
    dataset: dict[str, Any],
    force: bool = False,
    bbox: tuple[float, float, float, float] | None = None,
) -> dict[str, Any]:
    output = normalized_path(data_root, dataset["dataset_id"])
    config_hashes = _processing_config_hashes(config_dir)
    verification = verify_dataset(data_root, dataset)
    if output.exists() and not force:
        stats_path = output.with_suffix(".stats.json")
        if stats_path.exists():
            cached = json.loads(stats_path.read_text(encoding="utf-8"))
            cached_bbox = cached.get("bbox")
            if (
                cached_bbox == (list(bbox) if bbox is not None else None)
                and cached.get("rawSha256") == verification.get("sha256")
                and cached.get("configHashes") == config_hashes
            ):
                return cached
    if verification["status"] != "OK":
        status = verification["status"]
        result = {
            "datasetId": dataset["dataset_id"],
            "status": status,
            "raw": verification,
            "outputCount": 0,
            "bbox": list(bbox) if bbox is not None else None,
            "configHashes": config_hashes,
        }
        write_json(output.with_suffix(".stats.json"), result)
        return result
    handler = HANDLERS.get(dataset["processing_handler"])
    if handler is None:
        raise ValueError(f"unknown processing handler: {dataset['processing_handler']}")
    started = time.monotonic()
    stats: dict[str, Any] = {
        "datasetId": dataset["dataset_id"],
        "status": "OK",
        "startedAt": utc_now(),
        "rawSha256": verification.get("sha256"),
        "rawFileSize": verification.get("fileSize"),
        "outputCount": 0,
        "bbox": list(bbox) if bbox is not None else None,
        "configHashes": config_hashes,
    }
    try:
        if dataset["processing_handler"] in {"hydrorivers", "hydrolakes", "osm_canals"}:
            records = handler(repo_root, data_root, config_dir, dataset, stats, force, bbox)
        else:
            records = handler(repo_root, data_root, dataset, stats, force, bbox)
        write_normalized_records(output, _clip_records(records, bbox), stats)
        stats["elapsedSeconds"] = round(time.monotonic() - started, 3)
        write_json(output.with_suffix(".stats.json"), stats)
        return stats
    except Exception as exc:
        stats["status"] = "FAILED"
        stats["error"] = f"{type(exc).__name__}: {exc}"
        stats["elapsedSeconds"] = round(time.monotonic() - started, 3)
        write_json(output.with_suffix(".stats.json"), stats)
        raise


def normalize_all(
    repo_root: Path,
    data_root: Path,
    config_dir: Path,
    datasets: list[dict[str, Any]],
    force: bool = False,
    bbox: tuple[float, float, float, float] | None = None,
) -> list[dict[str, Any]]:
    statuses = []
    for dataset in datasets:
        if not dataset["enabled"]:
            continue
        try:
            statuses.append(normalize_dataset(repo_root, data_root, config_dir, dataset, force, bbox))
        except Exception as exc:
            statuses.append({"datasetId": dataset["dataset_id"], "status": "FAILED", "error": str(exc)})
    return statuses


def build_release(
    repo_root: Path,
    data_root: Path,
    config_dir: Path,
    release_version: str,
    force: bool = False,
    bbox: tuple[float, float, float, float] | None = None,
) -> dict[str, Any]:
    release_dir = data_root / "releases" / release_version
    if release_dir.exists():
        raise FileExistsError(f"release is immutable and already exists: {release_dir}")
    release_dir.mkdir(parents=True, exist_ok=True)
    datasets = [dataset for dataset in load_datasets(config_dir) if dataset["enabled"]]
    statuses = normalize_all(repo_root, data_root, config_dir, datasets, force=False, bbox=bbox)
    failed_auto = [
        status for status in statuses
        if status.get("status") in {"MISSING", "INVALID", "FAILED"}
        and not _is_manual_block(status, datasets)
    ]
    if failed_auto:
        raise RuntimeError(f"automatic dataset normalization failed: {failed_auto}")
    tile_root = release_dir / "tiles"
    qa_spool = release_dir / ".qa-spool"
    qa_spool.mkdir(parents=True, exist_ok=True)
    normalized_files = [
        normalized_path(data_root, dataset["dataset_id"])
        for dataset in datasets
        if normalized_path(data_root, dataset["dataset_id"]).exists()
    ]

    def all_records(kind: str):
        for path in normalized_files:
            for record in iter_normalized_records(path):
                if record.get("recordKind", "scene") == kind:
                    yield record

    scene_tile_counts = tile_records(all_records("scene"), tile_root, 1, "scene")
    coast_tile_counts = tile_records(all_records("coast"), tile_root, 1, "coast")
    for path in normalized_files:
        for record in iter_normalized_records(path):
            layer = record["sceneType"]
            spool = qa_spool / f"{layer}.ndjson"
            with spool.open("a", encoding="utf-8", newline="\n") as handle:
                handle.write(json.dumps(record, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n")
    spool_paths = {path.stem: path for path in qa_spool.glob("*.ndjson")}
    qa_order = load_yaml(config_dir / "processing-policy.yaml").get("qa_layer_order", [])
    ordered_layers = [layer for layer in qa_order if layer in spool_paths]
    ordered_layers.extend(sorted(set(spool_paths) - set(ordered_layers)))
    grouped = {layer: _iter_spool(spool_paths[layer]) for layer in ordered_layers}
    write_qa_geopackage(release_dir / "qa" / "maritime-scene-canonical.gpkg", grouped)
    review_files = [path for path in sorted(qa_spool.glob("*review_required*.ndjson"))]
    if review_files:
        review_target = release_dir / "qa" / "canal-review-required.ndjson.gz"
        with deterministic_gzip_text(review_target) as out:
            for path in review_files:
                out.write(path.read_text(encoding="utf-8"))
    shutil.rmtree(qa_spool)
    source_manifest_root = release_dir / "source-manifests"
    source_manifest_root.mkdir(parents=True, exist_ok=True)
    for dataset in datasets:
        source_manifest = data_root / "raw-manifests" / f"{dataset['dataset_id']}.json"
        if source_manifest.exists():
            shutil.copy2(source_manifest, source_manifest_root / source_manifest.name)
    write_json(source_manifest_root / "dataset-status.json", {"datasetStatuses": statuses})
    license_file = release_dir / "license-attribution.md"
    license_file.write_text(_license_text(datasets), encoding="utf-8")
    automatic = run_automatic_validation(release_dir, config_dir, data_root)
    preliminary = validate_release(release_dir, config_dir, statuses)
    fingerprint, manifest = write_release_manifest(
        repo_root, config_dir, data_root, release_dir, datasets, preliminary
    )
    report = validate_release(release_dir, config_dir, statuses)
    report["snapshotFingerprint"] = fingerprint
    write_json(release_dir / "validation-report.json", report)
    fingerprint, manifest = write_release_manifest(
        repo_root, config_dir, data_root, release_dir, datasets, report
    )
    return {
        "releaseVersion": release_version,
        "releasePath": str(release_dir),
        "manifest": manifest,
        "snapshotFingerprint": fingerprint,
        "automaticValidation": automatic,
        "validation": report,
        "sceneTileCount": len(scene_tile_counts),
        "coastTileCount": len(coast_tile_counts),
        "tileCount": len(release_tile_paths(tile_root)),
        "datasetStatuses": statuses,
    }


def _iter_spool(path: Path):
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            if line.strip():
                yield json.loads(line)


def _is_manual_block(status: dict[str, Any], datasets: list[dict[str, Any]]) -> bool:
    dataset = next((item for item in datasets if item["dataset_id"] == status.get("datasetId")), None)
    return bool(dataset and dataset["manual_download"] and status.get("status") == "BLOCKED_MANUAL")


def _clip_records(
    records: Iterator[dict[str, Any]],
    bbox: tuple[float, float, float, float] | None,
) -> Iterator[dict[str, Any]]:
    if bbox is None:
        yield from records
        return
    from shapely.geometry import box

    boundary = box(*bbox)
    for record in records:
        geometry = geometry_from_wkb_hex(record["geometryWkbHex"])
        if not geometry.intersects(boundary):
            continue
        clipped = geometry.intersection(boundary)
        if clipped.is_empty:
            continue
        clipped_record = dict(record)
        clipped_record["geometryWkbHex"] = clipped.wkb_hex
        yield clipped_record


def _processing_config_hashes(config_dir: Path) -> dict[str, str]:
    names = (
        "datasets.yaml",
        "processing-policy.yaml",
        "river-policy.yaml",
        "lake-policy.yaml",
        "major-canals.yaml",
        "golden-points.yaml",
        "jrc-policy.yaml",
        "auto-samples.yaml",
    )
    return {name: config_hash(config_dir / name) for name in names}


def _license_text(datasets: list[dict[str, Any]]) -> str:
    lines = [
        "# Maritime scene data release attribution",
        "",
        "This file records the source and license declarations captured at acquisition time.",
        "Raw source files are intentionally not included in Git.",
        "",
    ]
    for dataset in datasets:
        lines.extend([
            f"## {dataset['dataset_id']}",
            f"- Product: {dataset['product_name']}",
            f"- Official page: {dataset['official_page']}",
            f"- Version: {dataset.get('source_version', 'UNKNOWN')}",
            f"- License: {dataset['license']}",
            f"- Attribution: {dataset['attribution']}",
            "",
        ])
    lines.extend([
        "## jrc_global_surface_water_2024",
        "- Product: Joint Research Centre Global Surface Water 1984-2024 (GSW v1.5)",
        "- Official page: https://global-surface-water.appspot.com/download",
        "- Version: GSW1_5-2024",
        "- License: Copernicus Programme free of charge with attribution",
        "- Attribution: Source: EC JRC/Google; Pekel et al. (2016)",
        "",
    ])
    return "\n".join(lines)
