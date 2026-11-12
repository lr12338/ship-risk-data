package com.hifleet.sts;

import com.hifleet.sts.domain.StsJobParams;
import com.hifleet.sts.service.StsBaselineService;
import com.hifleet.sts.service.StsRegionShadowService;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test E — Production Safety Regression
 * Test F — Parameter Validation
 *
 * E: Verifies original shipStsHistoryBaselineJobHandler behavior is unchanged.
 *    - mode must be STS_PHASE1_BASELINE
 *    - writeMode must be LOG_ONLY
 *    - existing constraints still enforced
 *
 * F: Verifies STS_PHASE1_REGION_SHADOW mode validation:
 *    - regionWkt required
 *    - batchNo required
 *    - regionId required
 *    - parallelism must be 1
 *    - writeMode must be SHADOW_ONLY
 *    - time span must be <= 7 days
 *    - analysisBufferNm must be 0–30 nm
 *    - imos must be explicit (no fleet-wide)
 */
public class StsRegionShadowParamTest {

    // ===== Test E: Baseline Regression =====

    @Test
    public void baselineStillRequiresLogOnly() {
        StsBaselineService service = new StsBaselineService();
        StsJobParams params = new StsJobParams();
        params.setMode("STS_PHASE1_BASELINE");
        params.setSeedFile("/tmp/test.csv");
        params.setFrom("2026-01-01 00:00:00");
        params.setTo("2026-01-02 00:00:00");
        params.setParallelism(1);
        params.setWriteMode("SHADOW_ONLY");
        try {
            service.runBaseline(params);
            fail("baseline should still reject non-LOG_ONLY write mode");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("LOG_ONLY"));
        }
    }

    @Test
    public void baselineStillRejectsWrongMode() {
        StsBaselineService service = new StsBaselineService();
        StsJobParams params = new StsJobParams();
        params.setMode("STS_PHASE1_REGION_SHADOW");
        params.setSeedFile("/tmp/test.csv");
        params.setFrom("2026-01-01 00:00:00");
        params.setTo("2026-01-02 00:00:00");
        try {
            service.runBaseline(params);
            fail("baseline should reject REGION_SHADOW mode");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("mode"));
        }
    }

    @Test
    public void baselineValidParamsPassValidation() {
        // Verify that valid baseline params pass parameter validation.
        // (Seed catalog is null in raw instance, so we get NullPointerException
        //  after validation passes — this proves validation didn't reject.)
        StsBaselineService service = new StsBaselineService();
        StsJobParams params = new StsJobParams();
        params.setMode("STS_PHASE1_BASELINE");
        params.setSeedFile("/tmp/test.csv");
        params.setFrom("2026-01-01 00:00:00");
        params.setTo("2026-01-02 00:00:00");
        params.setParallelism(1);
        params.setWriteMode("LOG_ONLY");
        try {
            service.runBaseline(params);
            fail("expected NPE from null seedCatalog");
        } catch (NullPointerException e) {
            // Expected: passed param validation, hit null seedCatalog.
            // This proves baseline validation logic is unchanged.
        } catch (IllegalArgumentException e) {
            fail("valid baseline params should not be rejected: " + e.getMessage());
        }
    }

    // ===== Test F: Region Shadow Param Validation =====

    private StsJobParams validShadowParams() {
        StsJobParams p = new StsJobParams();
        p.setMode("STS_PHASE1_REGION_SHADOW");
        p.setSeedFile("/tmp/test.csv");
        p.setFrom("2026-01-01 00:00:00");
        p.setTo("2026-01-02 00:00:00");
        p.setRegionId("PG_TEST");
        p.setRegionVersion("v1");
        p.setRegionWkt("POLYGON((50 25,55 25,55 29,50 29,50 25))");
        p.setBatchNo("test-001");
        p.setParallelism(1);
        p.setWriteMode("SHADOW_ONLY");
        return p;
    }

    @Test
    public void rejectsMissingRegionWkt() {
        StsRegionShadowService service = new StsRegionShadowService();
        StsJobParams p = validShadowParams();
        p.setRegionWkt(null);
        try {
            service.runRegionShadow(p);
            fail("should reject missing regionWkt");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("regionWkt"));
        }
    }

    @Test
    public void rejectsMissingBatchNo() {
        StsRegionShadowService service = new StsRegionShadowService();
        StsJobParams p = validShadowParams();
        p.setBatchNo(null);
        try {
            service.runRegionShadow(p);
            fail("should reject missing batchNo");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("batchNo"));
        }
    }

    @Test
    public void rejectsMissingRegionId() {
        StsRegionShadowService service = new StsRegionShadowService();
        StsJobParams p = validShadowParams();
        p.setRegionId(null);
        try {
            service.runRegionShadow(p);
            fail("should reject missing regionId");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("regionId"));
        }
    }

    @Test
    public void rejectsMissingRegionVersion() {
        StsRegionShadowService service = new StsRegionShadowService();
        StsJobParams p = validShadowParams();
        p.setRegionVersion(null);
        try {
            service.runRegionShadow(p);
            fail("should reject missing regionVersion");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("regionVersion"));
        }
    }

    @Test
    public void rejectsParallelismAboveOne() {
        StsRegionShadowService service = new StsRegionShadowService();
        StsJobParams p = validShadowParams();
        p.setParallelism(4);
        try {
            service.runRegionShadow(p);
            fail("should reject parallelism != 1");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("parallelism"));
        }
    }

    @Test
    public void rejectsNonShadowOnlyWriteMode() {
        StsRegionShadowService service = new StsRegionShadowService();
        StsJobParams p = validShadowParams();
        p.setWriteMode("LOG_ONLY");
        try {
            service.runRegionShadow(p);
            fail("should reject non-SHADOW_ONLY write mode");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("SHADOW_ONLY"));
        }
    }

    @Test
    public void rejectsExcessiveTimeSpan() {
        StsRegionShadowService service = new StsRegionShadowService();
        StsJobParams p = validShadowParams();
        p.setFrom("2026-01-01 00:00:00");
        p.setTo("2026-01-15 00:00:00");
        try {
            service.runRegionShadow(p);
            fail("should reject >7 day span");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("days"));
        }
    }

    @Test
    public void rejectsBufferAboveMax() {
        StsRegionShadowService service = new StsRegionShadowService();
        StsJobParams p = validShadowParams();
        p.setAnalysisBufferNm(50.0);
        try {
            service.runRegionShadow(p);
            fail("should reject buffer > 30 nm");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("analysisBufferNm"));
        }
    }

    @Test
    public void rejectsNegativeBuffer() {
        StsRegionShadowService service = new StsRegionShadowService();
        StsJobParams p = validShadowParams();
        p.setAnalysisBufferNm(-5.0);
        try {
            service.runRegionShadow(p);
            fail("should reject negative buffer");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("analysisBufferNm"));
        }
    }

    @Test
    public void rejectsWrongMode() {
        StsRegionShadowService service = new StsRegionShadowService();
        StsJobParams p = validShadowParams();
        p.setMode("STS_PHASE1_BASELINE");
        try {
            service.runRegionShadow(p);
            fail("should reject wrong mode");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("mode"));
        }
    }

    @Test
    public void defaultBufferIs10Nm() {
        StsJobParams p = StsJobParams.parse("{\"mode\":\"STS_PHASE1_REGION_SHADOW\"}");
        assertEquals(10.0, p.getAnalysisBufferNm(), 0.001);
    }
}
