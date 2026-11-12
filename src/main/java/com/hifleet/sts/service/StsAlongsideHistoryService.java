package com.hifleet.sts.service;

import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.CandidateFunnelStats;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.job.services.ShipBehaviorAnalysisService;
import com.hifleet.service.ShipAlongsideDetector;
import com.hifleet.sts.domain.StsAlongsideHistoryJobParams;
import com.hifleet.sts.domain.StsBackfillBatchRow;
import com.hifleet.sts.domain.StsBackfillTaskRow;
import com.hifleet.sts.domain.StsRiskRegionRow;
import com.hifleet.sts.domain.StsSeedIdentityRow;
import com.hifleet.sts.domain.StsShadowEvent;
import com.hifleet.sts.repository.StsBackfillBatchRepository;
import com.hifleet.sts.repository.StsBackfillTaskRepository;
import com.hifleet.sts.repository.StsRiskRegionRepository;
import com.hifleet.sts.repository.StsSeedIdentityRepository;
import com.hifleet.sts.repository.StsShadowEventRepository;
import com.hifleet.trajectory.TrajectoryPoint;
import com.xxl.job.core.log.XxlJobLogger;
import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StsAlongsideHistoryService {
    static final String RUNNER_VERSION = "STS_FALSE_POSITIVE_REDUCTION_V1";
    static final String BEHAVIOR_TYPE = "OBSERVED_ALONGSIDE";
    static final String LEGACY_SEED_BATCH_NO = "sts-crude-identity-20260902-v1";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Logger log = LoggerFactory.getLogger(StsAlongsideHistoryService.class);

    @Autowired private StsSeedIdentityRepository seedRepository;
    @Autowired private StsRiskRegionRepository regionRepository;
    @Autowired private StsBackfillBatchRepository batchRepository;
    @Autowired private StsBackfillTaskRepository taskRepository;
    @Autowired private StsBackfillTaskHeartbeat taskHeartbeat;
    @Autowired private ShipBehaviorAnalysisService analysisService;
    @Autowired private StsShadowEventRepository shadowRepository;
    @Autowired @Qualifier("newDataanalysisDataSource") private DataSource dataSource;

    public RunResult runOneInvocation(StsAlongsideHistoryJobParams params) {
        final long invocationStarted = currentTimeMillis();
        params.validate();
        List<StsSeedIdentityRow> universe = loadValidatedSeed(params.getSeedBatchNo());
        List<String> available = canonicalImos(universe);
        List<String> selectedImos = selectImos(params, available);
        if (selectedImos.isEmpty()) throw new IllegalArgumentException("no seed IMOs selected");
        List<StsSeedIdentityRow> identities = new ArrayList<StsSeedIdentityRow>();
        for (StsSeedIdentityRow row : universe) if (selectedImos.contains(row.getImo())) identities.add(row);
        Map<String, List<StsSeedIdentityRow>> rowsByImo = groupByImo(identities);
        if (rowsByImo.size() != selectedImos.size()) {
            throw new IllegalStateException("selected IMO identity rows are incomplete");
        }

        List<StsRiskRegionRow> regionRows = regionRepository.loadRegionSet(params.getRegionSetCode());
        if (regionRows == null || regionRows.isEmpty()) {
            throw new IllegalArgumentException("no enabled regions for regionSetCode=" + params.getRegionSetCode());
        }
        StsRegionSetGeometry regions = new StsRegionSetGeometry(params.getRegionSetCode(), regionRows);
        String identityFingerprint = fingerprintSeedIdentities(params.getSeedBatchNo(), universe);
        String regionFingerprint = StsFleetBackfillService.computeRegionSetFingerprint(regionRows);
        String ruleVersion = analysisService.currentAlongsideRuleVersion();
        String ruleHash = analysisService.alongsideRuleConfigHash();
        String codeSha = analysisService.currentCodeCommitSha();
        String configFingerprint = fingerprintConfig(params, ruleVersion, ruleHash, codeSha);

        RunResult result = new RunResult();
        result.batchNo = params.getBatchNo();
        result.selectedPhysicalShips = selectedImos.size();
        result.selectedImos = selectedImos.size();
        initializeOrValidateBatch(params, identityFingerprint, regionFingerprint,
                configFingerprint, selectedImos.size(), regionRows.size(), available.size());
        refreshBatch(result);
        progressLog("[STS][JOB_ACCEPTED] batch=" + params.getBatchNo()
                + " parentRange=" + params.getFrom() + "/" + params.getTo()
                + " sliceDays=" + params.getSliceDays()
                + " selectedShips=" + selectedImos.size()
                + " resume=" + params.isResume()
                + " autoContinue=" + params.isAutoContinue()
                + " maxSlicesPerInvocation=" + params.getMaxSlicesPerInvocation()
                + " maxRuntimeMinutes=" + params.getMaxRuntimeMinutes()
                + " writeMode=" + params.getWriteMode());
        logBatchState(result);

        final Map<String, List<StsSeedIdentityRow>> preparedRowsByImo = rowsByImo;
        final StsRegionSetGeometry preparedRegions = regions;
        final String preparedRegionFingerprint = regionFingerprint;
        final String preparedRuleVersion = ruleVersion;
        final String preparedRuleHash = ruleHash;
        final String preparedCodeSha = codeSha;
        final int preparedSelectedShips = selectedImos.size();
        return runBatchInvocation(params, result, new SliceRunner() {
            @Override
            public RunResult run() {
                return runOneSlice(params, preparedRowsByImo, preparedRegions,
                        preparedRegionFingerprint, preparedRuleVersion, preparedRuleHash,
                        preparedCodeSha, preparedSelectedShips);
            }
        }, invocationStarted);
    }

    private RunResult runOneSlice(StsAlongsideHistoryJobParams params,
                                  Map<String, List<StsSeedIdentityRow>> rowsByImo,
                                  StsRegionSetGeometry regions, String regionFingerprint,
                                  String ruleVersion, String ruleHash, String codeSha,
                                  int selectedShips) {
        RunResult result = new RunResult();
        result.batchNo = params.getBatchNo();
        result.selectedPhysicalShips = selectedShips;
        result.selectedImos = selectedShips;
        refreshBatch(result);
        StsBackfillTaskRow task = taskRepository.claimNextTask(params.getBatchNo());
        if (task == null) {
            refreshBatch(result);
            logBatchProgress(result);
            return result;
        }

        result.currentTaskId = task.getId();
        result.currentSliceNumber = task.getSliceSeq() + 1;
        LocalDateTime sliceFrom = toLocalDateTime(task.getSliceFrom());
        LocalDateTime sliceTo = toLocalDateTime(task.getSliceTo());
        result.sliceFrom = sliceFrom;
        result.sliceTo = sliceTo;
        progressLog(formatSliceClaimed(params.getBatchNo(), result, sliceFrom, sliceTo));
        long started = System.currentTimeMillis();
        StsBackfillTaskHeartbeat.Handle heartbeat = taskHeartbeat.start(task.getId());
        try {
            processSlice(params, rowsByImo, regions, regionFingerprint, ruleVersion,
                    ruleHash, codeSha, sliceFrom, sliceTo, result);
            if (result.selfPairEvents > 0) {
                throw new IllegalStateException("selfPairEvents > 0: " + result.selfPairEvents);
            }
            if (result.failedShips == result.selectedPhysicalShips && result.failedShips > 0
                    && result.pairReplaySuccessful == 0
                    && result.pairReplayFailures + result.pairReplayTruncated > 0) {
                throw new SystemicFatalException("REPLAY_QUERY",
                        "entire replay source unavailable for claimed slice");
            }
            result.shadowInserted = shadowRepository.countByBatchAndWindow(
                    params.getBatchNo(), sliceFrom, sliceTo);
            if (result.failedShips > 0) {
                result.partial = true;
                result.outcome = "PARTIAL";
                markTaskCompleteWithWarnings(task.getId(), result);
            } else {
                result.outcome = "SUCCESS";
                markTaskComplete(task.getId(), result);
            }
            result.success = true;
        } catch (Exception ex) {
            try {
                result.shadowInserted = shadowRepository.countByBatchAndWindow(
                        params.getBatchNo(), sliceFrom, sliceTo);
            } catch (Exception countEx) {
                ex.addSuppressed(countEx);
                log.warn("failed to reconcile shadow count for failed taskId={}", task.getId(), countEx);
            }
            SystemicFatalException original = ex instanceof SystemicFatalException
                    ? (SystemicFatalException) ex : null;
            SystemicFatalException fatal = new SystemicFatalException(
                    original == null ? "UNKNOWN" : original.stage,
                    "SLICE=" + sliceLabel(result) + " "
                            + (original == null ? rootMessage(ex) : original.rootCause), ex);
            result.outcome = "FATAL";
            result.fatalStage = fatal.stage;
            markTaskFailed(task.getId(), fatal.getMessage(), result);
            result.success = false;
            throw fatal;
        } finally {
            heartbeat.close();
            result.durationMs = System.currentTimeMillis() - started;
            refreshBatch(result);
            logSliceSummary(result);
            logCandidateFunnel(result);
            logBatchProgress(result);
            logSummary(result);
        }
        return result;
    }

    RunResult runBatchInvocation(StsAlongsideHistoryJobParams params, RunResult invocation,
                                 SliceRunner runner, long invocationStarted) {
        while (true) {
            RunResult slice = runner.run();
            copyBatchState(invocation, slice);
            if (slice.currentTaskId == null) {
                if ("COMPLETE".equals(slice.batchStatus)
                        || "COMPLETE_WITH_WARNINGS".equals(slice.batchStatus)) {
                    invocation.success = true;
                    invocation.partial = "COMPLETE_WITH_WARNINGS".equals(slice.batchStatus);
                    invocation.outcome = invocation.partial
                            ? "SUCCESS_WITH_WARNINGS_COMPLETE" : "SUCCESS_COMPLETE";
                } else if (slice.failedTasks > 0 && !"RUNNING".equals(slice.batchStatus)) {
                    invocation.success = false;
                    invocation.outcome = "FATAL";
                    invocation.stopReason = "TERMINAL_FAILED_SLICE";
                } else {
                    invocation.success = true;
                    invocation.outcome = "SUCCESS_PAUSED_LIMIT";
                    invocation.stopReason = "NO_CLAIMABLE_SLICE";
                }
                break;
            }

            mergeSlice(invocation, slice);
            invocation.invocationSlices++;
            if (slice.partial) invocation.partial = true;
            int remaining = Math.max(0, invocation.totalTasks - invocation.completedTasks);
            if (remaining == 0 && ("COMPLETE".equals(invocation.batchStatus)
                    || "COMPLETE_WITH_WARNINGS".equals(invocation.batchStatus))) {
                invocation.success = true;
                invocation.partial = "COMPLETE_WITH_WARNINGS".equals(invocation.batchStatus);
                invocation.outcome = invocation.partial
                        ? "SUCCESS_WITH_WARNINGS_COMPLETE" : "SUCCESS_COMPLETE";
                break;
            }
            if (!params.isAutoContinue()) {
                invocation.success = true;
                invocation.outcome = slice.partial ? "PARTIAL" : "SUCCESS";
                invocation.stopReason = "AUTO_CONTINUE_DISABLED";
                break;
            }

            long elapsedMs = currentTimeMillis() - invocationStarted;
            if (invocation.invocationSlices >= params.getMaxSlicesPerInvocation()) {
                invocation.success = true;
                invocation.outcome = "SUCCESS_PAUSED_LIMIT";
                invocation.stopReason = "MAX_SLICES";
                logAutoContinueStop(invocation, elapsedMs);
                break;
            }
            if (elapsedMs >= params.getMaxRuntimeMinutes() * 60L * 1000L) {
                invocation.success = true;
                invocation.outcome = "SUCCESS_PAUSED_LIMIT";
                invocation.stopReason = "MAX_RUNTIME";
                logAutoContinueStop(invocation, elapsedMs);
                break;
            }
            progressLog("[STS][AUTO_CONTINUE] batch=" + invocation.batchNo
                    + " finishedSlice=" + invocation.currentSliceNumber + "/" + invocation.totalTasks
                    + " nextSlice=" + (invocation.currentSliceNumber + 1) + "/" + invocation.totalTasks
                    + " invocationSlices=" + invocation.invocationSlices + "/"
                    + params.getMaxSlicesPerInvocation()
                    + " elapsedSec=" + elapsedMs / 1000L
                    + " decision=CONTINUE");
        }
        invocation.durationMs = currentTimeMillis() - invocationStarted;
        if ("SUCCESS_COMPLETE".equals(invocation.outcome)
                || "SUCCESS_WITH_WARNINGS_COMPLETE".equals(invocation.outcome)) {
            loadBatchClassificationSummary(invocation);
            logBatchComplete(invocation);
        }
        invocation.summary = jobReturnSummary(invocation);
        logSummary(invocation);
        return invocation;
    }

    private void copyBatchState(RunResult target, RunResult source) {
        target.batchStatus = source.batchStatus;
        target.totalTasks = source.totalTasks;
        target.completedTasks = source.completedTasks;
        target.failedTasks = source.failedTasks;
        target.warningTasks = source.warningTasks;
        target.batchFailedShips = source.batchFailedShips;
        target.batchTotalShadow = source.batchTotalShadow;
        target.batchTotalProcessedShips = source.batchTotalProcessedShips;
        target.batchElapsedMs = source.batchElapsedMs;
    }

    private void mergeSlice(RunResult total, RunResult slice) {
        total.currentTaskId = slice.currentTaskId;
        total.currentSliceNumber = slice.currentSliceNumber;
        total.sliceFrom = slice.sliceFrom;
        total.sliceTo = slice.sliceTo;
        total.queriedMmsis += slice.queriedMmsis;
        total.processedPhysicalShips += slice.processedPhysicalShips;
        total.trajectoryLoadedShips += slice.trajectoryLoadedShips;
        total.ownTrajectoryPoints += slice.ownTrajectoryPoints;
        total.regionMatchedShips += slice.regionMatchedShips;
        total.regionMatchedWindows += slice.regionMatchedWindows;
        total.alongsideLowSpeedWindows += slice.alongsideLowSpeedWindows;
        total.pairReplayQueries += slice.pairReplayQueries;
        total.pairReplaySuccessful += slice.pairReplaySuccessful;
        total.pairReplayFailures += slice.pairReplayFailures;
        total.pairReplayTruncated += slice.pairReplayTruncated;
        total.pairReplayRawPoints += slice.pairReplayRawPoints;
        total.candidateFunnel.add(slice.candidateFunnel);
        total.candidateTargets += slice.candidateTargets;
        total.selfIdentityRejected += slice.selfIdentityRejected;
        total.alongsideEvents += slice.alongsideEvents;
        total.regionAcceptedEvents += slice.regionAcceptedEvents;
        total.physicalObserved += slice.physicalObserved;
        total.physicalFormal += slice.physicalFormal;
        total.physicalWeak += slice.physicalWeak;
        total.stsCandidates += slice.stsCandidates;
        total.stsReviewRequired += slice.stsReviewRequired;
        total.serviceSuppressed += slice.serviceSuppressed;
        total.portBerthSuppressed += slice.portBerthSuppressed;
        total.weakSuppressed += slice.weakSuppressed;
        total.shadowInserted += slice.shadowInserted;
        total.shadowDuplicateSkipped += slice.shadowDuplicateSkipped;
        total.selfPairEvents += slice.selfPairEvents;
        total.failedShips += slice.failedShips;
        total.currentSliceFailedShips = slice.failedShips;
        total.shipFailures.addAll(slice.shipFailures);
    }

    private void logAutoContinueStop(RunResult r, long elapsedMs) {
        progressLog("[STS][AUTO_CONTINUE_STOP] reason=" + r.stopReason
                + " invocationSlices=" + r.invocationSlices
                + " completedSlices=" + r.completedTasks + "/" + r.totalTasks
                + " remainingSlices=" + Math.max(0, r.totalTasks - r.completedTasks)
                + " elapsedSec=" + elapsedMs / 1000L);
    }

    private void logBatchComplete(RunResult r) {
        progressLog("[STS][BATCH_COMPLETE] batch=" + r.batchNo
                + " completedSlices=" + r.completedTasks + "/" + r.totalTasks
                + " warningSlices=" + r.warningTasks
                + " totalPhysicalObserved=" + r.batchPhysicalObserved
                + " stsCandidates=" + r.batchStsCandidates
                + " stsReviewRequired=" + r.batchStsReviewRequired
                + " serviceSuppressed=" + r.batchServiceSuppressed
                + " weakSuppressed=" + r.batchWeakSuppressed
                + " totalElapsedSec=" + r.batchElapsedMs / 1000L);
    }

    private void loadBatchClassificationSummary(RunResult r) {
        if (shadowRepository == null) {
            r.batchPhysicalObserved = r.batchTotalShadow;
            r.batchStsCandidates = r.stsCandidates;
            r.batchStsReviewRequired = r.stsReviewRequired;
            r.batchServiceSuppressed = r.serviceSuppressed;
            r.batchWeakSuppressed = r.weakSuppressed;
            return;
        }
        StsShadowEventRepository.BatchSummary summary = shadowRepository.summarizeBatch(r.batchNo);
        r.batchPhysicalObserved = summary.physicalObserved;
        r.batchStsCandidates = summary.stsCandidates;
        r.batchStsReviewRequired = summary.stsReviewRequired;
        r.batchServiceSuppressed = summary.serviceSuppressed;
        r.batchWeakSuppressed = summary.weakSuppressed;
    }

    private static String jobReturnSummary(RunResult r) {
        return "[STS][JOB_RETURN] outcome=" + r.outcome
                + " batch=" + r.batchNo
                + " invocationSlices=" + r.invocationSlices
                + " completedSlices=" + r.completedTasks + "/" + r.totalTasks
                + " remainingSlices=" + Math.max(0, r.totalTasks - r.completedTasks)
                + (r.stopReason == null ? "" : " stopReason=" + r.stopReason)
                + " completedShips=" + r.processedPhysicalShips
                + " currentSliceFailedShips=" + r.currentSliceFailedShips
                + " batchAccumulatedFailedShips=" + r.batchFailedShips
                + " partial=" + r.partial
                + " physicalObserved=" + r.physicalObserved
                + " stsCandidates=" + r.stsCandidates
                + " stsReviewRequired=" + r.stsReviewRequired
                + " serviceSuppressed=" + r.serviceSuppressed
                + " weakSuppressed=" + r.weakSuppressed
                + " shadowInserted=" + r.shadowInserted
                + " duplicateSkipped=" + r.shadowDuplicateSkipped;
    }

    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    private void processSlice(final StsAlongsideHistoryJobParams params,
                              Map<String, List<StsSeedIdentityRow>> rowsByImo,
                              final StsRegionSetGeometry regions, String regionFingerprint,
                              String ruleVersion, String ruleHash, String codeSha,
                              LocalDateTime sliceFrom, LocalDateTime sliceTo, RunResult total) {
        int shipIndex = 0;
        long started = System.currentTimeMillis();
        for (Map.Entry<String, List<StsSeedIdentityRow>> entry : rowsByImo.entrySet()) {
            shipIndex++;
            long shipStarted = System.currentTimeMillis();
            String imo = entry.getKey();
            List<StsSeedIdentityRow> rows = entry.getValue();
            Set<String> ownMmsis = new HashSet<String>();
            for (StsSeedIdentityRow row : rows) ownMmsis.add(row.getMmsi());
            boolean trajectoryLoaded = false;
            boolean regionMatched = false;
            try {
                for (StsSeedIdentityRow row : rows) {
                    TimeBounds bounds = intersectObservedBounds(row, sliceFrom, sliceTo);
                    if (bounds == null) continue;
                    total.queriedMmsis++;
                    RegionIdentityPolicy policy = new RegionIdentityPolicy(
                            regions, ownMmsis, imo, params.getSeedBatchNo(), seedRepository);
                    ShipBehaviorAnalysisService.AlongsideWindowResult computed =
                            analysisService.computeAlongsideHistory(row.getMmsi(),
                                    bounds.from.format(TIME_FMT), bounds.to.format(TIME_FMT),
                                    params.getBatchNo(), policy);
                    total.ownTrajectoryPoints += computed.getPoints();
                    trajectoryLoaded |= computed.getPoints() > 0;
                    total.alongsideLowSpeedWindows += computed.getAlongsideLowSpeedWindows();
                    total.regionMatchedWindows += computed.getLowSpeedWindows();
                    regionMatched |= computed.getLowSpeedWindows() > 0;
                    total.pairReplayQueries += computed.getAttemptedReplayQueries();
                    total.pairReplaySuccessful += computed.getSuccessfulReplayQueries();
                    total.pairReplayFailures += computed.getFailedReplayQueries();
                    total.pairReplayTruncated += computed.getTruncatedReplayQueryCount();
                    total.pairReplayRawPoints += computed.getPairReplayRawPoints();
                    total.candidateFunnel.add(computed.getCandidateFunnel());
                    total.candidateTargets += computed.getCandidateTargets();
                    total.selfIdentityRejected += computed.getPolicyRejectedCandidates();
                    total.alongsideEvents += computed.getEvents();
                    requireComplete(row.getMmsi(), computed);
                    for (ShipBehaviorTrace trace : computed.getTraces()) {
                        addPhysicalEvidence(trace, rows);
                        persistCandidate(params, regions, regionFingerprint, row, imo,
                                ownMmsis, trace.getEvent(), ruleVersion, ruleHash, codeSha, total);
                    }
                }
                total.processedPhysicalShips++;
                if (trajectoryLoaded) total.trajectoryLoadedShips++;
                if (regionMatched) total.regionMatchedShips++;
            } catch (ShipLocalFailureException ex) {
                total.failedShips++;
                ShipFailureDetail failure = new ShipFailureDetail(imo, ex.mmsi, ex.stage,
                        ex.getMessage(), ex.retryable, System.currentTimeMillis() - shipStarted);
                total.shipFailures.add(failure);
                log.error("STS alongside history ship failed: imo={}", imo, ex);
                progressLog("[STS][SHIP_FAILED] slice=" + sliceLabel(total)
                        + " shipIndex=" + shipIndex + "/" + rowsByImo.size()
                        + " imo=" + safe(imo) + " mmsi=" + safe(ex.mmsi)
                        + " stage=" + ex.stage + " retryable=" + ex.retryable
                        + " reason=" + concise(ex.getMessage())
                        + " elapsedMs=" + failure.elapsedMs);
            } catch (SystemicFatalException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new SystemicFatalException("UNKNOWN",
                        "ship imo=" + safe(imo) + " rootCause=" + rootMessage(ex), ex);
            }
            if (shipIndex % 50 == 0 || shipIndex == rowsByImo.size()) {
                logFleetProgress(total, shipIndex, rowsByImo.size(),
                        System.currentTimeMillis() - started);
            }
        }
    }

    void persistCandidate(StsAlongsideHistoryJobParams params, StsRegionSetGeometry regions,
                                  String regionFingerprint, StsSeedIdentityRow own, String ownImo,
                                  Set<String> ownMmsis, ShipBehaviorEvent event, String ruleVersion,
                                  String ruleHash, String codeSha, RunResult result) {
        String targetMmsi = event.getTargetMmsi();
        String targetImo = seedRepository.findHistoricalImoByMmsi(params.getSeedBatchNo(), targetMmsi);
        JSONObject provenance = parseDetail(event.getScoreDetailJson());
        provenance.put("targetSeedImo", targetImo);
        try {
            Map<String, Object> metadata = seedRepository.historicalTargetMetadata(targetMmsi);
            if (metadata != null && !metadata.isEmpty()) {
                provenance.put("targetShipTypeRaw", metadata.get("type"));
                provenance.put("targetShipMinTypeRaw", metadata.get("minotype"));
                provenance.put("targetMetadataSource", "ais41.recentships");
                provenance.put("targetMetadataAsOf", "UNKNOWN");
                String recentImo = String.valueOf(metadata.get("imonumber"));
                if (recentImo.matches("[1-9][0-9]{6}")) {
                    provenance.put("targetRecentImo", recentImo);
                    if (ownImo.equals(recentImo)) { result.selfIdentityRejected++; return; }
                    if (targetImo != null && !targetImo.equals(recentImo)) provenance.put("identityConflict", true);
                    if (targetImo == null) targetImo = recentImo;
                }
            }
        } catch (Exception ex) {
            provenance.put("targetMetadataLookupStatus", "UNAVAILABLE");
            log.warn("STS target metadata unavailable mmsi={}", targetMmsi);
        }
        if (ownMmsis.contains(targetMmsi) || ownImo.equals(targetImo)
                || own.getMmsi().equals(targetMmsi)) {
            result.selfPairEvents++;
            return;
        }
        if (event.getCenterLon() == null || event.getCenterLat() == null) return;
        StsRegionSetGeometry.AttributionResult attribution = regions.attributePrimary(
                new Coordinate(event.getCenterLon(), event.getCenterLat()));
        result.regionAcceptedEvents++;

        StsShadowEvent shadow = new StsShadowEvent();
        shadow.setBatchNo(params.getBatchNo());
        String ownPhysicalIdentity = ownImo == null ? own.getMmsi() : "IMO:" + ownImo;
        String targetPhysicalIdentity = targetImo == null ? targetMmsi : "IMO:" + targetImo;
        String pair = ownPhysicalIdentity.compareTo(targetPhysicalIdentity) <= 0
                ? ownPhysicalIdentity + "|" + targetPhysicalIdentity : targetPhysicalIdentity + "|" + ownPhysicalIdentity;
        shadow.setEventKey(sha256(pair + "|" + event.getStartTime() + "|" + event.getEndTime()
                + "|" + RUNNER_VERSION + "|" + ruleVersion + "|" + ruleHash));
        shadow.setAlgorithmVersion(RUNNER_VERSION);
        shadow.setSeedBatchNo(params.getSeedBatchNo());
        shadow.setRegionSetCode(params.getRegionSetCode());
        shadow.setRegionId(attribution.hasMatch() ? attribution.primary.regionCode : "OUTSIDE_MONITORED_REGIONS");
        shadow.setRegionVersion(attribution.hasMatch() ? attribution.primary.regionVersion : "CONTEXT_ONLY_V1");
        shadow.setOwnImo(ownImo);
        shadow.setOwnMmsi(own.getMmsi());
        shadow.setTargetImo(targetImo);
        shadow.setTargetMmsi(targetMmsi);
        shadow.setIdentityMode(own.getIdentityMode());
        shadow.setStartTime(event.getStartTime());
        shadow.setEndTime(event.getEndTime());
        if (event.getDurationMinutes() != null) {
            shadow.setDurationMinutes(event.getDurationMinutes().intValue());
        }
        shadow.setCenterLon(event.getCenterLon());
        shadow.setCenterLat(event.getCenterLat());
        shadow.setBehaviorType(BEHAVIOR_TYPE);
        shadow.setBaselineRule(ruleVersion);
        shadow.setMinDistanceNm(event.getMinDistanceNm());
        shadow.setAvgDistanceNm(event.getAvgDistanceNm());
        shadow.setOwnAvgSpeedKn(provenance.getDouble("ownAvgSpeedKn"));
        shadow.setTargetAvgSpeedKn(provenance.getDouble("targetAvgSpeedKn"));
        StsHistoricalClassification.classify(provenance);
        shadow.setReviewStatus(provenance.getBooleanValue("reviewQueueEligible") ? "PENDING" : "NOT_REQUIRED");
        result.physicalObserved++;
        if ("WEAK".equals(provenance.getString("physicalSupportLevel"))) {
            result.physicalWeak++;
            result.weakSuppressed++;
        } else {
            result.physicalFormal++;
        }
        if (provenance.getJSONArray("contextEvidence").contains("SERVICE_INTERACTION")) result.serviceSuppressed++;
        if (provenance.getJSONArray("contextEvidence").contains("PORT_BERTH_STATIONARY")) result.portBerthSuppressed++;
        if ("STS_CANDIDATE".equals(provenance.getString("stsClassification"))) result.stsCandidates++;
        if ("STS_REVIEW_REQUIRED".equals(provenance.getString("stsClassification"))) result.stsReviewRequired++;
        shadow.setTargetRole(provenance.getString("targetRole"));
        provenance.put("canonicalPhysicalPair", pair);
        provenance.put("ownImo", ownImo);
        provenance.put("ownMmsi", own.getMmsi());
        provenance.put("targetImo", targetImo);
        provenance.put("targetMmsi", targetMmsi);
        provenance.put("reviewPriority", attribution.hasMatch() ? "ELEVATED" : "NORMAL");
        List<String> associations = new ArrayList<String>();
        for (StsRegionSetGeometry.ResolvedRegion match : attribution.allMatches) associations.add(match.regionCode + "|" + match.regionVersion);
        provenance.put("regionAssociations", associations);
        provenance.put("regionSemantics", "CONTEXT_ONLY");
        provenance.put("timeZone", java.time.ZoneId.systemDefault().getId());
        provenance.put("rangeSemantics", "FROM_INCLUSIVE_TO_EXCLUSIVE");
        provenance.put("stsAdapterVersion", RUNNER_VERSION);
        provenance.put("alongsideRuleVersion", ruleVersion);
        provenance.put("alongsideRuleConfigHash", ruleHash);
        provenance.put("regionFingerprint", regionFingerprint);
        provenance.put("seedBatchNo", params.getSeedBatchNo());
        provenance.put("codeCommitSha", codeSha);
        shadow.setScoreDetailJson(provenance.toJSONString());
        int inserted = shadowRepository.insertIfNoOverlappingPhysicalEvent(shadow);
        if (inserted > 0) result.shadowInserted++; else result.shadowDuplicateSkipped++;
    }

    private static void addPhysicalEvidence(ShipBehaviorTrace trace, List<StsSeedIdentityRow> aliases) {
        JSONObject detail = parseDetail(trace.getEvent().getScoreDetailJson());
        double ownSpeed = 0.0, targetSpeed = 0.0;
        List<String> pairs = new ArrayList<String>();
        for (com.hifleet.domain.AlongsideEvidencePair pair : trace.getAlongsideEvidencePairs()) {
            TrajectoryPoint own = pair.getOwnPoint(), target = pair.getTargetPoint();
            ownSpeed += own.getSpeed();
            targetSpeed += target.getSpeed();
            pairs.add(own.getTime() + "|" + own.getLon() + "|" + own.getLat() + "|" + own.getSpeed()
                    + "|" + own.getCourse() + "|" + target.getTime() + "|" + target.getLon() + "|" + target.getLat()
                    + "|" + target.getSpeed() + "|" + target.getCourse());
        }
        if (!pairs.isEmpty()) {
            detail.put("ownAvgSpeedKn", ownSpeed / pairs.size());
            detail.put("targetAvgSpeedKn", targetSpeed / pairs.size());
            detail.put("matchedPairsFingerprint", sha256(pairs.toString()));
        }
        detail.put("sourceOwnPointCount", trace.getAlongsideSourceOwnPointCount());
        detail.put("sourceTargetPointCount", trace.getAlongsideSourceTargetPointCount());
        detail.put("aisGapCount", trace.getEvent().getAisGapCount());
        detail.put("maxAisGapMinutes", trace.getEvent().getMaxAisGapMinutes());
        detail.put("ownAliasFingerprint", fingerprintIdentities(aliases));
        List<String> aliasMmsis = new ArrayList<String>();
        for (StsSeedIdentityRow row : aliases) aliasMmsis.add(row.getMmsi());
        detail.put("ownAliasMmsis", aliasMmsis);
        detail.put("stsRuleVersion", RUNNER_VERSION);
        trace.getEvent().setScoreDetailJson(detail.toJSONString());
    }

    static List<String> selectImos(StsAlongsideHistoryJobParams params, List<String> available) {
        if (!params.getImos().isEmpty()) {
            if (!available.containsAll(params.getImos()) || new HashSet<String>(params.getImos()).size() != params.getImos().size())
                throw new IllegalArgumentException("explicit IMOs must be unique and present in seed batch");
            return params.getImos();
        }
        if (params.getShipOffset() >= available.size() || params.getShipCount() > available.size() - params.getShipOffset())
            throw new IllegalArgumentException("requested fleet range exceeds actual candidate batch");
        return new ArrayList<String>(available.subList(params.getShipOffset(), params.getShipOffset() + params.getShipCount()));
    }

    List<StsSeedIdentityRow> loadValidatedSeed(String seedBatchNo) {
        List<StsSeedIdentityRow> rows = seedRepository.selectHistoricalCandidateRows(seedBatchNo);
        validateSeed(seedBatchNo, rows);
        return rows;
    }

    static List<String> canonicalImos(List<StsSeedIdentityRow> rows) {
        return new ArrayList<String>(groupByImo(rows).keySet());
    }

    static void validateSeed(String seedBatchNo, List<StsSeedIdentityRow> rows) {
        if (rows == null || rows.isEmpty()) throw new IllegalStateException("empty identity candidate batch");
        Set<String> mmsis = new HashSet<String>();
        Map<String, Integer> ordinalByImo = new LinkedHashMap<String, Integer>();
        Map<Integer, String> imoByOrdinal = new LinkedHashMap<Integer, String>();
        StsSeedIdentityRow previous = null;
        for (StsSeedIdentityRow row : rows) {
            if (seedBatchNo == null || !seedBatchNo.equals(row.getSeedBatchNo())) {
                throw new IllegalStateException("seed batch mismatch");
            }
            if (row.getImo() == null || !row.getImo().matches("[1-9][0-9]{6}")
                    || row.getMmsi() == null || !row.getMmsi().matches("[1-9][0-9]{8}")
                    || !mmsis.add(row.getMmsi())) {
                throw new IllegalStateException("invalid or ambiguous seed identity");
            }
            if (row.getShipOrdinal() <= 0) throw new IllegalStateException("seed ordinal must be positive");
            Integer existingOrdinal = ordinalByImo.putIfAbsent(row.getImo(), row.getShipOrdinal());
            if (existingOrdinal != null && existingOrdinal.intValue() != row.getShipOrdinal()) {
                throw new IllegalStateException("same IMO has multiple seed ordinals");
            }
            String existingImo = imoByOrdinal.putIfAbsent(row.getShipOrdinal(), row.getImo());
            if (existingImo != null && !existingImo.equals(row.getImo())) {
                throw new IllegalStateException("seed ordinal belongs to multiple IMOs");
            }
            if (previous != null && compareSeedOrder(previous, row) > 0) {
                throw new IllegalStateException("seed rows are not stably ordered");
            }
            previous = row;
        }
        if (ordinalByImo.isEmpty()) throw new IllegalStateException("empty canonical identity batch");
    }

    private static int compareSeedOrder(StsSeedIdentityRow left, StsSeedIdentityRow right) {
        int byOrdinal = Integer.compare(left.getShipOrdinal(), right.getShipOrdinal());
        if (byOrdinal != 0) return byOrdinal;
        int byImo = left.getImo().compareTo(right.getImo());
        return byImo != 0 ? byImo : left.getMmsi().compareTo(right.getMmsi());
    }

    static void requireComplete(String mmsi,
                                ShipBehaviorAnalysisService.AlongsideWindowResult computed) {
        if (computed.getFailed() > 0 || computed.getFailedReplayQueries() > 0
                || computed.getTruncatedReplayQueryCount() > 0) {
            String reason = "incomplete alongside compute: mmsi="
                    + mmsi + ", candidateFailures=" + computed.getFailed()
                    + ", replayFailures=" + computed.getFailedReplayQueries()
                    + ", replayTruncated=" + computed.getTruncatedReplayQueryCount()
                    + ", root=" + safe(computed.getReplayFailureReason());
            if (computed.getFailedReplayQueries() > 0) {
                throw new ShipLocalFailureException(mmsi, "REPLAY_QUERY",
                        computed.getReplayFailureReason() == null || computed.isReplayFailureRetryable(), reason);
            }
            if (computed.getTruncatedReplayQueryCount() > 0) {
                throw new ShipLocalFailureException(mmsi, "REPLAY_TRUNCATION", true, reason);
            }
            throw new SystemicFatalException("DETECT", reason);
        }
    }

    private void initializeOrValidateBatch(StsAlongsideHistoryJobParams params,
                                           String identityFingerprint, String regionFingerprint,
                                           String configFingerprint, int selectedCount, int regionCount, int candidateCount) {
        final StsBackfillPlanner planner = new StsBackfillPlanner(
                params.getBatchNo(), params.getFrom(), params.getTo(), params.getSliceDays());
        planner.validate();
        final List<StsBackfillTaskRow> tasks = planHistoricalTasks(planner);
        final String batchNo = params.getBatchNo();
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        try {
            tx.execute(status -> {
                StsBackfillBatchRow existing = batchRepository.findByBatchNo(batchNo);
                if (existing != null) {
                    validateExistingBatch(existing, identityFingerprint, regionFingerprint,
                            configFingerprint, tasks.size());
                    return null;
                }
                StsBackfillBatchRow batch = new StsBackfillBatchRow();
                batch.setBatchNo(batchNo);
                batch.setRunnerVersion(RUNNER_VERSION);
                batch.setConfigFingerprint(configFingerprint);
                batch.setTargetListBatchNo(params.getSeedBatchNo());
                batch.setTargetListFingerprint(identityFingerprint);
                batch.setExpectedTargetCount(candidateCount);
                batch.setActualTargetCount(candidateCount);
                batch.setShipOffset(params.getImos().isEmpty() ? params.getShipOffset() : 0);
                batch.setShipCount(selectedCount);
                batch.setSelectedShipCount(selectedCount);
                batch.setRegionSetCode(params.getRegionSetCode());
                batch.setRegionSetFingerprint(regionFingerprint);
                batch.setResolvedRegionCount(regionCount);
                batch.setFromTime(Timestamp.valueOf(params.getFrom()));
                batch.setToTime(Timestamp.valueOf(params.getTo()));
                batch.setSliceDays(params.getSliceDays());
                batch.setStatus("RUNNING");
                batch.setTotalTasks(tasks.size());
                batchRepository.insert(batch);
                if (taskRepository.insertTasks(tasks) != tasks.size()) {
                    throw new IllegalStateException("batch task initialization incomplete");
                }
                return null;
            });
        } catch (DuplicateKeyException ex) {
            StsBackfillBatchRow existing = batchRepository.findByBatchNo(batchNo);
            if (existing == null) throw ex;
            validateExistingBatch(existing, identityFingerprint, regionFingerprint,
                    configFingerprint, tasks.size());
        }
    }

    static List<StsBackfillTaskRow> planHistoricalTasks(StsBackfillPlanner planner) {
        List<StsBackfillTaskRow> tasks = planner.planTasks();
        // The shared planner encodes wall-clock fields as UTC; JDBC uses the runtime zone.
        for (StsBackfillTaskRow task : tasks) {
            task.setSliceFrom(Timestamp.valueOf(StsBackfillPlanner.formatTime(task.getSliceFrom())));
            task.setSliceTo(Timestamp.valueOf(StsBackfillPlanner.formatTime(task.getSliceTo())));
        }
        return tasks;
    }

    private void validateExistingBatch(StsBackfillBatchRow existing,
                                       String identityFingerprint, String regionFingerprint,
                                       String configFingerprint, int expectedTasks) {
        if (!RUNNER_VERSION.equals(existing.getRunnerVersion())
                || !identityFingerprint.equals(existing.getTargetListFingerprint())
                || !regionFingerprint.equals(existing.getRegionSetFingerprint())
                || !configFingerprint.equals(existing.getConfigFingerprint())) {
            throw new IllegalStateException("batch resume fingerprint conflict");
        }
        int actualTasks = taskRepository.countByBatchNo(existing.getBatchNo());
        if (existing.getTotalTasks() != expectedTasks || actualTasks != expectedTasks) {
            throw new IllegalStateException("batch resume task set mismatch: expected="
                    + expectedTasks + ", batch=" + existing.getTotalTasks()
                    + ", actual=" + actualTasks);
        }
    }

    private void markTaskComplete(Long taskId, RunResult r) {
        taskRepository.markComplete(taskId, r.regionMatchedShips, r.processedPhysicalShips,
                r.trajectoryLoadedShips, r.alongsideLowSpeedWindows, r.regionMatchedWindows,
                r.pairReplayQueries, r.pairReplayFailures, r.pairReplayTruncated,
                r.alongsideEvents, r.regionAcceptedEvents, r.shadowInserted,
                r.shadowDuplicateSkipped, r.selfPairEvents);
    }

    private void markTaskCompleteWithWarnings(Long taskId, RunResult r) {
        taskRepository.markCompleteWithWarnings(taskId, warningDetail(r),
                r.regionMatchedShips, r.processedPhysicalShips,
                r.trajectoryLoadedShips, r.alongsideLowSpeedWindows, r.regionMatchedWindows,
                r.pairReplayQueries, r.pairReplayFailures, r.pairReplayTruncated,
                r.alongsideEvents, r.regionAcceptedEvents, r.shadowInserted,
                r.shadowDuplicateSkipped, r.selfPairEvents);
    }

    private void markTaskFailed(Long taskId, String error, RunResult r) {
        taskRepository.markFailed(taskId, error, r.regionMatchedShips, r.processedPhysicalShips,
                r.trajectoryLoadedShips, r.alongsideLowSpeedWindows, r.regionMatchedWindows,
                r.pairReplayQueries, r.pairReplayFailures, r.pairReplayTruncated,
                r.alongsideEvents, r.regionAcceptedEvents, r.shadowInserted,
                r.shadowDuplicateSkipped, r.selfPairEvents);
    }

    private void refreshBatch(RunResult result) {
        batchRepository.refreshProgressFromTasks(result.batchNo);
        StsBackfillBatchRow batch = batchRepository.findByBatchNo(result.batchNo);
        if (batch != null) {
            result.batchStatus = batch.getStatus();
            result.totalTasks = batch.getTotalTasks();
            result.completedTasks = batch.getCompletedTasks();
            result.failedTasks = batch.getFailedTasks();
            result.batchTotalShadow = batch.getTotalShadowInserted();
            result.batchTotalProcessedShips = batch.getTotalProcessedShips();
            result.batchElapsedMs = batch.getStartedAt() == null ? 0L
                    : currentTimeMillis() - batch.getStartedAt().getTime();
            result.warningTasks = taskRepository.countByStatus(result.batchNo,
                    "COMPLETE_WITH_WARNINGS");
            result.batchFailedShips = taskRepository.sumWarningFailedShips(
                    result.batchNo, batch.getSelectedShipCount());
        }
    }

    protected void progressLog(String message) {
        XxlJobLogger.log(message);
    }

    private void logBatchState(RunResult r) {
        int pending = Math.max(0, r.totalTasks - r.completedTasks - r.failedTasks);
        progressLog("[STS][BATCH_STATE] batch=" + r.batchNo
                + " totalSlices=" + r.totalTasks
                + " completedSlices=" + r.completedTasks
                + " failedSlices=" + r.failedTasks
                + " pendingSlices=" + pending);
    }

    private void logFleetProgress(RunResult r, int handled, int total, long elapsedMs) {
        progressLog("[STS][PROGRESS] slice=" + sliceLabel(r)
                + " processed=" + handled + "/" + total
                + " percent=" + String.format(java.util.Locale.ROOT, "%.1f", total == 0 ? 100.0 : handled * 100.0 / total)
                + " completedShips=" + r.processedPhysicalShips
                + " trajectoryLoaded=" + r.trajectoryLoadedShips
                + " physicalObserved=" + r.physicalObserved
                + " stsCandidates=" + r.stsCandidates
                + " stsReviewRequired=" + r.stsReviewRequired
                + " serviceSuppressed=" + r.serviceSuppressed
                + " weakSuppressed=" + r.weakSuppressed
                + " failedShips=" + r.failedShips
                + " elapsedSec=" + elapsedMs / 1000L);
    }

    private void logSliceSummary(RunResult r) {
        progressLog("[STS][SLICE_SUMMARY] slice=" + sliceLabel(r)
                + " processedShips=" + (r.processedPhysicalShips + r.failedShips)
                + " completedShips=" + r.processedPhysicalShips
                + " failedShips=" + r.failedShips
                + " partial=" + r.partial
                + " physicalObserved=" + r.physicalObserved
                + " physicalFormal=" + r.physicalFormal
                + " physicalWeak=" + r.physicalWeak
                + " stsCandidates=" + r.stsCandidates
                + " stsReviewRequired=" + r.stsReviewRequired
                + " serviceSuppressed=" + r.serviceSuppressed
                + " weakSuppressed=" + r.weakSuppressed
                + " shadowInserted=" + r.shadowInserted
                + " duplicateSkipped=" + r.shadowDuplicateSkipped
                + " elapsedSec=" + r.durationMs / 1000L);
    }

    private void logCandidateFunnel(RunResult r) {
        CandidateFunnelStats f = r.candidateFunnel;
        progressLog("[STS][CANDIDATE_FUNNEL] slice=" + sliceLabel(r)
                + " replayQueries=" + f.getReplayQueries()
                + " replaySuccessfulQueries=" + f.getReplaySuccessfulQueries()
                + " replayFailedQueries=" + f.getReplayFailedQueries()
                + " replayTruncatedQueries=" + f.getReplayTruncatedQueries()
                + " replayRawPoints=" + f.getReplayRawPoints()
                + " replayValidPoints=" + f.getReplayValidPoints()
                + " replayInvalidPoints=" + f.getReplayInvalidPoints()
                + " replayPointsOutsideRequestedTime=" + f.getReplayPointsOutsideRequestedTime()
                + " targetTracksGrouped=" + f.getTargetTracksGrouped()
                + " invalidTargets=" + f.getInvalidTargets()
                + " selfMmsiExcluded=" + f.getSelfMmsiExcluded()
                + " targetTracksEvaluated=" + f.getTargetTracksEvaluated()
                + " targetTracksWith1Point=" + f.getTargetTracksWith1Point()
                + " targetTracksWith2Points=" + f.getTargetTracksWith2Points()
                + " targetTracksWith3PlusPoints=" + f.getTargetTracksWith3PlusPoints()
                + " cleanPointsGeMin=" + f.getCleanPointsGeMin()
                + " hasLowSpeedPoint=" + f.getHasLowSpeedPoint()
                + " hasTimeAlignedPair=" + f.getHasTimeAlignedPair()
                + " hasDistanceEligiblePair=" + f.getHasDistanceEligiblePair()
                + " matchedPointsGeMin=" + f.getMatchedPointsGeMin()
                + " matchedDurationGeMin=" + f.getMatchedDurationGeMin()
                + " tooFewPoints=" + f.getRejectTooFewPoints()
                + " noLowSpeedPoint=" + f.getRejectNoLowSpeedPoint()
                + " noTimeMatch=" + f.getRejectNoTimeMatch()
                + " distanceFail=" + f.getRejectDistance()
                + " matchedPointsFail=" + f.getRejectMatchedPoints()
                + " durationFail=" + f.getRejectDuration()
                + " acceptedTrackEvaluations=" + f.getAcceptedTrackEvaluations()
                + " candidateAccepted=" + f.getCandidateAccepted()
                + " identityRejected=" + f.getIdentityPolicyRejected()
                + " identityAccepted=" + f.getIdentityPolicyAccepted()
                + " terminalInvariant=" + f.isTerminalInvariantValid()
                + " groupingInvariant=" + f.isGroupingInvariantValid());
        for (CandidateFunnelStats.RejectionReason reason
                : CandidateFunnelStats.RejectionReason.values()) {
            for (String sample : f.getSamples(reason)) {
                progressLog("[STS][CANDIDATE_FUNNEL_SAMPLE] slice=" + sliceLabel(r)
                        + " reason=" + reason + " " + sample);
            }
        }
    }

    private void logBatchProgress(RunResult r) {
        int remaining = Math.max(0, r.totalTasks - r.completedTasks);
        progressLog("[STS][BATCH_PROGRESS] batch=" + r.batchNo
                + " completedSlices=" + r.completedTasks + "/" + r.totalTasks
                + " remainingSlices=" + remaining
                + " currentStatus=" + safe(r.batchStatus)
                + " totalShadow=" + r.batchTotalShadow
                + " totalFailedShips=" + r.batchFailedShips);
    }

    private static String warningDetail(RunResult r) {
        JSONObject detail = new JSONObject(true);
        detail.put("partial", true);
        detail.put("completedShips", r.processedPhysicalShips);
        detail.put("failedShips", r.failedShips);
        List<JSONObject> failures = new ArrayList<JSONObject>();
        for (ShipFailureDetail failure : r.shipFailures) failures.add(failure.toJson());
        detail.put("failures", failures);
        return detail.toJSONString();
    }

    private static String sliceLabel(RunResult r) {
        return r.currentSliceNumber + "/" + r.totalTasks;
    }

    static String formatSliceClaimed(String batchNo, RunResult r,
                                     LocalDateTime sliceFrom, LocalDateTime sliceTo) {
        return "[STS][SLICE_CLAIMED] batch=" + batchNo
                + " slice=" + sliceLabel(r)
                + " from=" + sliceFrom.format(TIME_FMT)
                + " to=" + sliceTo.format(TIME_FMT)
                + " ships=" + r.selectedPhysicalShips;
    }

    private static String safe(String value) {
        return value == null ? "UNKNOWN" : value.replaceAll("[\\r\\n\\t]", " ");
    }

    private static String concise(String value) {
        String safe = safe(value);
        return safe.length() <= 300 ? safe : safe.substring(0, 300);
    }

    private static String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current.getClass().getSimpleName() + ": " + safe(current.getMessage());
    }

    private void logSummary(RunResult r) {
        JSONObject s = new JSONObject(true);
        s.put("event", "STS_ALONGSIDE_HISTORY_SUMMARY");
        s.put("batchNo", r.batchNo);
        s.put("selectedPhysicalShips", r.selectedPhysicalShips);
        s.put("selectedImos", r.selectedImos);
        s.put("queriedMmsis", r.queriedMmsis);
        s.put("trajectoryLoadedShips", r.trajectoryLoadedShips);
        s.put("ownTrajectoryPoints", r.ownTrajectoryPoints);
        s.put("regionMatchedShips", r.regionMatchedShips);
        s.put("regionMatchedWindows", r.regionMatchedWindows);
        s.put("alongsideLowSpeedWindows", r.alongsideLowSpeedWindows);
        s.put("pairReplayQueries", r.pairReplayQueries);
        s.put("pairReplayFailures", r.pairReplayFailures);
        s.put("pairReplayTruncated", r.pairReplayTruncated);
        s.put("pairReplayRawPoints", r.pairReplayRawPoints);
        s.put("candidateTargets", r.candidateTargets);
        s.put("selfIdentityRejected", r.selfIdentityRejected);
        s.put("alongsideEvents", r.alongsideEvents);
        s.put("regionAcceptedEvents", r.regionAcceptedEvents);
        s.put("physicalObserved", r.physicalObserved);
        s.put("physicalFormal", r.physicalFormal);
        s.put("physicalWeak", r.physicalWeak);
        s.put("stsCandidates", r.stsCandidates);
        s.put("stsReviewRequired", r.stsReviewRequired);
        s.put("serviceSuppressed", r.serviceSuppressed);
        s.put("portBerthSuppressed", r.portBerthSuppressed);
        s.put("weakSuppressed", r.weakSuppressed);
        s.put("shadowInserted", r.shadowInserted);
        s.put("shadowDuplicateSkipped", r.shadowDuplicateSkipped);
        s.put("selfPairEvents", r.selfPairEvents);
        s.put("failedShips", r.failedShips);
        s.put("partial", r.partial);
        s.put("outcome", r.outcome);
        List<JSONObject> failures = new ArrayList<JSONObject>();
        for (ShipFailureDetail failure : r.shipFailures) failures.add(failure.toJson());
        s.put("shipFailures", failures);
        s.put("durationMs", r.durationMs);
        log.info(s.toJSONString());
    }

    static TimeBounds intersectObservedBounds(StsSeedIdentityRow row,
                                              LocalDateTime sliceFrom, LocalDateTime sliceTo) {
        LocalDateTime from = row.getFirstSeen() == null ? sliceFrom
                : max(sliceFrom, LocalDateTime.parse(row.getFirstSeen(), TIME_FMT));
        LocalDateTime to = row.getLastSeen() == null ? sliceTo
                : min(sliceTo, LocalDateTime.parse(row.getLastSeen(), TIME_FMT));
        return from.isBefore(to) ? new TimeBounds(from, to) : null;
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) { return a.isAfter(b) ? a : b; }
    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) { return a.isBefore(b) ? a : b; }
    private static LocalDateTime toLocalDateTime(java.util.Date value) {
        return new Timestamp(value.getTime()).toLocalDateTime();
    }

    private static Map<String, List<StsSeedIdentityRow>> groupByImo(List<StsSeedIdentityRow> rows) {
        Map<String, List<StsSeedIdentityRow>> result = new LinkedHashMap<String, List<StsSeedIdentityRow>>();
        for (StsSeedIdentityRow row : rows) {
            List<StsSeedIdentityRow> values = result.get(row.getImo());
            if (values == null) {
                values = new ArrayList<StsSeedIdentityRow>();
                result.put(row.getImo(), values);
            }
            values.add(row);
        }
        return result;
    }

    static String fingerprintIdentities(List<StsSeedIdentityRow> rows) {
        List<String> lines = new ArrayList<String>();
        for (StsSeedIdentityRow row : rows) {
            lines.add(row.getImo() + "|" + row.getMmsi() + "|" + row.getFirstSeen()
                    + "|" + row.getLastSeen() + "|" + row.getIdentityMode());
        }
        Collections.sort(lines);
        return sha256(lines.toString());
    }

    static String fingerprintSeedIdentities(String seedBatchNo, List<StsSeedIdentityRow> rows) {
        if (LEGACY_SEED_BATCH_NO.equals(seedBatchNo)) return fingerprintIdentities(rows);
        List<String> lines = new ArrayList<String>();
        for (StsSeedIdentityRow row : rows) {
            lines.add(seedBatchNo + "|" + row.getShipOrdinal() + "|" + row.getImo() + "|"
                    + row.getMmsi() + "|" + row.getFirstSeen() + "|" + row.getLastSeen()
                    + "|" + row.getIdentityMode());
        }
        Collections.sort(lines);
        return sha256(lines.toString());
    }

    static String fingerprintConfig(StsAlongsideHistoryJobParams p, String ruleVersion,
                                    String ruleHash, String codeSha) {
        return sha256(p.getMode() + "|" + p.getSeedBatchNo() + "|" + p.getRegionSetCode()
                + "|" + p.getImos() + "|" + p.getShipOffset() + "|" + p.getShipCount()
                + "|" + p.getFrom() + "|" + p.getTo() + "|" + p.getSliceDays()
                + "|" + p.getBatchNo() + "|" + p.getWriteMode()
                + "|" + ruleVersion + "|" + ruleHash + "|" + codeSha);
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format("%02x", b & 0xff));
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static JSONObject parseDetail(String value) {
        try {
            return value == null ? new JSONObject(true) : JSONObject.parseObject(value);
        } catch (Exception ex) {
            return new JSONObject(true);
        }
    }

    static final class TimeBounds {
        final LocalDateTime from;
        final LocalDateTime to;
        TimeBounds(LocalDateTime from, LocalDateTime to) { this.from = from; this.to = to; }
    }

    static class ShipLocalFailureException extends IllegalStateException {
        final String mmsi;
        final String stage;
        final boolean retryable;

        ShipLocalFailureException(String mmsi, String stage, boolean retryable, String message) {
            super(message);
            this.mmsi = mmsi;
            this.stage = stage;
            this.retryable = retryable;
        }
    }

    static class SystemicFatalException extends IllegalStateException {
        final String stage;
        final String rootCause;

        SystemicFatalException(String stage, String rootCause) {
            this(stage, rootCause, null);
        }

        SystemicFatalException(String stage, String rootCause, Throwable cause) {
            super("FATAL_STAGE=" + stage + " ROOT_CAUSE=" + concise(rootCause), cause);
            this.stage = stage;
            this.rootCause = concise(rootCause);
        }
    }

    static final class ShipFailureDetail {
        final String imo;
        final String mmsi;
        final String stage;
        final String reason;
        final boolean retryable;
        final long elapsedMs;

        ShipFailureDetail(String imo, String mmsi, String stage, String reason,
                          boolean retryable, long elapsedMs) {
            this.imo = imo;
            this.mmsi = mmsi;
            this.stage = stage;
            this.reason = concise(reason);
            this.retryable = retryable;
            this.elapsedMs = elapsedMs;
        }

        JSONObject toJson() {
            JSONObject value = new JSONObject(true);
            value.put("imo", imo);
            value.put("mmsi", mmsi);
            value.put("stage", stage);
            value.put("reason", reason);
            value.put("retryable", retryable);
            return value;
        }
    }

    static final class RegionIdentityPolicy implements ShipBehaviorAnalysisService.AlongsideComputePolicy {
        private final Set<String> ownMmsis;
        private final String ownImo;
        private final String seedBatchNo;
        private final StsSeedIdentityRepository seedRepository;

        RegionIdentityPolicy(StsRegionSetGeometry regions, Set<String> ownMmsis, String ownImo,
                             String seedBatchNo, StsSeedIdentityRepository seedRepository) {
            this.ownMmsis = ownMmsis;
            this.ownImo = ownImo;
            this.seedBatchNo = seedBatchNo;
            this.seedRepository = seedRepository;
        }

        @Override
        public boolean acceptWindow(ShipAlongsideDetector.LowSpeedWindow window,
                                    List<TrajectoryPoint> points) {
            return true;
        }

        @Override public boolean ownsEligibilityDecision() { return true; }

        @Override
        public boolean acceptCandidate(String targetMmsi) {
            if (ownMmsis.contains(targetMmsi)) return false;
            String targetImo = seedRepository.findHistoricalImoByMmsi(seedBatchNo, targetMmsi);
            return targetImo == null || !ownImo.equals(targetImo);
        }
    }

    interface SliceRunner {
        RunResult run();
    }

    public static class RunResult {
        public boolean success;
        public String batchNo;
        public String batchStatus;
        public String summary;
        public Long currentTaskId;
        public int totalTasks;
        public int completedTasks;
        public int failedTasks;
        public int warningTasks;
        public int batchFailedShips;
        public int batchTotalShadow;
        public int batchTotalProcessedShips;
        public int batchPhysicalObserved;
        public int batchStsCandidates;
        public int batchStsReviewRequired;
        public int batchServiceSuppressed;
        public int batchWeakSuppressed;
        public long batchElapsedMs;
        public int selectedPhysicalShips;
        public int selectedImos;
        public int queriedMmsis;
        public int processedPhysicalShips;
        public int trajectoryLoadedShips;
        public int ownTrajectoryPoints;
        public int regionMatchedShips;
        public int regionMatchedWindows;
        public int alongsideLowSpeedWindows;
        public int pairReplayQueries;
        public int pairReplaySuccessful;
        public int pairReplayFailures;
        public int pairReplayTruncated;
        public int pairReplayRawPoints;
        public final CandidateFunnelStats candidateFunnel = new CandidateFunnelStats();
        public int candidateTargets;
        public int selfIdentityRejected;
        public int alongsideEvents;
        public int regionAcceptedEvents;
        public int physicalObserved;
        public int physicalFormal;
        public int physicalWeak;
        public int stsCandidates;
        public int stsReviewRequired;
        public int serviceSuppressed;
        public int portBerthSuppressed;
        public int weakSuppressed;
        public int shadowInserted;
        public int shadowDuplicateSkipped;
        public int selfPairEvents;
        public int failedShips;
        public int currentSliceFailedShips;
        public boolean partial;
        public String outcome;
        public String fatalStage;
        public String stopReason;
        public int invocationSlices;
        public int currentSliceNumber;
        public LocalDateTime sliceFrom;
        public LocalDateTime sliceTo;
        public final List<ShipFailureDetail> shipFailures = new ArrayList<ShipFailureDetail>();
        public long durationMs;
        public String getSummary() { return summary; }
    }
}
