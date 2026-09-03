package com.hifleet.sts;

import com.hifleet.sts.domain.StsRiskRegionRow;
import com.hifleet.sts.service.StsRegionSetGeometry;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Test — Multi-region overlap attribution (PRIMARY_REGION_ATTRIBUTION_V1).
 *
 * Attribution rules (lowest wins):
 *   1. region set item priority (lower = higher priority)
 *   2. core polygon area (smaller = higher priority)
 *   3. regionCode lexicographic
 */
public class StsRegionSetAttributionTest {

    private StsRegionSetGeometry regionSet;

    @Before
    public void setUp() {
        List<StsRiskRegionRow> rows = new ArrayList<StsRiskRegionRow>();

        // Region A: outer box, priority 100, larger area
        StsRiskRegionRow a = new StsRiskRegionRow();
        a.setRegionCode("OUTER_REGION");
        a.setRegionVersion("v1");
        a.setRegionName("Outer");
        a.setGeometryWkt("POLYGON((0 0, 20 0, 20 20, 0 20, 0 0))");
        a.setAnalysisBufferNm(0.0);
        a.setPriority(100);
        a.setStatus("TEST");
        a.setEnabled(1);
        rows.add(a);

        // Region B: inner box (overlaps inside A), priority 100, smaller area
        StsRiskRegionRow b = new StsRiskRegionRow();
        b.setRegionCode("INNER_REGION");
        b.setRegionVersion("v1");
        b.setRegionName("Inner");
        b.setGeometryWkt("POLYGON((5 5, 15 5, 15 15, 5 15, 5 5))");
        b.setAnalysisBufferNm(0.0);
        b.setPriority(100);
        b.setStatus("TEST");
        b.setEnabled(1);
        rows.add(b);

        // Region C: separate box, priority 50 (higher priority), same size as inner
        StsRiskRegionRow c = new StsRiskRegionRow();
        c.setRegionCode("HIGH_PRIORITY_REGION");
        c.setRegionVersion("v1");
        c.setRegionName("HighPriority");
        c.setGeometryWkt("POLYGON((7 7, 17 7, 17 17, 7 17, 7 7))");
        c.setAnalysisBufferNm(0.0);
        c.setPriority(50);
        c.setStatus("TEST");
        c.setEnabled(1);
        rows.add(c);

        // Region D: separate non-overlapping region
        StsRiskRegionRow d = new StsRiskRegionRow();
        d.setRegionCode("FAR_REGION");
        d.setRegionVersion("v1");
        d.setRegionName("Far");
        d.setGeometryWkt("POLYGON((50 50, 60 50, 60 60, 50 60, 50 50))");
        d.setAnalysisBufferNm(0.0);
        d.setPriority(200);
        d.setStatus("TEST");
        d.setEnabled(1);
        rows.add(d);

        regionSet = new StsRegionSetGeometry("TEST_SET", rows);
    }

    @Test
    public void loadsAllRegions() {
        assertEquals(4, regionSet.size());
        assertEquals("TEST_SET", regionSet.getRegionSetCode());
    }

    @Test
    public void pointInsideSingleRegionReturnsThatRegion() {
        // Point only inside FAR_REGION
        StsRegionSetGeometry.AttributionResult r =
                regionSet.attributePrimary(new Coordinate(55, 55));
        assertTrue(r.hasMatch());
        assertEquals(1, r.matchCount());
        assertEquals("FAR_REGION", r.primary.regionCode);
    }

    @Test
    public void pointOutsideAllRegionsReturnsNull() {
        StsRegionSetGeometry.AttributionResult r =
                regionSet.attributePrimary(new Coordinate(30, 30));
        assertFalse(r.hasMatch());
        assertEquals(0, r.matchCount());
        assertNull(r.primary);
    }

    @Test
    public void overlappingRegionsChooseByPriorityFirst() {
        // Point (10,10) is inside OUTER, INNER, and HIGH_PRIORITY
        // HIGH_PRIORITY has priority 50 (lowest = highest priority)
        StsRegionSetGeometry.AttributionResult r =
                regionSet.attributePrimary(new Coordinate(10, 10));
        assertTrue(r.hasMatch());
        assertEquals(3, r.matchCount());
        assertEquals("HIGH_PRIORITY_REGION", r.primary.regionCode);
    }

    @Test
    public void samePriorityChoosesSmallerArea() {
        // If we remove HIGH_PRIORITY, the remaining two (OUTER and INNER) have same priority 100.
        // INNER has smaller area, so it should win.
        List<StsRiskRegionRow> rows = new ArrayList<StsRiskRegionRow>();
        StsRiskRegionRow outer = new StsRiskRegionRow();
        outer.setRegionCode("OUTER");
        outer.setRegionVersion("v1");
        outer.setGeometryWkt("POLYGON((0 0, 20 0, 20 20, 0 20, 0 0))");
        outer.setAnalysisBufferNm(0.0);
        outer.setPriority(100);
        outer.setStatus("TEST");
        outer.setEnabled(1);
        rows.add(outer);

        StsRiskRegionRow inner = new StsRiskRegionRow();
        inner.setRegionCode("INNER");
        inner.setRegionVersion("v1");
        inner.setGeometryWkt("POLYGON((5 5, 15 5, 15 15, 5 15, 5 5))");
        inner.setAnalysisBufferNm(0.0);
        inner.setPriority(100);
        inner.setStatus("TEST");
        inner.setEnabled(1);
        rows.add(inner);

        StsRegionSetGeometry set = new StsRegionSetGeometry("TIE_TEST", rows);
        StsRegionSetGeometry.AttributionResult r =
                set.attributePrimary(new Coordinate(10, 10));
        assertTrue(r.hasMatch());
        assertEquals(2, r.matchCount());
        assertEquals("INNER", r.primary.regionCode);
    }

    @Test
    public void samePriorityAndAreaChoosesLexicographic() {
        // Two identical overlapping boxes with same priority.
        // Lexicographic order: "AAA" < "BBB", so AAA wins.
        List<StsRiskRegionRow> rows = new ArrayList<StsRiskRegionRow>();

        StsRiskRegionRow bbb = new StsRiskRegionRow();
        bbb.setRegionCode("BBB_REGION");
        bbb.setRegionVersion("v1");
        bbb.setGeometryWkt("POLYGON((0 0, 10 0, 10 10, 0 10, 0 0))");
        bbb.setAnalysisBufferNm(0.0);
        bbb.setPriority(100);
        bbb.setStatus("TEST");
        bbb.setEnabled(1);
        rows.add(bbb);

        StsRiskRegionRow aaa = new StsRiskRegionRow();
        aaa.setRegionCode("AAA_REGION");
        aaa.setRegionVersion("v1");
        aaa.setGeometryWkt("POLYGON((0 0, 10 0, 10 10, 0 10, 0 0))");
        aaa.setAnalysisBufferNm(0.0);
        aaa.setPriority(100);
        aaa.setStatus("TEST");
        aaa.setEnabled(1);
        rows.add(aaa);

        StsRegionSetGeometry set = new StsRegionSetGeometry("LEX_TEST", rows);
        StsRegionSetGeometry.AttributionResult r =
                set.attributePrimary(new Coordinate(5, 5));
        assertTrue(r.hasMatch());
        assertEquals(2, r.matchCount());
        assertEquals("AAA_REGION", r.primary.regionCode);
    }

    @Test
    public void analysisContainsAnyChecksAllRegions() {
        assertTrue(regionSet.analysisContainsAny(new Coordinate(10, 10)));
        assertTrue(regionSet.analysisContainsAny(new Coordinate(55, 55)));
        assertFalse(regionSet.analysisContainsAny(new Coordinate(30, 30)));
    }

    @Test
    public void allMatchesContainsEveryOverlappingRegion() {
        StsRegionSetGeometry.AttributionResult r =
                regionSet.attributePrimary(new Coordinate(10, 10));
        assertEquals(3, r.matchCount());
        List<String> codes = new ArrayList<String>();
        for (StsRegionSetGeometry.ResolvedRegion m : r.allMatches) {
            codes.add(m.regionCode);
        }
        assertTrue(codes.contains("OUTER_REGION"));
        assertTrue(codes.contains("INNER_REGION"));
        assertTrue(codes.contains("HIGH_PRIORITY_REGION"));
    }
}
