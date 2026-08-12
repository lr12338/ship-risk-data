package com.hifleet.loitering;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKBWriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LoiteringPortGeometryRepairTest {
    private final GeometryFactory factory = new GeometryFactory();

    @Test
    public void repairsExactAllowlistDeterministically() throws Exception {
        List<LoiteringPortGeometryRepair.SourceRow> rows = rows();
        Collections.reverse(rows);
        LoiteringPortGeometryRepair.RepairResult first = LoiteringPortGeometryRepair.repair(rows);
        LoiteringPortGeometryRepair.RepairResult second = LoiteringPortGeometryRepair.repair(rows());
        assertEquals(26, first.features.size());
        assertEquals(first.sourceFingerprint, second.sourceFingerprint);
        assertEquals(first.repairedFingerprint, second.repairedFingerprint);
        for (LoiteringPortGeometryRepair.FeatureRepair feature : first.features) {
            assertTrue(feature.repaired.isValid());
            assertFalse(feature.repaired.isEmpty());
            assertEquals(101, feature.repaired.getSRID());
            assertArrayEquals(feature.repairedWkb, LoiteringPortGeometryRepair.fixedWkb(feature.repaired));
            assertEquals(0d, feature.envelopeDelta, 1e-12);
        }
    }

    @Test
    public void repairsRingSelfIntersection() throws Exception {
        Geometry ring = factory.createPolygon(new Coordinate[]{new Coordinate(0,0),new Coordinate(0,2),
                new Coordinate(1,1),new Coordinate(2,2),new Coordinate(2,0),new Coordinate(1,1),new Coordinate(0,0)});
        LoiteringPortGeometryRepair.FeatureRepair repaired = LoiteringPortGeometryRepair.repairOne(row(312, ring, 1, 101));
        assertTrue(repaired.repaired.isValid());
        assertFalse(repaired.repaired.isEmpty());
    }

    @Test
    public void rejectsMissingOrUnexpectedIds() throws Exception {
        List<LoiteringPortGeometryRepair.SourceRow> missing = rows(); missing.remove(0);
        expectFailure(missing, "scope mismatch");
        List<LoiteringPortGeometryRepair.SourceRow> unexpected = rows(); unexpected.add(row(999, bowTie(), 1, 101));
        expectFailure(unexpected, "unexpected source ID=999");
    }

    @Test
    public void rejectsValidWrongSceneOrWrongSrid() throws Exception {
        Geometry valid = factory.createPolygon(new Coordinate[]{new Coordinate(0,0),new Coordinate(1,0),
                new Coordinate(1,1),new Coordinate(0,1),new Coordinate(0,0)});
        expectOneFailure(row(312, valid, 1, 101), "unexpectedly valid");
        expectOneFailure(row(312, bowTie(), 2, 101), "not anchorage");
        expectOneFailure(row(312, bowTie(), 1, 0), "unexpected SRID");
    }

    @Test
    public void marksApprovedLargeAreaDeltaIds() throws Exception {
        assertTrue(LoiteringPortGeometryRepair.repairOne(row(3298, bowTie(), 1, 101)).largeAreaDeltaAccepted);
        assertFalse(LoiteringPortGeometryRepair.repairOne(row(312, bowTie(), 1, 101)).largeAreaDeltaAccepted);
    }

    private List<LoiteringPortGeometryRepair.SourceRow> rows() {
        List<LoiteringPortGeometryRepair.SourceRow> rows = new ArrayList<LoiteringPortGeometryRepair.SourceRow>();
        for (Integer id : LoiteringPortGeometryRepair.REPAIR_IDS) rows.add(row(id, bowTie(), 1, 101));
        return rows;
    }
    private Geometry bowTie() {
        return factory.createPolygon(new Coordinate[]{new Coordinate(0,0),new Coordinate(2,2),
                new Coordinate(0,2),new Coordinate(2,0),new Coordinate(0,0)});
    }
    private LoiteringPortGeometryRepair.SourceRow row(int id, Geometry geometry, int zonesType, int srid) {
        return new LoiteringPortGeometryRepair.SourceRow(id, 10000 + id, zonesType, srid, "ANCH " + id,
                new WKBWriter().write(geometry));
    }
    private void expectFailure(List<LoiteringPortGeometryRepair.SourceRow> rows, String message) throws Exception {
        try { LoiteringPortGeometryRepair.repair(rows); fail("expected repair failure"); }
        catch (IllegalStateException exception) { assertTrue(exception.getMessage(), exception.getMessage().contains(message)); }
    }
    private void expectOneFailure(LoiteringPortGeometryRepair.SourceRow row, String message) throws Exception {
        try { LoiteringPortGeometryRepair.repairOne(row); fail("expected repair failure"); }
        catch (IllegalStateException exception) { assertTrue(exception.getMessage(), exception.getMessage().contains(message)); }
    }
}
