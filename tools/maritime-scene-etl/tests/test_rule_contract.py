import json
from pathlib import Path

from scene_etl.auto_samples import (
    _candidate_coast_distance_ok,
    _candidate_matches_core,
    validate_auto_samples,
)
from scene_etl.cross_source_validation import classify_point


def _record(scene_type, confidence="HIGH", review_status=None):
    properties = {}
    if review_status is not None:
        properties["review_status"] = review_status
    return {"sceneType": scene_type, "confidence": confidence, "properties": properties}


def test_nested_marine_layers_use_most_specific_class():
    decision = classify_point([
        _record("marine_internal_water"),
        _record("marine_territorial_sea"),
        _record("marine_eez"),
    ])

    assert decision["sceneClass"] == "MARITIME_INTERNAL_WATER"
    assert decision["confidence"] == "MEDIUM"


def test_territorial_interior_is_not_rejected_only_for_being_coastal():
    decision = classify_point(
        [_record("marine_territorial_sea")],
        coast_distance_nm=6.0,
        policy={"thresholds": {"very_near_coast_nm": 1, "coastal_nm": 12}},
    )

    assert decision["sceneClass"] == "TERRITORIAL_SEA"
    assert decision["confidence"] == "MEDIUM"


def test_core_candidate_excludes_higher_priority_overlap():
    assert _candidate_matches_core("OFFSHORE_EEZ", {"marine_eez"})
    assert not _candidate_matches_core(
        "OFFSHORE_EEZ", {"marine_eez", "marine_territorial_sea"})
    assert _candidate_matches_core(
        "MARINE_INTERNAL", {"marine_internal_water", "marine_territorial_sea"})
    assert not _candidate_matches_core(
        "HIGH_SEAS", {"marine_high_seas", "land"})


def test_marine_core_candidate_stays_outside_near_coast_band():
    assert not _candidate_coast_distance_ok(
        "TERRITORIAL", {"available": True, "distanceNm": 0.7}, 12.0, 1.0)
    assert _candidate_coast_distance_ok(
        "TERRITORIAL", {"available": True, "distanceNm": 2.0}, 12.0, 1.0)
    assert _candidate_coast_distance_ok(
        "MARINE_INTERNAL", {"available": False, "distanceNm": None}, 12.0, 1.0)
    assert not _candidate_coast_distance_ok(
        "OFFSHORE_EEZ", {"available": True, "distanceNm": 11.9}, 12.0, 1.0)


def test_auto_sample_validation_passes_attached_coast_distance(
    tmp_path: Path, monkeypatch,
):
    validation = tmp_path / "validation"
    validation.mkdir()
    (validation / "auto-samples.json").write_text(json.dumps({"samples": [{
        "sampleId": "eez",
        "sampleClass": "OFFSHORE_EEZ",
        "sampleRole": "positive",
        "sampleOrigin": "marine_eez",
        "expectedSceneClass": "OFFSHORE_EEZ",
        "longitude": 0.0,
        "latitude": 0.0,
        "coastDistanceNm": 20.0,
    }]}))
    monkeypatch.setattr(
        "scene_etl.auto_samples.point_hits",
        lambda *_args, **_kwargs: [_record("marine_eez")],
    )
    monkeypatch.setattr(
        "scene_etl.auto_samples.JrcRasterSampler.sample",
        lambda *_args, **_kwargs: {"jrcCoverageAvailable": False},
    )

    report = validate_auto_samples(
        tmp_path, Path(__file__).parents[1] / "config", tmp_path)

    assert report["results"][0]["sceneClass"] == "OFFSHORE_EEZ"
    assert report["results"][0]["confidence"] == "HIGH"
