package com.hifleet.sts;

import com.hifleet.sts.domain.StsIdentityRecord;
import com.hifleet.sts.service.StsSeedCatalog;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Test A — Identity
 *
 * Covers:
 *   SINGLE_OBSERVED
 *   DUAL_NON_OVERLAP
 *   DUAL_OVERLAP
 *   alternate MMSI self rejection (target MMSI is in ownIdentityMmsis)
 *   IMO lookup from MMSI
 */
public class StsSeedCatalogTest {

    private StsSeedCatalog catalog;

    @Before
    public void setUp() throws Exception {
        catalog = new StsSeedCatalog();
        java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("sts/test-sts-identity.csv");
        catalog.loadFromStream(in, "test-sts-identity.csv");
    }

    @Test
    public void loadsAllRecords() {
        assertTrue(catalog.isLoaded());
        assertEquals(3, catalog.getTotalImos());
        assertEquals(5, catalog.getTotalMmsis());
        assertEquals(1, catalog.getSingleMmsiImoCount());
        assertEquals(2, catalog.getDualMmsiImoCount());
    }

    @Test
    public void singleObservedIdentity() {
        String imo = "9000001";
        List<StsIdentityRecord> records = catalog.getByIdentity(imo);
        assertEquals(1, records.size());
        assertEquals("SINGLE_OBSERVED", catalog.getIdentityMode(imo));

        Set<String> mmsis = catalog.getIdentityMmsis(imo);
        assertEquals(1, mmsis.size());
        assertTrue(mmsis.contains("111111111"));
    }

    @Test
    public void dualNonOverlapIdentity() {
        String imo = "9000002";
        List<StsIdentityRecord> records = catalog.getByIdentity(imo);
        assertEquals(2, records.size());
        assertEquals("DUAL_NON_OVERLAP", catalog.getIdentityMode(imo));

        Set<String> mmsis = catalog.getIdentityMmsis(imo);
        assertEquals(2, mmsis.size());
        assertTrue("both old and new MMSI must be in identity set",
                mmsis.contains("222222222") && mmsis.contains("333333333"));
    }

    @Test
    public void dualOverlapIdentity() {
        String imo = "9000003";
        List<StsIdentityRecord> records = catalog.getByIdentity(imo);
        assertEquals(2, records.size());
        assertEquals("DUAL_OVERLAP", catalog.getIdentityMode(imo));

        Set<String> mmsis = catalog.getIdentityMmsis(imo);
        assertEquals(2, mmsis.size());
        assertTrue("both MMSIs must be in identity set even when overlapping",
                mmsis.contains("444444444") && mmsis.contains("555555555"));
    }

    @Test
    public void alternateMmsiSelfRejection() {
        String imo = "9000002";
        Set<String> ownIdentityMmsis = catalog.getIdentityMmsis(imo);

        String queriedMmsi = "222222222";
        String alternateMmsi = "333333333";

        assertTrue("queried MMSI must be in identity set",
                ownIdentityMmsis.contains(queriedMmsi));
        assertTrue("alternate MMSI must also be in identity set — this is the self-rejection guard",
                ownIdentityMmsis.contains(alternateMmsi));

        String targetMmsi = alternateMmsi;
        boolean isSelf = ownIdentityMmsis.contains(targetMmsi);
        assertTrue("old/new MMSI pair must be rejected as self-identity", isSelf);
    }

    @Test
    public void mmsiLookupFindsCorrectImo() {
        assertEquals("9000001", catalog.findImoByMmsi("111111111"));
        assertEquals("9000002", catalog.findImoByMmsi("222222222"));
        assertEquals("9000002", catalog.findImoByMmsi("333333333"));
        assertNull(catalog.findImoByMmsi("999999999"));
    }

    @Test
    public void unknownImoReturnsEmpty() {
        List<StsIdentityRecord> records = catalog.getByIdentity("NO_SUCH_IMO");
        assertNotNull(records);
        assertTrue(records.isEmpty());
        assertTrue(catalog.getIdentityMmsis("NO_SUCH_IMO").isEmpty());
    }

    @Test
    public void allImosListIsCorrect() {
        List<String> all = catalog.getAllImos();
        assertEquals(3, all.size());
        assertTrue(all.contains("9000001"));
        assertTrue(all.contains("9000002"));
        assertTrue(all.contains("9000003"));
    }
}
