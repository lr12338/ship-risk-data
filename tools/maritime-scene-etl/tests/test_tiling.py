from pathlib import Path

from scene_etl.export import iter_normalized_records, tile_records
from scene_etl.geometry import geometry_from_wkb_hex
from scene_etl.records import scene_record
from scene_etl.tiling import tile_bounds, tile_id, tiles_for_bounds


def test_tile_ids_and_intersections_are_deterministic():
    assert tile_id(121.2, 30.5) == "N30_E121"
    assert tiles_for_bounds((121.2, 30.5, 122.1, 31.1)) == [
        "N30_E121",
        "N30_E122",
        "N31_E121",
        "N31_E122",
    ]


def test_tile_ids_wrap_the_date_line_and_clamp_the_poles():
    assert tile_id(180.0, 90.0) == "N89_W180"
    assert tile_id(181.0, -91.0) == "S90_W179"
    assert tile_bounds("S90_W180") == (-180.0, -90.0, -179.0, -89.0)


def test_tile_output_has_single_stable_suffix_and_sorted_records(tmp_path: Path):
    from shapely.geometry import Point

    records = [
        scene_record("land", "test", "v1", "b", Point(121.2, 30.2), "VALID_ORIGINAL", "TEST", "HIGH", {}),
        scene_record("land", "test", "v1", "a", Point(121.1, 30.1), "VALID_ORIGINAL", "TEST", "HIGH", {}),
    ]
    counts = tile_records(records, tmp_path)

    assert counts == {"N30_E121": 2}
    output = tmp_path / "N30_E121.scene.ndjson.gz"
    assert output.exists()
    assert not (tmp_path / "N30_E121.scene.scene.ndjson.gz").exists()
    assert [item["sourceFeatureId"] for item in iter_normalized_records(output)] == ["a", "b"]


def test_polygon_is_clipped_but_keeps_source_boundary_evidence(tmp_path: Path):
    from shapely.geometry import box

    record = scene_record("land", "test", "v1", "wide", box(0.5, 0.2, 1.5, 0.8),
                          "VALID_ORIGINAL", "TEST", "HIGH", {})
    tile_records([record], tmp_path)
    west = next(iter_normalized_records(tmp_path / "N00_E000.scene.ndjson.gz"))

    assert "boundaryWkbHex" in west
    assert geometry_from_wkb_hex(west["geometryWkbHex"]).bounds == (0.5, 0.2, 1.0, 0.8)


def test_tile_gzip_bytes_are_deterministic(tmp_path: Path):
    from shapely.geometry import Point

    record = scene_record("land", "test", "v1", "a", Point(121.1, 30.1), "VALID_ORIGINAL", "TEST", "HIGH", {})
    first = tmp_path / "first"
    second = tmp_path / "second"
    tile_records([record], first)
    tile_records([record], second)

    assert (first / "N30_E121.scene.ndjson.gz").read_bytes() == (second / "N30_E121.scene.ndjson.gz").read_bytes()
