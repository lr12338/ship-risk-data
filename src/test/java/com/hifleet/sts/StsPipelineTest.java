package com.hifleet.sts;

import com.hifleet.sts.domain.StsJobParams;
import com.hifleet.sts.service.StsBaselineService;
import com.hifleet.sts.service.StsSeedCatalog;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test C — Pipeline Compatibility
 *
 * Verifies:
 *   - StsJobParams parsing
 *   - StsBaselineService validation (mode, span, parallelism, writeMode)
 *   - StsSeedCatalog integrates correctly with identity grouping
 *   - Self-identity exclusion set correctly contains all MMSIs for an IMO
 *
 * Full HBase/Replay/Detector integration requires external services
 * and is validated in remote smoke tests.
 */
public class StsPipelineTest {

    @Test
    public void jobParamsParsing() {
        String json = "{\"mode\":\"STS_PHASE1_BASELINE\"," +
                "\"seedFile\":\"/data/test.csv\"," +
                "\"from\":\"2026-08-01 00:00:00\"," +
                "\"to\":\"2026-08-08 00:00:00\"," +
                "\"shipLimit\":20," +
                "\"windowHours\":24," +
                "\"activityMinSpeedKn\":0.0," +
                "\"activityMaxSpeedKn\":4.0," +
                "\"parallelism\":1," +
                "\"writeMode\":\"LOG_ONLY\"}";

        StsJobParams p = StsJobParams.parse(json);
        assertEquals("STS_PHASE1_BASELINE", p.getMode());
        assertEquals("/data/test.csv", p.getSeedFile());
        assertEquals("2026-08-01 00:00:00", p.getFrom());
        assertEquals("2026-08-08 00:00:00", p.getTo());
        assertEquals(20, p.getShipLimit());
        assertEquals(24, p.getWindowHours());
        assertEquals(0.0, p.getActivityMinSpeedKn(), 0.001);
        assertEquals(4.0, p.getActivityMaxSpeedKn(), 0.001);
        assertEquals(1, p.getParallelism());
        assertEquals("LOG_ONLY", p.getWriteMode());
    }

    @Test
    public void jobParamsDefaults() {
        StsJobParams p = StsJobParams.parse("{}");
        assertEquals(24, p.getWindowHours());
        assertEquals(4.0, p.getActivityMaxSpeedKn(), 0.001);
        assertEquals(1, p.getParallelism());
        assertEquals("LOG_ONLY", p.getWriteMode());
    }

    @Test
    public void identityExclusionSetContainsAllMmsis() throws Exception {
        StsSeedCatalog catalog = new StsSeedCatalog();
        java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("sts/test-sts-identity.csv");
        catalog.loadFromStream(in, "test-sts-identity.csv");

        Set<String> dualMmsis = catalog.getIdentityMmsis("9000002");
        assertEquals(2, dualMmsis.size());
        assertTrue(dualMmsis.contains("222222222"));
        assertTrue(dualMmsis.contains("333333333"));

        String queriedMmsi = "222222222";
        String alternateMmsi = "333333333";
        assertTrue("when querying with MMSI A, alternate MMSI B must be in exclusion set",
                dualMmsis.contains(alternateMmsi));
        assertTrue("when querying with MMSI A, queried MMSI A must also be in set (excluded by upstream too)",
                dualMmsis.contains(queriedMmsi));
    }

    @Test
    public void mmsiReverseLookupSupportsImoLevelExclusion() throws Exception {
        StsSeedCatalog catalog = new StsSeedCatalog();
        java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("sts/test-sts-identity.csv");
        catalog.loadFromStream(in, "test-sts-identity.csv");

        String targetMmsi = "333333333";
        String targetImo = catalog.findImoByMmsi(targetMmsi);
        assertNotNull("target MMSI should resolve to IMO", targetImo);
        assertEquals("9000002", targetImo);

        String ownImo = "9000002";
        boolean isSameImo = ownImo.equals(targetImo);
        assertTrue("same-IMO target should be rejected as second-level guard", isSameImo);
    }

    @Test
    public void baselineServiceRejectsWrongMode() {
        StsBaselineService service = new StsBaselineService();
        StsJobParams params = StsJobParams.parse("{}");
        params.setMode("INVALID_MODE");
        params.setSeedFile("/tmp/test.csv");
        params.setFrom("2026-01-01 00:00:00");
        params.setTo("2026-01-02 00:00:00");
        try {
            service.runBaseline(params);
            fail("should have rejected invalid mode");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("mode"));
        }
    }

    @Test
    public void baselineServiceRejectsExcessiveSpan() {
        StsBaselineService service = new StsBaselineService();
        StsJobParams params = StsJobParams.parse("{}");
        params.setMode("STS_PHASE1_BASELINE");
        params.setSeedFile("/tmp/test.csv");
        params.setFrom("2026-01-01 00:00:00");
        params.setTo("2026-01-15 00:00:00");
        try {
            service.runBaseline(params);
            fail("should have rejected >7 day span");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("days"));
        }
    }

    @Test
    public void baselineServiceRejectsNonLogOnlyWriteMode() {
        StsBaselineService service = new StsBaselineService();
        StsJobParams params = StsJobParams.parse("{}");
        params.setMode("STS_PHASE1_BASELINE");
        params.setSeedFile("/tmp/test.csv");
        params.setFrom("2026-01-01 00:00:00");
        params.setTo("2026-01-02 00:00:00");
        params.setWriteMode("DB_WRITE");
        try {
            service.runBaseline(params);
            fail("should have rejected non-LOG_ONLY write mode");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("LOG_ONLY"));
        }
    }

    @Test
    public void baselineServiceRequiresFromAndTo() {
        StsBaselineService service = new StsBaselineService();
        StsJobParams params = StsJobParams.parse("{}");
        params.setMode("STS_PHASE1_BASELINE");
        params.setSeedFile("/tmp/test.csv");
        try {
            service.runBaseline(params);
            fail("should have rejected missing from/to");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("from") || e.getMessage().contains("to"));
        }
    }

    @Test
    public void baselineServiceRejectsParallelismAboveOne() {
        StsBaselineService service = new StsBaselineService();
        StsJobParams params = StsJobParams.parse("{}");
        params.setMode("STS_PHASE1_BASELINE");
        params.setSeedFile("/tmp/test.csv");
        params.setFrom("2026-01-01 00:00:00");
        params.setTo("2026-01-02 00:00:00");
        params.setParallelism(4);
        try {
            service.runBaseline(params);
            fail("should have rejected parallelism != 1");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("parallelism"));
        }
    }
}
