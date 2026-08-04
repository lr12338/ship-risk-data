from pathlib import Path

from scene_etl.jrc import JrcRasterSampler, plan, verify
from scene_etl.manifest import write_json


def test_disabled_plan_has_no_downloads(tmp_path: Path):
    config_dir = Path(__file__).parents[1] / "config"
    result = plan(tmp_path, config_dir, mode="DISABLED")

    assert result["mode"] == "DISABLED"
    assert result["tileCount"] == 0
    assert result["expectedFileCount"] == 0


def test_missing_manifest_and_index_are_no_coverage(tmp_path: Path):
    config_dir = Path(__file__).parents[1] / "config"
    result = verify(tmp_path, config_dir, all_files=True)
    assert result["status"] == "NO_COVERAGE"

    write_json(tmp_path / "gsw1_5_2024-index.json", {"files": []})
    sampler = JrcRasterSampler(tmp_path, config_dir)
    sample = sampler.sample(121.0, 31.0)
    sampler.close()
    assert sample["jrcCoverageAvailable"] is False
    assert sample["jrcEvidenceClass"] == "NO_COVERAGE"
