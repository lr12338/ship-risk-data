from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from .config import config_hash
from .manifest import canonical_sha256, git_commit, relative_path, sha256_file, write_json


def fingerprint_inputs(
    repo_root: Path,
    config_dir: Path,
    data_root: Path,
    release_dir: Path,
    datasets: list[dict[str, Any]],
) -> dict[str, Any]:
    raw_manifests = []
    for dataset in sorted(datasets, key=lambda item: item["dataset_id"]):
        path = data_root / "raw-manifests" / f"{dataset['dataset_id']}.json"
        if path.exists():
            raw_manifests.append(
                {"datasetId": dataset["dataset_id"], "sha256": sha256_file(path)}
            )
        else:
            raw_manifests.append(
                {"datasetId": dataset["dataset_id"], "sha256": "MISSING"}
            )
    config_files = [
        config_dir / "datasets.yaml",
        config_dir / "processing-policy.yaml",
        config_dir / "river-policy.yaml",
        config_dir / "lake-policy.yaml",
        config_dir / "major-canals.yaml",
        config_dir / "golden-points.yaml",
        config_dir / "jrc-policy.yaml",
        config_dir / "auto-samples.yaml",
    ]
    configs = [
        {"path": path.name, "sha256": config_hash(path)}
        for path in sorted(config_files)
        if path.exists()
    ]
    tiles = []
    for path in sorted((release_dir / "tiles").glob("*.ndjson.gz")):
        tiles.append(
            {
                "path": relative_path(path, release_dir),
                "sha256": sha256_file(path),
                "size": path.stat().st_size,
            }
        )
    jrc_manifest = data_root / "jrc-manifests" / "gsw1_5_2024.json"
    if jrc_manifest.exists():
        jrc_value = json.loads(jrc_manifest.read_text(encoding="utf-8"))
        jrc_value.pop("generatedAt", None)
        jrc = {"path": jrc_manifest.name, "sha256": canonical_sha256(jrc_value)}
    else:
        jrc = {"path": jrc_manifest.name, "sha256": "MISSING"}
    return {
        "schemaVersion": 1,
        "etlVersion": __import__("scene_etl").__version__,
        "etlGitCommit": git_commit(repo_root),
        "rawManifests": raw_manifests,
        "processingConfigs": configs,
        "jrcManifest": jrc,
        "tiles": tiles,
    }


def write_release_manifest(
    repo_root: Path,
    config_dir: Path,
    data_root: Path,
    release_dir: Path,
    datasets: list[dict[str, Any]],
    validation: dict[str, Any] | None = None,
) -> tuple[str, dict[str, Any]]:
    inputs = fingerprint_inputs(repo_root, config_dir, data_root, release_dir, datasets)
    fingerprint = canonical_sha256(inputs)
    manifest = {
        "schemaVersion": 1,
        "releaseVersion": release_dir.name,
        "builtAt": _utc_now(),
        "snapshotFingerprint": fingerprint,
        "fingerprintInputs": inputs,
        "validationSummary": validation or {},
    }
    write_json(release_dir / "manifest.json", manifest)
    checksums = release_dir / "checksums.sha256"
    lines = []
    for path in sorted(release_dir.rglob("*")):
        if path.is_file() and path.name != checksums.name:
            lines.append(f"{sha256_file(path)}  {relative_path(path, release_dir)}")
    checksums.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return fingerprint, manifest


def fingerprint_from_manifest(path: Path) -> str:
    value = json.loads(path.read_text(encoding="utf-8"))
    return value["snapshotFingerprint"]


def _utc_now() -> str:
    from datetime import datetime, timezone

    return datetime.now(timezone.utc).isoformat()
