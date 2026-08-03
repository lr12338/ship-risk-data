import json
from pathlib import Path

import fiona
from fiona.crs import CRS
from shapely.geometry import LineString, Point, mapping

from scene_etl.golden_candidates import generate_candidate_report


def _write_layer(path: Path, layer: str, geometry, source_id: str):
    schema = {
        "geometry": "Unknown",
        "properties": {
            "scene_type": "str:64",
            "source_name": "str:128",
            "source_version": "str:64",
            "source_id": "str:128",
            "authority": "str:32",
            "confidence": "str:32",
            "geometry_status": "str:32",
            "source_attributes": "str",
        },
    }
    with fiona.open(path, "w", driver="GPKG", layer=layer, schema=schema, crs=CRS.from_epsg(4326)) as sink:
        sink.write(
            {
                "geometry": mapping(geometry),
                "properties": {
                    "scene_type": layer,
                    "source_name": "fixture",
                    "source_version": "v1",
                    "source_id": source_id,
                    "authority": "TEST",
                    "confidence": "HIGH",
                    "geometry_status": "VALID_ORIGINAL",
                    "source_attributes": json.dumps({}),
                },
            }
        )


def test_candidates_are_nearest_sorted_and_do_not_edit_config(tmp_path: Path):
    release = tmp_path / "releases" / "test"
    qa = release / "qa"
    qa.mkdir(parents=True)
    qa_path = qa / "maritime-scene-canonical.gpkg"
    _write_layer(qa_path, "inland_river_corridor", LineString([(1, 0), (1, 1)]), "near")
    _write_layer(qa_path, "marine_eez", Point(5, 5), "alternative")

    config_dir = tmp_path / "config"
    config_dir.mkdir()
    config = {
        "schema_version": 1,
        "points": [
            {
                "id": "river",
                "longitude": 0.9,
                "latitude": 0.5,
                "expected_hits": ["inland_river_corridor"],
                "allowed_alternatives": [],
                "forbidden_hits": ["land"],
                "enabled": False,
            },
            {
                "id": "alternative",
                "longitude": 5,
                "latitude": 5,
                "expected_hits": ["marine_territorial_sea"],
                "allowed_alternatives": ["marine_eez"],
                "forbidden_hits": ["land"],
                "enabled": False,
            },
            {
                "id": "already-enabled",
                "longitude": 1,
                "latitude": 0.5,
                "expected_hits": ["inland_river_corridor"],
                "allowed_alternatives": [],
                "forbidden_hits": [],
                "enabled": True,
            },
        ],
    }
    import yaml

    config_path = config_dir / "golden-points.yaml"
    config_path.write_text(yaml.safe_dump(config), encoding="utf-8")
    before = config_path.read_bytes()

    report = generate_candidate_report(release, config_dir)
    repeat = generate_candidate_report(release, config_dir)

    assert report == repeat
    assert report["points"][0]["status"] == "NEAREST_CANDIDATE"
    assert report["points"][0]["candidates"][0]["sourceId"] == "near"
    assert report["points"][1]["status"] == "DIRECT_HIT"
    assert report["points"][1]["directHits"][0]["layer"] == "marine_eez"
    assert [point["id"] for point in report["points"]] == ["river", "alternative"]
    assert config_path.read_bytes() == before
