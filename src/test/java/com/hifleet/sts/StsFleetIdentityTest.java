package com.hifleet.sts;

import com.hifleet.sts.domain.StsFleetShipIdentity;
import com.hifleet.sts.service.StsBackfillFleetCatalog;
import com.hifleet.sts.service.StsFleetBackfillService;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Test — Physical ship identity grouping and fleet fingerprint.
 *
 * Test A: IMO grouping — same IMO, multiple MMSIs → 1 physical ship
 * Test B: MMSI fallback — no IMO, valid MMSI → MMSI physical key
 * Test C: Duplicate row — same IMO + same MMSI → 1 alias
 * Test E: Deterministic fingerprint — same fleet = same hash
 * Test I: Fleet fingerprint — list changed → different hash
 */
public class StsFleetIdentityTest {

    // Test A: same IMO, MMSI A + MMSI B → 1 physical ship with both aliases
    @Test
    public void imoGroupingCombinesMmsis() {
        StsFleetShipIdentity identity = new StsFleetShipIdentity();
        identity.setPhysicalShipKey("IMO:1234567");
        identity.setImo("1234567");
        identity.addMmsi("412345678");
        identity.addMmsi("412345679");
        identity.setShipName("TEST SHIP");
        identity.setShipType("TANKER");
        identity.setMinSeqNo(1);
        identity.setIdentitySource("IMO");

        assertEquals("IMO:1234567", identity.getPhysicalShipKey());
        assertEquals("1234567", identity.getImo());
        assertEquals(2, identity.getMmsis().size());
        assertTrue(identity.getMmsis().contains("412345678"));
        assertTrue(identity.getMmsis().contains("412345679"));
        assertEquals("IMO", identity.getIdentitySource());
    }

    // Test B: MMSI-only — no IMO, valid MMSI → MMSI key
    @Test
    public void mmsiOnlyFallback() {
        StsFleetShipIdentity identity = new StsFleetShipIdentity();
        identity.setPhysicalShipKey("MMSI:412345678");
        identity.setImo(null);
        identity.addMmsi("412345678");
        identity.setShipType("UNKNOWN");
        identity.setIdentitySource("MMSI_ONLY");

        assertEquals("MMSI:412345678", identity.getPhysicalShipKey());
        assertNull(identity.getImo());
        assertEquals(1, identity.getMmsis().size());
        assertEquals("MMSI_ONLY", identity.getIdentitySource());
    }

    // Test C: duplicate MMSI is deduped
    @Test
    public void duplicateMmsiDeduped() {
        StsFleetShipIdentity identity = new StsFleetShipIdentity();
        identity.setPhysicalShipKey("IMO:1234567");
        identity.setImo("1234567");
        identity.addMmsi("412345678");
        identity.addMmsi("412345678");
        identity.addMmsi("412345678");

        assertEquals(1, identity.getMmsis().size());
        assertTrue(identity.getMmsis().contains("412345678"));
    }

    // Test: fingerprint line format
    @Test
    public void fingerprintLineFormat() {
        StsFleetShipIdentity id = new StsFleetShipIdentity();
        id.setPhysicalShipKey("IMO:1234567");
        id.setImo("1234567");
        id.addMmsi("412345679");
        id.addMmsi("412345678"); // added out of order
        id.setShipType("TANKER");

        String line = id.toFingerprintLine();
        // MMSIs should be sorted
        assertEquals("IMO:1234567|1234567|412345678,412345679|TANKER", line);
    }

    // Test E: deterministic fleet fingerprint
    @Test
    public void fleetFingerprintIsDeterministic() {
        List<StsFleetShipIdentity> fleet1 = buildTestFleet();
        List<StsFleetShipIdentity> fleet2 = buildTestFleet();

        String fp1 = StsFleetBackfillService.computeFleetFingerprint(fleet1);
        String fp2 = StsFleetBackfillService.computeFleetFingerprint(fleet2);

        assertEquals(fp1, fp2);
        assertNotNull(fp1);
        assertEquals(64, fp1.length()); // SHA-256 hex
    }

    // Test I: fleet change → different fingerprint
    @Test
    public void fleetChangeChangesFingerprint() {
        List<StsFleetShipIdentity> fleet = buildTestFleet();
        String fpBefore = StsFleetBackfillService.computeFleetFingerprint(fleet);

        // Add a new ship
        StsFleetShipIdentity extra = new StsFleetShipIdentity();
        extra.setPhysicalShipKey("IMO:9999999");
        extra.setImo("9999999");
        extra.addMmsi("477777777");
        extra.setShipType("BULKER");
        List<StsFleetShipIdentity> modified = new ArrayList<StsFleetShipIdentity>(fleet);
        modified.add(extra);

        String fpAfter = StsFleetBackfillService.computeFleetFingerprint(modified);
        assertNotEquals(fpBefore, fpAfter);
    }

    // Test: fingerprint changes when MMSI aliases change
    @Test
    public void mmsiAliasChangeChangesFingerprint() {
        List<StsFleetShipIdentity> fleet = buildTestFleet();
        String fpBefore = StsFleetBackfillService.computeFleetFingerprint(fleet);

        // Add a new MMSI to first ship
        List<StsFleetShipIdentity> modified = new ArrayList<StsFleetShipIdentity>();
        StsFleetShipIdentity first = new StsFleetShipIdentity();
        first.setPhysicalShipKey(fleet.get(0).getPhysicalShipKey());
        first.setImo(fleet.get(0).getImo());
        for (String m : fleet.get(0).getMmsis()) first.addMmsi(m);
        first.addMmsi("455555555"); // new alias
        first.setShipType(fleet.get(0).getShipType());
        modified.add(first);
        for (int i = 1; i < fleet.size(); i++) modified.add(fleet.get(i));

        String fpAfter = StsFleetBackfillService.computeFleetFingerprint(modified);
        assertNotEquals(fpBefore, fpAfter);
    }

    // Test: BackfillFleetCatalog — IMO-based lookup
    @Test
    public void catalogImoLookup() {
        List<StsFleetShipIdentity> fleet = buildTestFleet();
        StsBackfillFleetCatalog catalog = new StsBackfillFleetCatalog(fleet);

        assertEquals(fleet.size(), catalog.getTotalShips());
        assertEquals(2, catalog.getByIdentity("IMO:1000001").size());
        assertEquals("IMO", catalog.getIdentityMode("IMO:1000001"));

        Set<String> mmsis = catalog.getIdentityMmsis("IMO:1000001");
        assertEquals(2, mmsis.size());
        assertTrue(mmsis.contains("410000001"));
        assertTrue(mmsis.contains("410000002"));
    }

    // Test: BackfillFleetCatalog — MMSI-only lookup
    @Test
    public void catalogMmsiOnlyLookup() {
        List<StsFleetShipIdentity> fleet = buildTestFleet();
        StsBackfillFleetCatalog catalog = new StsBackfillFleetCatalog(fleet);

        assertEquals(1, catalog.getByIdentity("MMSI:430000001").size());
        assertEquals("MMSI_ONLY", catalog.getIdentityMode("MMSI:430000001"));
    }

    // Test: BackfillFleetCatalog — findImoByMmsi returns physical key
    @Test
    public void catalogFindImoByMmsi() {
        List<StsFleetShipIdentity> fleet = buildTestFleet();
        StsBackfillFleetCatalog catalog = new StsBackfillFleetCatalog(fleet);

        assertEquals("IMO:1000001", catalog.findImoByMmsi("410000001"));
        assertEquals("IMO:1000001", catalog.findImoByMmsi("410000002"));
        assertEquals("IMO:1000002", catalog.findImoByMmsi("420000003"));
        assertEquals("MMSI:430000001", catalog.findImoByMmsi("430000001"));
        assertNull(catalog.findImoByMmsi("999999999"));
    }

    // Test: BackfillFleetCatalog — getActualImo returns real IMO or null
    @Test
    public void catalogGetActualImo() {
        List<StsFleetShipIdentity> fleet = buildTestFleet();
        StsBackfillFleetCatalog catalog = new StsBackfillFleetCatalog(fleet);

        assertEquals("1000001", catalog.getActualImo("IMO:1000001"));
        assertNull(catalog.getActualImo("MMSI:430000001"));
    }

    // Test F: physical count != row count (multi-MMSI)
    @Test
    public void physicalCountDiffersFromRowCount() {
        List<StsFleetShipIdentity> fleet = buildTestFleet();
        // 3 physical ships, but 5 "rows" (2+2+1 MMSIs)
        int mmsiCount = 0;
        for (StsFleetShipIdentity id : fleet) mmsiCount += id.getMmsis().size();
        assertEquals(5, mmsiCount);
        assertEquals(3, fleet.size());
        assertNotEquals(mmsiCount, fleet.size());
    }

    private List<StsFleetShipIdentity> buildTestFleet() {
        List<StsFleetShipIdentity> fleet = new ArrayList<StsFleetShipIdentity>();

        // Ship 1: IMO-based, 2 MMSIs
        StsFleetShipIdentity s1 = new StsFleetShipIdentity();
        s1.setPhysicalShipKey("IMO:1000001");
        s1.setImo("1000001");
        s1.addMmsi("410000001");
        s1.addMmsi("410000002");
        s1.setShipName("SHIP ONE");
        s1.setShipType("TANKER");
        s1.setMinSeqNo(1);
        s1.setIdentitySource("IMO");
        s1.setPrimaryMmsi("410000001");
        fleet.add(s1);

        // Ship 2: IMO-based, 2 MMSIs
        StsFleetShipIdentity s2 = new StsFleetShipIdentity();
        s2.setPhysicalShipKey("IMO:1000002");
        s2.setImo("1000002");
        s2.addMmsi("420000003");
        s2.addMmsi("420000004");
        s2.setShipName("SHIP TWO");
        s2.setShipType("CONTAINER");
        s2.setMinSeqNo(3);
        s2.setIdentitySource("IMO");
        fleet.add(s2);

        // Ship 3: MMSI-only
        StsFleetShipIdentity s3 = new StsFleetShipIdentity();
        s3.setPhysicalShipKey("MMSI:430000001");
        s3.setImo(null);
        s3.addMmsi("430000001");
        s3.setShipName("SHIP THREE");
        s3.setShipType("UNKNOWN");
        s3.setMinSeqNo(5);
        s3.setIdentitySource("MMSI_ONLY");
        fleet.add(s3);

        return fleet;
    }
}
