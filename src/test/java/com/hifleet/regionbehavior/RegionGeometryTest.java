package com.hifleet.regionbehavior;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RegionGeometryTest {

    @Test
    public void acceptsPolygonAndMultiPolygonWithDeterministicFingerprint() {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        RegionGeometry first = RegionGeometry.parse(
                "POLYGON((121 30,122 30,122 31,121 31,121 30))", 3.0, config);
        RegionGeometry reordered = RegionGeometry.parse(
                "POLYGON((122 31,121 31,121 30,122 30,122 31))", 3.0, config);
        RegionGeometry multi = RegionGeometry.parse(
                "MULTIPOLYGON(((121 30,121.1 30,121.1 30.1,121 30.1,121 30)))", 3.0, config);

        assertEquals(first.getFingerprint(), reordered.getFingerprint());
        assertEquals("MultiPolygon", multi.getCoreRegion().getGeometryType());
        assertTrue(first.getAnalysisRegion().getArea() > first.getCoreRegion().getArea());
        assertTrue(first.containsCore(new Coordinate(121.5, 30.5)));
    }

    @Test
    public void repairsSelfIntersectionToPolygonalGeometry() {
        RegionGeometry geometry = RegionGeometry.parse(
                "POLYGON((121 30,122 31,121 31,122 30,121 30))", 0.0,
                RegionBehaviorConfig.defaults());
        assertTrue(geometry.getCoreRegion().isValid());
        assertTrue(geometry.getAreaSqKm() > 0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOutOfRangeCoordinates() {
        RegionGeometry.parse("POLYGON((181 30,182 30,182 31,181 31,181 30))", 0.0,
                RegionBehaviorConfig.defaults());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDateLineCrossingFailClosed() {
        RegionGeometry.parse("POLYGON((179 10,-179 10,-179 11,179 11,179 10))", 0.0,
                RegionBehaviorConfig.defaults());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyPolygon() {
        RegionGeometry.parse("POLYGON EMPTY", 0.0, RegionBehaviorConfig.defaults());
    }

    @Test
    public void metricBufferIsNotDegreeBuffer() {
        RegionGeometry geometry = RegionGeometry.parse(
                "POLYGON((121 30,121.01 30,121.01 30.01,121 30.01,121 30))", 1.0,
                RegionBehaviorConfig.defaults());
        assertFalse(geometry.containsCore(new Coordinate(121.015, 30.005)));
        assertTrue(geometry.containsAnalysis(new Coordinate(121.015, 30.005)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBufferThatCrossesDateLine() {
        RegionGeometry.parse(
                "POLYGON((179.98 10,179.99 10,179.99 10.01,179.98 10.01,179.98 10))", 3.0,
                RegionBehaviorConfig.defaults());
    }
}
