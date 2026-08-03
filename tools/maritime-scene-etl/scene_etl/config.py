from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError:  # pragma: no cover - doctor can run before venv setup
    yaml = None


def _require_yaml():
    if yaml is None:
        raise RuntimeError(
            "PyYAML is required; run `make -C tools/maritime-scene-etl venv`"
        )
    return yaml


def load_yaml(path: Path) -> dict[str, Any]:
    yaml_module = _require_yaml()
    with path.open("r", encoding="utf-8") as handle:
        value = yaml_module.safe_load(handle) or {}
    if not isinstance(value, dict):
        raise ValueError(f"configuration must be a mapping: {path}")
    return value


def config_hash(path: Path) -> str:
    digest = hashlib.sha256()
    digest.update(path.read_bytes())
    return digest.hexdigest()


def load_datasets(config_dir: Path) -> list[dict[str, Any]]:
    data = load_yaml(config_dir / "datasets.yaml")
    datasets = data.get("datasets", [])
    if not isinstance(datasets, list):
        raise ValueError("datasets.yaml: datasets must be a list")
    result = []
    for item in datasets:
        if not isinstance(item, dict):
            raise ValueError("datasets.yaml: every dataset must be a mapping")
        required = {
            "dataset_id", "source_name", "product_name", "expected_layer",
            "official_page", "download_mode", "expected_format", "license",
            "attribution", "enabled", "manual_download", "raw_target_path",
            "processing_handler",
        }
        missing = sorted(required - set(item))
        if missing:
            raise ValueError(
                f"datasets.yaml: {item.get('dataset_id', '<unknown>')} missing {missing}"
            )
        result.append(item)
    return result


def dataset_by_id(config_dir: Path, dataset_id: str) -> dict[str, Any]:
    for item in load_datasets(config_dir):
        if item["dataset_id"] == dataset_id:
            return item
    raise KeyError(f"unknown dataset: {dataset_id}")


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
