package com.hifleet.worldsea;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKBWriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

public class WorldSeaMaskLoaderTest {
    private final GeometryFactory factory = new GeometryFactory();

    @Test
    public void loadsPolygonAndMultiPolygonAndKeepsChineseName() {
        List<WorldSeaMaskLoader.Row> rows = new ArrayList<WorldSeaMaskLoader.Row>();
        rows.add(row(1, "A", "海区甲", square(0, 0, 10, 10)));
        rows.add(row(2, "B", "海区乙", factory.createMultiPolygon(
                new org.locationtech.jts.geom.Polygon[]{square(20, 20, 21, 21)})));
        WorldSeaMaskSnapshot snapshot = loader(2).build(meta(rows), rows, System.currentTimeMillis());

        assertEquals(2, snapshot.getFeatureCount());
        assertEquals(1, snapshot.getPolygonCount());
        assertEquals(1, snapshot.getMultiPolygonCount());
        assertEquals("海区甲", snapshot.match(5, 5).getAreaCnName());
        assertNotNull(snapshot.match(0, 5));
    }

    @Test
    public void overlappingAreasChooseSmallerArea() {
        List<WorldSeaMaskLoader.Row> rows = rows(row(1, "Large", null, square(0, 0, 10, 10)),
                row(2, "Small", null, square(1, 1, 2, 2)));
        assertEquals("Small", loader(2).build(meta(rows), rows, System.currentTimeMillis())
                .match(1.5, 1.5).getAreaName());
    }

    @Test
    public void supportsBothDatelineSidesAndHighLatitude() {
        List<WorldSeaMaskLoader.Row> rows = rows(row(1, "East", null, square(179, 0, 180, 1)),
                row(2, "West", null, square(-180, 0, -179, 1)),
                row(3, "North", null, square(0, 80, 1, 90)));
        WorldSeaMaskSnapshot snapshot = loader(3).build(meta(rows), rows, System.currentTimeMillis());
        assertNotNull(snapshot.match(179.9, 0.5));
        assertNotNull(snapshot.match(-179.9, 0.5));
        assertNotNull(snapshot.match(0.5, 89.9));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsDuplicateId() {
        List<WorldSeaMaskLoader.Row> rows = rows(row(1, "A", null, square(0, 0, 1, 1)),
                row(1, "B", null, square(2, 2, 3, 3)));
        loader(2).build(meta(rows), rows, System.currentTimeMillis());
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsEmptyGeometry() {
        List<WorldSeaMaskLoader.Row> rows = rows(new WorldSeaMaskLoader.Row(1, "A", null, new byte[0]));
        loader(1).build(meta(rows), rows, System.currentTimeMillis());
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsUnsupportedGeometryType() {
        List<WorldSeaMaskLoader.Row> rows = rows(row(1, "A", null,
                factory.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(1, 1)})));
        loader(1).build(meta(rows), rows, System.currentTimeMillis());
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsOutOfRangeCoordinate() {
        List<WorldSeaMaskLoader.Row> rows = rows(row(1, "A", null, square(181, 0, 182, 1)));
        loader(1).build(meta(rows), rows, System.currentTimeMillis());
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsFeatureCountMismatch() {
        List<WorldSeaMaskLoader.Row> rows = rows(row(1, "A", null, square(0, 0, 1, 1)));
        loader(2).build(meta(rows), rows, System.currentTimeMillis());
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsFingerprintMismatch() {
        List<WorldSeaMaskLoader.Row> rows = rows(row(1, "A", null, square(0, 0, 1, 1)));
        Map<String, Object> meta = meta(rows);
        meta.put("geometry_fingerprint", "bad");
        loader(1).build(meta, rows, System.currentTimeMillis());
    }

    @Test
    public void configIdentityIncludesEveryStrictEvidenceGate() {
        String identity = new WorldSeaMaskProperties().identity(WorldSeaTestFixtures.snapshot());
        assertTrue(identity.contains("datasetVersion=WORLD_SEA_102_V1R1"));
        assertTrue(identity.contains("geometryFingerprint=fingerprint"));
        assertTrue(identity.contains("coverage=1.0"));
        assertTrue(identity.contains("shadowCoverage=0.95"));
        assertTrue(identity.contains("requireEventCenter=true"));
        assertTrue(identity.contains("requireOwnPoint=true"));
        assertTrue(identity.contains("requireTargetPoint=true"));
        assertTrue(identity.contains("requireMidpoint=true"));
    }

    private WorldSeaMaskLoader loader(int count) {
        WorldSeaMaskProperties properties = new WorldSeaMaskProperties();
        properties.setExpectedFeatureCount(count);
        return new WorldSeaMaskLoader(null, properties);
    }
    private WorldSeaMaskLoader.Row row(int id, String name, String cnName, Geometry geometry) {
        return new WorldSeaMaskLoader.Row(id, name, cnName, new WKBWriter().write(geometry));
    }
    private org.locationtech.jts.geom.Polygon square(double x1, double y1, double x2, double y2) {
        return factory.createPolygon(new Coordinate[]{new Coordinate(x1, y1), new Coordinate(x2, y1),
                new Coordinate(x2, y2), new Coordinate(x1, y2), new Coordinate(x1, y1)});
    }
    private List<WorldSeaMaskLoader.Row> rows(WorldSeaMaskLoader.Row... values) {
        List<WorldSeaMaskLoader.Row> result = new ArrayList<WorldSeaMaskLoader.Row>();
        java.util.Collections.addAll(result, values); return result;
    }
    private Map<String, Object> meta(List<WorldSeaMaskLoader.Row> rows) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("dataset_version", "WORLD_SEA_102_V1R1");
        result.put("source_file_sha256", "source");
        result.put("geometry_fingerprint", WorldSeaMaskLoader.fingerprint(rows));
        result.put("coordinate_assumption", WorldSeaMaskLoader.COORDINATE_ASSUMPTION);
        return result;
    }
}
