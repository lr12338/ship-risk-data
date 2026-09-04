package com.hifleet.sts.service;

import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.service.ShipAlongsideDetector;
import com.hifleet.sts.domain.StsBackfillBatchRow;
import com.hifleet.sts.domain.StsBackfillTaskRow;
import com.hifleet.sts.domain.StsFleetBackfillJobParams;
import com.hifleet.sts.domain.StsFleetShipIdentity;
import com.hifleet.sts.domain.StsRiskRegionRow;
import com.hifleet.sts.domain.StsShadowEvent;
import com.hifleet.sts.repository.StsBackfillBatchRepository;
import com.hifleet.sts.repository.StsBackfillTaskRepository;
import com.hifleet.sts.repository.StsRiskRegionRepository;
import com.hifleet.sts.repository.StsShadowEventRepository;
import com.hifleet.sts.repository.StsTargetFleetRepository;
import com.hifleet.trajectory.TrajectoryPoint;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class StsFleetBackfillService {

    private static final Logger log = LoggerFactory.getLogger(StsFleetBackfillService.class);
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String RUNNER_VERSION = "STS_PHASE1_4_FLEET_BACKFILL_V1";
    private static final String BASELINE_RULE = "ALONGSIDE_V3_BASELINE";
    private static final String BEHAVIOR_TYPE = "BASELINE_ALONGSIDE_CANDIDATE";
    private static final int MAX_SHIP_COUNT_PER_JOB = 5000;

    @Autowired
    private StsTargetFleetRepository targetFleetRepository;

    @Autowired
    private StsRiskRegionRepository riskRegionRepository;

    @Autowired
    private StsBackfillBatchRepository batchRepository;

    @Autowired
    private StsBackfillTaskRepository taskRepository;

    @Autowired
    private StsBaselineService stsBaselineService;

    @Autowired
    private StsFleetRegionPreselector regionPreselector;

    @Autowired
    private StsShadowEventRepository shadowEventRepository;

    public static class BackfillRunResult {
        public boolean success;
        public String batchNo;
        public String batchStatus;
        public String summary;
        public String targetListBatchNo;
        public int expectedTargetCount;
        public int actualTargetCount;
        public int shipOffset;
        public int shipCount;
        public int selectedShipCount;
        public String targetListFingerprint;
        public String regionSetCode;
        public int resolvedRegionCount;
        public String regionSetFingerprint;
        public String parentFrom;
        public String parentTo;
        public int sliceDays;
        public int totalTasks;
        public int completedTasks;
        public int failedTasks;
        public Long currentTaskId;
        public String currentSliceFrom;
        public String currentSliceTo;
        public int regionDiscoveredMmsis;
        public int candidatePhysicalShips;
        public int processedPhysicalShips;
        public int trajectoryLoadedShips;
        public int activityWindows;
        public int regionActivityWindows;
        public int replayQueries;
        public int replayFailures;
        public int replayTruncated;
        public int baselineEvents;
        public int regionAcceptedEvents;
        public int shadowInserted;
        public int shadowDuplicateSkipped;
        public int selfPairEvents;
        public long taskDurationMs;

        public String getSummary() { return summary; }
    }

    public BackfillRunResult runOneInvocation(StsFleetBackfillJobParams params) {
        validateParams(params);

        BackfillRunResult result = new BackfillRunResult();
        result.batchNo = params.getBatchNo();
        result.targetListBatchNo = params.getTargetListBatchNo();
        result.expectedTargetCount = params.getExpectedTargetCount();
        result.shipOffset = params.getShipOffset();
        result.shipCount = params.getShipCount();
        result.regionSetCode = params.getRegionSetCode();
        result.parentFrom = params.getFrom();
        result.parentTo = params.getTo();
        result.sliceDays = params.getSliceDays();

        // 1. Load and validate target fleet
        int actualCount = targetFleetRepository.countPhysicalShips(params.getTargetListBatchNo());
        result.actualTargetCount = actualCount;
        if (actualCount != params.getExpectedTargetCount()) {
            throw new IllegalArgumentException(
                    "expectedTargetCount mismatch: expected=" + params.getExpectedTargetCount()
                            + ", actual=" + actualCount
                            + " (targetListBatchNo=" + params.getTargetListBatchNo() + ")");
        }

        int conflictCount = targetFleetRepository.countMmsiIdentityConflicts(params.getTargetListBatchNo());
        if (conflictCount > 0) {
            throw new IllegalStateException(
                    "MMSI identity conflicts detected in target list: " + conflictCount);
        }

        // 2. Load selected fleet slice
        List<StsFleetShipIdentity> selectedFleet = targetFleetRepository.selectPhysicalShips(
                params.getTargetListBatchNo(), params.getShipOffset(), params.getShipCount());
        result.selectedShipCount = selectedFleet.size();
        if (selectedFleet.isEmpty()) {
            throw new IllegalArgumentException("no ships selected: offset=" + params.getShipOffset()
                    + " count=" + params.getShipCount());
        }

        String targetFingerprint = computeFleetFingerprint(selectedFleet);
        result.targetListFingerprint = targetFingerprint;

        // 3. Load region set
        List<StsRiskRegionRow> regionRows = riskRegionRepository.loadRegionSet(params.getRegionSetCode());
        if (regionRows == null || regionRows.isEmpty()) {
            throw new IllegalArgumentException(
                    "no enabled regions for regionSetCode=" + params.getRegionSetCode());
        }
        StsRegionSetGeometry regionSet = new StsRegionSetGeometry(
                params.getRegionSetCode(), regionRows);
        result.resolvedRegionCount = regionSet.size();
        String regionFingerprint = computeRegionSetFingerprint(regionRows);
        result.regionSetFingerprint = regionFingerprint;

        // 4. Config fingerprint
        String configFingerprint = computeConfigFingerprint(params);

        // 5. Init or resume batch
        StsBackfillBatchRow batch = batchRepository.findByBatchNo(params.getBatchNo());
        if (batch == null) {
            // First run: create batch and tasks
            createBatchAndTasks(params, targetFingerprint, regionFingerprint,
                    configFingerprint, actualCount, selectedFleet.size(), regionRows.size(), result);
        } else {
            // Resume: validate fingerprints
            validateBatchForResume(batch, params, targetFingerprint,
                    regionFingerprint, configFingerprint, actualCount, selectedFleet.size());
            result.totalTasks = batch.getTotalTasks();
            result.completedTasks = batch.getCompletedTasks();
            result.failedTasks = batch.getFailedTasks();
            result.batchStatus = batch.getStatus();
        }

        // 6. Claim next task
        StsBackfillTaskRow task = taskRepository.claimNextTask(params.getBatchNo());
        if (task == null) {
            // All tasks complete or blocked
            updateFinalBatchStatus(params.getBatchNo());
            StsBackfillBatchRow finalBatch = batchRepository.findByBatchNo(params.getBatchNo());
            result.batchStatus = finalBatch != null ? finalBatch.getStatus() : "UNKNOWN";
            result.totalTasks = finalBatch != null ? finalBatch.getTotalTasks() : 0;
            result.completedTasks = finalBatch != null ? finalBatch.getCompletedTasks() : 0;
            result.failedTasks = finalBatch != null ? finalBatch.getFailedTasks() : 0;
            result.success = result.failedTasks == 0;
            result.summary = String.format(
                    "STS fleet backfill: no task available, batch=%s, " +
                            "completed=%d/%d, failed=%d",
                    result.batchStatus, result.completedTasks, result.totalTasks, result.failedTasks);
            return result;
        }

        result.currentTaskId = task.getId();
        result.currentSliceFrom = StsBackfillPlanner.formatTime(task.getSliceFrom());
        result.currentSliceTo = StsBackfillPlanner.formatTime(task.getSliceTo());

        log.info("STS_FLEET_BACKFILL start task: batch={}, taskId={}, slice={}, {}-{}",
                params.getBatchNo(), task.getId(), task.getSliceSeq(),
                result.currentSliceFrom, result.currentSliceTo);

        long taskStartMs = System.currentTimeMillis();

        try {
            runOneTask(task, selectedFleet, regionRows, regionSet, params, result);
            taskRepository.markComplete(task.getId(),
                    result.candidatePhysicalShips, result.processedPhysicalShips,
                    result.trajectoryLoadedShips,
                    result.activityWindows, result.regionActivityWindows,
                    result.replayQueries, result.replayFailures, result.replayTruncated,
                    result.baselineEvents, result.regionAcceptedEvents,
                    result.shadowInserted, result.shadowDuplicateSkipped,
                    result.selfPairEvents);

            // Update parent batch progress
            batchRepository.updateProgress(params.getBatchNo(), 1, 0,
                    result.candidatePhysicalShips, result.processedPhysicalShips,
                    result.baselineEvents, result.shadowInserted, result.shadowDuplicateSkipped);

            result.success = result.selfPairEvents == 0;
            result.taskDurationMs = System.currentTimeMillis() - taskStartMs;

            log.info("STS_FLEET_BACKFILL task complete: taskId={}, candidates={}, processed={}, " +
                            "events={}, shadow={}, duration={}ms",
                    task.getId(), result.candidatePhysicalShips, result.processedPhysicalShips,
                    result.regionAcceptedEvents, result.shadowInserted, result.taskDurationMs);

        } catch (Exception ex) {
            log.error("STS_FLEET_BACKFILL task failed: taskId={}, error={}",
                    task.getId(), ex.getMessage(), ex);
            taskRepository.markFailed(task.getId(), ex.getMessage());
            batchRepository.updateProgress(params.getBatchNo(), 0, 1,
                    0, 0, 0, 0, 0);
            result.success = false;
            result.taskDurationMs = System.currentTimeMillis() - taskStartMs;
        }

        // Update summary
        StsBackfillBatchRow updatedBatch = batchRepository.findByBatchNo(params.getBatchNo());
        if (updatedBatch != null) {
            result.totalTasks = updatedBatch.getTotalTasks();
            result.completedTasks = updatedBatch.getCompletedTasks();
            result.failedTasks = updatedBatch.getFailedTasks();
            result.batchStatus = updatedBatch.getStatus();
        }

        // Update batch final status if all done
        if (result.completedTasks + result.failedTasks >= result.totalTasks) {
            updateFinalBatchStatus(params.getBatchNo());
        }

        result.summary = buildSummary(result);
        logSummary(result);
        return result;
    }

    private void runOneTask(StsBackfillTaskRow task,
                            List<StsFleetShipIdentity> fleet,
                            List<StsRiskRegionRow> regionRows,
                            StsRegionSetGeometry regionSet,
                            StsFleetBackfillJobParams params,
                            BackfillRunResult result) {
        LocalDateTime sliceFrom = StsBackfillPlanner.toLocalDateTime(task.getSliceFrom());
        LocalDateTime sliceTo = StsBackfillPlanner.toLocalDateTime(task.getSliceTo());

        // Collect all target fleet MMSIs
        Set<String> targetMmsis = new HashSet<String>();
        for (StsFleetShipIdentity id : fleet) {
            targetMmsis.addAll(id.getMmsis());
        }

        // Region-first preselection
        StsFleetRegionPreselector.PreselectResult preselect = regionPreselector.preselect(
                regionRows, sliceFrom, sliceTo, targetMmsis,
                params.getBatchNo() + ":s" + task.getSliceSeq());

        result.regionDiscoveredMmsis = preselect.allRegionMmsis.size();

        if (!preselect.complete) {
            throw new IllegalStateException(
                    "Region discovery incomplete: " + preselect.errorMessage
                            + " (queries=" + preselect.queryCount + ", "
                            + "truncated=" + preselect.truncatedQueries + ", "
                            + "failed=" + preselect.failedQueries + ", "
                            + "timeout=" + preselect.timeoutQueries + ")");
        }

        // Map discovered MMSIs back to physical ships
        Set<String> discoveredMmsis = preselect.discoveredTargetMmsis;
        List<StsFleetShipIdentity> candidateShips = new ArrayList<StsFleetShipIdentity>();
        Set<String> processedKeys = new HashSet<String>();
        for (StsFleetShipIdentity id : fleet) {
            for (String mmsi : id.getMmsis()) {
                if (discoveredMmsis.contains(mmsi)) {
                    if (!processedKeys.contains(id.getPhysicalShipKey())) {
                        candidateShips.add(id);
                        processedKeys.add(id.getPhysicalShipKey());
                    }
                    break;
                }
            }
        }

        result.candidatePhysicalShips = candidateShips.size();

        if (candidateShips.isEmpty()) {
            // Zero candidate is a success
            return;
        }

        // Build catalog for candidate ships only
        StsBackfillFleetCatalog catalog = new StsBackfillFleetCatalog(candidateShips);

        // Build per-ship result accumulator (via hooks)
        final TaskAccumulator acc = new TaskAccumulator();

        // Build pipeline hooks
        StsBaselineService.StsPipelineHooks hooks = buildHooks(regionSet, acc, params, catalog);

        // Build legacy params for the slice
        com.hifleet.sts.domain.StsJobParams legacyParams = buildLegacyParams(
                sliceFrom, sliceTo);

        // Run each candidate physical ship
        for (StsFleetShipIdentity ship : candidateShips) {
            try {
                String key = ship.getPhysicalShipKey();
                StsBaselineService.StsImoResult imoResult =
                        stsBaselineService.runOneImoWithHooks(
                                key, legacyParams, params.getBatchNo(), hooks, catalog);

                acc.trajectoryLoadedShips++;
                acc.trajectoryPoints += imoResult.trajectoryPoints;
                acc.activityWindows += imoResult.activityWindows;
                acc.replayQueries += imoResult.replayQueries;
                acc.replayFailures += imoResult.replayFailures;
                acc.replayTruncated += imoResult.replayTruncated;
                acc.selfPairEvents += imoResult.selfPairEvents;
                acc.baselineEvents += imoResult.baselineEvents;
                result.processedPhysicalShips++;
            } catch (Exception ex) {
                log.error("STS backfill ship failed: key={}, error={}",
                        ship.getPhysicalShipKey(), ex.getMessage());
                acc.failedShips++;
            }
        }

        if (acc.selfPairEvents > 0) {
            throw new IllegalStateException(
                    "selfPairEvents > 0: " + acc.selfPairEvents
                            + " — fail closed, task blocked");
        }

        result.trajectoryLoadedShips = acc.trajectoryLoadedShips;
        result.activityWindows = acc.activityWindows;
        result.regionActivityWindows = acc.regionActivityWindows;
        result.replayQueries = acc.replayQueries;
        result.replayFailures = acc.replayFailures;
        result.replayTruncated = acc.replayTruncated;
        result.baselineEvents = acc.baselineEvents;
        result.regionAcceptedEvents = acc.regionAcceptedEvents;
        result.shadowInserted = acc.shadowInserted;
        result.shadowDuplicateSkipped = acc.shadowDuplicateSkipped;
        result.selfPairEvents = acc.selfPairEvents;
    }

    private StsBaselineService.StsPipelineHooks buildHooks(
            final StsRegionSetGeometry regionSet,
            final TaskAccumulator acc,
            final StsFleetBackfillJobParams params,
            final StsBackfillFleetCatalog catalog) {

        return new StsBaselineService.StsPipelineHooks() {
            @Override
            public boolean acceptWindow(String mmsi, ShipAlongsideDetector.LowSpeedWindow window,
                                        List<TrajectoryPoint> windowPoints) {
                boolean accepted = false;
                for (TrajectoryPoint p : windowPoints) {
                    double lon = p.getLon();
                    double lat = p.getLat();
                    if (Double.isNaN(lon) || Double.isNaN(lat)) continue;
                    if (regionSet.analysisContainsAny(
                            new org.locationtech.jts.geom.Coordinate(lon, lat))) {
                        accepted = true;
                        break;
                    }
                }
                if (accepted) {
                    acc.regionActivityWindows++;
                }
                return accepted;
            }

            @Override
            public void onEvent(ShipBehaviorEvent event, String ownMmsi, String targetMmsi) {
                Double clon = event.getCenterLon();
                Double clat = event.getCenterLat();
                if (clon == null || clat == null) {
                    return;
                }

                StsRegionSetGeometry.AttributionResult attr =
                        regionSet.attributePrimary(
                                new org.locationtech.jts.geom.Coordinate(clon, clat));

                if (!attr.hasMatch()) {
                    return;
                }
                acc.regionAcceptedEvents++;

                StsRegionSetGeometry.ResolvedRegion primary = attr.primary;

                // Find own identity key
                String ownKey = catalog.findImoByMmsi(ownMmsi);
                String ownImo = ownKey != null ? catalog.getActualImo(ownKey) : null;
                String targetImo = catalog.findImoByMmsi(targetMmsi);

                String eventKey = StsRegionSetShadowService.computeEventKey(
                        primary.regionCode,
                        ownKey != null ? ownKey : ownMmsi,
                        ownMmsi, targetMmsi,
                        event.getStartTime(), event.getEndTime(), BASELINE_RULE);

                List<String> matchedCodes = new ArrayList<String>();
                for (StsRegionSetGeometry.ResolvedRegion m : attr.allMatches) {
                    matchedCodes.add(m.regionCode);
                }

                StsShadowEvent shadow = new StsShadowEvent();
                shadow.setBatchNo(params.getBatchNo());
                shadow.setEventKey(eventKey);
                shadow.setAlgorithmVersion(RUNNER_VERSION);
                shadow.setSeedBatchNo(params.getTargetListBatchNo());
                shadow.setRegionSetCode(params.getRegionSetCode());
                shadow.setRegionId(primary.regionCode);
                shadow.setRegionVersion(primary.regionVersion);
                shadow.setOwnImo(ownImo);
                shadow.setOwnMmsi(ownMmsi);
                shadow.setTargetImo(targetImo);
                shadow.setTargetMmsi(targetMmsi);
                shadow.setIdentityMode(
                        ownKey != null ? catalog.getIdentityMode(ownKey) : "UNKNOWN");
                shadow.setStartTime(event.getStartTime());
                shadow.setEndTime(event.getEndTime());
                if (event.getDurationMinutes() != null) {
                    shadow.setDurationMinutes(event.getDurationMinutes().intValue());
                }
                shadow.setCenterLon(clon);
                shadow.setCenterLat(clat);
                shadow.setBehaviorType(BEHAVIOR_TYPE);
                shadow.setBaselineRule(BASELINE_RULE);
                shadow.setMinDistanceNm(event.getMinDistanceNm());
                shadow.setAvgDistanceNm(event.getAvgDistanceNm());
                shadow.setOwnAvgSpeedKn(event.getAvgSpeed());
                shadow.setBehaviorScore(event.getEventScore());
                shadow.setReviewStatus("PENDING");

                String existingDetail = event.getScoreDetailJson();
                shadow.setScoreDetailJson(enrichWithMatchedRegions(existingDetail, matchedCodes));

                try {
                    int inserted = shadowEventRepository.insertIfAbsent(shadow);
                    if (inserted > 0) {
                        acc.shadowInserted++;
                    } else {
                        acc.shadowDuplicateSkipped++;
                    }
                } catch (Exception e) {
                    log.error("STS backfill shadow insert failed: ownMmsi={} targetMmsi={}: {}",
                            ownMmsi, targetMmsi, e.getMessage());
                    throw new RuntimeException("shadow event insert failed", e);
                }
            }
        };
    }

    private String enrichWithMatchedRegions(String existingDetail, List<String> matchedCodes) {
        if (matchedCodes.size() <= 1) {
            return existingDetail;
        }
        try {
            JSONObject detail = existingDetail != null && !existingDetail.isEmpty()
                    ? JSONObject.parseObject(existingDetail)
                    : new JSONObject();
            com.alibaba.fastjson.JSONArray arr = new com.alibaba.fastjson.JSONArray();
            for (String code : matchedCodes) arr.add(code);
            detail.put("matchedRegionCodes", arr);
            return detail.toJSONString();
        } catch (Exception e) {
            return existingDetail;
        }
    }

    private com.hifleet.sts.domain.StsJobParams buildLegacyParams(
            LocalDateTime from, LocalDateTime to) {
        com.hifleet.sts.domain.StsJobParams p = new com.hifleet.sts.domain.StsJobParams();
        p.setMode("STS_PHASE1_REGION_SHADOW");
        p.setFrom(from.format(TIME_FMT));
        p.setTo(to.format(TIME_FMT));
        p.setWindowHours(24);
        p.setActivityMinSpeedKn(0.0);
        p.setActivityMaxSpeedKn(4.0);
        p.setParallelism(1);
        p.setWriteMode("SHADOW_ONLY");
        return p;
    }

    private void validateParams(StsFleetBackfillJobParams params) {
        if (StringUtils.isBlank(params.getTargetListBatchNo())) {
            throw new IllegalArgumentException("targetListBatchNo is required");
        }
        if (params.getExpectedTargetCount() <= 0) {
            throw new IllegalArgumentException("expectedTargetCount must be > 0");
        }
        if (params.getShipOffset() < 0) {
            throw new IllegalArgumentException("shipOffset must be >= 0");
        }
        if (params.getShipCount() <= 0 || params.getShipCount() > MAX_SHIP_COUNT_PER_JOB) {
            throw new IllegalArgumentException(
                    "shipCount must be 1-" + MAX_SHIP_COUNT_PER_JOB
                            + ", got: " + params.getShipCount());
        }
        if (StringUtils.isBlank(params.getRegionSetCode())) {
            throw new IllegalArgumentException("regionSetCode is required");
        }
        if (StringUtils.isBlank(params.getFrom()) || StringUtils.isBlank(params.getTo())) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (StringUtils.isBlank(params.getBatchNo())) {
            throw new IllegalArgumentException("batchNo is required");
        }
    }

    private void createBatchAndTasks(StsFleetBackfillJobParams params,
                                     String targetFingerprint, String regionFingerprint,
                                     String configFingerprint, int actualCount,
                                     int selectedCount, int regionCount,
                                     BackfillRunResult result) {
        // Validate time range and plan tasks
        StsBackfillPlanner planner = new StsBackfillPlanner(
                params.getBatchNo(), params.getFrom(), params.getTo(), params.getSliceDays());
        planner.validate();
        List<StsBackfillTaskRow> tasks = planner.planTasks();

        StsBackfillBatchRow batch = new StsBackfillBatchRow();
        batch.setBatchNo(params.getBatchNo());
        batch.setRunnerVersion(RUNNER_VERSION);
        batch.setConfigFingerprint(configFingerprint);
        batch.setTargetListBatchNo(params.getTargetListBatchNo());
        batch.setTargetListFingerprint(targetFingerprint);
        batch.setExpectedTargetCount(params.getExpectedTargetCount());
        batch.setActualTargetCount(actualCount);
        batch.setShipOffset(params.getShipOffset());
        batch.setShipCount(params.getShipCount());
        batch.setSelectedShipCount(selectedCount);
        batch.setRegionSetCode(params.getRegionSetCode());
        batch.setRegionSetFingerprint(regionFingerprint);
        batch.setResolvedRegionCount(regionCount);
        batch.setFromTime(java.sql.Timestamp.valueOf(
                LocalDateTime.parse(params.getFrom(), TIME_FMT)));
        batch.setToTime(java.sql.Timestamp.valueOf(
                LocalDateTime.parse(params.getTo(), TIME_FMT)));
        batch.setSliceDays(params.getSliceDays());
        batch.setStatus("RUNNING");
        batch.setTotalTasks(tasks.size());
        batch.setCompletedTasks(0);
        batch.setFailedTasks(0);

        batchRepository.insert(batch);
        taskRepository.insertTasks(tasks);

        result.totalTasks = tasks.size();
        result.completedTasks = 0;
        result.failedTasks = 0;
        result.batchStatus = "RUNNING";
    }

    private void validateBatchForResume(StsBackfillBatchRow batch,
                                        StsFleetBackfillJobParams params,
                                        String targetFingerprint,
                                        String regionFingerprint,
                                        String configFingerprint,
                                        int actualCount, int selectedCount) {
        if (!targetFingerprint.equals(batch.getTargetListFingerprint())) {
            throw new IllegalStateException(
                    "TARGET_LIST_CHANGED_AFTER_BATCH_INIT: " +
                            "batch target fingerprint=" + batch.getTargetListFingerprint() +
                            ", current=" + targetFingerprint);
        }
        if (!regionFingerprint.equals(batch.getRegionSetFingerprint())) {
            throw new IllegalStateException(
                    "REGION_SET_CHANGED_AFTER_BATCH_INIT: " +
                            "batch region fingerprint=" + batch.getRegionSetFingerprint() +
                            ", current=" + regionFingerprint);
        }
        if (!configFingerprint.equals(batch.getConfigFingerprint())) {
            throw new IllegalStateException(
                    "BATCH_CONFIG_CONFLICT: config fingerprint mismatch " +
                            "(batch=" + batch.getConfigFingerprint() +
                            ", current=" + configFingerprint + ")");
        }
        if (actualCount != batch.getActualTargetCount()) {
            throw new IllegalStateException(
                    "TARGET_COUNT_CHANGED: batch actual=" + batch.getActualTargetCount() +
                            ", current=" + actualCount);
        }
        if (selectedCount != batch.getSelectedShipCount()) {
            throw new IllegalStateException(
                    "SELECTED_COUNT_CHANGED: batch selected=" + batch.getSelectedShipCount() +
                            ", current=" + selectedCount);
        }
    }

    private void updateFinalBatchStatus(String batchNo) {
        StsBackfillBatchRow batch = batchRepository.findByBatchNo(batchNo);
        if (batch == null) return;

        int done = batch.getCompletedTasks() + batch.getFailedTasks();
        if (done < batch.getTotalTasks()) return;

        String status;
        String error = null;
        if (batch.getFailedTasks() > 0) {
            status = "PARTIAL_FAILED";
            error = batch.getFailedTasks() + " tasks failed";
        } else {
            status = "COMPLETE";
        }
        batchRepository.markFinished(batchNo, status, error);
    }

    public static String computeFleetFingerprint(List<StsFleetShipIdentity> fleet) {
        List<String> lines = new ArrayList<String>();
        for (StsFleetShipIdentity id : fleet) {
            lines.add(id.toFingerprintLine());
        }
        Collections.sort(lines);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        return sha256(sb.toString());
    }

    public static String computeRegionSetFingerprint(List<StsRiskRegionRow> regions) {
        List<String> lines = new ArrayList<String>();
        for (StsRiskRegionRow r : regions) {
            StringBuilder sb = new StringBuilder();
            sb.append(r.getRegionCode()).append('|');
            sb.append(r.getRegionVersion()).append('|');
            sb.append(r.getGeometryFingerprint() == null ? "" : r.getGeometryFingerprint()).append('|');
            sb.append(r.getAnalysisBufferNm()).append('|');
            sb.append(r.getPriority());
            lines.add(sb.toString());
        }
        Collections.sort(lines);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        return sha256(sb.toString());
    }

    private static String computeConfigFingerprint(StsFleetBackfillJobParams params) {
        StringBuilder sb = new StringBuilder();
        sb.append("targetListBatchNo=").append(params.getTargetListBatchNo()).append('\n');
        sb.append("expectedTargetCount=").append(params.getExpectedTargetCount()).append('\n');
        sb.append("shipOffset=").append(params.getShipOffset()).append('\n');
        sb.append("shipCount=").append(params.getShipCount()).append('\n');
        sb.append("regionSetCode=").append(params.getRegionSetCode()).append('\n');
        sb.append("from=").append(params.getFrom()).append('\n');
        sb.append("to=").append(params.getTo()).append('\n');
        sb.append("sliceDays=").append(params.getSliceDays()).append('\n');
        sb.append("batchNo=").append(params.getBatchNo()).append('\n');
        return sha256(sb.toString());
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String buildSummary(BackfillRunResult r) {
        return String.format(
                "STS fleet backfill: batch=%s, task=%s, candidates=%d/%d, " +
                        "processed=%d, events=%d, shadow=%d, status=%s, %d/%d tasks",
                r.batchNo,
                r.currentTaskId != null ? r.currentTaskId : "none",
                r.candidatePhysicalShips, r.selectedShipCount,
                r.processedPhysicalShips, r.regionAcceptedEvents,
                r.shadowInserted, r.batchStatus,
                r.completedTasks, r.totalTasks);
    }

    private void logSummary(BackfillRunResult r) {
        JSONObject s = new JSONObject();
        s.put("event", "STS_FLEET_BACKFILL_SUMMARY");
        s.put("batchNo", r.batchNo);
        s.put("runnerVersion", RUNNER_VERSION);
        s.put("targetListBatchNo", r.targetListBatchNo);
        s.put("expectedTargetCount", r.expectedTargetCount);
        s.put("actualTargetCount", r.actualTargetCount);
        s.put("shipOffset", r.shipOffset);
        s.put("shipCount", r.shipCount);
        s.put("selectedShipCount", r.selectedShipCount);
        s.put("targetListFingerprint", r.targetListFingerprint);
        s.put("regionSetCode", r.regionSetCode);
        s.put("resolvedRegionCount", r.resolvedRegionCount);
        s.put("regionSetFingerprint", r.regionSetFingerprint);
        s.put("parentFrom", r.parentFrom);
        s.put("parentTo", r.parentTo);
        s.put("sliceDays", r.sliceDays);
        s.put("totalTasks", r.totalTasks);
        s.put("completedTasks", r.completedTasks);
        s.put("failedTasks", r.failedTasks);
        s.put("currentTaskId", r.currentTaskId);
        s.put("currentSliceFrom", r.currentSliceFrom);
        s.put("currentSliceTo", r.currentSliceTo);
        s.put("regionDiscoveredMmsis", r.regionDiscoveredMmsis);
        s.put("candidatePhysicalShips", r.candidatePhysicalShips);
        s.put("processedPhysicalShips", r.processedPhysicalShips);
        s.put("trajectoryLoadedShips", r.trajectoryLoadedShips);
        s.put("activityWindows", r.activityWindows);
        s.put("regionActivityWindows", r.regionActivityWindows);
        s.put("replayQueries", r.replayQueries);
        s.put("replayFailures", r.replayFailures);
        s.put("replayTruncated", r.replayTruncated);
        s.put("baselineEvents", r.baselineEvents);
        s.put("regionAcceptedEvents", r.regionAcceptedEvents);
        s.put("shadowInserted", r.shadowInserted);
        s.put("shadowDuplicateSkipped", r.shadowDuplicateSkipped);
        s.put("selfPairEvents", r.selfPairEvents);
        s.put("taskDurationMs", r.taskDurationMs);
        s.put("parentStatus", r.batchStatus);
        log.info(s.toJSONString());
    }

    private static class TaskAccumulator {
        int trajectoryLoadedShips;
        int trajectoryPoints;
        int activityWindows;
        int regionActivityWindows;
        int replayQueries;
        int replayFailures;
        int replayTruncated;
        int baselineEvents;
        int regionAcceptedEvents;
        int shadowInserted;
        int shadowDuplicateSkipped;
        int selfPairEvents;
        int failedShips;
    }
}
