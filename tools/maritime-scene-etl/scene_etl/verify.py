from __future__ import annotations

import json
import zipfile
from pathlib import Path
from typing import Any

from .acquire import target_path
from .manifest import raw_manifest_path, sha256_file


def verify_dataset(data_root: Path, dataset: dict[str, Any]) -> dict[str, Any]:
    target = target_path(data_root, dataset)
    manifest_file = raw_manifest_path(data_root, dataset["dataset_id"])
    result: dict[str, Any] = {
        "datasetId": dataset["dataset_id"],
        "status": "OK",
        "path": str(target),
        "issues": [],
    }
    if not target.exists():
        result["status"] = "BLOCKED_MANUAL" if dataset["manual_download"] else "MISSING"
        result["issues"].append("raw file is missing")
        return result
    if not manifest_file.exists():
        if dataset["manual_download"]:
            from .acquire import verify_existing

            verify_existing(Path.cwd(), data_root, dataset, target, manifest_file)
            result["manifestCreated"] = True
        else:
            result["status"] = "INVALID"
            result["issues"].append("raw manifest is missing")
            return result
    try:
        manifest = json.loads(manifest_file.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        result["status"] = "INVALID"
        result["issues"].append("raw manifest is not valid JSON")
        return result
    actual_hash = sha256_file(target)
    result["fileSize"] = target.stat().st_size
    result["sha256"] = actual_hash
    if manifest.get("sha256") != actual_hash:
        result["status"] = "INVALID"
        result["issues"].append("sha256 mismatch")
    configured_size = dataset.get("expected_size_bytes")
    if configured_size is not None and configured_size != target.stat().st_size:
        result["issues"].append(
            f"size changed from acquisition audit value {configured_size}; hash remains authoritative"
        )
    if target.suffix.lower() == ".zip":
        try:
            with zipfile.ZipFile(target) as archive:
                bad = archive.testzip()
                if bad:
                    result["status"] = "INVALID"
                    result["issues"].append(f"zip member failed CRC: {bad}")
                result["zipMembers"] = len(archive.namelist())
        except zipfile.BadZipFile:
            result["status"] = "INVALID"
            result["issues"].append("invalid ZIP archive")
    return result


def verify_all(data_root: Path, datasets: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [verify_dataset(data_root, dataset) for dataset in datasets if dataset["enabled"]]
