from scene_etl.auto_samples import _candidate_matches_core
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
