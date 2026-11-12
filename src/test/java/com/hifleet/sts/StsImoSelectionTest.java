package com.hifleet.sts;

import com.hifleet.sts.domain.StsJobParams;
import com.hifleet.sts.service.StsBaselineService;
import com.hifleet.sts.service.StsSeedCatalog;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Targeted tests for explicit IMO selection via the "imos" job parameter.
 *
 * Covers:
 *   - single explicit IMO
 *   - multiple explicit IMOs, stable order
 *   - unknown IMOs are skipped (not crashing)
 *   - imos takes priority over shipLimit
 *   - empty imos falls back to shipLimit
 *   - params parsing of imos array
 */
public class StsImoSelectionTest {

    private StsSeedCatalog catalog;
    private StsBaselineService service;

    @Before
    public void setUp() throws Exception {
        catalog = new StsSeedCatalog();
        java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("sts/test-sts-identity.csv");
        catalog.loadFromStream(in, "test-sts-identity.csv");

        service = new StsBaselineService();
        // Inject seed catalog via reflection (no Spring context needed for unit test)
        java.lang.reflect.Field f = StsBaselineService.class.getDeclaredField("seedCatalog");
        f.setAccessible(true);
        f.set(service, catalog);
    }

    @Test
    public void paramsParsesImosArray() {
        String json = "{\"mode\":\"STS_PHASE1_BASELINE\",\"seedFile\":\"/tmp/x.csv\"," +
                "\"from\":\"2026-01-01 00:00:00\",\"to\":\"2026-01-02 00:00:00\"," +
                "\"imos\":[\"9000001\",\"9000002\",\"9000003\"]}";

        StsJobParams p = StsJobParams.parse(json);
        assertTrue("hasExplicitImos should be true", p.hasExplicitImos());
        assertEquals(3, p.getImos().size());
        assertEquals("9000001", p.getImos().get(0));
        assertEquals("9000002", p.getImos().get(1));
        assertEquals("9000003", p.getImos().get(2));
    }

    @Test
    public void paramsEmptyImosFallsBack() {
        String json = "{\"mode\":\"STS_PHASE1_BASELINE\",\"seedFile\":\"/tmp/x.csv\"," +
                "\"from\":\"2026-01-01 00:00:00\",\"to\":\"2026-01-02 00:00:00\"," +
                "\"shipLimit\":5}";

        StsJobParams p = StsJobParams.parse(json);
        assertFalse("hasExplicitImos should be false without imos field", p.hasExplicitImos());
        assertEquals(5, p.getShipLimit());
    }

    @Test
    public void paramsImosWithBlanksAreFiltered() {
        String json = "{\"mode\":\"STS_PHASE1_BASELINE\",\"seedFile\":\"/tmp/x.csv\"," +
                "\"from\":\"2026-01-01 00:00:00\",\"to\":\"2026-01-02 00:00:00\"," +
                "\"imos\":[\"9000001\",\"  \",\"9000002\"]}";

        StsJobParams p = StsJobParams.parse(json);
        assertEquals(2, p.getImos().size());
    }

    @Test
    public void singleExplicitImoIsSelected() throws Exception {
        StsJobParams params = StsJobParams.parse("{}");
        params.setMode("STS_PHASE1_BASELINE");
        params.setSeedFile("/tmp/x.csv");
        params.setFrom("2026-01-01 00:00:00");
        params.setTo("2026-01-02 00:00:00");
        params.setImos(Arrays.asList("9000001"));
        params.setShipLimit(100);

        Method selectImos = StsBaselineService.class.getDeclaredMethod("selectImos", StsJobParams.class);
        selectImos.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) selectImos.invoke(service, params);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("9000001", result.get(0));
    }

    @Test
    public void multipleExplicitImosPreservesOrder() throws Exception {
        StsJobParams params = StsJobParams.parse("{}");
        params.setMode("STS_PHASE1_BASELINE");
        params.setSeedFile("/tmp/x.csv");
        params.setFrom("2026-01-01 00:00:00");
        params.setTo("2026-01-02 00:00:00");
        params.setImos(Arrays.asList("9000003", "9000001", "9000002"));

        Method selectImos = StsBaselineService.class.getDeclaredMethod("selectImos", StsJobParams.class);
        selectImos.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) selectImos.invoke(service, params);

        assertEquals(3, result.size());
        assertEquals("9000003", result.get(0));
        assertEquals("9000001", result.get(1));
        assertEquals("9000002", result.get(2));
    }

    @Test
    public void unknownImoIsSkippedGracefully() throws Exception {
        StsJobParams params = StsJobParams.parse("{}");
        params.setMode("STS_PHASE1_BASELINE");
        params.setSeedFile("/tmp/x.csv");
        params.setFrom("2026-01-01 00:00:00");
        params.setTo("2026-01-02 00:00:00");
        params.setImos(Arrays.asList("9000001", "NO_SUCH_IMO", "9000002"));

        Method selectImos = StsBaselineService.class.getDeclaredMethod("selectImos", StsJobParams.class);
        selectImos.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) selectImos.invoke(service, params);

        assertEquals(2, result.size());
        assertTrue(result.contains("9000001"));
        assertTrue(result.contains("9000002"));
        assertFalse(result.contains("NO_SUCH_IMO"));
    }

    @Test
    public void imosTakesPriorityOverShipLimit() throws Exception {
        StsJobParams params = StsJobParams.parse("{}");
        params.setMode("STS_PHASE1_BASELINE");
        params.setSeedFile("/tmp/x.csv");
        params.setFrom("2026-01-01 00:00:00");
        params.setTo("2026-01-02 00:00:00");
        params.setImos(Arrays.asList("9000001", "9000002"));
        params.setShipLimit(1);

        Method selectImos = StsBaselineService.class.getDeclaredMethod("selectImos", StsJobParams.class);
        selectImos.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) selectImos.invoke(service, params);

        assertEquals(2, result.size());
    }

    @Test
    public void noImosFallsBackToShipLimit() throws Exception {
        StsJobParams params = StsJobParams.parse("{}");
        params.setMode("STS_PHASE1_BASELINE");
        params.setSeedFile("/tmp/x.csv");
        params.setFrom("2026-01-01 00:00:00");
        params.setTo("2026-01-02 00:00:00");
        params.setShipLimit(2);

        Method selectImos = StsBaselineService.class.getDeclaredMethod("selectImos", StsJobParams.class);
        selectImos.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) selectImos.invoke(service, params);

        assertEquals(2, result.size());
    }

    @Test
    public void dualMmsiIdentitySelfExclusion() {
        String imo = "9000002";
        java.util.Set<String> ownMmsis = catalog.getIdentityMmsis(imo);

        assertEquals(2, ownMmsis.size());
        assertTrue(ownMmsis.contains("222222222"));
        assertTrue(ownMmsis.contains("333333333"));

        String queriedMmsi = "222222222";
        String alternateMmsi = "333333333";

        assertTrue("queried MMSI must be in identity set", ownMmsis.contains(queriedMmsi));
        assertTrue("alternate MMSI must be in identity set (self exclusion guard)",
                ownMmsis.contains(alternateMmsi));

        String targetMmsi = alternateMmsi;
        boolean isSelf = ownMmsis.contains(targetMmsi);
        assertTrue("target=alternate MMSI must be rejected as self pair", isSelf);

        String targetImo = catalog.findImoByMmsi(targetMmsi);
        assertNotNull("alternate MMSI should resolve back to same IMO", targetImo);
        assertEquals(imo, targetImo);
    }
}
