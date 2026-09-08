package com.hifleet.job.services;

import com.hifleet.domain.AlongsideCandidateTarget;
import com.hifleet.domain.AlongsideReplaySearchResult;
import com.hifleet.service.ShipAlongsideDetector;
import com.hifleet.service.ShipAlongsideReplayCandidateProvider;
import com.hifleet.service.ShipTypeClassifier;
import com.hifleet.service.OwnShipEligibility;
import com.hifleet.service.AlongsideSceneService;
import com.hifleet.repository.AIS41GeneralQueryRepository;
import com.hifleet.repository.ShipBehaviorEventRepository;
import com.hifleet.repository.ShipBehaviorEventDetailRepository;
import com.hifleet.trajectory.TrajectoryLoaderService;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AlongsideComputePolicyTest {

    @Test
    public void defaultPolicyMatchesProductionWindowEventContract() {
        Fixture production = fixtureWithCandidate();
        Fixture extracted = fixtureWithCandidate();

        ShipBehaviorAnalysisService.AlongsideWindowResult before =
                production.service.analysisAlongsideProductionWindow(
                        "413000001", "2024-01-01 00:00:00", "2024-01-02 00:00:00", "batch");
        ShipBehaviorAnalysisService.AlongsideWindowResult after =
                extracted.service.computeAlongsideHistory(
                        "413000001", "2024-01-01 00:00:00", "2024-01-02 00:00:00", "batch", null);

        assertEquals(before.getAttemptedReplayQueries(), after.getAttemptedReplayQueries());
        assertEquals(before.getLowSpeedWindows(), after.getLowSpeedWindows());
        assertEquals(before.getCandidateTargets(), after.getCandidateTargets());
        assertEquals(before.getEvents(), after.getEvents());
        assertEquals(before.getTraces().size(), after.getTraces().size());
        assertEquals(1, after.getEvents());
        com.hifleet.domain.ShipBehaviorEvent expected = before.getTraces().get(0).getEvent();
        com.hifleet.domain.ShipBehaviorEvent actual = after.getTraces().get(0).getEvent();
        assertEquals(expected.getStartTime(), actual.getStartTime());
        assertEquals(expected.getEndTime(), actual.getEndTime());
        assertEquals(expected.getTargetMmsi(), actual.getTargetMmsi());
        assertEquals(expected.getMinDistanceNm(), actual.getMinDistanceNm());
        assertEquals(expected.getEventScore(), actual.getEventScore());
        assertEquals(expected.getEventSubType(), actual.getEventSubType());
        assertNotNull(actual.getRuleVersion());
        assertEquals("2024-01-01 00:00:00", actual.getStartTime());
        assertEquals("2024-01-01 00:30:00", actual.getEndTime());
        assertEquals("413000002", actual.getTargetMmsi());
        assertEquals(0.05437846722207226d, actual.getMinDistanceNm(), 0.000000000001d);
        assertEquals(60.0d, actual.getEventScore(), 0.0001d);
        assertEquals("ALONGSIDE_CONFIRMED_CANDIDATE", actual.getEventSubType());
        assertEquals("ALONGSIDE_TEST", actual.getRuleVersion());
        verify(extracted.events, never()).saveEvents(anyList());
        verify(extracted.details, never()).saveDetails(anyList());
    }

    @Test
    public void rejectedOwnWindowDoesNotReplay() {
        Fixture fixture = fixture();
        ShipBehaviorAnalysisService.AlongsideWindowResult result = fixture.service.computeAlongsideHistory(
                "413000001", "2024-01-01 00:00:00", "2024-01-02 00:00:00", "batch",
                new ShipBehaviorAnalysisService.AlongsideComputePolicy() {
                    @Override public boolean acceptWindow(ShipAlongsideDetector.LowSpeedWindow window,
                                                          java.util.List<TrajectoryPoint> points) { return false; }
                    @Override public boolean acceptCandidate(String targetMmsi) { return true; }
                });

        assertEquals(0, result.getLowSpeedWindows());
        verify(fixture.replay, never()).findCandidates(anyString(), anyList(), anyMap(), anyString(), anyBoolean());
        verify(fixture.events, never()).saveEvents(anyList());
        verify(fixture.details, never()).saveDetails(anyList());
    }

    @Test
    public void acceptedWindowReplaysButRejectedIdentityNeverReachesDetector() {
        Fixture fixture = fixture();
        AlongsideReplaySearchResult replayResult = new AlongsideReplaySearchResult();
        AlongsideCandidateTarget candidate = mock(AlongsideCandidateTarget.class);
        when(candidate.getTargetMmsi()).thenReturn("413000002");
        replayResult.addCandidate(candidate);
        when(fixture.replay.findCandidates(anyString(), anyList(), anyMap(), anyString(), anyBoolean()))
                .thenReturn(replayResult);

        ShipBehaviorAnalysisService.AlongsideWindowResult result = fixture.service.computeAlongsideHistory(
                "413000001", "2024-01-01 00:00:00", "2024-01-02 00:00:00", "batch",
                new ShipBehaviorAnalysisService.AlongsideComputePolicy() {
                    @Override public boolean acceptWindow(ShipAlongsideDetector.LowSpeedWindow window,
                                                          java.util.List<TrajectoryPoint> points) { return true; }
                    @Override public boolean acceptCandidate(String targetMmsi) { return false; }
                });

        assertEquals(1, result.getPolicyRejectedCandidates());
        assertEquals(0, result.getEvents());
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.service = new ShipBehaviorAnalysisService();
        fixture.loader = mock(TrajectoryLoaderService.class);
        fixture.replay = mock(ShipAlongsideReplayCandidateProvider.class);
        fixture.events = mock(ShipBehaviorEventRepository.class);
        fixture.details = mock(ShipBehaviorEventDetailRepository.class);
        ShipTypeClassifier classifier = mock(ShipTypeClassifier.class);
        AIS41GeneralQueryRepository ships = mock(AIS41GeneralQueryRepository.class);
        ReflectionTestUtils.setField(fixture.service, "trajectoryLoaderService", fixture.loader);
        ReflectionTestUtils.setField(fixture.service, "alongsideReplayCandidateProvider", fixture.replay);
        ReflectionTestUtils.setField(fixture.service, "shipTypeClassifier", classifier);
        ReflectionTestUtils.setField(fixture.service, "shipBehaviorEventRepository", fixture.events);
        ReflectionTestUtils.setField(fixture.service, "shipBehaviorEventDetailRepository", fixture.details);
        ReflectionTestUtils.setField(fixture.service, "ais41GeneralQueryRepository", ships);
        ReflectionTestUtils.setField(fixture.service, "alongsideCandidateMinDurationMinutes", 20L);
        ReflectionTestUtils.setField(fixture.service, "alongsideCandidateSplitGapMinutes", 30L);
        ReflectionTestUtils.setField(fixture.service, "alongsideCandidateMaxSpeed", 3.0d);
        ReflectionTestUtils.setField(fixture.service, "alongsideConfirmMinSpeed", 1.0d);
        ReflectionTestUtils.setField(fixture.service, "alongsideBatchTrajectoryLoadSliceHours", 24);
        ReflectionTestUtils.setField(fixture.service, "alongsideBatchMaxPointsPerWindow", 10000);
        ReflectionTestUtils.setField(fixture.service, "alongsideBatchMaxReplayQueriesPerWindow", 1000);
        ReflectionTestUtils.setField(fixture.service, "alongsideRuleVersion", "ALONGSIDE_TEST");
        when(classifier.classifyOwnShip(any())).thenReturn(
                OwnShipEligibility.ELIGIBLE_MERCHANT);
        when(ships.selectShipTypes(any(java.util.Set.class)))
                .thenReturn(new java.util.HashMap<String, String>());
        java.util.List<TrajectoryPoint> points = Arrays.asList(
                point("2024-01-01 00:00:00", 1.5), point("2024-01-01 00:30:00", 1.5));
        when(fixture.loader.loadSafely(anyString(), anyString(), anyString(), anyInt(),
                anyInt(), anyInt())).thenReturn(points);
        when(fixture.replay.planQueries(anyList(), anyMap(), anyString()))
                .thenReturn(new com.hifleet.service.AlongsideReplayQueryPlanner().plan(
                        null, null, "batch", 30, 1.0, 0.5, 100));
        when(fixture.replay.findCandidates(anyString(), anyList(), anyMap(), anyString(), anyBoolean()))
                .thenReturn(new AlongsideReplaySearchResult());
        return fixture;
    }

    private Fixture fixtureWithCandidate() {
        Fixture fixture = fixture();
        ShipTypeClassifier classifier = new ShipTypeClassifier();
        AIS41GeneralQueryRepository ships = mock(AIS41GeneralQueryRepository.class);
        AlongsideSceneService scenes = mock(AlongsideSceneService.class);
        ReflectionTestUtils.setField(fixture.service, "shipTypeClassifier", classifier);
        ReflectionTestUtils.setField(fixture.service, "ais41GeneralQueryRepository", ships);
        ReflectionTestUtils.setField(fixture.service, "alongsideSceneService", scenes);
        when(ships.selectShipTypes(any(java.util.Set.class))).thenReturn(
                new java.util.HashMap<String, String>() {{
                    put("413000001", "oil tanker"); put("413000002", "oil tanker");
                }});
        when(scenes.classify(anyDouble(), anyDouble()))
                .thenReturn(AlongsideSceneService.Scene.unknown());
        setRuleDefaults(fixture.service);

        java.util.List<TrajectoryPoint> own = points("413000001", 55.0000);
        java.util.List<TrajectoryPoint> target = points("413000002", 55.0010);
        when(fixture.loader.loadSafely(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(own);
        AlongsideCandidateTarget candidate = new AlongsideCandidateTarget();
        candidate.setOwnMmsi("413000001"); candidate.setTargetMmsi("413000002");
        candidate.setOwnWindowPoints(own); candidate.setTargetPoints(target);
        AlongsideReplaySearchResult replayResult = new AlongsideReplaySearchResult();
        replayResult.addCandidate(candidate);
        when(fixture.replay.findCandidates(anyString(), anyList(), anyMap(), anyString(), anyBoolean()))
                .thenReturn(replayResult);
        return fixture;
    }

    private void setRuleDefaults(ShipBehaviorAnalysisService service) {
        ReflectionTestUtils.setField(service, "codeCommitSha", "test-sha");
        ReflectionTestUtils.setField(service, "alongsideMaxDistanceNm", 0.1d);
        ReflectionTestUtils.setField(service, "alongsideConfirmMaxDistanceNm", 0.15d);
        ReflectionTestUtils.setField(service, "alongsideConfirmMaxSpeed", 2.0d);
        ReflectionTestUtils.setField(service, "alongsideConfirmMaxRelativeSpeed", 1.0d);
        ReflectionTestUtils.setField(service, "alongsideConfirmMaxTimeGapMinutes", 10L);
        ReflectionTestUtils.setField(service, "alongsideConfirmHighConfidenceTimeGapMinutes", 5L);
        ReflectionTestUtils.setField(service, "alongsideConfirmMinDurationMinutes", 30L);
        ReflectionTestUtils.setField(service, "alongsideConfirmMinPoints", 4);
        ReflectionTestUtils.setField(service, "alongsideMinDurationMinutes", 20L);
        ReflectionTestUtils.setField(service, "alongsideMinPoints", 3);
        ReflectionTestUtils.setField(service, "alongsideHeadingEnabled", false);
        ReflectionTestUtils.setField(service, "alongsideMinParallelRatio", 0.0d);
        ReflectionTestUtils.setField(service, "alongsideMaxDistanceVarianceNm2", 1.0d);
        ReflectionTestUtils.setField(service, "alongsideCrowdedVesselCount", 5);
        ReflectionTestUtils.setField(service, "alongsideMergeEnabled", true);
        ReflectionTestUtils.setField(service, "alongsideMergeMaxGapMinutes", 30L);
        ReflectionTestUtils.setField(service, "alongsideMergeMaxCenterDistanceNm", 1.0d);
        ReflectionTestUtils.setField(service, "alongsideSeverityCapNoSceneDataMaxLevel", "L2");
    }

    private java.util.List<TrajectoryPoint> points(String mmsi, double lon) {
        java.util.List<TrajectoryPoint> values = new java.util.ArrayList<TrajectoryPoint>();
        for (int minute : new int[]{0, 10, 20, 30}) {
            values.add(new TrajectoryPoint(mmsi,
                    LocalDateTime.of(2024, 1, 1, 0, minute), lon, 25.0,
                    Double.valueOf(1.5), Double.valueOf(90.0), Double.valueOf(90.0), "0"));
        }
        return values;
    }

    private TrajectoryPoint point(String time, double speed) {
        return new TrajectoryPoint("413000001",
                LocalDateTime.parse(time, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                55.0, 25.0, speed, 90.0, "0");
    }

    private static class Fixture {
        ShipBehaviorAnalysisService service;
        TrajectoryLoaderService loader;
        ShipAlongsideReplayCandidateProvider replay;
        ShipBehaviorEventRepository events;
        ShipBehaviorEventDetailRepository details;
    }
}
