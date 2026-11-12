package com.hifleet.sts.service;

import com.hifleet.regionbehavior.RegionBehaviorConfig;
import com.hifleet.regionbehavior.RegionGeometry;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StsFleetDiscoveryCostModelTest {

    @Test
    public void hotspotV2InitialPlanCountsAreAuditableAndBounded() {
        StsFleetDiscoveryConfig config = new StsFleetDiscoveryConfig();
        StsFleetDiscoveryPlanner planner = new StsFleetDiscoveryPlanner(config);
        LocalDateTime from = LocalDateTime.of(2024, 1, 1, 0, 0);
        int oneDayTotal = 0;
        int sevenDayTotal = 0;
        int riauOneDay = 0;
        int controlOneDay = 0;

        for (Map.Entry<String, String> region : regions().entrySet()) {
            RegionGeometry geometry = RegionGeometry.parse(
                    region.getValue(), 10.0, RegionBehaviorConfig.defaults());
            int oneDay = planner.plan(geometry, from, from.plusDays(1)).getInitialSlices();
            int sevenDay = planner.plan(geometry, from, from.plusDays(7)).getInitialSlices();
            oneDayTotal += oneDay;
            sevenDayTotal += sevenDay;
            assertTrue(region.getKey(), sevenDay <= config.getMaxInitialSlicesPerRegion());
            if (region.getKey().startsWith("RIAU")) riauOneDay = oneDay;
            if (region.getKey().startsWith("AUGUSTA")) controlOneDay = oneDay;
        }

        assertEquals(24, riauOneDay);
        assertEquals(18, controlOneDay);
        assertEquals(322, oneDayTotal);
        assertEquals(2254, sevenDayTotal);
    }

    private Map<String, String> regions() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("RIAU_EOPL_NIPAH_STS_V2", "POLYGON((102.6 0.2,105.4 0.2,105.4 2.5,102.6 2.5,102.6 0.2))");
        values.put("LINGGI_MALACCA_STS_V2", "POLYGON((101.2 1.3,103.3 1.3,103.3 3.6,101.2 3.6,101.2 1.3))");
        values.put("LOME_OFFSHORE_STS_V2", "POLYGON((-0.8 4.2,1.8 4.2,1.8 7,-0.8 7,-0.8 4.2))");
        values.put("PORT_SAID_EASTMED_STS_V2", "POLYGON((31 30.6,33.8 30.6,33.8 32.8,31 32.8,31 30.6))");
        values.put("CYPRUS_OFFSHORE_STS_V2", "POLYGON((31.5 33.2,35.5 33.2,35.5 35.8,31.5 35.8,31.5 33.2))");
        values.put("MALTA_HURDS_BANK_STS_V2", "POLYGON((13.5 34.8,15.8 34.8,15.8 36.8,13.5 36.8,13.5 34.8))");
        values.put("AUGUSTA_SICILY_STS_V2", "POLYGON((14.2 36.5,16.2 36.5,16.2 38.2,14.2 38.2,14.2 36.5))");
        values.put("LACONIAN_KALAMATA_STS_V2", "POLYGON((21.3 35.4,23.4 35.4,23.4 37.2,21.3 37.2,21.3 35.4))");
        values.put("CEUTA_GIBRALTAR_STS_V2", "POLYGON((-6.4 35,-4.4 35,-4.4 36.7,-6.4 36.7,-6.4 35))");
        values.put("GULF_OMAN_FUJAIRAH_STS_V2", "POLYGON((54.8 23.2,58.8 23.2,58.8 26.4,54.8 26.4,54.8 23.2))");
        values.put("SOHAR_STS_V2", "POLYGON((56.3 23.2,58.2 23.2,58.2 24.9,56.3 24.9,56.3 23.2))");
        values.put("NAKHODKA_KOZMINO_STS_V2", "POLYGON((131.2 41,134.6 41,134.6 43.6,131.2 43.6,131.2 41))");
        values.put("HONG_KONG_OFFSHORE_STS_V2", "POLYGON((112.8 20.8,115.5 20.8,115.5 23.3,112.8 23.3,112.8 20.8))");
        values.put("ARUBA_CURACAO_STS_V2", "POLYGON((-71.6 11,-68 11,-68 13.6,-71.6 13.6,-71.6 11))");
        return values;
    }
}
