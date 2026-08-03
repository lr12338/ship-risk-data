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
