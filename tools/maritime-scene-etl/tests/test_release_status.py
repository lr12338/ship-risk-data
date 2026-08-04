from scene_etl.release_status import can_promote, derive_release_status


def test_shadow_ready_is_monotonic_and_enforce_is_not_available():
    assert can_promote("AUTO_CROSS_VALIDATED", "SHADOW_READY") is True
    assert can_promote("SHADOW_READY", "AUTO_CROSS_VALIDATED") is False
    assert can_promote("SHADOW_READY", "ENFORCE_READY") is False


def test_automatic_validation_derives_auto_cross_validated():
    assert derive_release_status([], {"status": "PASS"}) == "AUTO_CROSS_VALIDATED"
