package com.hifleet.job.services;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.beans.ShipForm;
import com.hifleet.domain.AlongsideCandidateTarget;
import com.hifleet.domain.AlongsideDailyStats;
import com.hifleet.domain.AlongsideReplaySearchResult;
import com.hifleet.domain.CandidateFunnelStats;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorEventDetail;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.repository.AIS41GeneralQueryRepository;
import com.hifleet.repository.ShipBehaviorAlongsideBatchStatusRepository;
import com.hifleet.repository.ShipBehaviorEventDetailRepository;
import com.hifleet.repository.ShipBehaviorEventRepository;
import com.hifleet.repository.ShipBehaviorLoiteringBatchStatusRepository;
import com.hifleet.service.ShipAlongsideDetector;
import com.hifleet.service.ShipAlongsideReplayCandidateProvider;
import com.hifleet.service.AlongsideReplayQueryPlan;
import com.hifleet.service.AlongsideReplayQueryPlanner;
import com.hifleet.service.ShipLoiteringDetector;
import com.hifleet.service.ShipTypeClassifier;
import com.hifleet.service.TraceRecipeService;
import com.hifleet.service.AlongsideSceneService;
import com.hifleet.service.OwnShipEligibility;
import com.hifleet.service.TargetRoleClassification;
import com.hifleet.trajectory.TrajectoryLoaderService;
import com.hifleet.trajectory.TrajectoryPoint;
import com.hifleet.trajectory.TrajectoryUtils;
import com.hifleet.worldsea.WorldSeaEventGate;
import com.hifleet.worldsea.WorldSeaMaskService;
import com.hifleet.worldsea.WorldSeaTrackSegmenter;
import com.hifleet.loitering.LoiteringPortEventGate;
import com.hifleet.loitering.LoiteringPortSceneProperties;
import com.hifleet.loitering.LoiteringPortSceneService;
import com.hifleet.loitering.LoiteringPortTrackSegmenter;
import com.xxl.job.core.log.XxlJobLogger;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ShipBehaviorAnalysisService {

    public interface AlongsideComputePolicy {
        boolean acceptWindow(ShipAlongsideDetector.LowSpeedWindow window,
                             List<TrajectoryPoint> ownWindowPoints);
        boolean acceptCandidate(String targetMmsi);
        default boolean ownsEligibilityDecision() { return false; }
    }

    private static final AlongsideComputePolicy ACCEPT_ALL_ALONGSIDE = new AlongsideComputePolicy() {
        @Override
        public boolean acceptWindow(ShipAlongsideDetector.LowSpeedWindow window,
                                    List<TrajectoryPoint> ownWindowPoints) {
            return true;
        }

        @Override
        public boolean acceptCandidate(String targetMmsi) {
            return true;
        }
    };

    private static final Logger log = LoggerFactory.getLogger(ShipBehaviorAnalysisService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ShipLoiteringDetector loiteringDetector = new ShipLoiteringDetector();
    private final ShipAlongsideDetector alongsideDetector = new ShipAlongsideDetector();

    @Value("${ship.behavior.trajectory.simplify-zoom:15}")
    private int trajectorySimplifyZoom;

    @Value("${ship.behavior.default-hours:6}")
    private int defaultHours;

    @Value("${ship.behavior.loitering.max-speed:3.0}")
    private double loiteringMaxSpeed;

    @Value("${ship.behavior.loitering.min-duration-minutes:30}")
    private long loiteringMinDurationMinutes;

    @Value("${ship.behavior.loitering.max-radius-nm:1.0}")
    private double loiteringMaxRadiusNm;

    @Value("${ship.behavior.loitering.min-points:3}")
    private int loiteringMinPoints;

    @Value("${ship.behavior.loitering.rule-version:LOITERING_V2_INSURANCE_SCENE_V1}")
    private String loiteringRuleVersion;

    @Value("${ship.behavior.loitering.max-gap-minutes:30}")
    private long loiteringMaxGapMinutes;

    @Value("${ship.behavior.loitering.event-split-gap-minutes:120}")
    private long loiteringEventSplitGapMinutes;

    @Value("${ship.behavior.loitering.max-valid-speed:80.0}")
    private double loiteringMaxValidSpeed;

    @Value("${ship.behavior.loitering.min-warning-points:6}")
    private int loiteringMinWarningPoints;

    @Value("${ship.behavior.loitering.slow.max-speed:2.0}")
    private double loiteringSlowMaxSpeed;

    @Value("${ship.behavior.loitering.slow.min-duration-minutes:240}")
    private long loiteringSlowMinDurationMinutes;

    @Value("${ship.behavior.loitering.slow.max-radius-nm:3.0}")
    private double loiteringSlowMaxRadiusNm;

    @Value("${ship.behavior.loitering.slow.min-points:12}")
    private int loiteringSlowMinPoints;

    @Value("${ship.behavior.loitering.maneuver.min-speed:0.5}")
    private double loiteringManeuverMinSpeed;

    @Value("${ship.behavior.loitering.maneuver.max-speed:5.0}")
    private double loiteringManeuverMaxSpeed;

    @Value("${ship.behavior.loitering.maneuver.min-duration-minutes:120}")
    private long loiteringManeuverMinDurationMinutes;

    @Value("${ship.behavior.loitering.maneuver.max-radius-nm:5.0}")
    private double loiteringManeuverMaxRadiusNm;

    @Value("${ship.behavior.loitering.maneuver.min-turn-count-per-hour:3}")
    private double loiteringManeuverMinTurnCountPerHour;

    @Value("${ship.behavior.loitering.maneuver.min-cog-change-per-hour:120}")
    private double loiteringManeuverMinCogChangePerHour;

    @Value("${ship.behavior.loitering.maneuver.min-tortuosity:3.0}")
    private double loiteringManeuverMinTortuosity;

    @Value("${ship.behavior.loitering.maneuver.turn-threshold-degree:45}")
    private double loiteringTurnThresholdDegree;

    @Value("${ship.behavior.loitering.high-risk.enabled:false}")
    private boolean loiteringHighRiskEnabled;

    @Value("${ship.behavior.loitering.high-risk.min-duration-minutes:60}")
    private long loiteringHighRiskMinDurationMinutes;

    @Value("${ship.behavior.loitering.high-risk.max-radius-nm:5.0}")
    private double loiteringHighRiskMaxRadiusNm;

    @Value("${ship.behavior.loitering.merge.enabled:true}")
    private boolean loiteringMergeEnabled;

    @Value("${ship.behavior.loitering.merge.min-overlap-ratio:0.8}")
    private double loiteringMergeMinOverlapRatio;

    @Value("${ship.behavior.loitering.merge.max-center-distance-nm:1.0}")
    private double loiteringMergeMaxCenterDistanceNm;

    @Value("${ship.behavior.loitering.merge.composite-subtype:COMPOSITE_LOITERING}")
    private String loiteringMergeCompositeSubtype;

    @Value("${ship.behavior.loitering.severity-cap.enabled:true}")
    private boolean loiteringSeverityCapEnabled;

    @Value("${ship.behavior.loitering.severity-cap.no-scene-data.max-level:L2}")
    private String loiteringSeverityCapNoSceneDataMaxLevel;

    @Value("${ship.behavior.loitering.severity-cap.no-scene-data.max-duration-minutes:720}")
    private long loiteringSeverityCapNoSceneDataMaxDurationMinutes;

    @Value("${ship.behavior.loitering.severity-cap.require-external-evidence-for-l3:true}")
    private boolean loiteringRequireExternalEvidenceForL3;

    @Value("${ship.behavior.loitering.severity-cap.auto-l4-enabled:false}")
    private boolean loiteringAutoL4Enabled;

    @Value("${ship.behavior.loitering.scene-data.available:false}")
    private boolean loiteringSceneDataAvailable;

    @Value("${ship.behavior.loitering.diagnostic.enabled:true}")
    private boolean loiteringDiagnosticEnabled;

    @Value("${ship.behavior.loitering.diagnostic.near-miss-limit:10}")
    private int loiteringNearMissLimit;

    @Value("${ship.behavior.loitering.weak.enabled:true}")
    private boolean loiteringWeakEnabled;

    @Value("${ship.behavior.loitering.weak.store-enabled:true}")
    private boolean loiteringWeakStoreEnabled;

    @Value("${ship.behavior.loitering.min-speed:1.0}")
    private double loiteringMinSpeed;

    @Value("${ship.behavior.loitering.weak.min-duration-minutes:60}")
    private long loiteringWeakMinDurationMinutes;

    @Value("${ship.behavior.loitering.weak.max-speed:3.0}")
    private double loiteringWeakMaxSpeed;

    @Value("${ship.behavior.loitering.weak.max-radius-nm:8.0}")
    private double loiteringWeakMaxRadiusNm;

    @Value("${ship.behavior.loitering.weak.min-points:6}")
    private int loiteringWeakMinPoints;

    @Value("${ship.behavior.loitering.weak.max-gap-minutes:180}")
    private long loiteringWeakMaxGapMinutes;

    @Value("${ship.behavior.loitering.weak.max-score:20}")
    private double loiteringWeakMaxScore;

    @Value("${ship.behavior.loitering.batch.parallelism:1}")
    private int loiteringBatchParallelism;

    @Value("${ship.behavior.loitering.batch.progress-interval:50}")
    private int loiteringBatchProgressInterval;

    @Value("${ship.behavior.alongside.batch.parallelism:1}")
    private int alongsideBatchParallelism;

    @Value("${ship.behavior.alongside.batch.progress-interval:50}")
    private int alongsideBatchProgressInterval;

    @Value("${ship.behavior.alongside.batch.trajectory-load-slice-hours:6}")
    private int alongsideBatchTrajectoryLoadSliceHours;

    @Value("${ship.behavior.alongside.batch.max-points-per-window:50000}")
    private int alongsideBatchMaxPointsPerWindow;

    @Value("${ship.behavior.alongside.batch.max-replay-queries-per-window:300}")
    private int alongsideBatchMaxReplayQueriesPerWindow;

    @Value("${ship.behavior.alongside.max-distance-nm:0.1}")
    private double alongsideMaxDistanceNm;

    @Value("${ship.behavior.alongside.max-speed:2.0}")
    private double alongsideMaxSpeed;

    @Value("${ship.behavior.alongside.max-time-gap-minutes:10}")
    private long alongsideMaxTimeGapMinutes;

    @Value("${ship.behavior.alongside.min-duration-minutes:20}")
    private long alongsideMinDurationMinutes;

    @Value("${ship.behavior.alongside.min-points:3}")
    private int alongsideMinPoints;

    @Value("${ship.behavior.alongside.rule-version:ALONGSIDE_V3_ASYMMETRIC_COUNTERPARTY_V1}")
    private String alongsideRuleVersion;

    @Value("${ship.behavior.code-commit-sha:UNKNOWN}")
    private String codeCommitSha;

    @Value("${ship.behavior.alongside.mode.auto.enabled:true}")
    private boolean alongsideAutoEnabled;

    @Value("${ship.behavior.alongside.candidate.max-distance-nm:0.5}")
    private double alongsideCandidateMaxDistanceNm;

    @Value("${ship.behavior.alongside.candidate.max-speed:4.0}")
    private double alongsideCandidateMaxSpeed;

    @Value("${ship.behavior.alongside.candidate.time-bucket-minutes:5}")
    private long alongsideCandidateTimeBucketMinutes;

    @Value("${ship.behavior.alongside.candidate.min-points:3}")
    private int alongsideCandidateMinPoints;

    @Value("${ship.behavior.alongside.candidate.min-duration-minutes:20}")
    private long alongsideCandidateMinDurationMinutes;

    @Value("${ship.behavior.alongside.candidate.split-gap-minutes:30}")
    private long alongsideCandidateSplitGapMinutes;

    @Value("${ship.behavior.alongside.confirm.max-distance-nm:0.15}")
    private double alongsideConfirmMaxDistanceNm;

    @Value("${ship.behavior.alongside.confirm.max-speed:2.0}")
    private double alongsideConfirmMaxSpeed;

    @Value("${ship.behavior.alongside.confirm.min-speed:1.0}")
    private double alongsideConfirmMinSpeed;

    @Value("${ship.behavior.alongside.confirm.max-relative-speed:1.0}")
    private double alongsideConfirmMaxRelativeSpeed;

    @Value("${ship.behavior.alongside.confirm.max-time-gap-minutes:10}")
    private long alongsideConfirmMaxTimeGapMinutes;

    @Value("${ship.behavior.alongside.confirm.high-confidence-time-gap-minutes:5}")
    private long alongsideConfirmHighConfidenceTimeGapMinutes;

    @Value("${ship.behavior.alongside.confirm.min-duration-minutes:30}")
    private long alongsideConfirmMinDurationMinutes;

    @Value("${ship.behavior.alongside.confirm.min-points:6}")
    private int alongsideConfirmMinPoints;

    @Value("${ship.behavior.alongside.heading.enabled:true}")
    private boolean alongsideHeadingEnabled;

    @Value("${ship.behavior.alongside.heading.parallel-threshold-degree:30}")
    private double alongsideHeadingParallelThresholdDegree;

    @Value("${ship.behavior.alongside.heading.min-parallel-ratio:0.8}")
    private double alongsideMinParallelRatio;

    @Value("${ship.behavior.alongside.distance.max-variance-nm2:0.0025}")
    private double alongsideMaxDistanceVarianceNm2;

    @Value("${ship.behavior.alongside.scene.crowded-vessel-count:5}")
    private int alongsideCrowdedVesselCount;

    @Value("${ship.behavior.alongside.merge.enabled:true}")
    private boolean alongsideMergeEnabled;

    @Value("${ship.behavior.alongside.merge.max-gap-minutes:30}")
    private long alongsideMergeMaxGapMinutes;

    @Value("${ship.behavior.alongside.merge.max-center-distance-nm:1.0}")
    private double alongsideMergeMaxCenterDistanceNm;

    @Value("${ship.behavior.alongside.severity-cap.no-scene-data.max-level:L2}")
    private String alongsideSeverityCapNoSceneDataMaxLevel;

    @Value("${ship.behavior.alongside.scene-data.available:false}")
    private boolean alongsideSceneDataAvailable;

    @Value("${ship.behavior.alongside.counterparty-risk.available:false}")
    private boolean alongsideCounterpartyRiskAvailable;

    @Value("${ship.behavior.alongside.auto-l4-enabled:false}")
    private boolean alongsideAutoL4Enabled;

    @Value("${ship.behavior.alongside.score.weight:0.20}")
    private double alongsideScoreWeight;

    @Value("${ship.behavior.trace.log-max-events:20}")
    private int traceLogMaxEvents;

    @Autowired
    private TrajectoryLoaderService trajectoryLoaderService;

    @Autowired
    private ShipBehaviorEventRepository shipBehaviorEventRepository;

    @Autowired
    private ShipBehaviorEventDetailRepository shipBehaviorEventDetailRepository;

    @Autowired
    private ShipBehaviorLoiteringBatchStatusRepository loiteringBatchStatusRepository;

    @Autowired
    private ShipBehaviorAlongsideBatchStatusRepository alongsideBatchStatusRepository;

    @Autowired
    private AIS41GeneralQueryRepository ais41GeneralQueryRepository;

    @Autowired
    private ShipAlongsideReplayCandidateProvider alongsideReplayCandidateProvider;

    @Autowired
    private ShipTypeClassifier shipTypeClassifier;

    @Autowired
    private AlongsideSceneService alongsideSceneService;

    @Autowired private WorldSeaMaskService worldSeaMaskService;
    @Autowired private WorldSeaTrackSegmenter worldSeaTrackSegmenter;
    @Autowired private WorldSeaEventGate worldSeaEventGate;
    @Autowired private LoiteringPortSceneService loiteringPortSceneService;
    @Autowired private LoiteringPortSceneProperties loiteringPortSceneProperties;
    @Autowired private LoiteringPortTrackSegmenter loiteringPortTrackSegmenter;
    @Autowired private LoiteringPortEventGate loiteringPortEventGate;

    public AnalysisResult analysisLoitering(String params) {
        JobParams jobParams = parseParams(params);
        applyDefaultTimeRange(jobParams);
        List<String> mmsiList = resolveMmsiList(jobParams);
        ShipLoiteringDetector.LoiteringConfig config = buildLoiteringConfig();

        int processed = 0;
        int failed = 0;
        int events = 0;
        for (String mmsi : mmsiList) {
            try {
                LoiteringWindowResult result = analysisLoiteringOneWindow(jobParams, mmsi, config,
                        new ShipLoiteringDetector());
                processed++;
                events += result.getEvents();
                XxlJobLogger.log("徘徊 mmsi=" + mmsi + " points=" + result.getPoints()
                        + " events=" + result.getEvents());
            } catch (Exception ex) {
                failed++;
                log.error("loitering analysis failed, mmsi={}", mmsi, ex);
                XxlJobLogger.log("徘徊计算失败 mmsi=" + mmsi + " error=" + ex.getMessage());
            }
        }
        return new AnalysisResult(processed, failed, events);
    }

    private LoiteringWindowResult analysisLoiteringOneWindow(JobParams jobParams, String mmsi,
                                                             ShipLoiteringDetector.LoiteringConfig config,
                                                             ShipLoiteringDetector detector) {
        if (worldSeaMaskService != null) worldSeaMaskService.requireSnapshot();
        loiteringPortSceneService.requireSnapshot();
        XxlJobLogger.log("LOITERING_V2 start mmsi=" + mmsi
                + " startTime=" + jobParams.getStartTime()
                + " endTime=" + jobParams.getEndTime()
                + " batchNo=" + jobParams.getBatchNo()
                + " ruleVersion=" + config.getRuleVersion());
        List<TrajectoryPoint> points = trajectoryLoaderService.load(
                mmsi, jobParams.getStartTime(), jobParams.getEndTime(), trajectorySimplifyZoom);
        List<ShipBehaviorTrace> traces = new ArrayList<ShipBehaviorTrace>();
        List<List<TrajectoryPoint>> seaSegments = worldSeaTrackSegmenter == null
                ? Collections.singletonList(points) : worldSeaTrackSegmenter.splitInside(points);
        for (List<TrajectoryPoint> seaSegment : seaSegments) {
            for (List<TrajectoryPoint> segment : loiteringPortTrackSegmenter.splitEligible(seaSegment)) {
                traces.addAll(detector.detectWithTrace(mmsi, segment, config));
            }
        }
        traces = loiteringPortEventGate.filterAndAnnotate(traces);
        if (worldSeaEventGate != null) traces = worldSeaEventGate.filterAndAnnotate(traces);
        ShipLoiteringDetector.DetectionStats stats = detector.getLastStats();
        List<ShipBehaviorEvent> detected = extractEvents(traces);
        tagBatch(detected, jobParams);
        shipBehaviorEventRepository.saveEvents(detected);
        shipBehaviorEventDetailRepository.saveDetails(toDetails(traces, jobParams));
        logLoiteringMerge(jobParams, stats);
        logEventSummaries(traces);
        logLoiteringSummary(mmsi, jobParams, stats, detected.size());
        if (detected.isEmpty()) {
            String reason = stats.getCandidates() == 0
                    ? "NO_CANDIDATE_AFTER_RULE_FILTER" : "ALL_CANDIDATES_EXCLUDED";
            XxlJobLogger.log("LOITERING_V2 no_event mmsi=" + mmsi
                    + " reason=" + reason
                    + " points=" + stats.getPoints()
                    + " cleanPoints=" + stats.getCleanPoints()
                    + " segments=" + stats.getSegments()
                    + " batchNo=" + jobParams.getBatchNo());
        }
        logLoiteringDiagnostics(mmsi, stats);
        return new LoiteringWindowResult(points.size(), detected.size());
    }

    public AnalysisResult analysisLoiteringBatch(String params) {
        BatchJobParams batchParams = parseBatchParams(params);
        List<String> mmsiList = resolveBatchMmsiList(batchParams);
        List<TimeWindow> windows = splitTimeWindows(batchParams);
        Set<String> completed = batchParams.isResume()
                ? loiteringBatchStatusRepository.findCompletedWindows(batchParams.getBatchNo())
                : Collections.<String>emptySet();
        List<LoiteringBatchTask> tasks = buildLoiteringBatchTasks(batchParams, mmsiList, windows, completed);
        int skipped = mmsiList.size() * windows.size() - tasks.size();
        int parallelism = resolveBatchParallelism(batchParams);
        long startMillis = System.currentTimeMillis();
        XxlJobLogger.log("LOITERING_BATCH start batchNo=" + batchParams.getBatchNo()
                + " ships=" + mmsiList.size()
                + " windows=" + windows.size()
                + " totalTasks=" + (mmsiList.size() * windows.size())
                + " pendingTasks=" + tasks.size()
                + " skipped=" + skipped
                + " sliceMode=" + batchParams.getSliceMode()
                + " parallelism=" + parallelism
                + " resume=" + batchParams.isResume());
        LoiteringBatchResult result = parallelism <= 1
                ? runLoiteringBatchSequential(tasks, startMillis)
                : runLoiteringBatchParallel(tasks, parallelism, startMillis);
        XxlJobLogger.log("LOITERING_BATCH summary batchNo=" + batchParams.getBatchNo()
                + " ships=" + mmsiList.size()
                + " windows=" + windows.size()
                + " totalTasks=" + (mmsiList.size() * windows.size())
                + " processed=" + result.getProcessed()
                + " skipped=" + skipped
                + " failed=" + result.getFailed()
                + " events=" + result.getEvents()
                + " doneStatus=" + loiteringBatchStatusRepository.countByStatus(batchParams.getBatchNo(),
                ShipBehaviorLoiteringBatchStatusRepository.STATUS_DONE)
                + " failedStatus=" + loiteringBatchStatusRepository.countByStatus(batchParams.getBatchNo(),
                ShipBehaviorLoiteringBatchStatusRepository.STATUS_FAILED)
                + " elapsedMs=" + (System.currentTimeMillis() - startMillis));
        return new AnalysisResult(result.getProcessed(), result.getFailed(), result.getEvents());
    }

    private LoiteringBatchResult runLoiteringBatchSequential(List<LoiteringBatchTask> tasks, long startMillis) {
        LoiteringBatchResult result = new LoiteringBatchResult();
        for (LoiteringBatchTask task : tasks) {
            LoiteringTaskResult taskResult = executeLoiteringBatchTask(task);
            result.add(taskResult);
            logLoiteringBatchProgress(result, tasks.size(), startMillis);
        }
        return result;
    }

    private LoiteringBatchResult runLoiteringBatchParallel(List<LoiteringBatchTask> tasks, int parallelism,
                                                          long startMillis) {
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        LoiteringBatchResult result = new LoiteringBatchResult();
        try {
            List<Future<LoiteringTaskResult>> futures = new ArrayList<Future<LoiteringTaskResult>>();
            for (final LoiteringBatchTask task : tasks) {
                futures.add(executor.submit(new Callable<LoiteringTaskResult>() {
                    @Override
                    public LoiteringTaskResult call() {
                        return executeLoiteringBatchTask(task);
                    }
                }));
            }
            for (Future<LoiteringTaskResult> future : futures) {
                try {
                    result.add(future.get());
                } catch (Exception ex) {
                    result.add(LoiteringTaskResult.failed(0));
                    log.error("loitering batch future failed", ex);
                    XxlJobLogger.log("LOITERING_BATCH future_failed error=" + ex.getMessage());
                }
                logLoiteringBatchProgress(result, tasks.size(), startMillis);
            }
        } finally {
            executor.shutdown();
        }
        return result;
    }

    private LoiteringTaskResult executeLoiteringBatchTask(LoiteringBatchTask task) {
        JobParams windowParams = new JobParams();
        windowParams.setMmsi(task.getMmsi());
        windowParams.setStartTime(task.getWindow().getStartTime());
        windowParams.setEndTime(task.getWindow().getEndTime());
        windowParams.setBatchNo(task.getBatchNo());
        try {
            LoiteringWindowResult result = analysisLoiteringOneWindow(windowParams, task.getMmsi(),
                    buildLoiteringConfig(), new ShipLoiteringDetector());
            loiteringBatchStatusRepository.markDone(task.getBatchNo(), task.getMmsi(),
                    task.getWindow().getStartTime(), task.getWindow().getEndTime(),
                    result.getPoints(), result.getEvents());
            XxlJobLogger.log("LOITERING_BATCH ship_window mmsi=" + task.getMmsi()
                    + " startTime=" + task.getWindow().getStartTime()
                    + " endTime=" + task.getWindow().getEndTime()
                    + " status=DONE points=" + result.getPoints()
                    + " events=" + result.getEvents()
                    + " batchNo=" + task.getBatchNo());
            return LoiteringTaskResult.done(result.getEvents());
        } catch (Exception ex) {
            loiteringBatchStatusRepository.markFailed(task.getBatchNo(), task.getMmsi(),
                    task.getWindow().getStartTime(), task.getWindow().getEndTime(), ex.getMessage());
            log.error("loitering batch task failed, batchNo={}, mmsi={}, window={}",
                    task.getBatchNo(), task.getMmsi(), task.getWindow(), ex);
            XxlJobLogger.log("LOITERING_BATCH ship_window mmsi=" + task.getMmsi()
                    + " startTime=" + task.getWindow().getStartTime()
                    + " endTime=" + task.getWindow().getEndTime()
                    + " status=FAILED error=" + ex.getMessage()
                    + " batchNo=" + task.getBatchNo());
            return LoiteringTaskResult.failed(0);
        }
    }

    private void logLoiteringBatchProgress(LoiteringBatchResult result, int totalTasks, long startMillis) {
        int interval = Math.max(1, loiteringBatchProgressInterval);
        int done = result.getProcessed() + result.getFailed();
        if (done == totalTasks || done % interval == 0) {
            XxlJobLogger.log("LOITERING_BATCH progress processed=" + result.getProcessed()
                    + " failed=" + result.getFailed()
                    + " totalTasks=" + totalTasks
                    + " events=" + result.getEvents()
                    + " elapsedMs=" + (System.currentTimeMillis() - startMillis));
        }
    }

    public AnalysisResult analysisAlongside(String params) {
        JobParams jobParams = parseParams(params);
        applyDefaultTimeRange(jobParams);
        if (StringUtils.isBlank(jobParams.getMmsi())) {
            XxlJobLogger.log("搭靠计算需要 mmsi");
            return new AnalysisResult(0, 1, 0);
        }
        String mode = resolveAlongsideMode(jobParams);
        ShipAlongsideDetector.AlongsideRule rule = buildAlongsideRule();
        if ("AUTO".equals(mode)) {
            return analysisAlongsideAuto(jobParams, rule);
        }
        return analysisAlongsidePair(jobParams, rule);
    }

    public AlongsideWindowResult analysisAlongsideHistoryWindow(
            String mmsi, String startTime, String endTime, String batchNo, boolean enableInsert) {
        JobParams jobParams = new JobParams();
        jobParams.setMmsi(mmsi);
        jobParams.setStartTime(startTime);
        jobParams.setEndTime(endTime);
        jobParams.setBatchNo(batchNo);
        jobParams.setMode("AUTO");
        return analysisAlongsideOneWindow(jobParams, buildAlongsideRule(), true, enableInsert, true);
    }

    public AlongsideWindowResult computeAlongsideHistory(
            String mmsi, String startTime, String endTime, String batchNo,
            AlongsideComputePolicy policy) {
        JobParams jobParams = new JobParams();
        jobParams.setMmsi(mmsi);
        jobParams.setStartTime(startTime);
        jobParams.setEndTime(endTime);
        jobParams.setBatchNo(batchNo);
        jobParams.setMode("AUTO");
        return analysisAlongsideOneWindow(jobParams, buildAlongsideRule(), true,
                false, false, true, policy == null ? ACCEPT_ALL_ALONGSIDE : policy);
    }

    public String currentAlongsideRuleVersion() {
        return buildAlongsideRule().getRuleVersion();
    }

    public String currentCodeCommitSha() {
        return codeCommitSha;
    }

    public AlongsideWindowResult analysisAlongsideProductionWindow(
            String mmsi, String startTime, String endTime, String batchNo) {
        JobParams jobParams = new JobParams();
        jobParams.setMmsi(mmsi);
        jobParams.setStartTime(startTime);
        jobParams.setEndTime(endTime);
        jobParams.setBatchNo(batchNo);
        jobParams.setMode("AUTO");
        return analysisAlongsideOneWindow(jobParams, buildAlongsideRule(),
                true, false, false, true);
    }

    public List<ShipBehaviorTrace> analysisLoiteringV2Production(
            String mmsi, List<TrajectoryPoint> points, String batchNo) {
        if (worldSeaMaskService != null) worldSeaMaskService.requireSnapshot();
        loiteringPortSceneService.requireSnapshot();
        ShipLoiteringDetector detector = new ShipLoiteringDetector();
        ShipLoiteringDetector.LoiteringConfig config = buildLoiteringConfig();
        List<ShipBehaviorTrace> traces = new ArrayList<ShipBehaviorTrace>();
        List<List<TrajectoryPoint>> seaSegments = worldSeaTrackSegmenter == null
                ? Collections.singletonList(points) : worldSeaTrackSegmenter.splitInside(points);
        for (List<TrajectoryPoint> seaSegment : seaSegments) {
            for (List<TrajectoryPoint> segment : loiteringPortTrackSegmenter.splitEligible(seaSegment)) {
                traces.addAll(detector.detectWithTrace(mmsi, segment, config));
            }
        }
        traces = loiteringPortEventGate.filterAndAnnotate(traces);
        if (worldSeaEventGate != null) traces = worldSeaEventGate.filterAndAnnotate(traces);
        for (ShipBehaviorTrace trace : traces) {
            ShipBehaviorEvent event = trace.getEvent();
            event.setBatchNo(batchNo);
            TraceRecipeService.attachLoitering(trace, "V2",
                    event.getStartTime(), event.getEndTime(), trajectorySimplifyZoom,
                    loiteringV2RuleConfigHash());
        }
        return traces;
    }

    public String loiteringV2RuleConfigHash() {
        JSONObject value = new JSONObject(true);
        value.put("rule", buildLoiteringConfig());
        value.put("worldSeaFingerprint", worldSeaMaskService.requireSnapshot().getGeometryFingerprint());
        value.put("portSceneFingerprint", loiteringPortSceneService.requireSnapshot().getFingerprint());
        value.put("minSpeed", loiteringMinSpeed);
        value.put("portApproachDistanceNm", loiteringPortSceneProperties.getMaxDistanceNm());
        value.put("scenePolicy", "PORT_OPERATIONAL_AND_ANCHORAGE_HARD_EXCLUDE;OFFSHORE_ENABLED=" + loiteringPortSceneProperties.isOffshoreEnabled());
        value.put("weakStoreEnabled", loiteringWeakStoreEnabled);
        value.put("ruleVersion", loiteringRuleVersion);
        return TraceRecipeService.configHash(value);
    }

    public LoiteringPortTrackSegmenter.Stats loiteringPortSplitStats() {
        return loiteringPortTrackSegmenter.getLastStats();
    }

    public String alongsideRuleConfigHash() {
        JSONObject value = new JSONObject(true);
        value.put("rule", buildAlongsideRule());
        value.put("worldSeaFingerprint", worldSeaMaskService.requireSnapshot().getGeometryFingerprint());
        value.put("portApproachDistanceNm", loiteringPortSceneProperties.getMaxDistanceNm());
        value.put("sceneClassificationVersion", "INSURANCE_SCENE_V1");
        value.put("sceneAvailabilityPolicy", "OPTIONAL_CONTEXT_UNKNOWN_ON_UNAVAILABLE");
        return TraceRecipeService.configHash(value);
    }

    public List<ShipBehaviorTrace> analysisAlongsideReplay(
            String mmsi, String targetMmsi,
            List<TrajectoryPoint> ownPoints, List<TrajectoryPoint> targetPoints) {
        Set<String> pair = new HashSet<String>();
        pair.add(mmsi);
        pair.add(targetMmsi);
        Map<String, String> shipTypes = loadShipTypes(pair);
        ShipAlongsideDetector.AlongsideContext context = buildAlongsideContext(
                mmsi, targetMmsi, shipTypes, ownPoints, targetPoints, 2);
        List<ShipBehaviorTrace> traces = alongsideDetector.detectWithTrace(mmsi, ownPoints, targetMmsi,
                targetPoints, buildAlongsideRule(), context);
        return worldSeaEventGate == null ? traces : worldSeaEventGate.filterAndAnnotate(traces);
    }

    private AnalysisResult analysisAlongsidePair(JobParams jobParams, ShipAlongsideDetector.AlongsideRule rule) {
        if (worldSeaMaskService != null) worldSeaMaskService.requireSnapshot();
        if (StringUtils.isBlank(jobParams.getTargetMmsi())) {
            XxlJobLogger.log("ALONGSIDE_V3 pair mode requires targetMmsi, mmsi=" + jobParams.getMmsi()
                    + " batchNo=" + jobParams.getBatchNo());
            return new AnalysisResult(0, 1, 0);
        }
        try {
            XxlJobLogger.log("ALONGSIDE_V3 start mode=PAIR mmsi=" + jobParams.getMmsi()
                    + " targetMmsi=" + jobParams.getTargetMmsi()
                    + " startTime=" + jobParams.getStartTime()
                    + " endTime=" + jobParams.getEndTime()
                    + " batchNo=" + jobParams.getBatchNo());
            Set<String> pairMmsis = new HashSet<String>();
            pairMmsis.add(jobParams.getMmsi());
            pairMmsis.add(jobParams.getTargetMmsi());
            Map<String, String> shipTypes = loadShipTypes(pairMmsis);
            OwnShipEligibility ownEligibility = shipTypeClassifier
                    .classifyOwnShip(shipTypes.get(jobParams.getMmsi()));
            if (ownEligibility != OwnShipEligibility.ELIGIBLE_MERCHANT) {
                XxlJobLogger.log("ALONGSIDE_V3 own_ship_ineligible mmsi=" + jobParams.getMmsi()
                        + " eligibility=" + ownEligibility + " batchNo=" + jobParams.getBatchNo());
                return new AnalysisResult(1, 0, 0);
            }
            List<TrajectoryPoint> ownPoints = trajectoryLoaderService.load(
                    jobParams.getMmsi(), jobParams.getStartTime(), jobParams.getEndTime(), trajectorySimplifyZoom);
            List<TrajectoryPoint> targetPoints = trajectoryLoaderService.load(
                    jobParams.getTargetMmsi(), jobParams.getStartTime(), jobParams.getEndTime(), trajectorySimplifyZoom);
            ShipAlongsideDetector.AlongsideContext context = buildAlongsideContext(
                    jobParams.getMmsi(), jobParams.getTargetMmsi(), shipTypes, ownPoints, targetPoints, 2);
            List<ShipBehaviorTrace> traces = alongsideDetector.detectWithTrace(
                    jobParams.getMmsi(), ownPoints, jobParams.getTargetMmsi(), targetPoints, rule, context);
            if (worldSeaEventGate != null) traces = worldSeaEventGate.filterAndAnnotate(traces);
            ShipAlongsideDetector.DetectionStats stats = alongsideDetector.getLastStats();
            List<ShipBehaviorEvent> detected = extractEvents(traces);
            tagBatch(detected, jobParams);
            shipBehaviorEventRepository.saveEvents(detected);
            shipBehaviorEventDetailRepository.saveDetails(toDetails(traces, jobParams));
            logEventSummaries(traces);
            XxlJobLogger.log("ALONGSIDE_V3 summary mode=PAIR mmsi=" + jobParams.getMmsi()
                    + " targetMmsi=" + jobParams.getTargetMmsi()
                    + " ownPoints=" + stats.getOwnPoints()
                    + " targetPoints=" + stats.getTargetPoints()
                    + " rawEvents=" + stats.getRawEvents()
                    + " mergedEvents=" + stats.getMergedEvents()
                    + " savedEvents=" + detected.size()
                    + " batchNo=" + jobParams.getBatchNo());
            return new AnalysisResult(1, 0, detected.size());
        } catch (Exception ex) {
            log.error("alongside analysis failed, params={}", jobParams, ex);
            XxlJobLogger.log("搭靠计算失败 error=" + ex.getMessage());
            return new AnalysisResult(0, 1, 0);
        }
    }

    private AnalysisResult analysisAlongsideAuto(JobParams jobParams, ShipAlongsideDetector.AlongsideRule rule) {
        if (!alongsideAutoEnabled) {
            XxlJobLogger.log("ALONGSIDE_V3 AUTO disabled mmsi=" + jobParams.getMmsi()
                    + " batchNo=" + jobParams.getBatchNo());
            return new AnalysisResult(0, 1, 0);
        }
        try {
            XxlJobLogger.log("ALONGSIDE_V3 start mode=AUTO mmsi=" + jobParams.getMmsi()
                    + " startTime=" + jobParams.getStartTime()
                    + " endTime=" + jobParams.getEndTime()
                    + " batchNo=" + jobParams.getBatchNo());
            AlongsideWindowResult result = analysisAlongsideOneWindow(jobParams, rule);
            return new AnalysisResult(1, result.getFailed(), result.getEvents());
        } catch (Exception ex) {
            log.error("auto alongside analysis failed, params={}", jobParams, ex);
            XxlJobLogger.log("AUTO 搭靠计算失败 error=" + ex.getMessage());
            return new AnalysisResult(0, 1, 0);
        }
    }

    private AlongsideWindowResult analysisAlongsideOneWindow(JobParams jobParams,
                                                             ShipAlongsideDetector.AlongsideRule rule) {
        return analysisAlongsideOneWindow(jobParams, rule, false, true, false);
    }

    private AlongsideWindowResult analysisAlongsideOneWindow(JobParams jobParams,
                                                             ShipAlongsideDetector.AlongsideRule rule,
                                                             boolean safeLoad) {
        return analysisAlongsideOneWindow(jobParams, rule, safeLoad, true, false);
    }

    private AlongsideWindowResult analysisAlongsideOneWindow(JobParams jobParams,
                                                             ShipAlongsideDetector.AlongsideRule rule,
                                                             boolean safeLoad,
                                                             boolean enableInsert,
                                                             boolean replaceExisting) {
        return analysisAlongsideOneWindow(jobParams, rule, safeLoad,
                enableInsert, replaceExisting, false);
    }

    private AlongsideWindowResult analysisAlongsideOneWindow(JobParams jobParams,
                                                             ShipAlongsideDetector.AlongsideRule rule,
                                                             boolean safeLoad,
                                                             boolean enableInsert,
                                                             boolean replaceExisting,
                                                             boolean conciseLog) {
        return analysisAlongsideOneWindow(jobParams, rule, safeLoad, enableInsert,
                replaceExisting, conciseLog, ACCEPT_ALL_ALONGSIDE);
    }

    private AlongsideWindowResult analysisAlongsideOneWindow(JobParams jobParams,
                                                             ShipAlongsideDetector.AlongsideRule rule,
                                                             boolean safeLoad,
                                                             boolean enableInsert,
                                                             boolean replaceExisting,
                                                             boolean conciseLog,
                                                             AlongsideComputePolicy policy) {
        if (worldSeaMaskService != null) worldSeaMaskService.requireSnapshot();
        Set<String> ownMmsi = Collections.singleton(jobParams.getMmsi());
        Map<String, String> ownShipTypes = loadShipTypes(ownMmsi);
        OwnShipEligibility ownEligibility = shipTypeClassifier
                .classifyOwnShip(ownShipTypes.get(jobParams.getMmsi()));
        if (ownEligibility != OwnShipEligibility.ELIGIBLE_MERCHANT && !policy.ownsEligibilityDecision()) {
            XxlJobLogger.log("ALONGSIDE_V3 own_ship_ineligible mmsi=" + jobParams.getMmsi()
                    + " eligibility=" + ownEligibility + " batchNo=" + jobParams.getBatchNo());
            return new AlongsideWindowResult(0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, Collections.<ShipBehaviorTrace>emptyList());
        }
        List<TrajectoryPoint> ownPoints = safeLoad
                ? trajectoryLoaderService.loadSafely(jobParams.getMmsi(), jobParams.getStartTime(),
                jobParams.getEndTime(), trajectorySimplifyZoom, alongsideBatchTrajectoryLoadSliceHours,
                alongsideBatchMaxPointsPerWindow)
                : trajectoryLoaderService.load(jobParams.getMmsi(), jobParams.getStartTime(),
                jobParams.getEndTime(), trajectorySimplifyZoom);
        List<ShipAlongsideDetector.LowSpeedWindow> rawWindows = alongsideDetector.extractLowSpeedWindows(
                jobParams.getMmsi(), ownPoints, alongsideConfirmMinSpeed, alongsideCandidateMaxSpeed,
                alongsideCandidateMinDurationMinutes, alongsideCandidateSplitGapMinutes);
        int rawLowSpeedWindowCount = rawWindows.size();
        List<ShipAlongsideDetector.LowSpeedWindow> windows = new ArrayList<ShipAlongsideDetector.LowSpeedWindow>();
        List<List<TrajectoryPoint>> seaSegments = worldSeaTrackSegmenter == null
                ? Collections.singletonList(ownPoints) : worldSeaTrackSegmenter.splitInside(ownPoints);
        for (List<TrajectoryPoint> seaSegment : seaSegments) {
            windows.addAll(alongsideDetector.extractLowSpeedWindows(
                    jobParams.getMmsi(), seaSegment, alongsideConfirmMinSpeed, alongsideCandidateMaxSpeed,
                    alongsideCandidateMinDurationMinutes, alongsideCandidateSplitGapMinutes));
        }
        int worldSeaRejectedWindowCount = Math.max(0, rawLowSpeedWindowCount - windows.size());
        int alongsideLowSpeedWindowCount = windows.size();
        if (policy != ACCEPT_ALL_ALONGSIDE) {
            List<ShipAlongsideDetector.LowSpeedWindow> accepted =
                    new ArrayList<ShipAlongsideDetector.LowSpeedWindow>();
            for (ShipAlongsideDetector.LowSpeedWindow window : windows) {
                List<TrajectoryPoint> windowPoints = collectOwnPointsByWindow(
                        Collections.singletonList(window), ownPoints).get(
                        AlongsideReplayQueryPlanner.windowKey(window));
                if (policy.acceptWindow(window, windowPoints)) accepted.add(window);
            }
            windows = accepted;
        }
        Map<String, List<TrajectoryPoint>> rawOwnPointsByWindow = collectOwnPointsByWindow(rawWindows, ownPoints);
        Map<String, List<TrajectoryPoint>> ownPointsByWindow = collectOwnPointsByWindow(windows, ownPoints);
        AlongsideReplayQueryPlan baselineReplayPlan = alongsideReplayCandidateProvider.planQueries(
                rawWindows, rawOwnPointsByWindow, jobParams.getBatchNo());
        AlongsideReplayQueryPlan filteredReplayPlan = alongsideReplayCandidateProvider.planQueries(
                windows, ownPointsByWindow, jobParams.getBatchNo());
        int savedReplayQueryCount = Math.max(0, baselineReplayPlan.size() - filteredReplayPlan.size());
        AlongsideReplaySearchResult replaySearchResult = windows.isEmpty() && policy != ACCEPT_ALL_ALONGSIDE
                ? new AlongsideReplaySearchResult()
                : conciseLog
                    ? alongsideReplayCandidateProvider.findCandidates(
                    jobParams.getMmsi(), windows, ownPointsByWindow,
                    jobParams.getBatchNo(), false)
                    : alongsideReplayCandidateProvider.findCandidates(
                    jobParams.getMmsi(), windows, ownPointsByWindow,
                    jobParams.getBatchNo());
        if (safeLoad
                && alongsideBatchMaxReplayQueriesPerWindow > 0
                && replaySearchResult.getReplayQueryCount() > alongsideBatchMaxReplayQueriesPerWindow) {
            throw new IllegalStateException("too many replay queries, mmsi=" + jobParams.getMmsi()
                    + ", startTime=" + jobParams.getStartTime()
                    + ", endTime=" + jobParams.getEndTime()
                    + ", replayQueries=" + replaySearchResult.getReplayQueryCount()
                    + ", maxReplayQueries=" + alongsideBatchMaxReplayQueriesPerWindow);
        }
        List<AlongsideCandidateTarget> candidates = replaySearchResult.getCandidates();
        CandidateFunnelStats candidateFunnel = replaySearchResult.getCandidateFunnel();
        int policyRejectedCandidates = 0;
        if (policy != ACCEPT_ALL_ALONGSIDE) {
            List<AlongsideCandidateTarget> accepted = new ArrayList<AlongsideCandidateTarget>();
            for (AlongsideCandidateTarget candidate : candidates) {
                if (policy.acceptCandidate(candidate.getTargetMmsi())) {
                    accepted.add(candidate);
                    candidateFunnel.recordIdentityPolicyAccepted();
                } else {
                    policyRejectedCandidates++;
                    candidateFunnel.recordIdentityPolicyRejected();
                }
            }
            candidates = accepted;
        }
        int candidateTargetCount = countUniqueTargets(candidates);
        Set<String> candidateMmsis = new HashSet<String>();
        candidateMmsis.add(jobParams.getMmsi());
        for (AlongsideCandidateTarget candidate : candidates) {
            candidateMmsis.add(candidate.getTargetMmsi());
        }
        Map<String, String> shipTypes = loadShipTypes(candidateMmsis);
        if (!conciseLog) {
            XxlJobLogger.log("ALONGSIDE_V3 low_speed_summary mmsi=" + jobParams.getMmsi()
                    + " points=" + ownPoints.size()
                    + " cleanPoints=" + alongsideDetector.cleanAndSort(ownPoints).size()
                    + " lowSpeedWindows=" + windows.size()
                    + " rawLowSpeedWindows=" + rawLowSpeedWindowCount
                    + " worldSeaRejectedWindows=" + worldSeaRejectedWindowCount
                    + " baselinePlannedReplayQueries=" + baselineReplayPlan.size()
                    + " filteredPlannedReplayQueries=" + filteredReplayPlan.size()
                    + " savedReplayQueries=" + savedReplayQueryCount
                    + " batchNo=" + jobParams.getBatchNo());
            XxlJobLogger.log("ALONGSIDE_V3 candidate_summary mmsi=" + jobParams.getMmsi()
                    + " replayQueries=" + replaySearchResult.getReplayQueryCount()
                    + " replayFailures=" + replaySearchResult.getReplayFailures()
                    + " truncatedQueries=" + replaySearchResult.getTruncatedQueries()
                    + " returnedVessels=" + replaySearchResult.getReturnedVessels()
                    + " rawPoints=" + replaySearchResult.getRawPoints()
                    + " validPoints=" + replaySearchResult.getValidPoints()
                    + " parseErrors=" + replaySearchResult.getParseErrors()
                    + " prefilteredTargets=" + replaySearchResult.getPrefilteredTargets()
                    + " candidateTargets=" + candidateTargetCount
                    + " candidatePairs=" + candidates.size()
                    + " batchNo=" + jobParams.getBatchNo());
        }

        List<ShipBehaviorTrace> allTraces = new ArrayList<ShipBehaviorTrace>();
        int failed = 0;
        for (AlongsideCandidateTarget candidate : candidates) {
            try {
                List<TrajectoryPoint> candidateOwnPoints = candidate.getOwnWindowPoints();
                if (candidateOwnPoints == null || candidateOwnPoints.isEmpty()) {
                    candidateOwnPoints = ownPoints;
                }
                int nearbyVesselCount = candidate.getReplayQueryResult() == null
                        ? replaySearchResult.getReturnedVessels()
                        : candidate.getReplayQueryResult().getRawVesselCount();
                ShipAlongsideDetector.AlongsideContext context = buildAlongsideContext(
                        jobParams.getMmsi(), candidate.getTargetMmsi(), shipTypes,
                        candidateOwnPoints, candidate.getTargetPoints(), nearbyVesselCount, policy.ownsEligibilityDecision());
                List<ShipBehaviorTrace> traces = alongsideDetector.detectWithTrace(jobParams.getMmsi(),
                        candidateOwnPoints, candidate.getTargetMmsi(), candidate.getTargetPoints(), rule, context);
                for (ShipBehaviorTrace trace : traces) {
                    TraceRecipeService.attachAlongside(trace, candidate,
                            trajectorySimplifyZoom, TraceRecipeService.configHash(rule));
                }
                enrichReplayScoreDetails(traces, replaySearchResult);
                allTraces.addAll(traces);
            } catch (Exception ex) {
                failed++;
                log.warn("auto alongside candidate failed, candidate={}", candidate, ex);
            }
        }
        if (worldSeaEventGate != null) allTraces = worldSeaEventGate.filterAndAnnotate(allTraces);
        List<ShipBehaviorEvent> detected = extractEvents(allTraces);
        tagBatch(detected, jobParams);
        boolean completeHistoryWindow = failed == 0
                && replaySearchResult.getReplayFailures() == 0
                && replaySearchResult.getTruncatedQueries() == 0;
        boolean shouldInsert = enableInsert && (!replaceExisting || completeHistoryWindow);
        if (shouldInsert) {
            if (replaceExisting) {
                shipBehaviorEventDetailRepository.deleteAlongsideWindow(
                        jobParams.getBatchNo(), jobParams.getMmsi(),
                        jobParams.getStartTime(), jobParams.getEndTime());
                shipBehaviorEventRepository.deleteAlongsideWindow(
                        jobParams.getBatchNo(), jobParams.getMmsi(),
                        jobParams.getStartTime(), jobParams.getEndTime());
            }
            shipBehaviorEventRepository.saveEvents(detected);
            shipBehaviorEventDetailRepository.saveDetails(toDetails(allTraces, jobParams));
        }
        if (!conciseLog) {
            logEventSummaries(allTraces);
        }
        AlongsideAnnualScore annualScore = calculateAlongsideAnnualScore(jobParams, detected);
        if (!conciseLog) {
            XxlJobLogger.log("ALONGSIDE_V3 annual_score mmsi=" + jobParams.getMmsi()
                    + " eventCount=" + annualScore.getEventCount()
                    + " validEventCount=" + annualScore.getValidEventCount()
                    + " highRiskEventCount=" + annualScore.getHighRiskEventCount()
                    + " severeEventCount=" + annualScore.getSevereEventCount()
                    + " ARI=" + annualScore.getAnnualRiskIndex()
                    + " subScore10=" + annualScore.getSubScore10()
                    + " weightedScore=" + annualScore.getWeightedScore()
                    + " manualReview=" + annualScore.isManualReviewRequired()
                    + " batchNo=" + jobParams.getBatchNo());
            XxlJobLogger.log("ALONGSIDE_V3 summary mode=AUTO mmsi=" + jobParams.getMmsi()
                    + " points=" + ownPoints.size()
                    + " lowSpeedWindows=" + windows.size()
                    + " candidateTargets=" + candidateTargetCount
                    + " rawEvents=" + detected.size()
                    + " confirmedEvents=" + countConfirmedAlongsideEvents(detected)
                    + " mergedEvents=" + detected.size()
                    + " savedEvents=" + (shouldInsert ? detected.size() : 0)
                    + " enableInsert=" + enableInsert
                    + " completeHistoryWindow=" + completeHistoryWindow
                    + " failed=" + failed
                    + " replayQueries=" + replaySearchResult.getReplayQueryCount()
                    + " replayElapsedMs=" + replaySearchResult.getElapsedMs()
                    + " safeLoad=" + safeLoad
                    + " batchNo=" + jobParams.getBatchNo());
        }
        AlongsideWindowResult result = new AlongsideWindowResult(ownPoints.size(), windows.size(), candidateTargetCount,
                candidates.size(), replaySearchResult.getReplayQueryCount(),
                replaySearchResult.getReplayFailures(),
                replaySearchResult.getTruncatedQueries(), detected.size(),
                countConfirmedAlongsideEvents(detected),
                annualScore.getHighRiskEventCount(), failed, allTraces,
                rawLowSpeedWindowCount, worldSeaRejectedWindowCount,
                baselineReplayPlan.size(), filteredReplayPlan.size(), savedReplayQueryCount,
                replaySearchResult.getAttemptedReplayQueryCount(),
                replaySearchResult.getSuccessfulReplayQueryCount(),
                replaySearchResult.getFailedReplayQueryCount(),
                replaySearchResult.getTruncatedReplayQueryCount());
        result.alongsideLowSpeedWindows = alongsideLowSpeedWindowCount;
        result.policyRejectedCandidates = policyRejectedCandidates;
        result.pairReplayRawPoints = replaySearchResult.getRawPoints();
        result.candidateFunnel.add(candidateFunnel);
        result.replayFailureReason = replaySearchResult.getReplayFailureReason();
        result.replayFailureRetryable = replaySearchResult.isReplayFailureRetryable();
        return result;
    }

    public AnalysisResult analysisAlongsideBatch(String params) {
        BatchJobParams batchParams = parseBatchParams(params, "alongside-batch-");
        List<String> mmsiList = resolveBatchMmsiList(batchParams);
        List<TimeWindow> windows = splitTimeWindows(batchParams);
        Set<String> completed = batchParams.isResume()
                ? alongsideBatchStatusRepository.findCompletedWindows(batchParams.getBatchNo())
                : Collections.<String>emptySet();
        List<AlongsideBatchTask> tasks = buildAlongsideBatchTasks(batchParams, mmsiList, windows, completed);
        int skipped = mmsiList.size() * windows.size() - tasks.size();
        int parallelism = resolveAlongsideBatchParallelism(batchParams);
        long startMillis = System.currentTimeMillis();
        XxlJobLogger.log("ALONGSIDE_BATCH start batchNo=" + batchParams.getBatchNo()
                + " ships=" + mmsiList.size()
                + " windows=" + windows.size()
                + " totalTasks=" + (mmsiList.size() * windows.size())
                + " pendingTasks=" + tasks.size()
                + " skipped=" + skipped
                + " sliceMode=" + batchParams.getSliceMode()
                + " parallelism=" + parallelism
                + " resume=" + batchParams.isResume());
        AlongsideBatchResult result = parallelism <= 1
                ? runAlongsideBatchSequential(tasks, startMillis)
                : runAlongsideBatchParallel(tasks, parallelism, startMillis);
        XxlJobLogger.log("ALONGSIDE_BATCH summary batchNo=" + batchParams.getBatchNo()
                + " ships=" + mmsiList.size()
                + " windows=" + windows.size()
                + " totalTasks=" + (mmsiList.size() * windows.size())
                + " processed=" + result.getProcessed()
                + " skipped=" + skipped
                + " failed=" + result.getFailed()
                + " events=" + result.getEvents()
                + " doneStatus=" + alongsideBatchStatusRepository.countByStatus(batchParams.getBatchNo(),
                ShipBehaviorAlongsideBatchStatusRepository.STATUS_DONE)
                + " failedStatus=" + alongsideBatchStatusRepository.countByStatus(batchParams.getBatchNo(),
                ShipBehaviorAlongsideBatchStatusRepository.STATUS_FAILED)
                + " elapsedMs=" + (System.currentTimeMillis() - startMillis));
        logAlongsideBatchAnnualScores(batchParams, mmsiList);
        return new AnalysisResult(result.getProcessed(), result.getFailed(), result.getEvents());
    }

    public AlongsideDailyStats analysisAlongsideDailyStats(String params) {
        JobParams jobParams = parseParams(params);
        applyDefaultTimeRange(jobParams);
        if (StringUtils.isBlank(jobParams.getBatchNo())) {
            throw new IllegalArgumentException("alongside daily stats requires batchNo");
        }
        AlongsideDailyStats stats = shipBehaviorEventRepository.findAlongsideDailyStats(
                jobParams.getBatchNo(), jobParams.getStartTime(), jobParams.getEndTime());
        XxlJobLogger.log("ALONGSIDE_V3 daily_stats"
                + " scannedMerchantShips=" + stats.getScannedMerchantShips()
                + " candidatePairs=" + stats.getCandidatePairs()
                + " confirmedEvents=" + stats.getConfirmedEvents()
                + " L1=" + stats.getL1Count()
                + " L2=" + stats.getL2Count()
                + " L3=" + stats.getL3Count()
                + " L4=" + stats.getL4Count()
                + " batchNo=" + jobParams.getBatchNo());
        return stats;
    }

    private AlongsideBatchResult runAlongsideBatchSequential(List<AlongsideBatchTask> tasks, long startMillis) {
        AlongsideBatchResult result = new AlongsideBatchResult();
        for (AlongsideBatchTask task : tasks) {
            AlongsideTaskResult taskResult = executeAlongsideBatchTask(task);
            result.add(taskResult);
            logAlongsideBatchProgress(result, tasks.size(), startMillis);
        }
        return result;
    }

    private AlongsideBatchResult runAlongsideBatchParallel(List<AlongsideBatchTask> tasks, int parallelism,
                                                          long startMillis) {
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        AlongsideBatchResult result = new AlongsideBatchResult();
        try {
            List<Future<AlongsideTaskResult>> futures = new ArrayList<Future<AlongsideTaskResult>>();
            for (final AlongsideBatchTask task : tasks) {
                futures.add(executor.submit(new Callable<AlongsideTaskResult>() {
                    @Override
                    public AlongsideTaskResult call() {
                        return executeAlongsideBatchTask(task);
                    }
                }));
            }
            for (Future<AlongsideTaskResult> future : futures) {
                try {
                    result.add(future.get());
                } catch (Exception ex) {
                    result.add(AlongsideTaskResult.failed(0));
                    log.error("alongside batch future failed", ex);
                    XxlJobLogger.log("ALONGSIDE_BATCH future_failed error=" + ex.getMessage());
                }
                logAlongsideBatchProgress(result, tasks.size(), startMillis);
            }
        } finally {
            executor.shutdown();
        }
        return result;
    }

    private AlongsideTaskResult executeAlongsideBatchTask(AlongsideBatchTask task) {
        JobParams windowParams = new JobParams();
        windowParams.setMmsi(task.getMmsi());
        windowParams.setStartTime(task.getWindow().getStartTime());
        windowParams.setEndTime(task.getWindow().getEndTime());
        windowParams.setBatchNo(task.getBatchNo());
        windowParams.setMode("AUTO");
        try {
            AlongsideWindowResult result = analysisAlongsideOneWindow(windowParams, buildAlongsideRule(), true);
            alongsideBatchStatusRepository.markDone(task.getBatchNo(), task.getMmsi(),
                    task.getWindow().getStartTime(), task.getWindow().getEndTime(),
                    result.getPoints(), result.getLowSpeedWindows(), result.getCandidateTargets(),
                    result.getReplayQueries(), result.getReplayFailures(), result.getEvents());
            XxlJobLogger.log("ALONGSIDE_BATCH ship_window mmsi=" + task.getMmsi()
                    + " startTime=" + task.getWindow().getStartTime()
                    + " endTime=" + task.getWindow().getEndTime()
                    + " status=DONE points=" + result.getPoints()
                    + " lowSpeedWindows=" + result.getLowSpeedWindows()
                    + " candidateTargets=" + result.getCandidateTargets()
                    + " replayQueries=" + result.getReplayQueries()
                    + " replayFailures=" + result.getReplayFailures()
                    + " events=" + result.getEvents()
                    + " loadSliceHours=" + alongsideBatchTrajectoryLoadSliceHours
                    + " batchNo=" + task.getBatchNo());
            return AlongsideTaskResult.done(result.getEvents());
        } catch (Throwable ex) {
            alongsideBatchStatusRepository.markFailed(task.getBatchNo(), task.getMmsi(),
                    task.getWindow().getStartTime(), task.getWindow().getEndTime(), throwableMessage(ex));
            log.error("alongside batch task failed, batchNo={}, mmsi={}, window={}",
                    task.getBatchNo(), task.getMmsi(), task.getWindow(), ex);
            XxlJobLogger.log("ALONGSIDE_BATCH ship_window mmsi=" + task.getMmsi()
                    + " startTime=" + task.getWindow().getStartTime()
                    + " endTime=" + task.getWindow().getEndTime()
                    + " status=FAILED error=" + throwableMessage(ex)
                    + " batchNo=" + task.getBatchNo());
            return AlongsideTaskResult.failed(0);
        }
    }

    private String throwableMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (StringUtils.isBlank(message) ? "" : ": " + message);
    }

    private void logAlongsideBatchAnnualScores(BatchJobParams batchParams, List<String> mmsiList) {
        for (String mmsi : mmsiList) {
            try {
                List<ShipBehaviorEvent> events = shipBehaviorEventRepository.findAlongsideEvents(
                        batchParams.getBatchNo(), mmsi, batchParams.getStartTime(), batchParams.getEndTime());
                JobParams statParams = new JobParams();
                statParams.setMmsi(mmsi);
                statParams.setStartTime(batchParams.getStartTime());
                statParams.setEndTime(batchParams.getEndTime());
                AlongsideAnnualScore score = calculateAlongsideAnnualScore(statParams, events);
                XxlJobLogger.log("ALONGSIDE_BATCH annual_score mmsi=" + mmsi
                        + " eventCount=" + score.getEventCount()
                        + " validEventCount=" + score.getValidEventCount()
                        + " highRiskEventCount=" + score.getHighRiskEventCount()
                        + " severeEventCount=" + score.getSevereEventCount()
                        + " ARI=" + score.getAnnualRiskIndex()
                        + " subScore10=" + score.getSubScore10()
                        + " weightedScore=" + score.getWeightedScore()
                        + " manualReview=" + score.isManualReviewRequired()
                        + " batchNo=" + batchParams.getBatchNo());
            } catch (Exception ex) {
                log.warn("alongside batch annual score failed, batchNo={}, mmsi={}",
                        batchParams.getBatchNo(), mmsi, ex);
                XxlJobLogger.log("ALONGSIDE_BATCH annual_score_failed mmsi=" + mmsi
                        + " error=" + ex.getMessage()
                        + " batchNo=" + batchParams.getBatchNo());
            }
        }
    }

    private void logAlongsideBatchProgress(AlongsideBatchResult result, int totalTasks, long startMillis) {
        int interval = Math.max(1, alongsideBatchProgressInterval);
        int done = result.getProcessed() + result.getFailed();
        if (done == totalTasks || done % interval == 0) {
            XxlJobLogger.log("ALONGSIDE_BATCH progress processed=" + result.getProcessed()
                    + " failed=" + result.getFailed()
                    + " totalTasks=" + totalTasks
                    + " events=" + result.getEvents()
                    + " elapsedMs=" + (System.currentTimeMillis() - startMillis));
        }
    }

    private void tagBatch(List<ShipBehaviorEvent> events, JobParams jobParams) {
        for (ShipBehaviorEvent event : events) {
            event.setBatchNo(jobParams.getBatchNo());
        }
    }

    private ShipLoiteringDetector.LoiteringConfig buildLoiteringConfig() {
        ShipLoiteringDetector.LoiteringConfig config = ShipLoiteringDetector.LoiteringConfig.defaultConfig();
        config.setRuleVersion(loiteringRuleVersion);
        config.setMaxGapMinutes(loiteringMaxGapMinutes);
        config.setEventSplitGapMinutes(loiteringEventSplitGapMinutes);
        config.setMaxValidSpeed(loiteringMaxValidSpeed);
        config.setMinWarningPoints(loiteringMinWarningPoints);
        config.setSlowMaxSpeed(loiteringSlowMaxSpeed);
        config.setSlowMinDurationMinutes(loiteringSlowMinDurationMinutes);
        config.setSlowMaxRadiusNm(loiteringSlowMaxRadiusNm);
        config.setSlowMinPoints(loiteringSlowMinPoints);
        config.setManeuverMinSpeed(loiteringManeuverMinSpeed);
        config.setManeuverMaxSpeed(loiteringManeuverMaxSpeed);
        config.setManeuverMinDurationMinutes(loiteringManeuverMinDurationMinutes);
        config.setManeuverMaxRadiusNm(loiteringManeuverMaxRadiusNm);
        config.setManeuverMinTurnCountPerHour(loiteringManeuverMinTurnCountPerHour);
        config.setManeuverMinCogChangePerHour(loiteringManeuverMinCogChangePerHour);
        config.setManeuverMinTortuosity(loiteringManeuverMinTortuosity);
        config.setTurnThresholdDegree(loiteringTurnThresholdDegree);
        config.setHighRiskEnabled(loiteringHighRiskEnabled);
        config.setHighRiskMinDurationMinutes(loiteringHighRiskMinDurationMinutes);
        config.setHighRiskMaxRadiusNm(loiteringHighRiskMaxRadiusNm);
        config.setMergeEnabled(loiteringMergeEnabled);
        config.setMergeMinOverlapRatio(loiteringMergeMinOverlapRatio);
        config.setMergeMaxCenterDistanceNm(loiteringMergeMaxCenterDistanceNm);
        config.setMergeCompositeSubtype(loiteringMergeCompositeSubtype);
        config.setSeverityCapEnabled(loiteringSeverityCapEnabled);
        config.setSeverityCapNoSceneDataMaxLevel(loiteringSeverityCapNoSceneDataMaxLevel);
        config.setSeverityCapNoSceneDataMaxDurationMinutes(loiteringSeverityCapNoSceneDataMaxDurationMinutes);
        config.setRequireExternalEvidenceForL3(loiteringRequireExternalEvidenceForL3);
        config.setAutoL4Enabled(loiteringAutoL4Enabled);
        config.setSceneDataAvailable(loiteringSceneDataAvailable);
        config.setDiagnosticEnabled(loiteringDiagnosticEnabled);
        config.setNearMissLimit(loiteringNearMissLimit);
        config.setWeakEnabled(loiteringWeakEnabled);
        config.setWeakStoreEnabled(loiteringWeakStoreEnabled);
        config.setWeakMinDurationMinutes(loiteringWeakMinDurationMinutes);
        config.setWeakMaxSpeed(loiteringWeakMaxSpeed);
        config.setWeakMaxRadiusNm(loiteringWeakMaxRadiusNm);
        config.setWeakMinPoints(loiteringWeakMinPoints);
        config.setWeakMaxGapMinutes(loiteringWeakMaxGapMinutes);
        config.setWeakMaxScore(loiteringWeakMaxScore);
        return config;
    }

    private ShipAlongsideDetector.AlongsideRule buildAlongsideRule() {
        ShipAlongsideDetector.AlongsideRule rule = ShipAlongsideDetector.AlongsideRule.defaultRule();
        rule.setRuleVersion(alongsideRuleVersion);
        rule.setCodeCommitSha(codeCommitSha);
        rule.setConfirmMaxDistanceNm(resolveAlongsideConfirmDistance());
        rule.setConfirmMinSpeed(alongsideConfirmMinSpeed);
        rule.setConfirmMaxSpeed(alongsideConfirmMaxSpeed);
        rule.setMaxRelativeSpeed(alongsideConfirmMaxRelativeSpeed);
        rule.setMaxTimeGapMinutes(alongsideConfirmMaxTimeGapMinutes);
        rule.setHighConfidenceTimeGapMinutes(alongsideConfirmHighConfidenceTimeGapMinutes);
        rule.setWeakMinDurationMinutes(Math.min(alongsideMinDurationMinutes, alongsideConfirmMinDurationMinutes));
        rule.setMinDurationMinutes(alongsideConfirmMinDurationMinutes);
        rule.setMinPoints(Math.max(1, alongsideMinPoints));
        rule.setFormalMinPoints(Math.max(alongsideMinPoints, alongsideConfirmMinPoints));
        rule.setHeadingEnabled(alongsideHeadingEnabled);
        rule.setHeadingParallelThresholdDegree(alongsideHeadingParallelThresholdDegree);
        rule.setMinParallelRatio(alongsideMinParallelRatio);
        rule.setMaxDistanceVarianceNm2(alongsideMaxDistanceVarianceNm2);
        rule.setCrowdedVesselCount(alongsideCrowdedVesselCount);
        rule.setMergeEnabled(alongsideMergeEnabled);
        rule.setMergeMaxGapMinutes(alongsideMergeMaxGapMinutes);
        rule.setMergeMaxCenterDistanceNm(alongsideMergeMaxCenterDistanceNm);
        rule.setAisGapMinutes(alongsideCandidateSplitGapMinutes);
        rule.setNoSceneDataMaxLevel(alongsideSeverityCapNoSceneDataMaxLevel);
        rule.setSceneDataAvailable(alongsideSceneDataAvailable);
        rule.setCounterpartyRiskAvailable(alongsideCounterpartyRiskAvailable);
        rule.setAutoL4Enabled(alongsideAutoL4Enabled);
        return rule;
    }

    private Map<String, String> loadShipTypes(Set<String> mmsis) {
        try {
            return ais41GeneralQueryRepository.selectShipTypes(mmsis);
        } catch (Exception ex) {
            log.warn("load alongside ship types failed, continue as unknown, mmsis={}", mmsis, ex);
            return Collections.emptyMap();
        }
    }

    private ShipAlongsideDetector.AlongsideContext buildAlongsideContext(
            String ownMmsi, String targetMmsi, Map<String, String> shipTypes,
            List<TrajectoryPoint> ownPoints, List<TrajectoryPoint> targetPoints, int nearbyVesselCount) {
        return buildAlongsideContext(ownMmsi, targetMmsi, shipTypes, ownPoints, targetPoints, nearbyVesselCount, false);
    }

    private ShipAlongsideDetector.AlongsideContext buildAlongsideContext(
            String ownMmsi, String targetMmsi, Map<String, String> shipTypes,
            List<TrajectoryPoint> ownPoints, List<TrajectoryPoint> targetPoints, int nearbyVesselCount,
            boolean policyOwnEligibility) {
        String ownType = shipTypes.get(ownMmsi);
        String targetType = shipTypes.get(targetMmsi);
        AlongsideSceneService.Scene scene = AlongsideSceneService.Scene.unknown();
        double[] eventLocation = pairedEventLocation(ownPoints, targetPoints);
        if (eventLocation != null) {
            scene = alongsideSceneService.classify(eventLocation[0], eventLocation[1]);
        }
        TargetRoleClassification target = shipTypeClassifier.classifyTargetShipWithReason(targetType, null);
        return ShipAlongsideDetector.AlongsideContext.asymmetricWithScene(
                ownType, targetType, policyOwnEligibility ? OwnShipEligibility.ELIGIBLE_MERCHANT
                        : shipTypeClassifier.classifyOwnShip(ownType), target,
                scene.getSceneType(), scene.getNearestPortId(), scene.getNearestPortName(),
                scene.getDistanceToNearestPortNm(), scene.isDataAvailable(), nearbyVesselCount);
    }

    private double[] pairedEventLocation(List<TrajectoryPoint> ownPoints, List<TrajectoryPoint> targetPoints) {
        if (ownPoints == null || targetPoints == null || ownPoints.isEmpty() || targetPoints.isEmpty()) {
            return null;
        }
        List<double[]> midpoints = new ArrayList<double[]>();
        int targetIndex = 0;
        for (TrajectoryPoint own : ownPoints) {
            int bestIndex = -1;
            long bestGap = Long.MAX_VALUE;
            for (int i = targetIndex; i < targetPoints.size(); i++) {
                TrajectoryPoint target = targetPoints.get(i);
                long gap = Math.abs(java.time.Duration.between(own.getTime(), target.getTime()).getSeconds());
                if (gap < bestGap) {
                    bestGap = gap;
                    bestIndex = i;
                }
                if (target.getTime().isAfter(own.getTime())
                        && gap > alongsideConfirmMaxTimeGapMinutes * 60L) {
                    break;
                }
            }
            if (bestIndex >= 0 && bestGap <= alongsideConfirmMaxTimeGapMinutes * 60L) {
                TrajectoryPoint target = targetPoints.get(bestIndex);
                midpoints.add(new double[]{(own.getLon() + target.getLon()) / 2.0,
                        (own.getLat() + target.getLat()) / 2.0});
                targetIndex = bestIndex + 1;
            }
        }
        if (midpoints.isEmpty()) { return null; }
        int count = midpoints.size();
        int[] samples = count == 1 ? new int[]{0} : new int[]{0, count / 2, count - 1};
        double lon = 0.0;
        double lat = 0.0;
        for (int index : samples) {
            lon += midpoints.get(index)[0];
            lat += midpoints.get(index)[1];
        }
        return new double[]{lon / samples.length, lat / samples.length};
    }

    private double resolveAlongsideConfirmDistance() {
        if (alongsideConfirmMaxDistanceNm > 0.0) {
            return alongsideConfirmMaxDistanceNm;
        }
        return alongsideMaxDistanceNm;
    }

    private String resolveAlongsideMode(JobParams jobParams) {
        if ("AUTO".equalsIgnoreCase(jobParams.getMode()) || StringUtils.isBlank(jobParams.getTargetMmsi())) {
            return "AUTO";
        }
        return "PAIR";
    }

    private int countUniqueTargets(List<AlongsideCandidateTarget> candidates) {
        Set<String> targets = new HashSet<String>();
        for (AlongsideCandidateTarget candidate : candidates) {
            if (StringUtils.isNotBlank(candidate.getTargetMmsi())) {
                targets.add(candidate.getTargetMmsi());
            }
        }
        return targets.size();
    }

    private int countConfirmedAlongsideEvents(List<ShipBehaviorEvent> events) {
        int count = 0;
        for (ShipBehaviorEvent event : events) {
            if (event != null
                    && !ShipAlongsideDetector.SUB_TYPE_WEAK.equals(event.getEventSubType())
                    && !ShipAlongsideDetector.EXCLUDED_NORMAL.equals(event.getExclusionFlag())) {
                count++;
            }
        }
        return count;
    }

    private Map<String, List<TrajectoryPoint>> collectOwnPointsByWindow(
            List<ShipAlongsideDetector.LowSpeedWindow> windows, List<TrajectoryPoint> ownPoints) {
        Map<String, List<TrajectoryPoint>> result = new HashMap<String, List<TrajectoryPoint>>();
        for (ShipAlongsideDetector.LowSpeedWindow window : windows) {
            LocalDateTime start = LocalDateTime.parse(window.getStartTime(), TIME_FORMATTER);
            LocalDateTime end = LocalDateTime.parse(window.getEndTime(), TIME_FORMATTER);
            List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
            for (TrajectoryPoint point : ownPoints) {
                if (point.getTime() == null) {
                    continue;
                }
                if (!point.getTime().isBefore(start) && !point.getTime().isAfter(end)) {
                    points.add(point);
                }
            }
            result.put(window.getStartTime() + "|" + window.getEndTime(), points);
        }
        return result;
    }

    private void enrichReplayScoreDetails(List<ShipBehaviorTrace> traces, AlongsideReplaySearchResult replaySearchResult) {
        for (ShipBehaviorTrace trace : traces) {
            ShipBehaviorEvent event = trace.getEvent();
            if (event == null) {
                continue;
            }
            try {
                JSONObject detail = StringUtils.isBlank(event.getScoreDetailJson())
                        ? new JSONObject() : JSON.parseObject(event.getScoreDetailJson());
                detail.put("candidateSource", "SPATIAL_AIS_REPLAY");
                detail.put("replayQueryCount", replaySearchResult.getReplayQueryCount());
                detail.put("replayFailures", replaySearchResult.getReplayFailures());
                detail.put("replayTruncatedQueries", replaySearchResult.getTruncatedQueries());
                detail.put("replayReturnedVessels", replaySearchResult.getReturnedVessels());
                detail.put("replayRawPoints", replaySearchResult.getRawPoints());
                detail.put("replayValidPoints", replaySearchResult.getValidPoints());
                detail.put("replayParseErrors", replaySearchResult.getParseErrors());
                detail.put("replayElapsedMs", replaySearchResult.getElapsedMs());
                event.setScoreDetailJson(detail.toJSONString());
            } catch (Exception ex) {
                log.warn("enrich replay score detail failed, event={}", event.getTargetMmsi(), ex);
            }
        }
    }

    private AlongsideAnnualScore calculateAlongsideAnnualScore(JobParams jobParams, List<ShipBehaviorEvent> events) {
        int eventCount = 0;
        int validEventCount = 0;
        int highRiskEventCount = 0;
        int severeEventCount = 0;
        int uniqueTargetCount;
        int aisGapRelatedCount = 0;
        long totalDurationMinutes = 0L;
        long maxDurationMinutes = 0L;
        double maxEventScore = 0.0;
        double ari = 0.0;
        Set<String> targets = new HashSet<String>();
        for (ShipBehaviorEvent event : events) {
            if (event == null) {
                continue;
            }
            eventCount++;
            if (StringUtils.isNotBlank(event.getTargetMmsi())) {
                targets.add(event.getTargetMmsi());
            }
            if (ShipAlongsideDetector.VALID_RISK_EVENT.equals(event.getExclusionFlag())) {
                validEventCount++;
            }
            if (severityRank(event.getSeverityLevel()) >= 3) {
                highRiskEventCount++;
            }
            if ("L4".equals(event.getSeverityLevel())) {
                severeEventCount++;
            }
            long duration = event.getDurationMinutes() == null ? 0L : event.getDurationMinutes();
            if (!ShipAlongsideDetector.SUB_TYPE_WEAK.equals(event.getEventSubType())
                    && ShipAlongsideDetector.VALID_RISK_EVENT.equals(event.getExclusionFlag())) {
                totalDurationMinutes += duration;
                maxDurationMinutes = Math.max(maxDurationMinutes, duration);
                double weight = severityWeight(event.getSeverityLevel());
                if (event.getAisGapCount() != null && event.getAisGapCount() > 0) {
                    weight *= 1.2;
                    aisGapRelatedCount++;
                }
                ari += weight;
            }
            if (event.getEventScore() != null) {
                maxEventScore = Math.max(maxEventScore, event.getEventScore());
            }
        }
        uniqueTargetCount = targets.size();
        int subScore10 = mapAlongsideSubScore10(ari, severeEventCount);
        double weightedScore = subScore10 * alongsideScoreWeight;
        boolean manualReview = severeEventCount > 0 || highRiskEventCount > 0 || ari > 15.0;
        return new AlongsideAnnualScore(jobParams.getMmsi(), jobParams.getStartTime(), jobParams.getEndTime(),
                eventCount, validEventCount, highRiskEventCount, severeEventCount, uniqueTargetCount,
                totalDurationMinutes, maxDurationMinutes, aisGapRelatedCount, maxEventScore, ari,
                subScore10, weightedScore, manualReview);
    }

    private double severityWeight(String severityLevel) {
        if ("L4".equals(severityLevel)) {
            return 8.0;
        }
        if ("L3".equals(severityLevel)) {
            return 4.0;
        }
        if ("L2".equals(severityLevel)) {
            return 2.0;
        }
        if ("L1".equals(severityLevel)) {
            return 1.0;
        }
        return 0.0;
    }

    private int mapAlongsideSubScore10(double ari, int severeEventCount) {
        if (severeEventCount > 0 || ari > 15.0) {
            return 0;
        }
        if (ari == 0.0) {
            return 10;
        }
        if (ari <= 3.0) {
            return 8;
        }
        if (ari <= 8.0) {
            return 6;
        }
        return 3;
    }

    private int severityRank(String severity) {
        if ("L4".equals(severity)) {
            return 4;
        }
        if ("L3".equals(severity)) {
            return 3;
        }
        if ("L2".equals(severity)) {
            return 2;
        }
        if ("L1".equals(severity)) {
            return 1;
        }
        return 0;
    }

    private List<ShipBehaviorEvent> extractEvents(List<ShipBehaviorTrace> traces) {
        List<ShipBehaviorEvent> events = new ArrayList<ShipBehaviorEvent>();
        for (ShipBehaviorTrace trace : traces) {
            if (trace != null && trace.getEvent() != null) {
                events.add(trace.getEvent());
            }
        }
        return events;
    }

    private void logLoiteringMerge(JobParams jobParams, ShipLoiteringDetector.DetectionStats stats) {
        XxlJobLogger.log("LOITERING_V2 merge rawCandidates=" + stats.getRawCandidates()
                + " mergedCandidates=" + stats.getMergedCandidates()
                + " duplicateMerged=" + stats.getDuplicateMerged()
                + " batchNo=" + jobParams.getBatchNo());
    }

    private void logLoiteringSummary(String mmsi, JobParams jobParams,
                                     ShipLoiteringDetector.DetectionStats stats, int detectedSize) {
        XxlJobLogger.log("LOITERING_V2 summary mmsi=" + mmsi
                + " points=" + stats.getPoints()
                + " cleanPoints=" + stats.getCleanPoints()
                + " segments=" + stats.getSegments()
                + " rawCandidates=" + stats.getRawCandidates()
                + " mergedCandidates=" + stats.getMergedCandidates()
                + " savedEvents=" + stats.getSavedEvents()
                + " events=" + detectedSize
                + " validRiskEvents=" + stats.getValidRiskEvents()
                + " excludedNormalEvents=" + stats.getExcludedNormalEvents()
                + " downgradedNormalLikelyEvents=" + stats.getDowngradedNormalLikelyEvents()
                + " lowConfidenceEvents=" + stats.getLowConfidenceEvents()
                + " weakCandidates=" + stats.getWeakCandidates()
                + " duplicateMerged=" + stats.getDuplicateMerged()
                + " sceneDataAvailable=" + stats.isSceneDataAvailable()
                + " riskAreaEnabled=" + stats.isRiskAreaEnabled()
                + " batchNo=" + jobParams.getBatchNo());
    }

    private void logLoiteringDiagnostics(String mmsi, ShipLoiteringDetector.DetectionStats stats) {
        int formalSavedEvents = Math.max(0, stats.getSavedEvents() - stats.getWeakCandidates());
        if (!loiteringDiagnosticEnabled || (stats.getRawCandidates() > 0 && formalSavedEvents > 0)) {
            return;
        }
        ShipLoiteringDetector.RejectSummary reject = stats.getRejectSummary();
        XxlJobLogger.log("LOITERING_V2 reject_summary mmsi=" + mmsi
                + " segments=" + stats.getSegments()
                + " rejectByDuration=" + reject.getRejectByDuration()
                + " rejectByPointCount=" + reject.getRejectByPointCount()
                + " rejectByRadius=" + reject.getRejectByRadius()
                + " rejectBySpeed=" + reject.getRejectBySpeed()
                + " rejectByTurnFeature=" + reject.getRejectByTurnFeature()
                + " rejectByGapSplit=" + reject.getRejectByGapSplit()
                + " rejectByCoverage=" + reject.getRejectByCoverage()
                + " rejectByNormalScene=" + reject.getRejectByNormalScene()
                + " rejectByUnknown=" + reject.getRejectByUnknown()
                + " nearMissCount=" + reject.getNearMisses().size());
        int limit = Math.max(0, loiteringNearMissLimit);
        for (int i = 0; i < reject.getNearMisses().size() && i < limit; i++) {
            ShipLoiteringDetector.NearMiss nearMiss = reject.getNearMisses().get(i);
            ShipLoiteringDetector.LoiteringEventFeature feature = nearMiss.getFeature();
            XxlJobLogger.log("LOITERING_V2 near_miss[" + (i + 1) + "]"
                    + " start=" + nearMiss.getStartTime()
                    + " end=" + nearMiss.getEndTime()
                    + " duration=" + feature.getDurationMinutes()
                    + " pointCount=" + feature.getPointCount()
                    + " avgSpeed=" + feature.getAvgSog()
                    + " radiusNm=" + feature.getRadiusNm()
                    + " maxGap=" + feature.getMaxAisGapMinutes()
                    + " coverage=" + feature.getTrackCoverageRatio()
                    + " failedRules=" + nearMiss.getFailedRules());
        }
    }

    private List<ShipBehaviorEventDetail> toDetails(List<ShipBehaviorTrace> traces, JobParams jobParams) {
        List<ShipBehaviorEventDetail> details = new ArrayList<ShipBehaviorEventDetail>();
        for (ShipBehaviorTrace trace : traces) {
            ShipBehaviorEvent event = trace.getEvent();
            appendDetails(details, event, trace.getOwnPoints(), ShipBehaviorEventDetail.ROLE_OWN, jobParams);
            appendDetails(details, event, trace.getTargetPoints(), ShipBehaviorEventDetail.ROLE_TARGET, jobParams);
        }
        return details;
    }

    private void appendDetails(List<ShipBehaviorEventDetail> details, ShipBehaviorEvent event,
                               List<TrajectoryPoint> points, String pointRole, JobParams jobParams) {
        if (event == null || points == null || points.isEmpty()) {
            return;
        }
        for (int i = 0; i < points.size(); i++) {
            TrajectoryPoint point = points.get(i);
            ShipBehaviorEventDetail detail = new ShipBehaviorEventDetail();
            detail.setBehaviorType(event.getBehaviorType());
            detail.setMmsi(event.getMmsi());
            detail.setTargetMmsi(event.getTargetMmsi());
            detail.setPointRole(pointRole);
            detail.setEventStartTime(event.getStartTime());
            detail.setEventEndTime(event.getEndTime());
            detail.setPointTime(TrajectoryUtils.formatTime(point));
            detail.setPointIndex(i + 1);
            detail.setLon(point.getLon());
            detail.setLat(point.getLat());
            detail.setSpeed(point.getSpeed());
            detail.setCourse(point.getCourse());
            detail.setNavStatus(point.getNavStatus());
            detail.setBatchNo(jobParams.getBatchNo());
            detail.setRuleVersion(event.getRuleVersion());
            details.add(detail);
        }
    }

    private void logEventSummaries(List<ShipBehaviorTrace> traces) {
        int maxCount = Math.max(0, traceLogMaxEvents);
        for (int i = 0; i < traces.size() && i < maxCount; i++) {
            ShipBehaviorEvent event = traces.get(i).getEvent();
            if (ShipBehaviorEvent.TYPE_LOITERING.equals(event.getBehaviorType())) {
                XxlJobLogger.log("event[" + (i + 1) + "] type=" + event.getBehaviorType()
                        + " subType=" + event.getEventSubType()
                        + " severity=" + event.getSeverityLevel()
                        + " confidence=" + event.getConfidenceLevel()
                        + " start=" + event.getStartTime()
                        + " end=" + event.getEndTime()
                        + " duration=" + event.getDurationMinutes()
                        + " radiusNm=" + event.getRadiusNm()
                        + " avgSpeed=" + event.getAvgSpeed()
                        + " pointCount=" + event.getPointCount()
                        + " maxGap=" + event.getMaxAisGapMinutes()
                        + " coverage=" + event.getTrackCoverageRatio()
                        + " score=" + event.getEventScore()
                        + " exclusion=" + event.getExclusionFlag()
                        + " reason=" + event.getExclusionReason()
                        + " batchNo=" + event.getBatchNo());
            } else {
                XxlJobLogger.log("ALONGSIDE_V3 event[" + (i + 1) + "] type=" + event.getBehaviorType()
                        + " severity=" + event.getSeverityLevel()
                        + " confidence=" + event.getConfidenceLevel()
                        + " start=" + event.getStartTime()
                        + " end=" + event.getEndTime()
                        + " duration=" + event.getDurationMinutes()
                        + " minDistanceNm=" + event.getMinDistanceNm()
                        + " avgDistanceNm=" + extractScoreDetailValue(event.getScoreDetailJson(), "avgDistanceNm")
                        + " maxDistanceNm=" + event.getMaxDistanceNm()
                        + " avgRelativeSpeed=" + extractScoreDetailValue(event.getScoreDetailJson(), "avgRelativeSpeed")
                        + " avgSpeed=" + event.getAvgSpeed()
                        + " pointCount=" + event.getPointCount()
                        + " exclusion=" + event.getExclusionFlag()
                        + " score=" + event.getEventScore()
                        + " targetMmsi=" + event.getTargetMmsi()
                        + " batchNo=" + event.getBatchNo());
            }
        }
        if (traces.size() > maxCount) {
            XxlJobLogger.log("event summaries truncated, total=" + traces.size() + ", logged=" + maxCount);
        }
    }

    private String extractScoreDetailValue(String json, String key) {
        if (StringUtils.isBlank(json)) {
            return "";
        }
        try {
            JSONObject object = JSON.parseObject(json);
            Object value = object.get(key);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception ex) {
            return "";
        }
    }

    private List<String> resolveMmsiList(JobParams jobParams) {
        if (!jobParams.getMmsiList().isEmpty()) {
            return jobParams.getMmsiList();
        }
        if (StringUtils.isNotBlank(jobParams.getMmsi())) {
            return Collections.singletonList(jobParams.getMmsi());
        }
        List<ShipForm> ships = ais41GeneralQueryRepository.selectRecentShips();
        List<String> result = new ArrayList<String>();
        if (ships != null) {
            for (ShipForm ship : ships) {
                if (ship != null && StringUtils.isNotBlank(ship.getMmsi())) {
                    result.add(ship.getMmsi().trim());
                }
            }
        }
        return result;
    }

    private List<String> resolveBatchMmsiList(BatchJobParams batchParams) {
        List<String> result = new ArrayList<String>();
        if (!batchParams.getMmsiList().isEmpty()) {
            result.addAll(batchParams.getMmsiList());
        } else {
            List<ShipForm> ships = ais41GeneralQueryRepository.selectRecentShips();
            if (ships != null) {
                for (ShipForm ship : ships) {
                    if (ship != null && StringUtils.isNotBlank(ship.getMmsi())) {
                        result.add(ship.getMmsi().trim());
                    }
                }
            }
        }
        int maxShips = batchParams.getMaxShips();
        if (maxShips > 0 && result.size() > maxShips) {
            return new ArrayList<String>(result.subList(0, maxShips));
        }
        return result;
    }

    private BatchJobParams parseBatchParams(String params) {
        return parseBatchParams(params, "loitering-batch-");
    }

    private BatchJobParams parseBatchParams(String params, String defaultBatchPrefix) {
        BatchJobParams batchParams = new BatchJobParams();
        if (StringUtils.isBlank(params)) {
            applyDefaultBatchTimeRange(batchParams, defaultBatchPrefix);
            return batchParams;
        }
        String trimmed = params.trim();
        JSONObject json = JSON.parseObject(trimmed);
        batchParams.setStartTime(json.getString("startTime"));
        batchParams.setEndTime(json.getString("endTime"));
        batchParams.setBatchNo(json.getString("batchNo"));
        batchParams.setSliceMode(json.getString("sliceMode"));
        batchParams.setResume(json.getBooleanValue("resume"));
        batchParams.setParallelism(json.getIntValue("parallelism"));
        batchParams.setMaxShips(json.getIntValue("maxShips"));
        batchParams.setSliceDays(json.getIntValue("sliceDays"));
        if (json.getJSONArray("mmsiList") != null) {
            for (Object item : json.getJSONArray("mmsiList")) {
                if (item != null && StringUtils.isNotBlank(String.valueOf(item))) {
                    batchParams.getMmsiList().add(String.valueOf(item).trim());
                }
            }
        }
        if (StringUtils.isNotBlank(json.getString("mmsi"))) {
            batchParams.getMmsiList().add(json.getString("mmsi").trim());
        }
        applyDefaultBatchTimeRange(batchParams, defaultBatchPrefix);
        return batchParams;
    }

    private void applyDefaultBatchTimeRange(BatchJobParams batchParams) {
        applyDefaultBatchTimeRange(batchParams, "loitering-batch-");
    }

    private void applyDefaultBatchTimeRange(BatchJobParams batchParams, String defaultBatchPrefix) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(Math.max(1, defaultHours));
        if (StringUtils.isBlank(batchParams.getStartTime())) {
            batchParams.setStartTime(start.format(TIME_FORMATTER));
        }
        if (StringUtils.isBlank(batchParams.getEndTime())) {
            batchParams.setEndTime(end.format(TIME_FORMATTER));
        }
        if (StringUtils.isBlank(batchParams.getBatchNo())) {
            batchParams.setBatchNo(defaultBatchPrefix + batchParams.getStartTime().substring(0, 10));
        }
        if (StringUtils.isBlank(batchParams.getSliceMode())) {
            batchParams.setSliceMode("MONTH");
        }
    }

    private List<TimeWindow> splitTimeWindows(BatchJobParams batchParams) {
        LocalDateTime start = LocalDateTime.parse(batchParams.getStartTime(), TIME_FORMATTER);
        LocalDateTime end = LocalDateTime.parse(batchParams.getEndTime(), TIME_FORMATTER);
        List<TimeWindow> windows = new ArrayList<TimeWindow>();
        LocalDateTime cursor = start;
        while (cursor.isBefore(end)) {
            LocalDateTime next = nextWindowEnd(cursor, batchParams);
            if (next.isAfter(end)) {
                next = end;
            }
            windows.add(new TimeWindow(cursor.format(TIME_FORMATTER), next.format(TIME_FORMATTER)));
            cursor = next;
        }
        return windows;
    }

    private LocalDateTime nextWindowEnd(LocalDateTime cursor, BatchJobParams batchParams) {
        String mode = batchParams.getSliceMode() == null ? "" : batchParams.getSliceMode().toUpperCase();
        if ("DAY".equals(mode)) {
            return cursor.plusDays(1);
        }
        if ("WEEK".equals(mode)) {
            return cursor.plusWeeks(1);
        }
        if ("CUSTOM_DAYS".equals(mode)) {
            return cursor.plusDays(Math.max(1, batchParams.getSliceDays()));
        }
        return cursor.plusMonths(1);
    }

    private List<LoiteringBatchTask> buildLoiteringBatchTasks(BatchJobParams batchParams,
                                                               List<String> mmsiList,
                                                               List<TimeWindow> windows,
                                                               Set<String> completed) {
        List<LoiteringBatchTask> tasks = new ArrayList<LoiteringBatchTask>();
        for (String mmsi : mmsiList) {
            for (TimeWindow window : windows) {
                String key = ShipBehaviorLoiteringBatchStatusRepository.windowKey(
                        mmsi, window.getStartTime(), window.getEndTime());
                if (completed.contains(key)) {
                    continue;
                }
                tasks.add(new LoiteringBatchTask(batchParams.getBatchNo(), mmsi, window));
            }
        }
        return tasks;
    }

    private List<AlongsideBatchTask> buildAlongsideBatchTasks(BatchJobParams batchParams,
                                                               List<String> mmsiList,
                                                               List<TimeWindow> windows,
                                                               Set<String> completed) {
        List<AlongsideBatchTask> tasks = new ArrayList<AlongsideBatchTask>();
        for (String mmsi : mmsiList) {
            for (TimeWindow window : windows) {
                String key = ShipBehaviorAlongsideBatchStatusRepository.windowKey(
                        mmsi, window.getStartTime(), window.getEndTime());
                if (completed.contains(key)) {
                    continue;
                }
                tasks.add(new AlongsideBatchTask(batchParams.getBatchNo(), mmsi, window));
            }
        }
        return tasks;
    }

    private int resolveBatchParallelism(BatchJobParams batchParams) {
        int requested = batchParams.getParallelism() > 0 ? batchParams.getParallelism() : loiteringBatchParallelism;
        return Math.max(1, Math.min(8, requested));
    }

    private int resolveAlongsideBatchParallelism(BatchJobParams batchParams) {
        int requested = batchParams.getParallelism() > 0 ? batchParams.getParallelism() : alongsideBatchParallelism;
        return Math.max(1, Math.min(8, requested));
    }

    private JobParams parseParams(String params) {
        JobParams jobParams = new JobParams();
        if (StringUtils.isBlank(params)) {
            return jobParams;
        }
        String trimmed = params.trim();
        try {
            if (trimmed.startsWith("{")) {
                JSONObject json = JSON.parseObject(trimmed);
                jobParams.setMmsi(json.getString("mmsi"));
                jobParams.setTargetMmsi(json.getString("targetMmsi"));
                jobParams.setStartTime(json.getString("startTime"));
                jobParams.setEndTime(json.getString("endTime"));
                jobParams.setBatchNo(json.getString("batchNo"));
                jobParams.setMode(json.getString("mode"));
                if (json.getJSONArray("mmsiList") != null) {
                    for (Object item : json.getJSONArray("mmsiList")) {
                        jobParams.getMmsiList().add(String.valueOf(item));
                    }
                }
            } else {
                parseKeyValueParams(trimmed, jobParams);
            }
        } catch (Exception ex) {
            log.warn("parse ship behavior params failed, raw={}", trimmed, ex);
            parseKeyValueParams(trimmed, jobParams);
        }
        return jobParams;
    }

    private void parseKeyValueParams(String params, JobParams jobParams) {
        String[] parts = params.split("[;,]");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim();
            String value = kv[1].trim();
            if ("mmsi".equalsIgnoreCase(key)) {
                jobParams.setMmsi(value);
            } else if ("targetMmsi".equalsIgnoreCase(key)) {
                jobParams.setTargetMmsi(value);
            } else if ("mmsiList".equalsIgnoreCase(key)) {
                for (String mmsi : value.split("\\|")) {
                    if (StringUtils.isNotBlank(mmsi)) {
                        jobParams.getMmsiList().add(mmsi.trim());
                    }
                }
            } else if ("startTime".equalsIgnoreCase(key)) {
                jobParams.setStartTime(value);
            } else if ("endTime".equalsIgnoreCase(key)) {
                jobParams.setEndTime(value);
            } else if ("batchNo".equalsIgnoreCase(key)) {
                jobParams.setBatchNo(value);
            } else if ("mode".equalsIgnoreCase(key)) {
                jobParams.setMode(value);
            }
        }
    }

    private void applyDefaultTimeRange(JobParams jobParams) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(Math.max(1, defaultHours));
        if (StringUtils.isBlank(jobParams.getStartTime())) {
            jobParams.setStartTime(start.format(TIME_FORMATTER));
        }
        if (StringUtils.isBlank(jobParams.getEndTime())) {
            jobParams.setEndTime(end.format(TIME_FORMATTER));
        }
        if (StringUtils.isBlank(jobParams.getBatchNo())) {
            jobParams.setBatchNo(jobParams.getStartTime().substring(0, 10));
        }
    }

    public static class AnalysisResult {
        private final int processed;
        private final int failed;
        private final int events;

        public AnalysisResult(int processed, int failed, int events) {
            this.processed = processed;
            this.failed = failed;
            this.events = events;
        }

        public boolean isSuccess() {
            return failed == 0;
        }

        public String getSummary() {
            return "processed=" + processed + ", failed=" + failed + ", events=" + events;
        }
    }

    private static class LoiteringWindowResult {
        private final int points;
        private final int events;

        LoiteringWindowResult(int points, int events) {
            this.points = points;
            this.events = events;
        }

        int getPoints() {
            return points;
        }

        int getEvents() {
            return events;
        }
    }

    public static class AlongsideWindowResult {
        private final int points;
        private final int lowSpeedWindows;
        private final int candidateTargets;
        private final int candidatePairs;
        private final int replayQueries;
        private final int replayFailures;
        private final int truncatedQueries;
        private final int events;
        private final int confirmedEvents;
        private final int highRiskEvents;
        private final int failed;
        private final List<ShipBehaviorTrace> traces;
        private final int rawLowSpeedWindows;
        private final int worldSeaRejectedWindows;
        private final int savedReplayQueries;
        private final int baselinePlannedReplayQueries;
        private final int filteredPlannedReplayQueries;
        private final int attemptedReplayQueries;
        private final int successfulReplayQueries;
        private final int failedReplayQueries;
        private final int truncatedReplayQueryCount;
        private int alongsideLowSpeedWindows;
        private int policyRejectedCandidates;
        private int pairReplayRawPoints;
        private String replayFailureReason;
        private boolean replayFailureRetryable;
        private final CandidateFunnelStats candidateFunnel = new CandidateFunnelStats();

        public AlongsideWindowResult(int points, int lowSpeedWindows, int candidateTargets,
                                     int candidatePairs, int replayQueries, int replayFailures,
                                     int events, int confirmedEvents, int highRiskEvents, int failed) {
            this(points, lowSpeedWindows, candidateTargets, candidatePairs, replayQueries,
                    replayFailures, 0, events, confirmedEvents, highRiskEvents, failed,
                    Collections.<ShipBehaviorTrace>emptyList(), lowSpeedWindows, 0, 0);
        }

        public AlongsideWindowResult(int points, int lowSpeedWindows, int candidateTargets,
                                     int candidatePairs, int replayQueries, int replayFailures,
                                     int truncatedQueries, int events, int confirmedEvents,
                                     int highRiskEvents, int failed,
                                     List<ShipBehaviorTrace> traces) {
            this.points = points;
            this.lowSpeedWindows = lowSpeedWindows;
            this.candidateTargets = candidateTargets;
            this.candidatePairs = candidatePairs;
            this.replayQueries = replayQueries;
            this.replayFailures = replayFailures;
            this.truncatedQueries = truncatedQueries;
            this.events = events;
            this.confirmedEvents = confirmedEvents;
            this.highRiskEvents = highRiskEvents;
            this.failed = failed;
            this.traces = traces == null ? Collections.<ShipBehaviorTrace>emptyList()
                    : Collections.unmodifiableList(new ArrayList<ShipBehaviorTrace>(traces));
            this.rawLowSpeedWindows = lowSpeedWindows;
            this.worldSeaRejectedWindows = 0;
            this.savedReplayQueries = 0;
            this.baselinePlannedReplayQueries = replayQueries;
            this.filteredPlannedReplayQueries = replayQueries;
            this.attemptedReplayQueries = replayQueries;
            this.successfulReplayQueries = Math.max(0, replayQueries - replayFailures);
            this.failedReplayQueries = replayFailures;
            this.truncatedReplayQueryCount = truncatedQueries;
        }

        public AlongsideWindowResult(int points, int lowSpeedWindows, int candidateTargets,
                                     int candidatePairs, int replayQueries, int replayFailures,
                                     int truncatedQueries, int events, int confirmedEvents,
                                     int highRiskEvents, int failed, List<ShipBehaviorTrace> traces,
                                     int rawLowSpeedWindows, int worldSeaRejectedWindows,
                                     int savedReplayQueries) {
            this(points, lowSpeedWindows, candidateTargets, candidatePairs, replayQueries,
                    replayFailures, truncatedQueries, events, confirmedEvents, highRiskEvents,
                    failed, traces, rawLowSpeedWindows, worldSeaRejectedWindows,
                    replayQueries + Math.max(0, savedReplayQueries), replayQueries,
                    savedReplayQueries, replayQueries, Math.max(0, replayQueries - replayFailures),
                    replayFailures, truncatedQueries);
        }

        public AlongsideWindowResult(int points, int lowSpeedWindows, int candidateTargets,
                                     int candidatePairs, int replayQueries, int replayFailures,
                                     int truncatedQueries, int events, int confirmedEvents,
                                     int highRiskEvents, int failed, List<ShipBehaviorTrace> traces,
                                     int rawLowSpeedWindows, int worldSeaRejectedWindows,
                                     int baselinePlannedReplayQueries, int filteredPlannedReplayQueries,
                                     int savedReplayQueries, int attemptedReplayQueries,
                                     int successfulReplayQueries, int failedReplayQueries,
                                     int truncatedReplayQueryCount) {
            if (baselinePlannedReplayQueries < 0 || filteredPlannedReplayQueries < 0
                    || savedReplayQueries < 0 || attemptedReplayQueries < 0
                    || successfulReplayQueries < 0 || failedReplayQueries < 0
                    || truncatedReplayQueryCount < 0
                    || savedReplayQueries != Math.max(0,
                    baselinePlannedReplayQueries - filteredPlannedReplayQueries)
                    || successfulReplayQueries + failedReplayQueries
                    + truncatedReplayQueryCount > attemptedReplayQueries) {
                throw new IllegalArgumentException("invalid alongside replay query metrics: attempted="
                        + attemptedReplayQueries + ", successful=" + successfulReplayQueries
                        + ", failed=" + failedReplayQueries + ", truncated="
                        + truncatedReplayQueryCount + ", baselinePlanned="
                        + baselinePlannedReplayQueries + ", filteredPlanned="
                        + filteredPlannedReplayQueries + ", saved=" + savedReplayQueries);
            }
            this.points = points; this.lowSpeedWindows = lowSpeedWindows;
            this.candidateTargets = candidateTargets; this.candidatePairs = candidatePairs;
            this.replayQueries = replayQueries; this.replayFailures = replayFailures;
            this.truncatedQueries = truncatedQueries; this.events = events;
            this.confirmedEvents = confirmedEvents; this.highRiskEvents = highRiskEvents;
            this.failed = failed;
            this.traces = traces == null ? Collections.<ShipBehaviorTrace>emptyList()
                    : Collections.unmodifiableList(new ArrayList<ShipBehaviorTrace>(traces));
            this.rawLowSpeedWindows = rawLowSpeedWindows;
            this.worldSeaRejectedWindows = worldSeaRejectedWindows;
            this.savedReplayQueries = savedReplayQueries;
            this.baselinePlannedReplayQueries = baselinePlannedReplayQueries;
            this.filteredPlannedReplayQueries = filteredPlannedReplayQueries;
            this.attemptedReplayQueries = attemptedReplayQueries;
            this.successfulReplayQueries = successfulReplayQueries;
            this.failedReplayQueries = failedReplayQueries;
            this.truncatedReplayQueryCount = truncatedReplayQueryCount;
            this.alongsideLowSpeedWindows = lowSpeedWindows;
        }

        public int getPoints() {
            return points;
        }

        public int getLowSpeedWindows() {
            return lowSpeedWindows;
        }

        public int getCandidateTargets() {
            return candidateTargets;
        }

        public int getCandidatePairs() {
            return candidatePairs;
        }

        public int getReplayQueries() {
            // Backward-compatible alias: this is the attempted execution count, not planned or saved queries.
            return replayQueries;
        }

        public int getReplayFailures() {
            return replayFailures;
        }

        public int getTruncatedQueries() {
            return truncatedQueries;
        }

        public int getEvents() {
            return events;
        }

        public int getConfirmedEvents() {
            return confirmedEvents;
        }

        public int getHighRiskEvents() {
            return highRiskEvents;
        }

        public int getFailed() {
            return failed;
        }

        public List<ShipBehaviorTrace> getTraces() {
            return traces;
        }
        public int getRawLowSpeedWindows() { return rawLowSpeedWindows; }
        public int getWorldSeaRejectedWindows() { return worldSeaRejectedWindows; }
        public int getSavedReplayQueries() { return savedReplayQueries; }
        public int getBaselinePlannedReplayQueries() { return baselinePlannedReplayQueries; }
        public int getFilteredPlannedReplayQueries() { return filteredPlannedReplayQueries; }
        public int getAttemptedReplayQueries() { return attemptedReplayQueries; }
        public int getSuccessfulReplayQueries() { return successfulReplayQueries; }
        public int getFailedReplayQueries() { return failedReplayQueries; }
        public int getTruncatedReplayQueryCount() { return truncatedReplayQueryCount; }
        public int getAlongsideLowSpeedWindows() { return alongsideLowSpeedWindows; }
        public int getPolicyRejectedCandidates() { return policyRejectedCandidates; }
        public int getPairReplayRawPoints() { return pairReplayRawPoints; }
        public String getReplayFailureReason() { return replayFailureReason; }
        public boolean isReplayFailureRetryable() { return replayFailureRetryable; }
        public CandidateFunnelStats getCandidateFunnel() { return candidateFunnel; }
    }

    private static class LoiteringTaskResult {
        private final int processed;
        private final int failed;
        private final int events;

        private LoiteringTaskResult(int processed, int failed, int events) {
            this.processed = processed;
            this.failed = failed;
            this.events = events;
        }

        static LoiteringTaskResult done(int events) {
            return new LoiteringTaskResult(1, 0, events);
        }

        static LoiteringTaskResult failed(int events) {
            return new LoiteringTaskResult(0, 1, events);
        }
    }

    private static class AlongsideTaskResult {
        private final int processed;
        private final int failed;
        private final int events;

        private AlongsideTaskResult(int processed, int failed, int events) {
            this.processed = processed;
            this.failed = failed;
            this.events = events;
        }

        static AlongsideTaskResult done(int events) {
            return new AlongsideTaskResult(1, 0, events);
        }

        static AlongsideTaskResult failed(int events) {
            return new AlongsideTaskResult(0, 1, events);
        }
    }

    private static class LoiteringBatchResult {
        private final AtomicInteger processed = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();
        private final AtomicInteger events = new AtomicInteger();

        void add(LoiteringTaskResult result) {
            processed.addAndGet(result.processed);
            failed.addAndGet(result.failed);
            events.addAndGet(result.events);
        }

        int getProcessed() {
            return processed.get();
        }

        int getFailed() {
            return failed.get();
        }

        int getEvents() {
            return events.get();
        }
    }

    private static class AlongsideBatchResult {
        private final AtomicInteger processed = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();
        private final AtomicInteger events = new AtomicInteger();

        void add(AlongsideTaskResult result) {
            processed.addAndGet(result.processed);
            failed.addAndGet(result.failed);
            events.addAndGet(result.events);
        }

        int getProcessed() {
            return processed.get();
        }

        int getFailed() {
            return failed.get();
        }

        int getEvents() {
            return events.get();
        }
    }

    private static class TimeWindow {
        private final String startTime;
        private final String endTime;

        TimeWindow(String startTime, String endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        String getStartTime() {
            return startTime;
        }

        String getEndTime() {
            return endTime;
        }

        @Override
        public String toString() {
            return startTime + "~" + endTime;
        }
    }

    private static class LoiteringBatchTask {
        private final String batchNo;
        private final String mmsi;
        private final TimeWindow window;

        LoiteringBatchTask(String batchNo, String mmsi, TimeWindow window) {
            this.batchNo = batchNo;
            this.mmsi = mmsi;
            this.window = window;
        }

        String getBatchNo() {
            return batchNo;
        }

        String getMmsi() {
            return mmsi;
        }

        TimeWindow getWindow() {
            return window;
        }
    }

    private static class AlongsideBatchTask {
        private final String batchNo;
        private final String mmsi;
        private final TimeWindow window;

        AlongsideBatchTask(String batchNo, String mmsi, TimeWindow window) {
            this.batchNo = batchNo;
            this.mmsi = mmsi;
            this.window = window;
        }

        String getBatchNo() {
            return batchNo;
        }

        String getMmsi() {
            return mmsi;
        }

        TimeWindow getWindow() {
            return window;
        }
    }

    private static class BatchJobParams {
        private final List<String> mmsiList = new ArrayList<String>();
        private String startTime;
        private String endTime;
        private String batchNo;
        private String sliceMode;
        private boolean resume = true;
        private int parallelism;
        private int maxShips;
        private int sliceDays;

        List<String> getMmsiList() {
            return mmsiList;
        }

        String getStartTime() {
            return startTime;
        }

        void setStartTime(String startTime) {
            this.startTime = startTime;
        }

        String getEndTime() {
            return endTime;
        }

        void setEndTime(String endTime) {
            this.endTime = endTime;
        }

        String getBatchNo() {
            return batchNo;
        }

        void setBatchNo(String batchNo) {
            this.batchNo = batchNo;
        }

        String getSliceMode() {
            return sliceMode;
        }

        void setSliceMode(String sliceMode) {
            this.sliceMode = sliceMode;
        }

        boolean isResume() {
            return resume;
        }

        void setResume(boolean resume) {
            this.resume = resume;
        }

        int getParallelism() {
            return parallelism;
        }

        void setParallelism(int parallelism) {
            this.parallelism = parallelism;
        }

        int getMaxShips() {
            return maxShips;
        }

        void setMaxShips(int maxShips) {
            this.maxShips = maxShips;
        }

        int getSliceDays() {
            return sliceDays;
        }

        void setSliceDays(int sliceDays) {
            this.sliceDays = sliceDays;
        }
    }

    private static class JobParams {
        private final List<String> mmsiList = new ArrayList<String>();
        private String mmsi;
        private String targetMmsi;
        private String startTime;
        private String endTime;
        private String batchNo;
        private String mode;

        public List<String> getMmsiList() {
            return mmsiList;
        }

        public String getMmsi() {
            return mmsi;
        }

        public void setMmsi(String mmsi) {
            this.mmsi = mmsi;
        }

        public String getTargetMmsi() {
            return targetMmsi;
        }

        public void setTargetMmsi(String targetMmsi) {
            this.targetMmsi = targetMmsi;
        }

        public String getStartTime() {
            return startTime;
        }

        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }

        public String getEndTime() {
            return endTime;
        }

        public void setEndTime(String endTime) {
            this.endTime = endTime;
        }

        public String getBatchNo() {
            return batchNo;
        }

        public void setBatchNo(String batchNo) {
            this.batchNo = batchNo;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        @Override
        public String toString() {
            return "JobParams{"
                    + "mmsi='" + mmsi + '\''
                    + ", targetMmsi='" + targetMmsi + '\''
                    + ", startTime='" + startTime + '\''
                    + ", endTime='" + endTime + '\''
                    + ", batchNo='" + batchNo + '\''
                    + ", mode='" + mode + '\''
                    + '}';
        }
    }

    private static class AlongsideAnnualScore {
        private final String mmsi;
        private final String statStartTime;
        private final String statEndTime;
        private final int eventCount;
        private final int validEventCount;
        private final int highRiskEventCount;
        private final int severeEventCount;
        private final int uniqueTargetCount;
        private final long totalDurationMinutes;
        private final long maxDurationMinutes;
        private final int aisGapRelatedCount;
        private final double maxEventScore;
        private final double annualRiskIndex;
        private final int subScore10;
        private final double weightedScore;
        private final boolean manualReviewRequired;

        private AlongsideAnnualScore(String mmsi, String statStartTime, String statEndTime, int eventCount,
                                     int validEventCount, int highRiskEventCount, int severeEventCount,
                                     int uniqueTargetCount, long totalDurationMinutes, long maxDurationMinutes,
                                     int aisGapRelatedCount, double maxEventScore, double annualRiskIndex,
                                     int subScore10, double weightedScore, boolean manualReviewRequired) {
            this.mmsi = mmsi;
            this.statStartTime = statStartTime;
            this.statEndTime = statEndTime;
            this.eventCount = eventCount;
            this.validEventCount = validEventCount;
            this.highRiskEventCount = highRiskEventCount;
            this.severeEventCount = severeEventCount;
            this.uniqueTargetCount = uniqueTargetCount;
            this.totalDurationMinutes = totalDurationMinutes;
            this.maxDurationMinutes = maxDurationMinutes;
            this.aisGapRelatedCount = aisGapRelatedCount;
            this.maxEventScore = maxEventScore;
            this.annualRiskIndex = annualRiskIndex;
            this.subScore10 = subScore10;
            this.weightedScore = weightedScore;
            this.manualReviewRequired = manualReviewRequired;
        }

        public int getEventCount() {
            return eventCount;
        }

        public int getValidEventCount() {
            return validEventCount;
        }

        public int getHighRiskEventCount() {
            return highRiskEventCount;
        }

        public int getSevereEventCount() {
            return severeEventCount;
        }

        public double getAnnualRiskIndex() {
            return annualRiskIndex;
        }

        public int getSubScore10() {
            return subScore10;
        }

        public double getWeightedScore() {
            return weightedScore;
        }

        public boolean isManualReviewRequired() {
            return manualReviewRequired;
        }
    }
}
