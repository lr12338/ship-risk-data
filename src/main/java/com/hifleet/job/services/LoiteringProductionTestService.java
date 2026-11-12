package com.hifleet.job.services;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.domain.TargetShip;
import com.hifleet.repository.ShipBehaviorLoiteringBatchStatusRepository;
import com.hifleet.repository.TargetShipRepository;
import com.hifleet.repository.ShipBehaviorRunBatchRepository;
import com.hifleet.repository.WorldSeaMaskAuditRepository;
import com.hifleet.service.BehaviorEventTransactionStore;
import com.hifleet.trajectory.TrajectoryLoaderService;
import com.hifleet.trajectory.TrajectoryPoint;
import com.hifleet.worldsea.WorldSeaMaskProperties;
import com.hifleet.worldsea.WorldSeaMaskService;
import com.hifleet.worldsea.WorldSeaMaskSnapshot;
import com.hifleet.worldsea.WorldSeaEventGate;
import com.hifleet.worldsea.WorldSeaTrackSegmenter;
import com.hifleet.loitering.LoiteringPortEventGate;
import com.hifleet.loitering.LoiteringPortSceneProperties;
import com.hifleet.loitering.LoiteringPortSceneService;
import com.hifleet.loitering.LoiteringPortSceneSnapshot;
import com.xxl.job.core.log.XxlJobLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class LoiteringProductionTestService {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private TargetShipRepository targetShipRepository;

    @Autowired
    private ShipBehaviorLoiteringBatchStatusRepository statusRepository;

    @Autowired
    private TrajectoryLoaderService trajectoryLoaderService;

    @Autowired
    private ShipBehaviorAnalysisService analysisService;

    @Autowired
    private ShipLoiteringV3BatchService v3Service;

    @Autowired
    private BehaviorEventTransactionStore eventStore;

    @Autowired
    private ShipBehaviorRunBatchRepository runBatchRepository;

    @Autowired private WorldSeaMaskService worldSeaMaskService;
    @Autowired private WorldSeaMaskProperties worldSeaMaskProperties;
    @Autowired private WorldSeaTrackSegmenter worldSeaTrackSegmenter;
    @Autowired private WorldSeaEventGate worldSeaEventGate;
    @Autowired private LoiteringPortSceneService loiteringPortSceneService;
    @Autowired private LoiteringPortSceneProperties loiteringPortSceneProperties;
    @Autowired private LoiteringPortEventGate loiteringPortEventGate;
    @Autowired(required = false) private WorldSeaMaskAuditRepository worldSeaMaskAuditRepository;

    @Value("${ship.behavior.trajectory.simplify-zoom:15}")
    private int simplifyZoom;

    @Value("${ship.behavior.loitering.production.max-points-per-ship:200000}")
    private int maxPointsPerShip;

    @Value("${ship.behavior.loitering.rule-version:LOITERING_V2_INSURANCE_SCENE_V1}")
    private String loiteringRuleVersion;

    public Result run(String rawParams) {
        final ProductionTestParams params = ProductionTestParams.parse(rawParams);
        params.validateLoitering();
        WorldSeaMaskSnapshot mask = worldSeaMaskService == null ? null : worldSeaMaskService.requireSnapshot();
        LoiteringPortSceneSnapshot portScene = loiteringPortSceneService.requireSnapshot();
        if (runBatchRepository != null) {
            String versions = selectedVersions(params).toString();
            runBatchRepository.claim(params.getBatchNo(), ShipBehaviorEvent.TYPE_LOITERING,
                    params.getListBatchNo(), params.getStartTime(), params.getEndTime(),
                    versions, params.isEnableInsert(),
                    params.configHash(ShipBehaviorEvent.TYPE_LOITERING, versions,
                            sceneConfigIdentity(mask, portScene)));
        }
        targetShipRepository.validate(params.getListBatchNo(), params.getExpectedShipCount());
        if (params.getEngines().contains("V3")) {
            v3Service.assertProductionReady();
        }
        final Set<String> completed = params.isResume()
                ? statusRepository.findCompletedWindows(params.getBatchNo())
                : java.util.Collections.<String>emptySet();
        Result total = new Result(params.getBatchNo());
        final List<Window> windows = windows(params);
        XxlJobLogger.log("Loitering Production Start batchNo=" + params.getBatchNo()
                + " listBatchNo=" + params.getListBatchNo()
                + " shipLimit=" + params.getShipLimit()
                + " engines=" + params.getEngines()
                + " parallelism=" + params.getParallelism()
                + " storeDetails=" + params.isStoreDetails());
        int afterSeq = 0;
        while (total.shipsSeen < params.getShipLimit()) {
            int limit = Math.min(params.getPageSize(), params.getShipLimit() - total.shipsSeen);
            List<TargetShip> page = targetShipRepository.selectPage(
                    params.getListBatchNo(), afterSeq, limit);
            if (page.isEmpty()) {
                break;
            }
            afterSeq = page.get(page.size() - 1).getSeqNo();
            total.add(runPage(params, windows, completed, page));
            if (page.size() < limit) {
                break;
            }
        }
        if (total.shipsSeen != params.getShipLimit()) {
            throw new IllegalStateException("target page ended early expected="
                    + params.getShipLimit() + " actual=" + total.shipsSeen);
        }
        XxlJobLogger.log(total.summary());
        return total;
    }

    private Result runPage(final ProductionTestParams params, final List<Window> windows,
                           final Set<String> completed,
                           List<TargetShip> ships) {
        ExecutorService executor = Executors.newFixedThreadPool(params.getParallelism());
        List<Future<Result>> futures = new ArrayList<Future<Result>>();
        try {
            for (final TargetShip ship : ships) {
                futures.add(executor.submit(new Callable<Result>() {
                    @Override
                    public Result call() {
                        return runShip(params, windows, completed, ship);
                    }
                }));
            }
            Result result = new Result(params.getBatchNo());
            for (Future<Result> future : futures) {
                try {
                    result.add(future.get());
                } catch (Exception ex) {
                    result.shipsSeen++;
                    result.shipsFailed++;
                }
                if (result.shipsSeen % params.getLogIntervalShips() == 0
                        || result.shipsSeen == ships.size()) {
                    XxlJobLogger.log(result.summary());
                }
            }
            return result;
        } finally {
            executor.shutdown();
        }
    }

    private Result runShip(ProductionTestParams params, List<Window> windows,
                           Set<String> completed, TargetShip ship) {
        Result result = new Result(params.getBatchNo());
        result.shipsSeen = 1;
        boolean executed = false;
        boolean failed = false;
        for (Window window : windows) {
            String key = ShipBehaviorLoiteringBatchStatusRepository.windowKey(
                    ship.getMmsi(), window.start, window.end);
            if (completed.contains(key)) {
                result.windowsSkipped++;
                continue;
            }
            executed = true;
            RuntimeException last = null;
            boolean done = false;
            for (int attempt = 0; attempt <= params.getRetryCount(); attempt++) {
                try {
                    List<TrajectoryPoint> points = trajectoryLoaderService.loadSafely(
                            ship.getMmsi(), window.contextStart, window.contextEnd,
                            simplifyZoom, params.getTrajectorySliceHours(), maxPointsPerShip);
                    List<ShipBehaviorTrace> traces = analyze(params, ship, points);
                    traces = startsInWindow(traces, window);
                    if (worldSeaMaskAuditRepository != null && worldSeaEventGate != null) {
                        worldSeaMaskAuditRepository.record(params.getBatchNo(), ship.getMmsi(),
                                ShipBehaviorEvent.TYPE_LOITERING, window.start, window.end,
                                worldSeaEventGate.getLastStats(), 0, 0);
                    }
                    LoiteringPortEventGate.GateStats sceneStats = loiteringPortEventGate.getLastStats();
                    XxlJobLogger.log("LOITERING_PORT_SCENE audit rawCandidateCount=" + sceneStats.getRawCandidateCount()
                            + " scenePassCount=" + sceneStats.getScenePassCount()
                            + " sceneRejectedCount=" + sceneStats.getSceneRejectedCount()
                            + " belowMinSpeedSplitCount=" + analysisService.loiteringPortSplitStats().getBelowMinSpeedSplitCount()
                            + " portOperationalRejectedCount=" + analysisService.loiteringPortSplitStats().getPortOperationalRejectedCount()
                            + " anchorRejectedCount=" + analysisService.loiteringPortSplitStats().getAnchorRejectedCount()
                            + " tooFarFromPortRejectedCount=" + analysisService.loiteringPortSplitStats().getTooFarFromPortRejectedCount());
                    if (params.isEnableInsert()) {
                        final int pointCount = points.size();
                        final int eventCount = traces.size();
                        eventStore.replace(ShipBehaviorEvent.TYPE_LOITERING,
                                params.getBatchNo(), ship.getMmsi(), window.start, window.end,
                                selectedVersions(params), traces, params.isStoreDetails(), () ->
                                statusRepository.markDone(params.getBatchNo(), ship.getMmsi(),
                                        window.start, window.end, pointCount, eventCount));
                    } else {
                        statusRepository.markDone(params.getBatchNo(), ship.getMmsi(),
                                window.start, window.end, points.size(), traces.size());
                    }
                    result.windowsDone++;
                    result.points += points.size();
                    result.events += traces.size();
                    done = true;
                    break;
                } catch (RuntimeException ex) {
                    last = ex;
                }
            }
            if (!done) {
                failed = true;
                result.windowsFailed++;
                statusRepository.markFailed(params.getBatchNo(), ship.getMmsi(),
                        window.start, window.end,
                        last == null ? "unknown failure" : last.getMessage());
                XxlJobLogger.log("Loitering Production FAILED batchNo="
                        + params.getBatchNo() + " mmsi=" + ship.getMmsi()
                        + " startTime=" + window.start + " endTime=" + window.end
                        + " error=" + (last == null ? "unknown failure" : last.getMessage()));
            }
        }
        if (failed) result.shipsFailed = 1;
        else if (executed) result.shipsDone = 1;
        else result.shipsSkipped = 1;
        return result;
    }

    private List<ShipBehaviorTrace> analyze(ProductionTestParams params, TargetShip ship,
                                             List<TrajectoryPoint> points) {
        List<ShipBehaviorTrace> traces = new ArrayList<ShipBehaviorTrace>();
        if (params.getEngines().contains("V2")) {
            // analysisLoiteringV2Production owns the single final V2 WORLD_SEA gate.
            traces.addAll(analysisService.analysisLoiteringV2Production(
                    ship.getMmsi(), points, params.getBatchNo()));
        }
        if (params.getEngines().contains("V3")) {
            List<ShipBehaviorTrace> v3Traces = new ArrayList<ShipBehaviorTrace>();
            List<List<TrajectoryPoint>> seaSegments = worldSeaTrackSegmenter == null
                    ? java.util.Collections.singletonList(points) : worldSeaTrackSegmenter.splitInside(points);
            for (List<TrajectoryPoint> segment : seaSegments) {
                v3Traces.addAll(v3Service.analyzeV3Production(
                        new ShipLoiteringV3BatchService.TargetShipAdapter(
                                ship.getMmsi(), ship.getImo(), ship.getShipName(), ship.getShipType()),
                        segment, params.getBatchNo(), simplifyZoom));
            }
            traces.addAll(worldSeaEventGate == null ? v3Traces
                    : worldSeaEventGate.filterAndAnnotate(v3Traces));
        }
        return traces;
    }

    private List<ShipBehaviorTrace> startsInWindow(List<ShipBehaviorTrace> traces, Window window) {
        List<ShipBehaviorTrace> result = new ArrayList<ShipBehaviorTrace>();
        LocalDateTime start = LocalDateTime.parse(window.start, TIME_FORMATTER);
        LocalDateTime end = LocalDateTime.parse(window.end, TIME_FORMATTER);
        for (ShipBehaviorTrace trace : traces) {
            String value = trace == null || trace.getEvent() == null
                    ? null : trace.getEvent().getStartTime();
            if (value == null) continue;
            LocalDateTime eventStart = LocalDateTime.parse(value, TIME_FORMATTER);
            if (!eventStart.isBefore(start) && eventStart.isBefore(end)) result.add(trace);
        }
        return result;
    }

    private List<Window> windows(ProductionTestParams params) {
        LocalDateTime cursor = LocalDateTime.parse(params.getStartTime(), TIME_FORMATTER);
        LocalDateTime end = LocalDateTime.parse(params.getEndTime(), TIME_FORMATTER);
        List<Window> result = new ArrayList<Window>();
        while (cursor.isBefore(end)) {
            LocalDateTime next = cursor.plusDays(params.getWindowDays());
            if (next.isAfter(end)) next = end;
            result.add(new Window(cursor, next));
            cursor = next;
        }
        return result;
    }

    private static class Window {
        private final String start;
        private final String end;
        private final String contextStart;
        private final String contextEnd;
        private Window(LocalDateTime start, LocalDateTime end) {
            this.start = start.format(TIME_FORMATTER);
            this.end = end.format(TIME_FORMATTER);
            this.contextStart = start.minusHours(6).format(TIME_FORMATTER);
            this.contextEnd = end.plusHours(6).format(TIME_FORMATTER);
        }
    }

    private List<String> selectedVersions(ProductionTestParams params) {
        List<String> versions = new ArrayList<String>();
        if (params.getEngines().contains("V2")) {
            versions.add(loiteringRuleVersion);
        }
        if (params.getEngines().contains("V3")) {
            versions.add("LOITERING_V3_PROD_TEST_2026H1");
        }
        return versions;
    }

    private String sceneConfigIdentity(WorldSeaMaskSnapshot mask, LoiteringPortSceneSnapshot portScene) {
        return "WORLD_SEA=" + (mask == null ? "" : worldSeaMaskProperties.identity(mask))
                + "|PORT_SCENE=" + portScene.getFingerprint()
                + "|MIN_SPEED=" + loiteringPortSceneProperties.getMinSpeed()
                + "|PORT_DISTANCE_NM=" + loiteringPortSceneProperties.getMaxDistanceNm()
                + "|SCENE_POLICY=PORT_OPERATIONAL_AND_ANCHORAGE_HARD_EXCLUDE"
                + "|OFFSHORE_ENABLED=" + loiteringPortSceneProperties.isOffshoreEnabled()
                + "|RULE=" + loiteringRuleVersion;
    }

    public static class Result {
        private final String batchNo;
        private int shipsSeen;
        private int shipsDone;
        private int shipsFailed;
        private int shipsSkipped;
        private int windowsDone;
        private int windowsFailed;
        private int windowsSkipped;
        private long points;
        private int events;

        private Result(String batchNo) {
            this.batchNo = batchNo;
        }

        private void add(Result other) {
            shipsSeen += other.shipsSeen;
            shipsDone += other.shipsDone;
            shipsFailed += other.shipsFailed;
            shipsSkipped += other.shipsSkipped;
            windowsDone += other.windowsDone;
            windowsFailed += other.windowsFailed;
            windowsSkipped += other.windowsSkipped;
            points += other.points;
            events += other.events;
        }

        public boolean isSuccess() { return shipsFailed == 0; }

        public String summary() {
            return "Loitering Production Summary batchNo=" + batchNo
                    + " shipsSeen=" + shipsSeen + " shipsDone=" + shipsDone
                    + " shipsFailed=" + shipsFailed + " shipsSkipped=" + shipsSkipped
                    + " windowsDone=" + windowsDone + " windowsFailed=" + windowsFailed
                    + " windowsSkipped=" + windowsSkipped
                    + " points=" + points + " events=" + events;
        }
    }
}
