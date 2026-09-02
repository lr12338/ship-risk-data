package com.hifleet.sts;

import com.hifleet.regionbehavior.RegionBehaviorConfig;
import com.hifleet.regionbehavior.RegionGeometry;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test A — Region Geometry
 *
 * Verifies that RegionGeometry correctly supports:
 *   - point inside core region → containsCore = true
 *   - point outside core region → containsCore = false
 *   - point inside analysis buffer (outside core) → analysis true / core false
 *
 * Uses a simple rectangular polygon as test geometry.
 * The Persian Gulf production boundary is NOT hardcoded — this test
 * validates the region machinery, not a specific maritime boundary.
 */
public class StsRegionGeometryTest {

    private static final String TEST_BOX_WKT =
            "POLYGON((50.0 25.0, 55.0 25.0, 55.0 29.0, 50.0 29.0, 50.0 25.0))";

    private RegionGeometry region;

    @Before
    public void setUp() {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        region = RegionGeometry.parse(TEST_BOX_WKT, 10.0, config);
    }

    @Test
    public void parsesValidWkt() {
        assertNotNull(region);
        assertNotNull(region.getFingerprint());
        assertTrue("fingerprint should be non-empty SHA-256 hex",
                region.getFingerprint().length() == 64);
    }

    @Test
    public void pointInsideCoreReturnsTrueForBoth() {
        Coordinate inside = new Coordinate(52.5, 27.0);
        assertTrue("point inside core should be in core", region.containsCore(inside));
        assertTrue("point inside core should be in analysis", region.containsAnalysis(inside));
    }

    @Test
    public void pointOutsideCoreButInsideAnalysisBuffer() {
        // Core is lon 50–55, lat 25–29.
        // 10 nm buffer at lat ~26° ≈ ~0.17° longitude, ~0.167° latitude.
        // Point ~5 nm east of core east edge: lon = 55.0 + ~0.085°
        Coordinate justOutsideCore = new Coordinate(55.08, 27.0);
        assertFalse("point just outside core should NOT be in core",
                region.containsCore(justOutsideCore));
        assertTrue("point just outside core but within 10nm buffer should be in analysis",
                region.containsAnalysis(justOutsideCore));
    }

    @Test
    public void pointFarOutsideReturnsFalseForBoth() {
        Coordinate farOutside = new Coordinate(60.0, 20.0);
        assertFalse("point far outside should not be in core", region.containsCore(farOutside));
        assertFalse("point far outside should not be in analysis", region.containsAnalysis(farOutside));
    }

    @Test
    public void coreBoundaryPointIsInside() {
        Coordinate onBoundary = new Coordinate(50.0, 27.0);
        assertTrue("point on core boundary should be in core (covers)",
                region.containsCore(onBoundary));
    }

    @Test
    public void zeroBufferMakesAnalysisEqualToCore() {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        RegionGeometry zeroBuf = RegionGeometry.parse(TEST_BOX_WKT, 0.0, config);

        Coordinate inside = new Coordinate(52.5, 27.0);
        assertTrue(zeroBuf.containsCore(inside));
        assertTrue(zeroBuf.containsAnalysis(inside));

        Coordinate outside = new Coordinate(55.08, 27.0);
        assertFalse(zeroBuf.containsCore(outside));
        assertFalse("zero buffer means analysis = core — outside point should not be in analysis",
                zeroBuf.containsAnalysis(outside));
    }
}
