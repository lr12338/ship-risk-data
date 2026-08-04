from pathlib import Path

from scene_etl.config import config_hash, load_datasets, load_yaml


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


def test_global_base_candidate_policies_are_explicit_and_conservative():
    lake = load_yaml(CONFIG_DIR / "lake-policy.yaml")
    river = load_yaml(CONFIG_DIR / "river-policy.yaml")
    canals = load_yaml(CONFIG_DIR / "major-canals.yaml")["canals"]

    assert lake["areaField"] == "Lake_area"
    assert lake["minAreaKm2"] == 50
    assert lake["boundaryToleranceMeters"] > 0
    assert lake["retainReservoirs"] is True
    assert river["fallback_confidence"] == "LOW"
    assert all(item["review_status"] == "APPROVED" for item in canals)
    assert all(item["osm_way_ids"] for item in canals)
