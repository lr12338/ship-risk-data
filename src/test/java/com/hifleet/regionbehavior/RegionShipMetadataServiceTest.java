package com.hifleet.regionbehavior;

import com.hifleet.service.ShipTypeClassifier;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RegionShipMetadataServiceTest {

    @Test
    public void reusesMerchantClassifierAndExcludesUnknownMetadata() {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        RegionShipMetadataService service = new StubService(config, Arrays.asList(
                new RegionShipMetadataService.ShipMetadata("413000001", "集装箱船", 200.0),
                new RegionShipMetadataService.ShipMetadata("413000002", "渔船", 200.0),
                new RegionShipMetadataService.ShipMetadata("413000003", "集装箱船", 60.0)));
        Map<String, RegionShipCandidate> candidates = candidates("413000001", "413000002",
                "413000003", "413000004");

        RegionShipMetadataSummary summary = service.filter(candidates);

        assertEquals(4, summary.getDiscoveredVessels());
        assertEquals(3, summary.getMetadataFound());
        assertEquals(1, summary.getEligibleMainShips());
        assertEquals(2, summary.getExcludedByType());
        assertEquals(1, summary.getUnknownShipType());
        assertTrue(candidates.get("413000001").isEligible());
        assertFalse(candidates.get("413000004").isEligible());
        assertEquals("UNKNOWN_SHIP_TYPE", candidates.get("413000004").getEligibilityStatus());
    }

    @Test
    public void batchesInQueries() {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        config.setMetadataBatchSize(2);
        StubService service = new StubService(config, new ArrayList<RegionShipMetadataService.ShipMetadata>());

        service.filter(candidates("413000001", "413000002", "413000003", "413000004", "413000005"));

        assertEquals(3, service.queryCount);
    }

    @Test
    public void metadataQueryFailureFailsClosedAsUnknown() {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        RegionShipMetadataService service = new RegionShipMetadataService(null,
                new ShipTypeClassifier(), config) {
            @Override
            protected List<ShipMetadata> queryBatch(List<String> mmsis) {
                throw new IllegalStateException("metadata unavailable");
            }
        };
        Map<String, RegionShipCandidate> candidates = candidates("413000001");

        RegionShipMetadataSummary summary = service.filter(candidates);

        assertEquals(1, summary.getMetadataQueryFailures());
        assertEquals(0, summary.getEligibleMainShips());
        assertEquals("UNKNOWN_SHIP_TYPE", candidates.get("413000001").getEligibilityStatus());
    }

    private Map<String, RegionShipCandidate> candidates(String... mmsis) {
        Map<String, RegionShipCandidate> result = new LinkedHashMap<String, RegionShipCandidate>();
        for (String mmsi : mmsis) {
            result.put(mmsi, new RegionShipCandidate(mmsi));
        }
        return result;
    }

    private static class StubService extends RegionShipMetadataService {
        private final List<ShipMetadata> rows;
        private int queryCount;

        StubService(RegionBehaviorConfig config, List<ShipMetadata> rows) {
            super(null, new ShipTypeClassifier(), config);
            this.rows = rows;
        }

        @Override
        protected List<ShipMetadata> queryBatch(List<String> mmsis) {
            queryCount++;
            List<ShipMetadata> result = new ArrayList<ShipMetadata>();
            for (ShipMetadata row : rows) {
                if (mmsis.contains(row.getMmsi())) {
                    result.add(row);
                }
            }
            return result;
        }
    }
}
