import gzip
import json
from pathlib import Path

from scene_etl.records import scene_record
from scene_etl.validate import _validate_golden_points, _validate_point


def _write_scene_tile(release_dir: Path, records: list[dict], tile: str = "N00_E000") -> None:
    tile_dir = release_dir / "tiles"
    tile_dir.mkdir(parents=True)
    with gzip.open(tile_dir / f"{tile}.scene.ndjson.gz", "wt", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record) + "\n")


def test_allowed_alternative_is_accepted(tmp_path: Path):
    from shapely.geometry import Point

    _write_scene_tile(
        tmp_path,
        [scene_record("marine_eez", "Marine Regions", "v1", "1", Point(0.5, 0.5), "VALID_ORIGINAL", "TEST", "HIGH", {})],
    )

    result = _validate_point(
        tmp_path,
        {
            "id": "alternative",
            "longitude": 0.5,
            "latitude": 0.5,
            "expected_hits": ["marine_territorial_sea"],
            "allowed_alternatives": ["marine_eez"],
            "forbidden_hits": ["land"],
        },
    )

    assert result["passed"] is True
    assert result["matchedExpected"] == []
    assert result["matchedAlternatives"] == ["marine_eez"]
    assert result["forbiddenHits"] == []


def test_forbidden_hit_overrides_accepted_hit(tmp_path: Path):
    from shapely.geometry import Point

    _write_scene_tile(
        tmp_path,
        [
            scene_record("marine_eez", "Marine Regions", "v1", "1", Point(0.5, 0.5), "VALID_ORIGINAL", "TEST", "HIGH", {}),
            scene_record("land", "GSHHG", "v1", "2", Point(0.5, 0.5), "VALID_ORIGINAL", "TEST", "HIGH", {}),
        ],
    )

    result = _validate_point(
        tmp_path,
        {
            "id": "forbidden",
            "longitude": 0.5,
            "latitude": 0.5,
            "expected_hits": ["marine_eez"],
            "allowed_alternatives": [],
            "forbidden_hits": ["land"],
        },
    )

    assert result["passed"] is False
    assert result["matchedExpected"] == ["marine_eez"]
    assert result["forbiddenHits"] == ["land"]


def test_current_yangtze_point_is_not_falsely_accepted(tmp_path: Path):
    from shapely.geometry import LineString

    _write_scene_tile(
        tmp_path,
        [
            scene_record(
                "inland_river_corridor",
                "HydroSHEDS",
                "v1",
                "40646746:corridor",
                LineString([(121.092719, 31.0185979), (121.1, 31.02)]),
                "VALID_ORIGINAL",
                "TEST",
                "MEDIUM",
                {},
            )
        ],
        tile="N31_E121",
    )

    result = _validate_point(
        tmp_path,
        {
            "id": "yangtze_inner_river",
            "longitude": 121.0,
            "latitude": 31.0,
            "expected_hits": ["inland_river_corridor"],
            "allowed_alternatives": ["marine_internal_water"],
            "forbidden_hits": ["land"],
        },
    )

    assert result["hits"] == []
    assert result["matchedExpected"] == []
    assert result["matchedAlternatives"] == []
    assert result["passed"] is False


def test_unconfirmed_points_keep_validation_pending(tmp_path: Path):
    import yaml
    from shapely.geometry import Point

    _write_scene_tile(
        tmp_path,
        [scene_record("marine_eez", "Marine Regions", "v1", "1", Point(0.5, 0.5), "VALID_ORIGINAL", "TEST", "HIGH", {})],
    )
    config_dir = tmp_path / "config"
    config_dir.mkdir()
    (config_dir / "golden-points.yaml").write_text(
        yaml.safe_dump(
            {
                "points": [
                    {
                        "id": "confirmed",
                        "longitude": 0.5,
                        "latitude": 0.5,
                        "expected_hits": ["marine_eez"],
                        "allowed_alternatives": [],
                        "forbidden_hits": [],
                        "enabled": True,
                    },
                    {
                        "id": "unconfirmed",
                        "longitude": 1.5,
                        "latitude": 0.5,
                        "expected_hits": ["marine_eez"],
                        "allowed_alternatives": [],
                        "forbidden_hits": [],
                        "enabled": False,
                    },
                ]
            }
        ),
        encoding="utf-8",
    )

    result = _validate_golden_points(tmp_path, config_dir)

    assert result["status"] == "PENDING_MANUAL_COORDINATE_REVIEW"
    assert result["configured"] == 2
    assert result["enabled"] == 1
    assert result["results"][0]["passed"] is True


def test_golden_points_are_optional_when_configured_as_reference(tmp_path: Path):
    import yaml

    config_dir = tmp_path / "config"
    config_dir.mkdir()
    (config_dir / "golden-points.yaml").write_text(
        yaml.safe_dump(
            {
                "validation_role": "OPTIONAL_MANUAL_REFERENCE",
                "blocking": False,
                "points": [
                    {
                        "id": "reference",
                        "longitude": 0.5,
                        "latitude": 0.5,
                        "expected_hits": ["marine_eez"],
                        "enabled": True,
                    }
                ],
            }
        ),
        encoding="utf-8",
    )

    result = _validate_golden_points(tmp_path, config_dir)

    assert result["status"] == "OPTIONAL_REVIEW"
    assert result["blocking"] is False
