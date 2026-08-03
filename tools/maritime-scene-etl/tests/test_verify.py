import zipfile
from pathlib import Path

from scene_etl.config import load_datasets
from scene_etl.verify import verify_dataset


def test_manual_raw_verification_creates_manifest(tmp_path: Path):
    config_dir = Path(__file__).parents[1] / "config"
    dataset = load_datasets(config_dir)[0]
    target = tmp_path / "raw" / dataset["raw_target_path"]
    target.parent.mkdir(parents=True)
    with zipfile.ZipFile(target, "w") as archive:
        archive.writestr("README.txt", "fixture")

    result = verify_dataset(tmp_path, dataset)

    assert result["status"] == "OK"
    assert result["manifestCreated"] is True
    assert (tmp_path / "raw-manifests" / f"{dataset['dataset_id']}.json").exists()
