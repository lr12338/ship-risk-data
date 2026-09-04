package com.hifleet.sts.service;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.job.services.ShipBehaviorAnalysisService;
import com.hifleet.sts.domain.StsAlongsideHistoryJobParams;
import com.hifleet.sts.domain.StsRiskRegionRow;
import com.hifleet.sts.domain.StsSeedIdentityRow;
import com.hifleet.sts.domain.StsShadowEvent;
import com.hifleet.sts.repository.StsSeedIdentityRepository;
import com.hifleet.sts.repository.StsShadowEventRepository;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StsAlongsideHistoryServiceTest {

    @Test
    public void observedBoundsIntersectSliceAndDoNotInventSwitchDate() {
        StsSeedIdentityRow row = identity("9000001", "413000001");
        row.setFirstSeen("2024-01-02 00:00:00");
        row.setLastSeen("2024-01-03 00:00:00");
        StsAlongsideHistoryService.TimeBounds bounds =
                StsAlongsideHistoryService.intersectObservedBounds(row,
                        LocalDateTime.parse("2024-01-01T00:00:00"),
                        LocalDateTime.parse("2024-01-04T00:00:00"));
        assertEquals(LocalDateTime.parse("2024-01-02T00:00:00"), bounds.from);
        assertEquals(LocalDateTime.parse("2024-01-03T00:00:00"), bounds.to);

        row.setFirstSeen("2024-01-05 00:00:00");
        assertNull(StsAlongsideHistoryService.intersectObservedBounds(row,
                LocalDateTime.parse("2024-01-01T00:00:00"),
                LocalDateTime.parse("2024-01-04T00:00:00")));
    }

    @Test
    public void alongsideEventMapsToShadowCandidateAndDuplicateIsSkipped() {
        StsAlongsideHistoryService service = new StsAlongsideHistoryService();
        StsSeedIdentityRepository seed = mock(StsSeedIdentityRepository.class);
        StsShadowEventRepository shadow = mock(StsShadowEventRepository.class);
        ReflectionTestUtils.setField(service, "seedRepository", seed);
        ReflectionTestUtils.setField(service, "shadowRepository", shadow);
        when(seed.findImoByMmsi("seed", "413000002")).thenReturn("9000002");
        when(shadow.insertIfNoOverlappingPhysicalEvent(any(StsShadowEvent.class))).thenReturn(1, 0);

        StsAlongsideHistoryService.RunResult result = new StsAlongsideHistoryService.RunResult();
        service.persistCandidate(params(), regions(), "region-hash",
                identity("9000001", "413000003"), "9000001",
                new HashSet<String>(java.util.Arrays.asList("413000001", "413000003")), event(),
                "ALONGSIDE_RULE", "rule-hash", "code-sha", result);
        service.persistCandidate(params(), regions(), "region-hash",
                identity("9000001", "413000001"), "9000001",
                new HashSet<String>(Collections.singletonList("413000001")), event(),
                "ALONGSIDE_RULE", "rule-hash", "code-sha", result);

        ArgumentCaptor<StsShadowEvent> captured = ArgumentCaptor.forClass(StsShadowEvent.class);
        verify(shadow, org.mockito.Mockito.times(2)).insertIfNoOverlappingPhysicalEvent(captured.capture());
        assertEquals(captured.getAllValues().get(0).getEventKey(),
                captured.getAllValues().get(1).getEventKey());
        assertEquals("ALONGSIDE_DERIVED_STS_CANDIDATE", captured.getValue().getBehaviorType());
        assertEquals("ALONGSIDE_RULE", captured.getValue().getBaselineRule());
        assertTrue(captured.getValue().getScoreDetailJson().contains("rule-hash"));
        assertEquals(1, result.shadowInserted);
        assertEquals(1, result.shadowDuplicateSkipped);
    }

    @Test
    public void realRegionIdentityPolicyGatesWindowAndSameImoCandidate() {
        StsSeedIdentityRepository seed = mock(StsSeedIdentityRepository.class);
        when(seed.findImoByMmsi("seed", "413000002")).thenReturn("9000001");
        when(seed.findImoByMmsi("seed", "413000009")).thenReturn("9000009");
        StsAlongsideHistoryService.RegionIdentityPolicy policy =
                new StsAlongsideHistoryService.RegionIdentityPolicy(regions(),
                        new HashSet<String>(Collections.singletonList("413000001")),
                        "9000001", "seed", seed);

        assertTrue(policy.acceptWindow(null,
                Collections.singletonList(point("413000001", 55.0, 25.0))));
        assertFalse(policy.acceptWindow(null,
                Collections.singletonList(point("413000001", 10.0, 10.0))));
        assertFalse(policy.acceptCandidate("413000001"));
        assertFalse(policy.acceptCandidate("413000002"));
        assertTrue(policy.acceptCandidate("413000009"));
    }

    @Test
    public void processSlicePassesRealRegionIdentityPolicyToAlongsideCore() {
        StsAlongsideHistoryService service = new StsAlongsideHistoryService();
        StsSeedIdentityRepository seed = mock(StsSeedIdentityRepository.class);
        ShipBehaviorAnalysisService analysis = mock(ShipBehaviorAnalysisService.class);
        ReflectionTestUtils.setField(service, "seedRepository", seed);
        ReflectionTestUtils.setField(service, "analysisService", analysis);
        ShipBehaviorAnalysisService.AlongsideWindowResult empty =
                new ShipBehaviorAnalysisService.AlongsideWindowResult(
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        when(analysis.computeAlongsideHistory(anyString(), anyString(), anyString(), anyString(),
                any(ShipBehaviorAnalysisService.AlongsideComputePolicy.class))).thenReturn(empty);

        ReflectionTestUtils.invokeMethod(service, "processSlice", params(),
                Collections.singletonMap("9000001",
                        Collections.singletonList(identity("9000001", "413000001"))),
                regions(), "region-hash", "rule", "rule-hash", "code-sha",
                LocalDateTime.of(2024, 1, 1, 0, 0),
                LocalDateTime.of(2024, 1, 2, 0, 0),
                new StsAlongsideHistoryService.RunResult());

        ArgumentCaptor<ShipBehaviorAnalysisService.AlongsideComputePolicy> policy =
                ArgumentCaptor.forClass(ShipBehaviorAnalysisService.AlongsideComputePolicy.class);
        verify(analysis).computeAlongsideHistory(anyString(), anyString(), anyString(), anyString(),
                policy.capture());
        assertTrue(policy.getValue() instanceof StsAlongsideHistoryService.RegionIdentityPolicy);
        assertTrue(policy.getValue().acceptWindow(null,
                Collections.singletonList(point("413000001", 55.0, 25.0))));
        assertFalse(policy.getValue().acceptWindow(null,
                Collections.singletonList(point("413000001", 10.0, 10.0))));
    }

    @Test(expected = IllegalStateException.class)
    public void partialReplayResultFailsClosed() {
        ShipBehaviorAnalysisService.AlongsideWindowResult incomplete =
                new ShipBehaviorAnalysisService.AlongsideWindowResult(
                        2, 1, 1, 1, 1, 0, 1, 0, 0, 0, 0,
                        Collections.emptyList());
        StsAlongsideHistoryService.requireComplete("413000001", incomplete);
    }

    @Test(expected = IllegalStateException.class)
    public void meetingRegionSetRejectsMissingRegion() {
        StsAlongsideHistoryService.validateMeetingRegions(
                "STS_MEETING_PRIORITY_REGION_SET_V1",
                Collections.singletonList(regionRow("PERSIAN_GULF_TEST_V1", "20260902-v1")));
    }

    @Test
    public void runnerHasNoRegionFirstDiscoveryDependency() {
        for (java.lang.reflect.Field field : StsAlongsideHistoryService.class.getDeclaredFields()) {
            String type = field.getType().getName();
            assertFalse(type.contains("StsFleetRegionPreselector"));
            assertFalse(type.contains("RegionAisDiscovery"));
        }
    }

    @Test
    public void sameImoAlternateMmsiFailsClosedBeforeShadowWrite() {
        StsAlongsideHistoryService service = new StsAlongsideHistoryService();
        StsSeedIdentityRepository seed = mock(StsSeedIdentityRepository.class);
        StsShadowEventRepository shadow = mock(StsShadowEventRepository.class);
        ReflectionTestUtils.setField(service, "seedRepository", seed);
        ReflectionTestUtils.setField(service, "shadowRepository", shadow);
        when(seed.findImoByMmsi("seed", "413000002")).thenReturn("9000001");
        StsAlongsideHistoryService.RunResult result = new StsAlongsideHistoryService.RunResult();

        service.persistCandidate(params(), regions(), "region-hash",
                identity("9000001", "413000001"), "9000001",
                new HashSet<String>(java.util.Arrays.asList("413000001", "413000002")), event(),
                "ALONGSIDE_RULE", "rule-hash", "code-sha", result);

        assertEquals(1, result.selfPairEvents);
        verify(shadow, never()).insertIfNoOverlappingPhysicalEvent(any(StsShadowEvent.class));
    }

    @Test
    public void overlappingAliasDerivedEventIsNotInsertedTwice() {
        StsAlongsideHistoryService service = new StsAlongsideHistoryService();
        StsSeedIdentityRepository seed = mock(StsSeedIdentityRepository.class);
        StsShadowEventRepository shadow = mock(StsShadowEventRepository.class);
        ReflectionTestUtils.setField(service, "seedRepository", seed);
        ReflectionTestUtils.setField(service, "shadowRepository", shadow);
        when(seed.findImoByMmsi("seed", "413000002")).thenReturn("9000002");
        when(shadow.insertIfNoOverlappingPhysicalEvent(any(StsShadowEvent.class))).thenReturn(1, 0);
        StsAlongsideHistoryService.RunResult result = new StsAlongsideHistoryService.RunResult();

        service.persistCandidate(params(), regions(), "region-hash",
                identity("9000001", "413000001"), "9000001",
                new HashSet<String>(java.util.Arrays.asList("413000001", "413000003")), event(),
                "ALONGSIDE_RULE", "rule-hash", "code-sha", result);
        ShipBehaviorEvent shifted = event();
        shifted.setStartTime("2024-01-01 00:01:00");
        shifted.setEndTime("2024-01-01 00:31:00");
        service.persistCandidate(params(), regions(), "region-hash",
                identity("9000001", "413000003"), "9000001",
                new HashSet<String>(java.util.Arrays.asList("413000001", "413000003")), shifted,
                "ALONGSIDE_RULE", "rule-hash", "code-sha", result);

        verify(shadow, org.mockito.Mockito.times(2))
                .insertIfNoOverlappingPhysicalEvent(any(StsShadowEvent.class));
        assertEquals(1, result.shadowInserted);
        assertEquals(1, result.shadowDuplicateSkipped);
    }

    private StsAlongsideHistoryJobParams params() {
        return StsAlongsideHistoryJobParams.parse("{\"mode\":\"STS_ALONGSIDE_HISTORY_V1\","
                + "\"seedBatchNo\":\"seed\",\"regionSetCode\":\"regions\","
                + "\"from\":\"2024-01-01 00:00:00\",\"to\":\"2024-01-02 00:00:00\","
                + "\"batchNo\":\"batch\"}");
    }

    private StsRegionSetGeometry regions() {
        StsRiskRegionRow row = regionRow("TEST", "v1");
        row.setGeometryWkt("POLYGON((54 24,56 24,56 26,54 26,54 24))");
        row.setAnalysisBufferNm(1.0);
        return new StsRegionSetGeometry("regions", Collections.singletonList(row));
    }

    private StsRiskRegionRow regionRow(String code, String version) {
        StsRiskRegionRow row = new StsRiskRegionRow();
        row.setRegionCode(code); row.setRegionVersion(version); row.setPriority(1);
        return row;
    }

    private com.hifleet.trajectory.TrajectoryPoint point(String mmsi, double lon, double lat) {
        return new com.hifleet.trajectory.TrajectoryPoint(mmsi,
                LocalDateTime.of(2024, 1, 1, 0, 0), lon, lat, 1.5, 90.0, "0");
    }

    private StsSeedIdentityRow identity(String imo, String mmsi) {
        StsSeedIdentityRow row = new StsSeedIdentityRow();
        row.setImo(imo); row.setMmsi(mmsi); row.setIdentityMode("SINGLE_OBSERVED");
        return row;
    }

    private ShipBehaviorEvent event() {
        ShipBehaviorEvent event = new ShipBehaviorEvent();
        event.setMmsi("413000001"); event.setTargetMmsi("413000002");
        event.setStartTime("2024-01-01 00:00:00"); event.setEndTime("2024-01-01 00:30:00");
        event.setDurationMinutes(30L); event.setCenterLon(55.0); event.setCenterLat(25.0);
        event.setMinDistanceNm(0.05); event.setAvgDistanceNm(0.06);
        event.setAvgSpeed(1.5); event.setEventScore(60.0);
        return event;
    }
}
