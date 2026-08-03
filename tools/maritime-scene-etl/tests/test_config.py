from pathlib import Path

from scene_etl.config import config_hash, load_datasets


CONFIG_DIR = Path(__file__).parents[1] / "config"


def test_registered_sources_keep_manual_and_automatic_modes_explicitly():
    datasets = load_datasets(CONFIG_DIR)

    assert len(datasets) == 11
    assert sum(item["manual_download"] for item in datasets) == 5
    assert {item["download_mode"] for item in datasets} >= {"MANUAL", "DIRECT_HTTP", "GEOFABRIK_PBF"}
    assert all(item["official_page"].startswith("https://") for item in datasets)


def test_config_hash_is_stable():
    path = CONFIG_DIR / "datasets.yaml"
    assert config_hash(path) == config_hash(path)
