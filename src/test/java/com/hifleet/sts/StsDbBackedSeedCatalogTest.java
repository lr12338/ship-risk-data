package com.hifleet.sts;

import com.hifleet.sts.domain.StsIdentityRecord;
import com.hifleet.sts.domain.StsSeedIdentityRow;
import com.hifleet.sts.service.StsRegionSetShadowService;
import com.hifleet.sts.service.StsSeedLookup;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Test — DbBackedSeedCatalog: deterministic IMO ordering,
 * dual-MMSI grouping, MMSI→IMO lookup.
 */
public class StsDbBackedSeedCatalogTest {

    private StsRegionSetShadowService.DbBackedSeedCatalog catalog;

    @Before
    public void setUp() {
        List<StsSeedIdentityRow> rows = new ArrayList<StsSeedIdentityRow>();

        // IMO AAA with 2 MMSIs (same ship_ordinal)
        StsSeedIdentityRow r1 = new StsSeedIdentityRow();
        r1.setSeedBatchNo("test-v1");
        r1.setShipOrdinal(1);
        r1.setImo("1000001");
        r1.setMmsi("200000001");
        r1.setIdentityMode("DUAL_MMSI_OBSERVED");
        r1.setName("SHIP_A");
        r1.setEnabled(1);
        rows.add(r1);

        StsSeedIdentityRow r2 = new StsSeedIdentityRow();
        r2.setSeedBatchNo("test-v1");
        r2.setShipOrdinal(1);
        r2.setImo("1000001");
        r2.setMmsi("200000002");
        r2.setIdentityMode("DUAL_MMSI_OBSERVED");
        r2.setName("SHIP_A");
        r2.setEnabled(1);
        rows.add(r2);

        // IMO BBB with 1 MMSI (ship_ordinal 2)
        StsSeedIdentityRow r3 = new StsSeedIdentityRow();
        r3.setSeedBatchNo("test-v1");
        r3.setShipOrdinal(2);
        r3.setImo("1000002");
        r3.setMmsi("200000003");
        r3.setIdentityMode("SINGLE_OBSERVED");
        r3.setName("SHIP_B");
        r3.setEnabled(1);
        rows.add(r3);

        // IMO CCC with 1 MMSI (ship_ordinal 3)
        StsSeedIdentityRow r4 = new StsSeedIdentityRow();
        r4.setSeedBatchNo("test-v1");
        r4.setShipOrdinal(3);
        r4.setImo("1000003");
        r4.setMmsi("200000004");
        r4.setIdentityMode("SINGLE_OBSERVED");
        r4.setName("SHIP_C");
        r4.setEnabled(1);
        rows.add(r4);

        catalog = new StsRegionSetShadowService.DbBackedSeedCatalog(rows);
    }

    @Test
    public void totalImosCountIsDistinct() {
        assertEquals(3, catalog.getTotalImos());
    }

    @Test
    public void dualMmsiImoHasTwoIdentityRecords() {
        List<StsIdentityRecord> records = catalog.getByIdentity("1000001");
        assertEquals(2, records.size());
        assertEquals("200000001", records.get(0).getMmsi());
        assertEquals("200000002", records.get(1).getMmsi());
    }

    @Test
    public void singleMmsiImoHasOneRecord() {
        List<StsIdentityRecord> records = catalog.getByIdentity("1000002");
        assertEquals(1, records.size());
        assertEquals("200000003", records.get(0).getMmsi());
    }

    @Test
    public void unknownImoReturnsEmptyList() {
        List<StsIdentityRecord> records = catalog.getByIdentity("9999999");
        assertNotNull(records);
        assertTrue(records.isEmpty());
    }

    @Test
    public void identityMmsisSetForDualMmsi() {
        Set<String> mmsis = catalog.getIdentityMmsis("1000001");
        assertEquals(2, mmsis.size());
        assertTrue(mmsis.contains("200000001"));
        assertTrue(mmsis.contains("200000002"));
    }

    @Test
    public void identityModeIsFromFirstRecord() {
        assertEquals("DUAL_MMSI_OBSERVED", catalog.getIdentityMode("1000001"));
        assertEquals("SINGLE_OBSERVED", catalog.getIdentityMode("1000002"));
    }

    @Test
    public void unknownModeReturnsUnknown() {
        assertEquals("UNKNOWN", catalog.getIdentityMode("9999999"));
    }

    @Test
    public void findImoByMmsiWorksForAllMmsis() {
        assertEquals("1000001", catalog.findImoByMmsi("200000001"));
        assertEquals("1000001", catalog.findImoByMmsi("200000002"));
        assertEquals("1000002", catalog.findImoByMmsi("200000003"));
        assertEquals("1000003", catalog.findImoByMmsi("200000004"));
    }

    @Test
    public void findImoByUnknownMmsiReturnsNull() {
        assertNull(catalog.findImoByMmsi("999999999"));
    }

    @Test
    public void implementsSeedLookupInterface() {
        assertTrue(catalog instanceof StsSeedLookup);
    }

    @Test
    public void toIdentityRecordCopiesAllFields() {
        StsSeedIdentityRow row = new StsSeedIdentityRow();
        row.setImo("1234567");
        row.setMmsi("7654321");
        row.setFirstSeen("2026-01-01 00:00:00");
        row.setLastSeen("2026-08-01 00:00:00");
        row.setName("TEST_SHIP");
        row.setCallsign("TEST123");
        row.setLength(300);
        row.setWidth(50);
        row.setType("油轮");
        row.setMinotype("原油船");
        row.setIdentityMode("SINGLE_OBSERVED");

        StsIdentityRecord rec = row.toIdentityRecord();
        assertEquals("1234567", rec.getImo());
        assertEquals("7654321", rec.getMmsi());
        assertEquals("2026-01-01 00:00:00", rec.getFirstSeen());
        assertEquals("2026-08-01 00:00:00", rec.getLastSeen());
        assertEquals("TEST_SHIP", rec.getName());
        assertEquals("TEST123", rec.getCallsign());
        assertEquals(300, rec.getLength());
        assertEquals(50, rec.getWidth());
        assertEquals("油轮", rec.getType());
        assertEquals("原油船", rec.getMinotype());
        assertEquals("SINGLE_OBSERVED", rec.getIdentityMode());
    }
}
