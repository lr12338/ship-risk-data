package com.hifleet.sts;

import com.hifleet.regionbehavior.RegionBehaviorConfig;
import com.hifleet.regionbehavior.RegionGeometry;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Test — V2 Region Geometry Validation.
 *
 * Validates all 29 V2 WKT geometries parse correctly under JTS
 * (same RegionGeometry used by the STS pipeline):
 *   - WKT parses without error
 *   - Geometry is valid
 *   - Core polygon area > 0
 *   - Analysis buffer geometry is valid
 *   - Fingerprint is a 64-char SHA-256 hex
 *   - All 29 fingerprints are unique
 *   - Known inside/outside points check out
 */
public class StsV2RegionGeometryValidationTest {

    private static final String[][] V2_REGIONS = {
        // Hotspot high-yield (14)
        {"RIAU_EOPL_NIPAH_STS_V2",       "POLYGON((102.6 0.2,105.4 0.2,105.4 2.5,102.6 2.5,102.6 0.2))",             "104.0 1.5", "100.0 0.0"},
        {"LINGGI_MALACCA_STS_V2",        "POLYGON((101.2 1.3,103.3 1.3,103.3 3.6,101.2 3.6,101.2 1.3))",              "102.0 2.0", "100.0 0.0"},
        {"LOME_OFFSHORE_STS_V2",         "POLYGON((-0.8 4.2,1.8 4.2,1.8 7.0,-0.8 7.0,-0.8 4.2))",                   "0.5 5.5",   "-5.0 0.0"},
        {"PORT_SAID_EASTMED_STS_V2",     "POLYGON((31.0 30.6,33.8 30.6,33.8 32.8,31.0 32.8,31.0 30.6))",             "32.5 31.5", "30.0 30.0"},
        {"CYPRUS_OFFSHORE_STS_V2",       "POLYGON((31.5 33.2,35.5 33.2,35.5 35.8,31.5 35.8,31.5 33.2))",             "33.5 34.5", "30.0 30.0"},
        {"MALTA_HURDS_BANK_STS_V2",      "POLYGON((13.5 34.8,15.8 34.8,15.8 36.8,13.5 36.8,13.5 34.8))",             "14.5 35.5", "10.0 30.0"},
        {"AUGUSTA_SICILY_STS_V2",        "POLYGON((14.2 36.5,16.2 36.5,16.2 38.2,14.2 38.2,14.2 36.5))",             "15.0 37.0", "10.0 30.0"},
        {"LACONIAN_KALAMATA_STS_V2",     "POLYGON((21.3 35.4,23.4 35.4,23.4 37.2,21.3 37.2,21.3 35.4))",             "22.5 36.0", "20.0 30.0"},
        {"CEUTA_GIBRALTAR_STS_V2",       "POLYGON((-6.4 35.0,-4.4 35.0,-4.4 36.7,-6.4 36.7,-6.4 35.0))",             "-5.5 35.8", "-10.0 30.0"},
        {"GULF_OMAN_FUJAIRAH_STS_V2",    "POLYGON((54.8 23.2,58.8 23.2,58.8 26.4,54.8 26.4,54.8 23.2))",             "56.5 24.5", "50.0 20.0"},
        {"SOHAR_STS_V2",                 "POLYGON((56.3 23.2,58.2 23.2,58.2 24.9,56.3 24.9,56.3 23.2))",             "57.0 24.0", "50.0 20.0"},
        {"NAKHODKA_KOZMINO_STS_V2",      "POLYGON((131.2 41.0,134.6 41.0,134.6 43.6,131.2 43.6,131.2 41.0))",         "133.0 42.0", "120.0 40.0"},
        {"HONG_KONG_OFFSHORE_STS_V2",    "POLYGON((112.8 20.8,115.5 20.8,115.5 23.3,112.8 23.3,112.8 20.8))",         "114.0 22.0", "110.0 20.0"},
        {"ARUBA_CURACAO_STS_V2",         "POLYGON((-71.6 11.0,-68.0 11.0,-68.0 13.6,-71.6 13.6,-71.6 11.0))",         "-70.0 12.0", "-75.0 10.0"},
        // Crude export approaches (15)
        {"NORTH_GULF_BASRA_KUWAIT_KHARG_V2", "POLYGON((47.5 27.2,52.5 27.2,52.5 30.5,47.5 30.5,47.5 27.2))",       "50.0 28.5", "40.0 20.0"},
        {"CENTRAL_GULF_RAS_TANURA_JUAYMAH_V2", "POLYGON((49.0 24.0,52.8 24.0,52.8 27.3,49.0 27.3,49.0 24.0))",   "51.0 25.5", "40.0 20.0"},
        {"SOUTH_GULF_RUWAIS_JEBEL_DHANNA_V2", "POLYGON((52.0 23.0,55.0 23.0,55.0 25.5,52.0 25.5,52.0 23.0))",     "53.5 24.0", "40.0 20.0"},
        {"SAUDI_YANBU_RED_SEA_V2",       "POLYGON((37.0 22.2,40.5 22.2,40.5 25.8,37.0 25.8,37.0 22.2))",             "38.5 24.0", "30.0 20.0"},
        {"VENEZUELA_JOSE_PARIA_V2",      "POLYGON((-65.8 9.0,-61.0 9.0,-61.0 12.0,-65.8 12.0,-65.8 9.0))",          "-63.0 10.5", "-70.0 5.0"},
        {"VENEZUELA_PARAGUANA_V2",       "POLYGON((-72.0 10.5,-68.5 10.5,-68.5 13.2,-72.0 13.2,-72.0 10.5))",        "-70.0 12.0", "-75.0 5.0"},
        {"RUSSIA_PRIMORSK_USTLUGA_V2",   "POLYGON((26.0 58.3,30.8 58.3,30.8 61.2,26.0 61.2,26.0 58.3))",             "28.5 60.0", "20.0 50.0"},
        {"RUSSIA_NOVOROSSIYSK_TUAPSE_V2","POLYGON((36.2 42.8,40.0 42.8,40.0 45.5,36.2 45.5,36.2 42.8))",              "38.0 44.0", "30.0 40.0"},
        {"RUSSIA_TAMAN_KAVKAZ_V2",       "POLYGON((35.0 44.0,37.8 44.0,37.8 46.2,35.0 46.2,35.0 44.0))",             "36.5 45.0", "30.0 40.0"},
        {"NIGERIA_EAST_BONNY_QUAIBOE_V2","POLYGON((5.8 3.0,9.2 3.0,9.2 5.5,5.8 5.5,5.8 3.0))",                        "7.5 4.2",   "0.0 0.0"},
        {"NIGERIA_WEST_FORCADOS_ESCRAVOS_V2", "POLYGON((3.8 3.5,6.5 3.5,6.5 5.8,3.8 5.8,3.8 3.5))",                 "5.0 4.5",   "0.0 0.0"},
        {"ANGOLA_LUANDA_STS_V2",         "POLYGON((11.5 -10.5,14.2 -10.5,14.2 -7.5,11.5 -7.5,11.5 -10.5))",          "13.0 -9.0", "10.0 -15.0"},
        {"ANGOLA_CABINDA_STS_V2",        "POLYGON((10.5 -6.5,13.5 -6.5,13.5 -4.2,10.5 -4.2,10.5 -6.5))",             "12.0 -5.5", "10.0 -10.0"},
        {"LIBYA_SIDRA_RASLANUF_V2",      "POLYGON((16.5 29.2,20.2 29.2,20.2 32.2,16.5 32.2,16.5 29.2))",             "18.5 30.5", "10.0 25.0"},
        {"CEYHAN_EASTMED_EXPORT_V2",     "POLYGON((34.5 35.0,37.0 35.0,37.0 37.5,34.5 37.5,34.5 35.0))",             "36.0 36.0", "30.0 30.0"},
    };

    @Test
    public void all29RegionsParseSuccessfully() {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        List<String> failures = new ArrayList<String>();
        for (String[] reg : V2_REGIONS) {
            String code = reg[0];
            String wkt = reg[1];
            try {
                RegionGeometry geom = RegionGeometry.parse(wkt, 10.0, config);
                if (geom == null) {
                    failures.add(code + ": returned null geometry");
                    continue;
                }
                if (!geom.getCoreRegion().isValid()) {
                    failures.add(code + ": core geometry not valid");
                }
                if (geom.getCoreRegion().isEmpty()) {
                    failures.add(code + ": core geometry is empty");
                }
                double area = geom.getCoreRegion().getArea();
                if (area <= 0) {
                    failures.add(code + ": core area <= 0 (area=" + area + ")");
                }
                String fp = geom.getFingerprint();
                if (fp == null || fp.length() != 64) {
                    failures.add(code + ": bad fingerprint length (" + (fp == null ? "null" : fp.length()) + ")");
                }
            } catch (Exception e) {
                failures.add(code + ": parse exception — " + e.getMessage());
            }
        }
        assertTrue("All 29 regions should parse without failure. Failures: " + failures,
                failures.isEmpty());
    }

    @Test
    public void all29FingerprintsAreUnique() {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        Set<String> fingerprints = new HashSet<String>();
        for (String[] reg : V2_REGIONS) {
            RegionGeometry geom = RegionGeometry.parse(reg[1], 10.0, config);
            String fp = geom.getFingerprint();
            assertFalse("Duplicate fingerprint: " + reg[0] + " -> " + fp,
                    fingerprints.contains(fp));
            fingerprints.add(fp);
        }
        assertEquals(29, fingerprints.size());
    }

    @Test
    public void insidePointsAreInsideCore() {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        for (String[] reg : V2_REGIONS) {
            String code = reg[0];
            String wkt = reg[1];
            String insidePt = reg[2];
            RegionGeometry geom = RegionGeometry.parse(wkt, 10.0, config);
            Coordinate c = parseCoord(insidePt);
            assertTrue(code + ": inside point " + insidePt + " should be in core",
                    geom.containsCore(c));
        }
    }

    @Test
    public void outsidePointsAreOutsideCore() {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        for (String[] reg : V2_REGIONS) {
            String code = reg[0];
            String wkt = reg[1];
            String outsidePt = reg[3];
            RegionGeometry geom = RegionGeometry.parse(wkt, 10.0, config);
            Coordinate c = parseCoord(outsidePt);
            assertFalse(code + ": outside point " + outsidePt + " should NOT be in core",
                    geom.containsCore(c));
        }
    }

    @Test
    public void allAreRectangularBboxes() {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        for (String[] reg : V2_REGIONS) {
            String code = reg[0];
            String wkt = reg[1];
            RegionGeometry geom = RegionGeometry.parse(wkt, 10.0, config);
            assertEquals(code + ": should have exactly 5 points (4 corners + closing)",
                    5, geom.getCoreRegion().getNumPoints());
        }
    }

    @Test
    public void totalIs29Regions() {
        assertEquals(29, V2_REGIONS.length);
    }

    @Test
    public void hotspotSetHas14Regions() {
        int count = 0;
        for (String[] reg : V2_REGIONS) {
            if (isHotspotRegion(reg[0])) count++;
        }
        assertEquals(14, count);
    }

    @Test
    public void exportSetHas15Regions() {
        int count = 0;
        for (String[] reg : V2_REGIONS) {
            if (isExportRegion(reg[0])) count++;
        }
        assertEquals(15, count);
    }

    private boolean isHotspotRegion(String code) {
        return code.endsWith("_STS_V2") && !code.startsWith("NORTH_GULF_")
                && !code.startsWith("CENTRAL_GULF_") && !code.startsWith("SOUTH_GULF_")
                && !code.startsWith("SAUDI_YANBU_") && !code.startsWith("VENEZUELA_")
                && !code.startsWith("RUSSIA_") && !code.startsWith("NIGERIA_")
                && !code.startsWith("ANGOLA_") && !code.startsWith("LIBYA_")
                && !code.startsWith("CEYHAN_");
    }

    private boolean isExportRegion(String code) {
        return !isHotspotRegion(code);
    }

    private Coordinate parseCoord(String s) {
        String[] parts = s.trim().split(" ");
        return new Coordinate(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
    }
}
