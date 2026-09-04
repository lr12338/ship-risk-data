package com.hifleet.sts.service;

import com.hifleet.domain.ReplayQueryRequest;
import com.hifleet.domain.ReplayQueryResult;
import com.hifleet.regionbehavior.RegionAisDiscoveryService;
import com.hifleet.regionbehavior.RegionBehaviorConfig;
import com.hifleet.service.SpatialAisReplayClient;
import com.hifleet.sts.domain.StsRiskRegionRow;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StsFleetRegionPreselectorTest {

    @Test
    public void incompleteTerminalTruncationFailsPreselectionClosed() {
        RegionBehaviorConfig regionConfig = RegionBehaviorConfig.defaults();
        regionConfig.setMinTimeSliceMinutes(5);
        regionConfig.setMaxSpatialRefinementDepth(0);
        RegionAisDiscoveryService discovery = new RegionAisDiscoveryService(
                new SpatialAisReplayClient() {
                    @Override
                    public ReplayQueryResult query(ReplayQueryRequest request) {
                        ReplayQueryResult result = new ReplayQueryResult();
                        result.setSuccess(true);
                        result.setTruncated(true);
                        result.setRawPointCount(50000);
                        result.setTracksByMmsi(Collections.emptyMap());
                        return result;
                    }
                }, regionConfig);
        StsFleetRegionPreselector preselector = new StsFleetRegionPreselector();
        ReflectionTestUtils.setField(preselector, "discoveryService", discovery);
        ReflectionTestUtils.setField(preselector, "regionBehaviorConfig", regionConfig);
        ReflectionTestUtils.setField(preselector, "discoveryPlanner",
                new StsFleetDiscoveryPlanner(new StsFleetDiscoveryConfig()));

        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
        StsFleetRegionPreselector.PreselectResult result = preselector.preselect(
                Collections.singletonList(region()), from, from.plusMinutes(5),
                Collections.singleton("413000001"), "batch-a", "region-set-fp");

        assertFalse(result.complete);
        assertTrue(result.truncatedQueries > 0);
        assertTrue(result.errorMessage.contains("incomplete"));
    }

    @Test
    public void discoveryIdentityIsStableAndTargetSpecific() {
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
        String first = StsFleetRegionPreselector.discoveryBatchNo(
                "set-fp", from, from.plusDays(1), region(), "targets-a");
        String repeated = StsFleetRegionPreselector.discoveryBatchNo(
                "set-fp", from, from.plusDays(1), region(), "targets-a");
        String otherFleet = StsFleetRegionPreselector.discoveryBatchNo(
                "set-fp", from, from.plusDays(1), region(), "targets-b");

        assertTrue(first.startsWith("STS_PRESELECT_"));
        assertTrue(first.length() <= 64);
        assertTrue(first.equals(repeated));
        assertFalse(first.equals(otherFleet));
    }

    private StsRiskRegionRow region() {
        StsRiskRegionRow row = new StsRiskRegionRow();
        row.setRegionCode("CONTROL_STS_V1");
        row.setRegionVersion("v1");
        row.setGeometryWkt("POLYGON((121 30,122 30,122 31,121 31,121 30))");
        row.setAnalysisBufferNm(0);
        return row;
    }
}
