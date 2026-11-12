package com.hifleet.sts.service;

import com.hifleet.domain.CandidateFunnelStats;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

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
    public void autoContinueFalseRunsExactlyOneSlice() {
        CapturingService service = new CapturingService();
        SequenceRunner runner = new SequenceRunner(slice(1, 53, 1, "RUNNING", false),
                slice(2, 53, 2, "RUNNING", false));

        StsAlongsideHistoryService.RunResult result = service.runBatchInvocation(
                orchestrationParams(false, 3, 90), invocation(53, 0), runner, 0L);

        assertEquals(1, runner.calls);
        assertEquals(1, result.invocationSlices);
        assertEquals("SUCCESS", result.outcome);
        assertEquals("AUTO_CONTINUE_DISABLED", result.stopReason);
    }

    @Test
    public void autoContinueRunsFromThirdSliceAndCompletesMultipleSlices() {
        CapturingService service = new CapturingService();
        SequenceRunner runner = new SequenceRunner(
                slice(3, 4, 3, "RUNNING", false),
                slice(4, 4, 4, "COMPLETE", false));

        StsAlongsideHistoryService.RunResult result = service.runBatchInvocation(
                orchestrationParams(true, 8, 90), invocation(4, 2), runner, 0L);

        assertEquals(2, runner.calls);
        assertEquals(2, result.invocationSlices);
        assertEquals(4, result.completedTasks);
        assertEquals("SUCCESS_COMPLETE", result.outcome);
        assertTrue(service.contains("[STS][AUTO_CONTINUE]", "finishedSlice=3/4", "nextSlice=4/4"));
        assertTrue(service.contains("[STS][BATCH_COMPLETE]", "completedSlices=4/4"));
    }

    @Test
    public void maxSlicesStopsAfterExactlyThreeCompletedSlices() {
        CapturingService service = new CapturingService();
        SequenceRunner runner = new SequenceRunner(
                slice(1, 53, 1, "RUNNING", false),
                slice(2, 53, 2, "RUNNING", false),
                slice(3, 53, 3, "RUNNING", false),
                slice(4, 53, 4, "RUNNING", false));

        StsAlongsideHistoryService.RunResult result = service.runBatchInvocation(
                orchestrationParams(true, 3, 90), invocation(53, 0), runner, 0L);

        assertEquals(3, runner.calls);
        assertEquals("SUCCESS_PAUSED_LIMIT", result.outcome);
        assertEquals("MAX_SLICES", result.stopReason);
        assertEquals(50, result.totalTasks - result.completedTasks);
        assertTrue(service.contains("[STS][AUTO_CONTINUE_STOP]", "reason=MAX_SLICES"));
    }

    @Test
    public void maxRuntimeStopsOnlyAfterClaimedSliceCompletes() {
        CapturingService service = new CapturingService();
        service.now = 60_000L;
        SequenceRunner runner = new SequenceRunner(
                slice(1, 3, 1, "RUNNING", false),
                slice(2, 3, 2, "RUNNING", false));

        StsAlongsideHistoryService.RunResult result = service.runBatchInvocation(
                orchestrationParams(true, 8, 1), invocation(3, 0), runner, 0L);

        assertEquals(1, runner.calls);
        assertEquals(1, result.completedTasks);
        assertEquals("MAX_RUNTIME", result.stopReason);
        assertTrue(service.contains("[STS][AUTO_CONTINUE_STOP]", "reason=MAX_RUNTIME"));
    }

    @Test
    public void localWarningContinuesAndHistoricalWarningIsNotCleared() {
        CapturingService service = new CapturingService();
        StsAlongsideHistoryService.RunResult warning = slice(2, 3, 2, "RUNNING", true);
        warning.failedShips = 1;
        warning.batchFailedShips = 3;
        StsAlongsideHistoryService.RunResult complete = slice(3, 3, 3,
                "COMPLETE_WITH_WARNINGS", false);
        complete.warningTasks = 2;
        complete.batchFailedShips = 3;

        StsAlongsideHistoryService.RunResult result = service.runBatchInvocation(
                orchestrationParams(true, 8, 90), invocation(3, 1),
                new SequenceRunner(warning, complete), 0L);

        assertEquals("SUCCESS_WITH_WARNINGS_COMPLETE", result.outcome);
        assertTrue(result.partial);
        assertEquals(3, result.batchFailedShips);
        assertEquals(0, result.currentSliceFailedShips);
        assertTrue(result.summary.contains("batchAccumulatedFailedShips=3"));
    }

    @Test
    public void systemicFatalImmediatelyStopsWithoutClaimingNextSlice() {
        CapturingService service = new CapturingService();
        StsAlongsideHistoryService.SliceRunner runner = new StsAlongsideHistoryService.SliceRunner() {
            int calls;
            @Override public StsAlongsideHistoryService.RunResult run() {
                calls++;
                throw new StsAlongsideHistoryService.SystemicFatalException("WORLD_SEA", "unavailable");
            }
        };
        try {
            service.runBatchInvocation(orchestrationParams(true, 8, 90),
                    invocation(53, 2), runner, 0L);
            org.junit.Assert.fail("expected fatal");
        } catch (StsAlongsideHistoryService.SystemicFatalException ex) {
            assertEquals("WORLD_SEA", ex.stage);
        }
    }

    @Test
    public void completedWithWarningsResumeDoesNotRunAnotherSlice() {
        CapturingService service = new CapturingService();
        StsAlongsideHistoryService.RunResult none = invocation(53, 53);
        none.batchStatus = "COMPLETE_WITH_WARNINGS";
        none.warningTasks = 1;
        SequenceRunner runner = new SequenceRunner(none);

        StsAlongsideHistoryService.RunResult result = service.runBatchInvocation(
                orchestrationParams(true, 8, 90), invocation(53, 53), runner, 0L);

        assertEquals(1, runner.calls);
        assertEquals(0, result.invocationSlices);
        assertEquals("SUCCESS_WITH_WARNINGS_COMPLETE", result.outcome);
    }

    @Test
    public void invocationCountersAccumulateAcrossSlices() {
        CapturingService service = new CapturingService();
        StsAlongsideHistoryService.RunResult first = slice(1, 2, 1, "RUNNING", false);
        first.processedPhysicalShips = 786;
        first.physicalObserved = 26;
        first.serviceSuppressed = 14;
        first.weakSuppressed = 25;
        StsAlongsideHistoryService.RunResult second = slice(2, 2, 2, "COMPLETE", false);
        second.processedPhysicalShips = 786;
        second.physicalObserved = 10;

        StsAlongsideHistoryService.RunResult result = service.runBatchInvocation(
                orchestrationParams(true, 8, 90), invocation(2, 0),
                new SequenceRunner(first, second), 0L);

        assertEquals(1572, result.processedPhysicalShips);
        assertEquals(36, result.physicalObserved);
        assertEquals(14, result.serviceSuppressed);
        assertEquals(25, result.weakSuppressed);
    }

    @Test
    public void allFiftyThreeSlicesCompleteInOneFullContinuousInvocation() {
        CapturingService service = new CapturingService();
        StsAlongsideHistoryService.RunResult[] slices =
                new StsAlongsideHistoryService.RunResult[53];
        for (int i = 0; i < slices.length; i++) {
            slices[i] = slice(i + 1, 53, i + 1,
                    i == 52 ? "COMPLETE" : "RUNNING", false);
        }
        SequenceRunner runner = new SequenceRunner(slices);

        StsAlongsideHistoryService.RunResult result = service.runBatchInvocation(
                orchestrationParams(true, 53, 600), invocation(53, 0), runner, 0L);

        assertEquals(53, runner.calls);
        assertEquals(53, result.completedTasks);
        assertEquals("COMPLETE", result.batchStatus);
        assertEquals("SUCCESS_COMPLETE", result.outcome);
    }

    @Test
    public void invocationBoundsDoNotChangeBatchFingerprint() {
        StsAlongsideHistoryJobParams oneSlice = orchestrationParams(false, 1, 1);
        StsAlongsideHistoryJobParams continuous = orchestrationParams(true, 53, 600);

        assertEquals(StsAlongsideHistoryService.fingerprintConfig(
                        oneSlice, "rule", "rule-hash", "code-sha"),
                StsAlongsideHistoryService.fingerprintConfig(
                        continuous, "rule", "rule-hash", "code-sha"));
    }

    @Test
    public void replayFailureIsolatedAndProgressLogsAtFiftyAndFinal() {
        CapturingService service = new CapturingService();
        StsSeedIdentityRepository seed = mock(StsSeedIdentityRepository.class);
        ShipBehaviorAnalysisService analysis = mock(ShipBehaviorAnalysisService.class);
        ReflectionTestUtils.setField(service, "seedRepository", seed);
        ReflectionTestUtils.setField(service, "analysisService", analysis);
        Map<String, List<StsSeedIdentityRow>> byImo = new java.util.LinkedHashMap<String, List<StsSeedIdentityRow>>();
        for (int i = 1; i <= 51; i++) {
            StsSeedIdentityRow row = identity(String.valueOf(9000000 + i), String.valueOf(413000000 + i));
            byImo.put(row.getImo(), Collections.singletonList(row));
        }
        ShipBehaviorAnalysisService.AlongsideWindowResult ok = result(1, 0, 0, 0);
        ShipBehaviorAnalysisService.AlongsideWindowResult replayFailed = result(2, 1, 0, 0);
        when(analysis.computeAlongsideHistory(anyString(), anyString(), anyString(), anyString(),
                any(ShipBehaviorAnalysisService.AlongsideComputePolicy.class)))
                .thenReturn(ok, replayFailed, ok);
        StsAlongsideHistoryService.RunResult total = new StsAlongsideHistoryService.RunResult();
        total.totalTasks = 53;
        total.currentSliceNumber = 1;
        total.selectedPhysicalShips = 51;

        ReflectionTestUtils.invokeMethod(service, "processSlice", params(), byImo,
                regions(), "region-hash", "rule", "rule-hash", "code-sha",
                LocalDateTime.of(2025, 9, 2, 0, 0), LocalDateTime.of(2025, 9, 9, 0, 0), total);

        assertEquals(50, total.processedPhysicalShips);
        assertEquals(1, total.failedShips);
        assertEquals("9000002", total.shipFailures.get(0).imo);
        assertEquals("413000002", total.shipFailures.get(0).mmsi);
        assertEquals("REPLAY_QUERY", total.shipFailures.get(0).stage);
        assertTrue(service.contains("[STS][SHIP_FAILED]", "imo=9000002", "mmsi=413000002", "stage=REPLAY_QUERY"));
        assertTrue(service.contains("[STS][PROGRESS]", "processed=50/51"));
        assertTrue(service.contains("[STS][PROGRESS]", "processed=51/51"));
    }

    @Test
    public void unexpectedShipExceptionRemainsSystemicFatal() {
        CapturingService service = new CapturingService();
        ShipBehaviorAnalysisService analysis = mock(ShipBehaviorAnalysisService.class);
        ReflectionTestUtils.setField(service, "seedRepository", mock(StsSeedIdentityRepository.class));
        ReflectionTestUtils.setField(service, "analysisService", analysis);
        when(analysis.computeAlongsideHistory(anyString(), anyString(), anyString(), anyString(),
                any(ShipBehaviorAnalysisService.AlongsideComputePolicy.class)))
                .thenThrow(new IllegalStateException("world sea snapshot unavailable"));
        Map<String, List<StsSeedIdentityRow>> ships = Collections.singletonMap("9000001",
                Collections.singletonList(identity("9000001", "413000001")));

        try {
            ReflectionTestUtils.invokeMethod(service, "processSlice", params(), ships,
                    regions(), "region-hash", "rule", "rule-hash", "code-sha",
                    LocalDateTime.of(2025, 9, 2, 0, 0), LocalDateTime.of(2025, 9, 9, 0, 0),
                    new StsAlongsideHistoryService.RunResult());
            org.junit.Assert.fail("expected systemic fatal");
        } catch (StsAlongsideHistoryService.SystemicFatalException ex) {
            assertEquals("UNKNOWN", ex.stage);
            assertTrue(ex.getMessage().contains("world sea snapshot unavailable"));
        }
    }

    @Test
    public void sliceClaimedLogUsesOneBasedSliceAndTotal() {
        StsAlongsideHistoryService.RunResult result = new StsAlongsideHistoryService.RunResult();
        result.currentSliceNumber = 1;
        result.totalTasks = 53;
        result.selectedPhysicalShips = 786;

        String message = StsAlongsideHistoryService.formatSliceClaimed("batch", result,
                LocalDateTime.of(2025, 9, 2, 0, 0), LocalDateTime.of(2025, 9, 9, 0, 0));

        assertTrue(message.contains("slice=1/53"));
        assertTrue(message.contains("from=2025-09-02 00:00:00"));
        assertTrue(message.contains("to=2025-09-09 00:00:00"));
        assertTrue(message.contains("ships=786"));
    }

    @Test
    public void sliceSummaryReportsPhysicalClassificationAndPartialCounters() {
        CapturingService service = new CapturingService();
        StsAlongsideHistoryService.RunResult result = new StsAlongsideHistoryService.RunResult();
        result.currentSliceNumber = 1;
        result.totalTasks = 53;
        result.processedPhysicalShips = 784;
        result.failedShips = 2;
        result.partial = true;
        result.physicalObserved = 26;
        result.physicalFormal = 1;
        result.physicalWeak = 25;
        result.stsCandidates = 0;
        result.stsReviewRequired = 0;
        result.serviceSuppressed = 14;
        result.weakSuppressed = 25;
        result.shadowInserted = 26;

        ReflectionTestUtils.invokeMethod(service, "logSliceSummary", result);

        assertTrue(service.contains("[STS][SLICE_SUMMARY]", "slice=1/53",
                "processedShips=786", "completedShips=784", "failedShips=2", "partial=true",
                "physicalObserved=26", "physicalFormal=1", "physicalWeak=25",
                "stsCandidates=0", "stsReviewRequired=0", "serviceSuppressed=14",
                "weakSuppressed=25", "shadowInserted=26"));
    }

    @Test
    public void candidateFunnelLogUsesExplicitUnitsAndBoundedSamples() {
        CapturingService service = new CapturingService();
        StsAlongsideHistoryService.RunResult result = new StsAlongsideHistoryService.RunResult();
        result.currentSliceNumber = 1;
        result.totalTasks = 1;
        result.candidateFunnel.recordReplayQuery();
        result.candidateFunnel.addReplaySuccessfulQueries(1);
        result.candidateFunnel.addReplayPoints(10, 8, 2, 3);
        for (int i = 0; i < 5; i++) {
            CandidateFunnelStats ship = new CandidateFunnelStats();
            ship.recordTargetTrackGrouped();
            ship.recordTargetTrackEvaluated(2);
            ship.recordRejection(CandidateFunnelStats.RejectionReason.TOO_FEW_POINTS,
                    "ownMmsi=own" + i + " targetMmsi=target" + i + " targetPoints=2");
            result.candidateFunnel.add(ship);
        }

        ReflectionTestUtils.invokeMethod(service, "logCandidateFunnel", result);

        assertTrue(service.contains("[STS][CANDIDATE_FUNNEL]", "slice=1/1",
                "replayQueries=1", "replayRawPoints=10", "replayValidPoints=8",
                "replayInvalidPoints=2", "replayPointsOutsideRequestedTime=3",
                "targetTracksEvaluated=5", "tooFewPoints=5",
                "terminalInvariant=true", "groupingInvariant=true"));
        assertEquals(3, service.countContaining("[STS][CANDIDATE_FUNNEL_SAMPLE]",
                "reason=TOO_FEW_POINTS"));
    }

    @Test
    public void taskInitializationPreservesShanghaiWallClockAndExclusiveEnd() {
        java.util.TimeZone previous = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
            StsBackfillPlanner planner = new StsBackfillPlanner("time-test", "2024-09-02 00:00:00", "2026-09-03 00:00:00", 7);
            java.util.List<com.hifleet.sts.domain.StsBackfillTaskRow> tasks = StsAlongsideHistoryService.planHistoricalTasks(planner);
            assertEquals(105, tasks.size());
            assertEquals("2024-09-02 00:00:00.0", new java.sql.Timestamp(tasks.get(0).getSliceFrom().getTime()).toString());
            assertEquals("2026-09-03 00:00:00.0", new java.sql.Timestamp(tasks.get(tasks.size()-1).getSliceTo().getTime()).toString());
            for (int i=1; i<tasks.size(); i++) assertEquals(tasks.get(i-1).getSliceTo(), tasks.get(i).getSliceFrom());
        } finally { java.util.TimeZone.setDefault(previous); }
    }

    @Test
    public void candidateIdentityValidationPreservesDisabledAndUnknownAliases() {
        StsSeedIdentityRow a = seedIdentity("seed", 1, "9212929", "370969000");
        StsSeedIdentityRow b = seedIdentity("seed", 1, "9212929", "422222000");
        a.setMinotype("原油船"); a.setEnabled(1);
        b.setMinotype("渔船"); b.setEnabled(0);
        StsSeedIdentityRow unknown = seedIdentity("seed", 2, "9088689", "538006511");
        unknown.setEnabled(0);
        StsAlongsideHistoryService.validateSeed("seed", java.util.Arrays.asList(a, b, unknown));
        assertEquals(0, b.getEnabled());
        assertNull(unknown.getMinotype());
    }

    @Test
    public void legacyAndValidMixedSeedAreAcceptedAfterRepositoryLookup() {
        StsAlongsideHistoryService service = new StsAlongsideHistoryService();
        StsSeedIdentityRepository seed = mock(StsSeedIdentityRepository.class);
        ReflectionTestUtils.setField(service, "seedRepository", seed);
        List<StsSeedIdentityRow> legacy = Collections.singletonList(seedIdentity(
                StsAlongsideHistoryService.LEGACY_SEED_BATCH_NO, 1, "9000001", "413000001"));
        List<StsSeedIdentityRow> mixed = Arrays.asList(
                seedIdentity("sts-mixed-5000-identity-20260911-v1", 1, "9000001", "413000001"),
                seedIdentity("sts-mixed-5000-identity-20260911-v1", 2, "9000002", "413000002"));
        when(seed.selectHistoricalCandidateRows(StsAlongsideHistoryService.LEGACY_SEED_BATCH_NO))
                .thenReturn(legacy);
        when(seed.selectHistoricalCandidateRows("sts-mixed-5000-identity-20260911-v1"))
                .thenReturn(mixed);

        assertEquals(legacy, service.loadValidatedSeed(StsAlongsideHistoryService.LEGACY_SEED_BATCH_NO));
        assertEquals(mixed, service.loadValidatedSeed("sts-mixed-5000-identity-20260911-v1"));
    }

    @Test(expected = IllegalStateException.class)
    public void nonexistentSeedIsRejected() {
        StsAlongsideHistoryService service = new StsAlongsideHistoryService();
        StsSeedIdentityRepository seed = mock(StsSeedIdentityRepository.class);
        ReflectionTestUtils.setField(service, "seedRepository", seed);
        when(seed.selectHistoricalCandidateRows("missing")).thenReturn(Collections.<StsSeedIdentityRow>emptyList());
        service.loadValidatedSeed("missing");
    }

    @Test(expected = IllegalStateException.class)
    public void mismatchedSeedBatchIsRejected() {
        StsAlongsideHistoryService.validateSeed("requested", Collections.singletonList(
                seedIdentity("different", 1, "9000001", "413000001")));
    }

    @Test(expected = IllegalStateException.class)
    public void malformedImoIsRejected() {
        StsAlongsideHistoryService.validateSeed("seed", Collections.singletonList(
                seedIdentity("seed", 1, "IMO1234", "413000001")));
    }

    @Test(expected = IllegalStateException.class)
    public void malformedMmsiIsRejected() {
        StsAlongsideHistoryService.validateSeed("seed", Collections.singletonList(
                seedIdentity("seed", 1, "9000001", "123")));
    }

    @Test
    public void sameImoAliasesAreOneCanonicalOwn() {
        List<StsSeedIdentityRow> aliases = Arrays.asList(
                seedIdentity("seed", 1, "9000001", "413000001"),
                seedIdentity("seed", 1, "9000001", "413000002"));
        StsAlongsideHistoryService.validateSeed("seed", aliases);
        assertEquals(Collections.singletonList("9000001"),
                StsAlongsideHistoryService.canonicalImos(aliases));
    }

    @Test(expected = IllegalStateException.class)
    public void sameImoDifferentOrdinalIsRejected() {
        StsAlongsideHistoryService.validateSeed("seed", Arrays.asList(
                seedIdentity("seed", 1, "9000001", "413000001"),
                seedIdentity("seed", 2, "9000001", "413000002")));
    }

    @Test(expected = IllegalStateException.class)
    public void differentImoSameOrdinalIsRejected() {
        StsAlongsideHistoryService.validateSeed("seed", Arrays.asList(
                seedIdentity("seed", 1, "9000001", "413000001"),
                seedIdentity("seed", 1, "9000002", "413000002")));
    }

    @Test(expected = IllegalStateException.class)
    public void nonPositiveOrdinalIsRejected() {
        StsAlongsideHistoryService.validateSeed("seed", Collections.singletonList(
                seedIdentity("seed", 0, "9000001", "413000001")));
    }

    @Test(expected = IllegalStateException.class)
    public void unstableSeedOrderingIsRejected() {
        StsAlongsideHistoryService.validateSeed("seed", Arrays.asList(
                seedIdentity("seed", 2, "9000002", "413000002"),
                seedIdentity("seed", 1, "9000001", "413000001")));
    }

    @Test
    public void runtimeCountUsesActualBatchWithoutPermanentFleetCount() {
        java.util.List<String> available = new java.util.ArrayList<String>();
        for (int i=0; i<900; i++) available.add(String.valueOf(1000000+i));
        for (int count : new int[]{1,20,100,786,900}) {
            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("mode", "STS_ALONGSIDE_HISTORY_V1"); json.put("seedBatchNo", "seed");
            json.put("regionSetCode", "regions"); json.put("from", "2024-09-02 00:00:00");
            json.put("to", "2026-09-03 00:00:00"); json.put("sliceDays", 7);
            json.put("batchNo", "batch"); json.put("shipCount", count);
            StsAlongsideHistoryJobParams p = StsAlongsideHistoryJobParams.parse(json.toJSONString());
            p.validate();
            assertEquals(available.subList(0,count), StsAlongsideHistoryService.selectImos(p,available));
        }
    }

    @Test(expected = IllegalStateException.class)
    public void ambiguousIdentityStillFailsClosed() {
        StsAlongsideHistoryService.validateSeed("seed", java.util.Arrays.asList(
                seedIdentity("seed", 1, "9000001", "413000001"),
                seedIdentity("seed", 2, "9000002", "413000001")));
    }

    @Test
    public void shipOffsetAndCountOperateOnCanonicalImos() {
        List<StsSeedIdentityRow> rows = Arrays.asList(
                seedIdentity("seed", 1, "9000001", "413000001"),
                seedIdentity("seed", 1, "9000001", "413000002"),
                seedIdentity("seed", 2, "9000002", "413000003"),
                seedIdentity("seed", 3, "9000003", "413000004"));
        StsAlongsideHistoryJobParams p = StsAlongsideHistoryJobParams.parse(
                "{\"mode\":\"STS_ALONGSIDE_HISTORY_V1\",\"seedBatchNo\":\"seed\","
                        + "\"regionSetCode\":\"regions\",\"shipOffset\":1,\"shipCount\":2,"
                        + "\"from\":\"2024-01-01 00:00:00\",\"to\":\"2024-01-02 00:00:00\","
                        + "\"batchNo\":\"batch\"}");

        assertEquals(Arrays.asList("9000002", "9000003"),
                StsAlongsideHistoryService.selectImos(p, StsAlongsideHistoryService.canonicalImos(rows)));
    }

    @Test
    public void newSeedFingerprintBindsBatchAliasesAndOrdinal() {
        List<StsSeedIdentityRow> base = Collections.singletonList(
                seedIdentity("new-seed", 1, "9000001", "413000001"));
        List<StsSeedIdentityRow> aliasChanged = Arrays.asList(
                seedIdentity("new-seed", 1, "9000001", "413000001"),
                seedIdentity("new-seed", 1, "9000001", "413000002"));
        List<StsSeedIdentityRow> ordinalChanged = Collections.singletonList(
                seedIdentity("new-seed", 2, "9000001", "413000001"));
        String fingerprint = StsAlongsideHistoryService.fingerprintSeedIdentities("new-seed", base);

        assertFalse(fingerprint.equals(StsAlongsideHistoryService.fingerprintSeedIdentities(
                "another-seed", base)));
        assertFalse(fingerprint.equals(StsAlongsideHistoryService.fingerprintSeedIdentities(
                "new-seed", aliasChanged)));
        assertFalse(fingerprint.equals(StsAlongsideHistoryService.fingerprintSeedIdentities(
                "new-seed", ordinalChanged)));
    }

    @Test
    public void legacySeedFingerprintRemainsCompatible() {
        List<StsSeedIdentityRow> rows = Arrays.asList(
                seedIdentity(StsAlongsideHistoryService.LEGACY_SEED_BATCH_NO,
                        1, "9000001", "413000001"),
                seedIdentity(StsAlongsideHistoryService.LEGACY_SEED_BATCH_NO,
                        2, "9000002", "413000002"));
        assertEquals(StsAlongsideHistoryService.fingerprintIdentities(rows),
                StsAlongsideHistoryService.fingerprintSeedIdentities(
                        StsAlongsideHistoryService.LEGACY_SEED_BATCH_NO, rows));
    }

    @Test
    public void outsideRegionPersistsAndRegionOnlyChangesPriority() {
        StsAlongsideHistoryService service = new StsAlongsideHistoryService();
        StsSeedIdentityRepository seed = mock(StsSeedIdentityRepository.class);
        StsShadowEventRepository shadow = mock(StsShadowEventRepository.class);
        ReflectionTestUtils.setField(service, "seedRepository", seed);
        ReflectionTestUtils.setField(service, "shadowRepository", shadow);
        ShipBehaviorEvent outside = event(); outside.setCenterLon(10.0); outside.setCenterLat(10.0);
        outside.setScoreDetailJson("{\"targetShipTypeRaw\":\"Oil tanker\",\"sceneType\":\"UNKNOWN\",\"behaviorConfidence\":\"MEDIUM\"}");
        service.persistCandidate(params(), regions(), "r", identity("9000001", "413000001"), "9000001",
                Collections.singleton("413000001"), outside, "rule", "hash", "sha", new StsAlongsideHistoryService.RunResult());
        ShipBehaviorEvent inside = event(); inside.setScoreDetailJson(outside.getScoreDetailJson());
        service.persistCandidate(params(), regions(), "r", identity("9000001", "413000001"), "9000001",
                Collections.singleton("413000001"), inside, "rule", "hash", "sha", new StsAlongsideHistoryService.RunResult());
        ArgumentCaptor<StsShadowEvent> captured = ArgumentCaptor.forClass(StsShadowEvent.class);
        verify(shadow, org.mockito.Mockito.times(2)).insertIfNoOverlappingPhysicalEvent(captured.capture());
        com.alibaba.fastjson.JSONObject first = com.alibaba.fastjson.JSONObject.parseObject(captured.getAllValues().get(0).getScoreDetailJson());
        com.alibaba.fastjson.JSONObject second = com.alibaba.fastjson.JSONObject.parseObject(captured.getAllValues().get(1).getScoreDetailJson());
        assertEquals("NORMAL", first.getString("reviewPriority"));
        assertEquals("ELEVATED", second.getString("reviewPriority"));
        assertEquals(first.getString("stsClassification"), second.getString("stsClassification"));
        assertEquals("MEDIUM", second.getString("behaviorConfidence"));
        assertEquals(captured.getAllValues().get(0).getEventKey(), captured.getAllValues().get(1).getEventKey());
    }

    @Test
    public void reversedOwnTargetDirectionSharesCanonicalKey() {
        StsAlongsideHistoryService service = new StsAlongsideHistoryService();
        StsSeedIdentityRepository seed = mock(StsSeedIdentityRepository.class);
        StsShadowEventRepository shadow = mock(StsShadowEventRepository.class);
        ReflectionTestUtils.setField(service, "seedRepository", seed);
        ReflectionTestUtils.setField(service, "shadowRepository", shadow);
        when(seed.findHistoricalImoByMmsi("seed", "413000002")).thenReturn("9000002");
        when(seed.findHistoricalImoByMmsi("seed", "413000001")).thenReturn("9000001");
        service.persistCandidate(params(), regions(), "r", identity("9000001", "413000001"), "9000001",
                Collections.singleton("413000001"), event(), "rule", "hash", "sha", new StsAlongsideHistoryService.RunResult());
        ShipBehaviorEvent reverse = event(); reverse.setTargetMmsi("413000001");
        service.persistCandidate(params(), regions(), "r", identity("9000002", "413000002"), "9000002",
                Collections.singleton("413000002"), reverse, "rule", "hash", "sha", new StsAlongsideHistoryService.RunResult());
        ArgumentCaptor<StsShadowEvent> captured = ArgumentCaptor.forClass(StsShadowEvent.class);
        verify(shadow, org.mockito.Mockito.times(2)).insertIfNoOverlappingPhysicalEvent(captured.capture());
        assertEquals(captured.getAllValues().get(0).getEventKey(), captured.getAllValues().get(1).getEventKey());
        assertEquals("9000002", captured.getAllValues().get(1).getOwnImo());
    }

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
        when(seed.findHistoricalImoByMmsi("seed", "413000002")).thenReturn("9000002");
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
        assertEquals("OBSERVED_ALONGSIDE", captured.getValue().getBehaviorType());
        assertEquals("ALONGSIDE_RULE", captured.getValue().getBaselineRule());
        assertTrue(captured.getValue().getScoreDetailJson().contains("rule-hash"));
        assertEquals(1, result.shadowInserted);
        assertEquals(1, result.shadowDuplicateSkipped);
    }

    @Test
    public void servicePhysicalEvidenceRemainsTraceableOutsideReviewQueue() {
        StsAlongsideHistoryService service = new StsAlongsideHistoryService();
        StsSeedIdentityRepository seed = mock(StsSeedIdentityRepository.class);
        StsShadowEventRepository shadow = mock(StsShadowEventRepository.class);
        ReflectionTestUtils.setField(service, "seedRepository", seed);
        ReflectionTestUtils.setField(service, "shadowRepository", shadow);
        when(seed.historicalTargetMetadata("413000002"))
                .thenReturn(metadata("Tug", "拖轮", "9000002"));
        when(shadow.insertIfNoOverlappingPhysicalEvent(any(StsShadowEvent.class))).thenReturn(1);

        StsAlongsideHistoryService.RunResult result = new StsAlongsideHistoryService.RunResult();
        service.persistCandidate(params(), regions(), "region-hash",
                identity("9000001", "413000001"), "9000001",
                Collections.singleton("413000001"), event(),
                "ALONGSIDE_RULE", "rule-hash", "code-sha", result);

        ArgumentCaptor<StsShadowEvent> captured = ArgumentCaptor.forClass(StsShadowEvent.class);
        verify(shadow).insertIfNoOverlappingPhysicalEvent(captured.capture());
        StsShadowEvent row = captured.getValue();
        com.alibaba.fastjson.JSONObject evidence = com.alibaba.fastjson.JSONObject.parseObject(row.getScoreDetailJson());
        assertEquals("OBSERVED_ALONGSIDE", row.getBehaviorType());
        assertEquals("NOT_REQUIRED", row.getReviewStatus());
        assertEquals("PHYSICAL_ONLY", evidence.getString("stsClassification"));
        assertEquals("FORMAL", evidence.getString("physicalSupportLevel"));
        assertEquals("NOT_PROMOTED", evidence.getString("stsPromotionStatus"));
        assertTrue(evidence.getJSONArray("contextEvidence").contains("SERVICE_INTERACTION"));
        assertEquals(1, result.physicalObserved);
        assertEquals(1, result.physicalFormal);
        assertEquals(1, result.serviceSuppressed);
        assertEquals(0, result.stsReviewRequired);
    }

    @Test
    public void classificationCountersSeparatePhysicalFromPromotions() {
        StsAlongsideHistoryService service = new StsAlongsideHistoryService();
        StsSeedIdentityRepository seed = mock(StsSeedIdentityRepository.class);
        StsShadowEventRepository shadow = mock(StsShadowEventRepository.class);
        ReflectionTestUtils.setField(service, "seedRepository", seed);
        ReflectionTestUtils.setField(service, "shadowRepository", shadow);
        when(seed.historicalTargetMetadata(anyString())).thenReturn(
                metadata("Oil tanker", "原油船", "9000002"),
                Collections.<String, Object>emptyMap(),
                metadata("Oil tanker", "原油船", "9000003"),
                metadata("Oil tanker", "原油船", "9000004"));
        when(shadow.insertIfNoOverlappingPhysicalEvent(any(StsShadowEvent.class))).thenReturn(1);
        StsAlongsideHistoryService.RunResult result = new StsAlongsideHistoryService.RunResult();

        persist(service, result, event(), "413000002");
        persist(service, result, event(), "413000003");
        ShipBehaviorEvent weak = event();
        weak.setScoreDetailJson("{\"weakCandidate\":true}");
        persist(service, result, weak, "413000004");
        ShipBehaviorEvent port = event();
        port.setScoreDetailJson("{\"sceneDataAvailable\":true,\"sceneType\":\"PORT_OPERATIONAL\","
                + "\"behaviorMode\":\"STATIONARY_SIDE_BY_SIDE_REVIEW\"}");
        persist(service, result, port, "413000005");

        assertEquals(4, result.physicalObserved);
        assertEquals(3, result.physicalFormal);
        assertEquals(1, result.physicalWeak);
        assertEquals(1, result.stsCandidates);
        assertEquals(1, result.stsReviewRequired);
        assertEquals(1, result.weakSuppressed);
        assertEquals(1, result.portBerthSuppressed);
    }

    @Test
    public void realRegionIdentityPolicyGatesWindowAndSameImoCandidate() {
        StsSeedIdentityRepository seed = mock(StsSeedIdentityRepository.class);
        when(seed.findHistoricalImoByMmsi("seed", "413000002")).thenReturn("9000001");
        when(seed.findHistoricalImoByMmsi("seed", "413000009")).thenReturn("9000009");
        StsAlongsideHistoryService.RegionIdentityPolicy policy =
                new StsAlongsideHistoryService.RegionIdentityPolicy(regions(),
                        new HashSet<String>(Collections.singletonList("413000001")),
                        "9000001", "seed", seed);

        assertTrue(policy.acceptWindow(null,
                Collections.singletonList(point("413000001", 55.0, 25.0))));
        assertTrue(policy.acceptWindow(null,
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
        assertTrue(policy.getValue().acceptWindow(null,
                Collections.singletonList(point("413000001", 10.0, 10.0))));
        assertTrue(policy.getValue().ownsEligibilityDecision());
    }

    @Test
    public void bothDisabledAndEnabledHistoricalAliasesReachCore() {
        StsAlongsideHistoryService service = new StsAlongsideHistoryService();
        ShipBehaviorAnalysisService analysis = mock(ShipBehaviorAnalysisService.class);
        ReflectionTestUtils.setField(service, "analysisService", analysis);
        ReflectionTestUtils.setField(service, "seedRepository", mock(StsSeedIdentityRepository.class));
        when(analysis.computeAlongsideHistory(anyString(), anyString(), anyString(), anyString(),
                any(ShipBehaviorAnalysisService.AlongsideComputePolicy.class)))
                .thenReturn(new ShipBehaviorAnalysisService.AlongsideWindowResult(0,0,0,0,0,0,0,0,0,0));
        StsSeedIdentityRow a = identity("9212929", "370969000"); a.setEnabled(1); a.setMinotype("原油船");
        StsSeedIdentityRow b = identity("9212929", "422222000"); b.setEnabled(0); b.setMinotype("渔船");
        ReflectionTestUtils.invokeMethod(service, "processSlice", params(),
                Collections.singletonMap("9212929", java.util.Arrays.asList(a,b)), regions(), "r", "rule", "hash", "sha",
                LocalDateTime.of(2024,1,1,0,0), LocalDateTime.of(2024,1,2,0,0), new StsAlongsideHistoryService.RunResult());
        ArgumentCaptor<String> mmsi = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ShipBehaviorAnalysisService.AlongsideComputePolicy> policy = ArgumentCaptor.forClass(ShipBehaviorAnalysisService.AlongsideComputePolicy.class);
        verify(analysis, org.mockito.Mockito.times(2)).computeAlongsideHistory(mmsi.capture(), anyString(), anyString(), anyString(), policy.capture());
        assertEquals(java.util.Arrays.asList("370969000","422222000"), mmsi.getAllValues());
        for (ShipBehaviorAnalysisService.AlongsideComputePolicy p : policy.getAllValues()) {
            assertFalse(p.acceptCandidate("370969000")); assertFalse(p.acceptCandidate("422222000"));
        }
    }

    @Test(expected = IllegalStateException.class)
    public void partialReplayResultFailsClosed() {
        ShipBehaviorAnalysisService.AlongsideWindowResult incomplete =
                new ShipBehaviorAnalysisService.AlongsideWindowResult(
                        2, 1, 1, 1, 1, 0, 1, 0, 0, 0, 0,
                        Collections.emptyList());
        StsAlongsideHistoryService.requireComplete("413000001", incomplete);
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
        when(seed.findHistoricalImoByMmsi("seed", "413000002")).thenReturn("9000001");
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
        when(seed.findHistoricalImoByMmsi("seed", "413000002")).thenReturn("9000002");
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

    private StsAlongsideHistoryJobParams orchestrationParams(boolean autoContinue,
                                                              int maxSlices, int maxRuntime) {
        return StsAlongsideHistoryJobParams.parse("{\"mode\":\"STS_ALONGSIDE_HISTORY_V1\","
                + "\"seedBatchNo\":\"seed\",\"regionSetCode\":\"regions\","
                + "\"from\":\"2024-01-01 00:00:00\",\"to\":\"2024-01-02 00:00:00\","
                + "\"batchNo\":\"batch\",\"autoContinue\":" + autoContinue + ","
                + "\"maxSlicesPerInvocation\":" + maxSlices + ","
                + "\"maxRuntimeMinutes\":" + maxRuntime + "}");
    }

    private StsAlongsideHistoryService.RunResult invocation(int total, int completed) {
        StsAlongsideHistoryService.RunResult result = new StsAlongsideHistoryService.RunResult();
        result.batchNo = "batch";
        result.totalTasks = total;
        result.completedTasks = completed;
        result.batchStatus = completed == total ? "COMPLETE" : "RUNNING";
        return result;
    }

    private StsAlongsideHistoryService.RunResult slice(int number, int total, int completed,
                                                        String status, boolean partial) {
        StsAlongsideHistoryService.RunResult result = invocation(total, completed);
        result.currentTaskId = (long) number;
        result.currentSliceNumber = number;
        result.batchStatus = status;
        result.success = true;
        result.partial = partial;
        return result;
    }

    private void persist(StsAlongsideHistoryService service,
                         StsAlongsideHistoryService.RunResult result,
                         ShipBehaviorEvent event, String targetMmsi) {
        event.setTargetMmsi(targetMmsi);
        service.persistCandidate(params(), regions(), "region-hash",
                identity("9000001", "413000001"), "9000001",
                Collections.singleton("413000001"), event,
                "ALONGSIDE_RULE", "rule-hash", "code-sha", result);
    }

    private Map<String, Object> metadata(String type, String minotype, String imo) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("type", type);
        result.put("minotype", minotype);
        result.put("imonumber", imo);
        return result;
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

    private StsSeedIdentityRow seedIdentity(String batch, int ordinal, String imo, String mmsi) {
        StsSeedIdentityRow row = identity(imo, mmsi);
        row.setSeedBatchNo(batch);
        row.setShipOrdinal(ordinal);
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

    private ShipBehaviorAnalysisService.AlongsideWindowResult result(
            int replayQueries, int replayFailures, int replayTruncated, int candidateFailures) {
        return new ShipBehaviorAnalysisService.AlongsideWindowResult(
                10, 1, 0, 0, replayQueries, replayFailures, replayTruncated,
                0, 0, 0, candidateFailures, Collections.<com.hifleet.domain.ShipBehaviorTrace>emptyList());
    }

    private static class CapturingService extends StsAlongsideHistoryService {
        private final List<String> messages = new ArrayList<String>();
        private long now;

        @Override
        protected void progressLog(String message) {
            messages.add(message);
        }

        @Override
        protected long currentTimeMillis() {
            return now;
        }

        boolean contains(String... fragments) {
            for (String message : messages) {
                boolean match = true;
                for (String fragment : fragments) match &= message.contains(fragment);
                if (match) return true;
            }
            return false;
        }


        int countContaining(String... fragments) {
            int count = 0;
            for (String message : messages) {
                boolean match = true;
                for (String fragment : fragments) match &= message.contains(fragment);
                if (match) count++;
            }
            return count;
        }
    }

    private static class SequenceRunner implements StsAlongsideHistoryService.SliceRunner {
        private final List<StsAlongsideHistoryService.RunResult> slices;
        private int calls;

        SequenceRunner(StsAlongsideHistoryService.RunResult... slices) {
            this.slices = Arrays.asList(slices);
        }

        @Override
        public StsAlongsideHistoryService.RunResult run() {
            return slices.get(calls++);
        }
    }
}
