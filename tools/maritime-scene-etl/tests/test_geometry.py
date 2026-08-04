from scene_etl.geometry import normalize_geometry, select_vector_path


def test_invalid_polygon_is_repaired_and_dimension_is_checked():
    bow_tie = {
        "type": "Polygon",
        "coordinates": [[[0, 0], [1, 1], [1, 0], [0, 1], [0, 0]]],
    }

    repaired = normalize_geometry(bow_tie, "UNKNOWN", expected_dimension=2, require_kind="polygon")
    wrong_dimension = normalize_geometry(
        {"type": "LineString", "coordinates": [[0, 0], [1, 1]]},
        "UNKNOWN",
        expected_dimension=2,
        require_kind="polygon",
    )

    assert repaired.geometry is not None
    assert repaired.status == "VALID_REPAIRED"
    assert wrong_dimension.status == "REJECTED_WRONG_DIMENSION"


def test_select_vector_path_prefers_polygon_schema(tmp_path):
    import fiona
    from fiona.crs import CRS
    from shapely.geometry import LineString, Polygon, mapping

    schema = {"geometry": "Unknown", "properties": {"id": "int"}}
    line_path = tmp_path / "boundaries.shp"
    polygon_path = tmp_path / "polygons.shp"
    with fiona.open(line_path, "w", driver="ESRI Shapefile", schema=schema, crs=CRS.from_epsg(4326)) as sink:
        sink.write({"geometry": mapping(LineString([(0, 0), (1, 1)])), "properties": {"id": 1}})
    with fiona.open(polygon_path, "w", driver="ESRI Shapefile", schema=schema, crs=CRS.from_epsg(4326)) as sink:
        sink.write({"geometry": mapping(Polygon([(0, 0), (1, 0), (1, 1), (0, 0)])), "properties": {"id": 1}})

    assert select_vector_path([line_path, polygon_path], expected_kind="polygon") == polygon_path


def test_polygon_crossing_date_line_is_split_without_world_spanning_bounds():
    polygon = {
        "type": "Polygon",
        "coordinates": [[[179.5, 10], [-179.5, 10], [-179.5, 11], [179.5, 11], [179.5, 10]]],
    }

    normalized = normalize_geometry(
        polygon, "EPSG:4326", expected_dimension=2, require_kind="polygon")

    assert normalized.geometry is not None
    assert normalized.geometry.geom_type == "MultiPolygon"
    assert all(part.bounds[2] - part.bounds[0] <= 1.0 for part in normalized.geometry.geoms)


def test_date_line_split_does_not_expand_unrelated_greenwich_polygon():
    geometry = {
        "type": "MultiPolygon",
        "coordinates": [
            [[[-10, 0], [10, 0], [10, 10], [-10, 10], [-10, 0]]],
            [[[179.5, 20], [-179.5, 20], [-179.5, 21], [179.5, 21], [179.5, 20]]],
        ],
    }

    normalized = normalize_geometry(
        geometry, "EPSG:4326", expected_dimension=2, require_kind="polygon")

    assert normalized.geometry is not None
    assert round(normalized.geometry.area, 6) == 201.0


def test_polar_cap_keeps_full_longitude_extent():
    geometry = {
        "type": "Polygon",
        "coordinates": [[
            [-180, 80], [180, 80], [180, 90], [-180, 90], [-180, 80],
        ]],
    }

    normalized = normalize_geometry(
        geometry, "EPSG:4326", expected_dimension=2, require_kind="polygon")

    assert normalized.geometry is not None
    assert round(normalized.geometry.area, 6) == 3600.0
