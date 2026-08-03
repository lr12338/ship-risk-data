import gzip
import json
from pathlib import Path

from scene_etl.config import load_datasets
from scene_etl.fingerprint import fingerprint_inputs
from scene_etl.manifest import canonical_sha256, write_json


def test_fingerprint_excludes_absolute_paths_and_is_repeatable(tmp_path: Path):
    config_dir = Path(__file__).parents[1] / "config"
    data_root = tmp_path / "data"
    release_dir = data_root / "releases" / "test"
    (release_dir / "tiles").mkdir(parents=True)
    (data_root / "raw-manifests").mkdir(parents=True)
    write_json(
        data_root / "raw-manifests" / "hydrorivers_v1.json",
        {"datasetId": "hydrorivers_v1", "sha256": "fixture"},
    )
    with gzip.open(release_dir / "tiles" / "N30_E121.scene.ndjson.gz", "wt", encoding="utf-8") as handle:
        handle.write(json.dumps({"sourceFeatureId": "1"}) + "\n")

    datasets = [item for item in load_datasets(config_dir) if item["dataset_id"] == "hydrorivers_v1"]
    first = fingerprint_inputs(tmp_path, config_dir, data_root, release_dir, datasets)
    second = fingerprint_inputs(tmp_path, config_dir, data_root, release_dir, datasets)

    assert canonical_sha256(first) == canonical_sha256(second)
    assert str(tmp_path) not in json.dumps(first)


def test_jrc_manifest_generated_at_is_excluded_from_fingerprint(tmp_path: Path):
    config_dir = Path(__file__).parents[1] / "config"
    data_root = tmp_path / "data"
    release_dir = data_root / "releases" / "test"
    (release_dir / "tiles").mkdir(parents=True)
    (data_root / "jrc-manifests").mkdir(parents=True)
    write_json(data_root / "jrc-manifests" / "gsw1_5_2024.json", {
        "generatedAt": "2026-08-03T00:00:00Z",
        "sourceVersion": "GSW1_5-2024",
        "files": [{"product": "occurrence", "tileId": "120E_40N", "sha256": "a"}],
    })
    datasets = []
    first = fingerprint_inputs(tmp_path, config_dir, data_root, release_dir, datasets)
    write_json(data_root / "jrc-manifests" / "gsw1_5_2024.json", {
        "generatedAt": "2026-08-04T00:00:00Z",
        "sourceVersion": "GSW1_5-2024",
        "files": [{"product": "occurrence", "tileId": "120E_40N", "sha256": "a"}],
    })
    second = fingerprint_inputs(tmp_path, config_dir, data_root, release_dir, datasets)
    assert first["jrcManifest"] == second["jrcManifest"]
