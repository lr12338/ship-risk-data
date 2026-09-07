package com.hifleet.sts.service;

import com.alibaba.fastjson.JSONObject;
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
    static final String RUNNER_VERSION = "ALONGSIDE_DERIVED_STS_CANDIDATE_V1";
    static final String BEHAVIOR_TYPE = "ALONGSIDE_DERIVED_STS_CANDIDATE";
    private static final int EXPECTED_IMOS = 757;
    private static final int EXPECTED_IDENTITIES = 825;
    private static final String MEETING_REGION_SET = "STS_MEETING_PRIORITY_REGION_SET_V1";
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
        params.validate();
        validateSeed(params.getSeedBatchNo());
        List<String> selectedImos = params.getImos().isEmpty()
                ? seedRepository.selectImos(params.getSeedBatchNo(), params.getShipOffset(), params.getShipCount())
                : validateExplicitImos(params);
        if (selectedImos.isEmpty()) throw new IllegalArgumentException("no seed IMOs selected");
        List<StsSeedIdentityRow> identities = seedRepository.selectIdentityRows(
                params.getSeedBatchNo(), selectedImos);
        Map<String, List<StsSeedIdentityRow>> rowsByImo = groupByImo(identities);
        if (rowsByImo.size() != selectedImos.size()) {
            throw new IllegalStateException("selected IMO identity rows are incomplete");
        }

        List<StsRiskRegionRow> regionRows = regionRepository.loadRegionSet(params.getRegionSetCode());
        if (regionRows == null || regionRows.isEmpty()) {
            throw new IllegalArgumentException("no enabled regions for regionSetCode=" + params.getRegionSetCode());
        }
        validateMeetingRegions(params.getRegionSetCode(), regionRows);
        StsRegionSetGeometry regions = new StsRegionSetGeometry(params.getRegionSetCode(), regionRows);
        String identityFingerprint = fingerprintIdentities(identities);
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
                configFingerprint, selectedImos.size(), regionRows.size());

        StsBackfillTaskRow task = taskRepository.claimNextTask(params.getBatchNo());
        if (task == null) {
            refreshBatch(result);
            result.success = result.failedTasks == 0;
            result.summary = "STS alongside history: no task available, batch=" + params.getBatchNo();
            logSummary(result);
            return result;
        }

        result.currentTaskId = task.getId();
        LocalDateTime sliceFrom = toLocalDateTime(task.getSliceFrom());
        LocalDateTime sliceTo = toLocalDateTime(task.getSliceTo());
        long started = System.currentTimeMillis();
        StsBackfillTaskHeartbeat.Handle heartbeat = taskHeartbeat.start(task.getId());
        try {
            processSlice(params, rowsByImo, regions, regionFingerprint, ruleVersion,
                    ruleHash, codeSha, sliceFrom, sliceTo, result);
            if (result.selfPairEvents > 0) {
                throw new IllegalStateException("selfPairEvents > 0: " + result.selfPairEvents);
            }
            if (result.failedShips > 0) {
                throw new IllegalStateException("FAILED_SHIPS=" + result.failedShips);
            }
            result.shadowInserted = shadowRepository.countByBatchAndWindow(
                    params.getBatchNo(), sliceFrom, sliceTo);
            markTaskComplete(task.getId(), result);
            result.success = true;
        } catch (Exception ex) {
            try {
                result.shadowInserted = shadowRepository.countByBatchAndWindow(
                        params.getBatchNo(), sliceFrom, sliceTo);
            } catch (Exception countEx) {
                ex.addSuppressed(countEx);
                log.warn("failed to reconcile shadow count for failed taskId={}", task.getId(), countEx);
            }
            markTaskFailed(task.getId(), ex.getMessage(), result);
            result.success = false;
            throw ex instanceof RuntimeException
                    ? (RuntimeException) ex : new IllegalStateException(ex);
        } finally {
            heartbeat.close();
            result.durationMs = System.currentTimeMillis() - started;
            refreshBatch(result);
            logSummary(result);
        }
        result.summary = "STS alongside history: batch=" + params.getBatchNo()
                + ", ships=" + result.selectedPhysicalShips
                + ", events=" + result.regionAcceptedEvents
                + ", shadow=" + result.shadowInserted;
        return result;
    }

    private void processSlice(final StsAlongsideHistoryJobParams params,
                              Map<String, List<StsSeedIdentityRow>> rowsByImo,
                              final StsRegionSetGeometry regions, String regionFingerprint,
                              String ruleVersion, String ruleHash, String codeSha,
                              LocalDateTime sliceFrom, LocalDateTime sliceTo, RunResult total) {
        for (Map.Entry<String, List<StsSeedIdentityRow>> entry : rowsByImo.entrySet()) {
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
                    total.pairReplayFailures += computed.getFailedReplayQueries();
                    total.pairReplayTruncated += computed.getTruncatedReplayQueryCount();
                    total.pairReplayRawPoints += computed.getPairReplayRawPoints();
                    total.candidateTargets += computed.getCandidateTargets();
                    total.selfIdentityRejected += computed.getPolicyRejectedCandidates();
                    total.alongsideEvents += computed.getEvents();
                    requireComplete(row.getMmsi(), computed);
                    for (ShipBehaviorTrace trace : computed.getTraces()) {
                        persistCandidate(params, regions, regionFingerprint, row, imo,
                                ownMmsis, trace.getEvent(), ruleVersion, ruleHash, codeSha, total);
                    }
                }
                total.processedPhysicalShips++;
                if (trajectoryLoaded) total.trajectoryLoadedShips++;
                if (regionMatched) total.regionMatchedShips++;
            } catch (Exception ex) {
                total.failedShips++;
                log.error("STS alongside history ship failed: imo={}", imo, ex);
            }
        }
    }

    void persistCandidate(StsAlongsideHistoryJobParams params, StsRegionSetGeometry regions,
                                  String regionFingerprint, StsSeedIdentityRow own, String ownImo,
                                  Set<String> ownMmsis, ShipBehaviorEvent event, String ruleVersion,
                                  String ruleHash, String codeSha, RunResult result) {
        String targetMmsi = event.getTargetMmsi();
        String targetImo = seedRepository.findImoByMmsi(params.getSeedBatchNo(), targetMmsi);
        if (ownMmsis.contains(targetMmsi) || ownImo.equals(targetImo)
                || own.getMmsi().equals(targetMmsi)) {
            result.selfPairEvents++;
            return;
        }
        if (event.getCenterLon() == null || event.getCenterLat() == null) return;
        StsRegionSetGeometry.AttributionResult attribution = regions.attributePrimary(
                new Coordinate(event.getCenterLon(), event.getCenterLat()));
        if (!attribution.hasMatch()) return;
        result.regionAcceptedEvents++;

        StsShadowEvent shadow = new StsShadowEvent();
        shadow.setBatchNo(params.getBatchNo());
        String ownPhysicalIdentity = ownImo == null ? own.getMmsi() : "IMO:" + ownImo;
        String targetPhysicalIdentity = targetImo == null ? targetMmsi : "IMO:" + targetImo;
        shadow.setEventKey(StsRegionSetShadowService.computeEventKey(
                attribution.primary.regionCode, ownPhysicalIdentity, ownPhysicalIdentity,
                targetPhysicalIdentity, event.getStartTime(), event.getEndTime(),
                ruleVersion + "|" + ruleHash));
        shadow.setAlgorithmVersion(RUNNER_VERSION);
        shadow.setSeedBatchNo(params.getSeedBatchNo());
        shadow.setRegionSetCode(params.getRegionSetCode());
        shadow.setRegionId(attribution.primary.regionCode);
        shadow.setRegionVersion(attribution.primary.regionVersion);
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
        shadow.setOwnAvgSpeedKn(event.getAvgSpeed());
        shadow.setBehaviorScore(event.getEventScore());
        shadow.setReviewStatus("PENDING");
        JSONObject provenance = parseDetail(event.getScoreDetailJson());
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

    private List<String> validateExplicitImos(StsAlongsideHistoryJobParams params) {
        List<StsSeedIdentityRow> rows = seedRepository.selectIdentityRows(
                params.getSeedBatchNo(), params.getImos());
        Set<String> found = new HashSet<String>();
        for (StsSeedIdentityRow row : rows) found.add(row.getImo());
        if (found.size() != params.getImos().size()) {
            throw new IllegalArgumentException("one or more explicit IMOs are absent from seed batch");
        }
        return params.getImos();
    }

    private void validateSeed(String seedBatchNo) {
        int imos = seedRepository.countDistinctImos(seedBatchNo);
        int rows = seedRepository.countIdentityRows(seedBatchNo);
        int mmsis = seedRepository.countDistinctMmsis(seedBatchNo);
        if (imos != EXPECTED_IMOS || rows != EXPECTED_IDENTITIES || mmsis != EXPECTED_IDENTITIES) {
            throw new IllegalStateException("seed acceptance mismatch: imos=" + imos
                    + ", rows=" + rows + ", mmsis=" + mmsis);
        }
        if (seedRepository.countNonCrudeRows(seedBatchNo) > 0) {
            throw new IllegalStateException("seed contains enabled non-crude identity rows");
        }
    }

    static void validateMeetingRegions(String regionSetCode, List<StsRiskRegionRow> rows) {
        if (!MEETING_REGION_SET.equals(regionSetCode)) {
            throw new IllegalArgumentException("regionSetCode must be " + MEETING_REGION_SET);
        }
        Set<String> expected = new HashSet<String>();
        Collections.addAll(expected,
                "PERSIAN_GULF_TEST_V1|20260902-v1",
                "GULF_OMAN_FUJAIRAH_STS_V2|20260903-v2",
                "SOHAR_STS_V2|20260903-v2",
                "SINGAPORE_MALACCA_TEST_V1|20260903-v1",
                "VENEZUELA_JOSE_TEST_V1|20260903-v1",
                "VENEZUELA_PARAGUANA_TEST_V1|20260903-v1",
                "RUSSIA_BALTIC_TEST_V1|20260903-v1",
                "RUSSIA_BLACK_SEA_TEST_V1|20260903-v1",
                "RUSSIA_FAR_EAST_TEST_V1|20260903-v1");
        Set<String> actual = new HashSet<String>();
        for (StsRiskRegionRow row : rows) {
            actual.add(row.getRegionCode() + "|" + row.getRegionVersion());
        }
        if (!expected.equals(actual)) {
            throw new IllegalStateException("meeting region set mismatch: expected="
                    + expected + ", actual=" + actual);
        }
    }

    static void requireComplete(String mmsi,
                                ShipBehaviorAnalysisService.AlongsideWindowResult computed) {
        if (computed.getFailed() > 0 || computed.getFailedReplayQueries() > 0
                || computed.getTruncatedReplayQueryCount() > 0) {
            throw new IllegalStateException("incomplete alongside compute: mmsi="
                    + mmsi + ", candidateFailures=" + computed.getFailed()
                    + ", replayFailures=" + computed.getFailedReplayQueries()
                    + ", replayTruncated=" + computed.getTruncatedReplayQueryCount());
        }
    }

    private void initializeOrValidateBatch(StsAlongsideHistoryJobParams params,
                                           String identityFingerprint, String regionFingerprint,
                                           String configFingerprint, int selectedCount, int regionCount) {
        final StsBackfillPlanner planner = new StsBackfillPlanner(
                params.getBatchNo(), params.getFrom(), params.getTo(), params.getSliceDays());
        planner.validate();
        final List<StsBackfillTaskRow> tasks = planner.planTasks();
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
                batch.setExpectedTargetCount(EXPECTED_IMOS);
                batch.setActualTargetCount(EXPECTED_IMOS);
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
        }
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
        s.put("shadowInserted", r.shadowInserted);
        s.put("shadowDuplicateSkipped", r.shadowDuplicateSkipped);
        s.put("selfPairEvents", r.selfPairEvents);
        s.put("failedShips", r.failedShips);
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

    private static String fingerprintIdentities(List<StsSeedIdentityRow> rows) {
        List<String> lines = new ArrayList<String>();
        for (StsSeedIdentityRow row : rows) {
            lines.add(row.getImo() + "|" + row.getMmsi() + "|" + row.getFirstSeen()
                    + "|" + row.getLastSeen() + "|" + row.getIdentityMode());
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

    static final class RegionIdentityPolicy implements ShipBehaviorAnalysisService.AlongsideComputePolicy {
        private final StsRegionSetGeometry regions;
        private final Set<String> ownMmsis;
        private final String ownImo;
        private final String seedBatchNo;
        private final StsSeedIdentityRepository seedRepository;

        RegionIdentityPolicy(StsRegionSetGeometry regions, Set<String> ownMmsis, String ownImo,
                             String seedBatchNo, StsSeedIdentityRepository seedRepository) {
            this.regions = regions;
            this.ownMmsis = ownMmsis;
            this.ownImo = ownImo;
            this.seedBatchNo = seedBatchNo;
            this.seedRepository = seedRepository;
        }

        @Override
        public boolean acceptWindow(ShipAlongsideDetector.LowSpeedWindow window,
                                    List<TrajectoryPoint> points) {
            if (points == null) return false;
            for (TrajectoryPoint point : points) {
                if (regions.analysisContainsAny(new Coordinate(point.getLon(), point.getLat()))) return true;
            }
            return false;
        }

        @Override
        public boolean acceptCandidate(String targetMmsi) {
            if (ownMmsis.contains(targetMmsi)) return false;
            String targetImo = seedRepository.findImoByMmsi(seedBatchNo, targetMmsi);
            return targetImo == null || !ownImo.equals(targetImo);
        }
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
        public int pairReplayFailures;
        public int pairReplayTruncated;
        public int pairReplayRawPoints;
        public int candidateTargets;
        public int selfIdentityRejected;
        public int alongsideEvents;
        public int regionAcceptedEvents;
        public int shadowInserted;
        public int shadowDuplicateSkipped;
        public int selfPairEvents;
        public int failedShips;
        public long durationMs;
        public String getSummary() { return summary; }
    }
}
