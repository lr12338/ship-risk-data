from pathlib import Path

import pytest

from scene_etl.pipeline import _clip_records
from scene_etl.pipeline import build_release
from scene_etl.records import scene_record


def test_bbox_clips_geometry_before_tiling():
    from shapely import from_wkb
    from shapely.geometry import box

    record = scene_record(
        "land",
        "test",
        "v1",
        "1",
        box(0, 0, 10, 10),
        "VALID_ORIGINAL",
        "TEST",
        "HIGH",
        {},
    )

    clipped = list(_clip_records(iter([record]), (2, 3, 4, 5)))

    assert len(clipped) == 1
    assert tuple(round(value, 6) for value in from_wkb(bytes.fromhex(clipped[0]["geometryWkbHex"])).bounds) == (2, 3, 4, 5)


def test_release_directory_is_immutable(tmp_path):
    repo_root = Path(__file__).parents[3]
    data_root = tmp_path / "data"
    release_dir = data_root / "releases" / "existing"
    release_dir.mkdir(parents=True)

    with pytest.raises(FileExistsError):
        build_release(
            repo_root,
            data_root,
            repo_root / "tools/maritime-scene-etl/config",
            "existing",
        )
