from scene_etl.atlas import covered_by_release


def test_region_coverage_does_not_claim_unavailable_global_areas():
    release_bbox = (120.5, 30.5, 122.5, 32.0)

    assert covered_by_release(release_bbox, (120.5, 30.5, 122.5, 32.0))
    assert not covered_by_release(release_bbox, (4.0, 50.0, 8.0, 53.0))
