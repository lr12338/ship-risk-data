import json
from pathlib import Path

from scene_etl.estimate_build import estimate_build
from scene_etl.global_coverage import write_global_coverage


def test_estimate_global_build_reports_required_resource_fields(tmp_path: Path):
    config_dir = Path(__file__).parents[1] / "config"
    result = estimate_build(tmp_path, config_dir, "global", release="phase1-global-base-v1")

    assert result["release"] == "phase1-global-base-v1"
    assert result["estimatedSceneFileCount"] >= 1
    assert result["estimatedCoastFileCount"] >= 1
    assert isinstance(result["estimatedFeatureCounts"], list)
    assert result["recommendedPartitions"]
    assert result["onePassBuildSuitable"] in {True, False}


def test_estimate_uses_inspected_source_and_normalized_filter_counts(tmp_path: Path):
    reports = tmp_path / "reports"
    reports.mkdir(parents=True)
    (reports / "inspect.json").write_text(json.dumps({"result": [
        {"datasetId": "hydrolakes_v1", "featureCount": 100}
    ]}))
    normalized = tmp_path / "staging" / "normalized"
    normalized.mkdir(parents=True)
    (normalized / "hydrolakes_v1.ndjson.stats.json").write_text(json.dumps({
        "bbox": None, "rawCount": 100, "outputCount": 5
    }))

    result = estimate_build(
        tmp_path, Path(__file__).parents[1] / "config", "global",
        release="phase1-global-base-v1")
    lake = next(item for item in result["estimatedFeatureCounts"]
                if item["datasetId"] == "hydrolakes_v1")

    assert lake["sourceFeatures"] == 100
    assert lake["filteredFeatures"] == 5


def test_global_coverage_distinguishes_empty_from_not_covered(tmp_path: Path):
    report = write_global_coverage(tmp_path, {"N00_E000": 1}, {}, True)

    assert report["coveredTileCount"] == 64800
    assert report["emptyCoveredTileCount"] == 64799
    assert report["notCoveredTileCount"] == 0
    csv_text = (tmp_path / "validation" / "global-tile-coverage.csv").read_text()
    assert "N00_E000,COVERED_WITH_FEATURES,1,0" in csv_text
    assert "N00_E001,COVERED_EMPTY,0,0" in csv_text
