package com.hifleet.worldsea;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKBWriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorldSeaGeometryRepairTest {
    private final GeometryFactory factory = new GeometryFactory();

    @Test
    public void repairsOnlyAllowlistAndLeavesNinetyFiveWkbByteIdentical() throws Exception {
        List<WorldSeaGeometryRepair.SourceRow> rows = new ArrayList<WorldSeaGeometryRepair.SourceRow>();
        int validId = 1000;
        for (int i = 0; i < 95; i++) rows.add(row(validId++, square(i, 0, 0.5)));
        for (int id : WorldSeaGeometryRepair.REPAIR_IDS) rows.add(row(id, bowTie(id % 20, 2)));

        WorldSeaGeometryRepair.DatasetRepair result = WorldSeaGeometryRepair.repair(rows);

        assertEquals(102, result.rows.size());
        assertEquals(95, result.unchangedCount);
        assertEquals(7, result.changedCount);
        assertEquals(WorldSeaGeometryRepair.REPAIR_IDS, result.changedIds);
        for (WorldSeaGeometryRepair.RepairedRow repaired : result.rows) {
            if (!WorldSeaGeometryRepair.REPAIR_IDS.contains(repaired.source.id)) {
                assertArrayEquals(repaired.source.wkb, repaired.wkb);
                assertFalse(repaired.repaired);
            } else {
                assertFalse(Arrays.equals(repaired.source.wkb, repaired.wkb));
                assertTrue(repaired.repaired);
            }
            assertTrue(repaired.geometry.isValid());
            assertEquals(101, repaired.geometry.getSRID());
        }
    }

    @Test(expected = IllegalStateException.class)
    public void validAllowlistedGeometryIsRejectedInsteadOfRewritten() throws Exception {
        WorldSeaGeometryRepair.repair(Arrays.asList(row(11, square(0, 0, 1))));
    }

    @Test(expected = IllegalStateException.class)
    public void invalidGeometryOutsideAllowlistIsRejected() throws Exception {
        WorldSeaGeometryRepair.repair(Arrays.asList(row(777, bowTie(0, 0))));
    }

    @Test(expected = IllegalStateException.class)
    public void nonPolygonalInputIsRejected() throws Exception {
        LineString line = factory.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(1, 1)});
        WorldSeaGeometryRepair.repair(Arrays.asList(row(11, line)));
    }

    @Test(expected = IllegalStateException.class)
    public void emptyGeometryIsRejected() throws Exception {
        WorldSeaGeometryRepair.repair(Arrays.asList(row(11, factory.createPolygon())));
    }

    @Test(expected = IllegalStateException.class)
    public void outOfRangeCoordinateIsRejected() throws Exception {
        WorldSeaGeometryRepair.repair(Arrays.asList(row(11, bowTie(181, 0))));
    }

    @Test
    public void polygonMayBecomeMultiPolygonAndAllComponentsAreRetained() throws Exception {
        WorldSeaGeometryRepair.DatasetRepair value = WorldSeaGeometryRepair.repair(
                Arrays.asList(row(24556, bowTie(0, 0))));
        WorldSeaGeometryRepair.FeatureRepair feature = value.features.get(0);
        assertTrue(feature.repairedGeometry instanceof MultiPolygon);
        assertEquals(feature.repairedGeometry.getNumGeometries(), feature.polygonComponentAreas.size());
        assertEquals("RETAIN", feature.tinyComponentDecision);
    }

    @Test
    public void repairIsByteStableWhenRepeated() throws Exception {
        WorldSeaGeometryRepair.DatasetRepair first = WorldSeaGeometryRepair.repair(
                Arrays.asList(row(11, bowTie(0, 0))));
        Geometry fixed = first.rows.get(0).geometry;
        byte[] second = WorldSeaGeometryRepair.fixedWkb(fixed);
        assertArrayEquals(first.rows.get(0).wkb, second);
        assertTrue(first.features.get(0).idempotent);
    }

    @Test
    public void baffinCollapsedRingSpikeIsAuditedButNotWrittenAsLinework() throws Exception {
        Polygon spike = factory.createPolygon(new Coordinate[]{new Coordinate(0, 0), new Coordinate(2, 0),
                new Coordinate(2, 2), new Coordinate(1, 2), new Coordinate(1, 3), new Coordinate(1, 2),
                new Coordinate(0, 2), new Coordinate(0, 0)});
        WorldSeaGeometryRepair.FeatureRepair value = WorldSeaGeometryRepair.repair(
                Arrays.asList(row(26, spike))).features.get(0);
        assertEquals(Arrays.asList("LineString"), value.collapsedComponentTypes);
        assertEquals(2d, value.collapsedComponentLength, 0d);
        assertTrue(value.repairedGeometry instanceof org.locationtech.jts.geom.Polygonal);
        assertTrue(value.repairedGeometry.isValid());
    }

    private WorldSeaGeometryRepair.SourceRow row(int id, Geometry geometry) {
        geometry.setSRID(101);
        return new WorldSeaGeometryRepair.SourceRow(id, "Sea " + id, "海区" + id,
                new WKBWriter(2, 1, false).write(geometry), 101);
    }

    private Polygon square(double x, double y, double size) {
        return factory.createPolygon(new Coordinate[]{new Coordinate(x, y), new Coordinate(x + size, y),
                new Coordinate(x + size, y + size), new Coordinate(x, y + size), new Coordinate(x, y)});
    }

    private Polygon bowTie(double x, double y) {
        return factory.createPolygon(new Coordinate[]{new Coordinate(x, y), new Coordinate(x + 1, y + 1),
                new Coordinate(x, y + 1), new Coordinate(x + 1, y), new Coordinate(x, y)});
    }
}
