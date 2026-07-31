package com.hifleet.tankeropensea;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.repository.ShipBehaviorLoiteringBatchStatusRepository;
import com.hifleet.service.BehaviorEventTransactionStore;
import com.hifleet.trajectory.TrajectoryLoaderService;
import com.hifleet.trajectory.TrajectoryPoint;
import com.xxl.job.core.log.XxlJobLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class TankerOpenSeaLoiteringService {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private TankerOpenSeaTargetShipRepository targetRepository;
    @Autowired
    private PortAnchorBerthExclusionService exclusionService;
    @Autowired
    private TrajectoryLoaderService trajectoryLoaderService;
    @Autowired
    private ShipBehaviorLoiteringBatchStatusRepository statusRepository;
    @Autowired
    private BehaviorEventTransactionStore eventStore;
    @Autowired
    private DegradedCandidateRepository degradedRepository;

    private final TankerOpenSeaLoiteringRule rule =
            new TankerOpenSeaLoiteringRule(
                    TankerOpenSeaLoiteringRule.Config.defaults());

    public TankerOpenSeaBatchResult run(String rawParams) {
        TankerOpenSeaJobParams params = TankerOpenSeaJobParams.parse(
                rawParams, TankerOpenSeaJobParams.Behavior.LOITERING);
        PortAnchorBerthExclusionService.Snapshot exclusionSnapshot =
                exclusionService.requireReady(
                        params.getExpectedExclusionFingerprint());
        if (params.isEnableInsert()) degradedRepository.requireReady();
        XxlJobLogger.log("LIQUID_CONTAINER exclusion fingerprint="
                + exclusionSnapshot.getFingerprint() + " sourceCounts="
                + exclusionSnapshot.getSourceCounts() + " health="
                + exclusionSnapshot.getHealth() + " sourceHealth="
                + exclusionSnapshot.getHealthReportJson());
        targetRepository.validate(params.getListBatchNo(), params.getShipLimit());
        Set<String> completed = params.isResume()
                ? statusRepository.findCompletedWindows(params.getBatchNo())
                : Collections.<String>emptySet();
        TankerOpenSeaBatchResult total = new TankerOpenSeaBatchResult(
                "LOITERING_TANKER", params.getBatchNo());
        total.setExclusionSnapshot(exclusionSnapshot);
        int afterSeq = 0;
        while (afterSeq < params.getShipLimit()) {
            int limit = Math.min(50, params.getShipLimit() - afterSeq);
            List<ListedTanker> page = targetRepository.selectPage(
                    params.getListBatchNo(), afterSeq, limit);
            if (page.isEmpty()) break;
            for (ListedTanker ship : page) {
                total.shipSeen();
                boolean shipFailed = false;
                for (Window window : windows(params)) {
                    String key = ShipBehaviorLoiteringBatchStatusRepository.windowKey(
                            ship.getMetadata().getMmsi(), window.start, window.end);
                    if (completed.contains(key)) {
                        total.windowSkipped();
                        continue;
                    }
                    long started = System.currentTimeMillis();
                    try {
                        WindowResult result = executeWithRetry(
                                params, ship, window, exclusionSnapshot);
                        statusRepository.markDone(params.getBatchNo(),
                                ship.getMetadata().getMmsi(), window.start, window.end,
                                result.pointCount, result.candidates, 0, 0,
                                result.detectedEvents, result.storedEvents,
                                result.pointCount == 0
                                        ? ShipBehaviorLoiteringBatchStatusRepository.RESULT_NO_DATA
                                        : ShipBehaviorLoiteringBatchStatusRepository.RESULT_SUCCESS,
                                System.currentTimeMillis() - started);
                        total.windowDone();
                        total.addCandidates(result.candidates);
                        total.addDetected(result.detectedEvents);
                        total.addStored(result.storedEvents);
                        total.addDegradedStored(result.degradedStored);
                        total.addQuarantineHits(result.quarantineHits);
                        XxlJobLogger.log("LIQUID_CONTAINER LOITERING window done mmsi="
                                + ship.getMetadata().getMmsi() + " start=" + window.start
                                + " end=" + window.end + " event saved="
                                + result.storedEvents);
                    } catch (RuntimeException ex) {
                        shipFailed = true;
                        total.windowFailed();
                        statusRepository.markFailed(params.getBatchNo(),
                                ship.getMetadata().getMmsi(), window.start, window.end,
                                ex.getMessage(), System.currentTimeMillis() - started);
                        XxlJobLogger.log("LIQUID_CONTAINER LOITERING window failed mmsi="
                                + ship.getMetadata().getMmsi() + " error=" + ex.getMessage());
                    }
                }
                if (shipFailed) total.shipFailed();
                afterSeq = ship.getSeqNo();
                if (afterSeq % params.getLogIntervalShips() == 0) {
                    XxlJobLogger.log(total.summary());
                }
            }
        }
        if (afterSeq < params.getShipLimit()) {
            throw new IllegalStateException("tanker list ended early expected="
                    + params.getShipLimit() + " actual=" + afterSeq);
        }
        XxlJobLogger.log(total.summary());
        return total;
    }

    private WindowResult executeWithRetry(TankerOpenSeaJobParams params,
                                          ListedTanker ship, Window window,
                                          PortAnchorBerthExclusionService.Snapshot snapshot) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= params.getRetryCount(); attempt++) {
            try {
                return executeWindow(params, ship, window, snapshot);
            } catch (RuntimeException ex) {
                last = ex;
            }
        }
        throw last == null ? new IllegalStateException("window failed") : last;
    }

    private WindowResult executeWindow(TankerOpenSeaJobParams params,
                                       ListedTanker ship, Window window,
                                       PortAnchorBerthExclusionService.Snapshot snapshot) {
        List<TrajectoryPoint> points = trajectoryLoaderService.loadSafely(
                ship.getMetadata().getMmsi(), window.start, window.end,
                15, 6, 100000);
        List<List<TrajectoryPoint>> episodes =
                TankerOpenSeaTrajectory.lowSpeedEpisodes(points,
                        1.0, 3.0, 120L, 30L);
        List<ShipBehaviorTrace> accepted = new ArrayList<ShipBehaviorTrace>();
        List<ShipBehaviorTrace> formal = new ArrayList<ShipBehaviorTrace>();
        List<DegradedCandidate> degraded = new ArrayList<DegradedCandidate>();
        int quarantineHits = 0;
        for (List<TrajectoryPoint> episode : episodes) {
            ExclusionDecision exclusion =
                    exclusionService.classify(episode, snapshot);
            if (exclusion.isExcluded()) {
                XxlJobLogger.log("LIQUID_CONTAINER exclusion rejected mmsi="
                        + ship.getMetadata().getMmsi() + " reason="
                        + exclusion.getReason());
                continue;
            }
            if (exclusion.isQuarantined()) quarantineHits++;
            RuleResult decision = rule.evaluate(
                    episode, ship.getEligibility(), exclusion);
            if (decision.isAccepted()) {
                ShipBehaviorTrace trace = TankerOpenSeaEventFactory.loitering(
                        params.getBatchNo(), ship, episode, decision);
                accepted.add(trace);
                String degradationReason =
                        DegradationPolicy.reason(snapshot, exclusion);
                if (degradationReason != null) {
                    degraded.add(new DegradedCandidate(
                            trace, degradationReason));
                } else {
                    formal.add(trace);
                }
            }
        }
        int storedEvents = params.isEnableInsert() ? formal.size() : 0;
        int degradedStored = params.isEnableInsert() ? degraded.size() : 0;
        if (params.isEnableInsert()) {
            eventStore.replace(ShipBehaviorEvent.TYPE_LOITERING,
                    params.getBatchNo(), ship.getMetadata().getMmsi(),
                    window.start, window.end,
                    Collections.singletonList(TankerOpenSeaLoiteringRule.RULE_VERSION),
                    formal, params.isStoreDetails());
            degradedRepository.replaceWindow(ShipBehaviorEvent.TYPE_LOITERING,
                    params.getBatchNo(), ship.getMetadata().getMmsi(),
                    window.start, window.end, degraded, snapshot);
        }
        return new WindowResult(points.size(), episodes.size(),
                accepted.size(), storedEvents, degradedStored, quarantineHits);
    }

    private List<Window> windows(TankerOpenSeaJobParams params) {
        List<Window> result = new ArrayList<Window>();
        LocalDateTime cursor = LocalDateTime.parse(params.getStartTime(), FORMATTER);
        LocalDateTime end = LocalDateTime.parse(params.getEndTime(), FORMATTER);
        while (cursor.isBefore(end)) {
            LocalDateTime next = cursor.plusHours(params.getWindowHours());
            if (next.isAfter(end)) next = end;
            result.add(new Window(cursor.format(FORMATTER), next.format(FORMATTER)));
            cursor = next;
        }
        return result;
    }

    private static class Window {
        private final String start;
        private final String end;

        private Window(String start, String end) {
            this.start = start;
            this.end = end;
        }
    }

    private static class WindowResult {
        private final int pointCount;
        private final int candidates;
        private final int detectedEvents;
        private final int storedEvents;
        private final int degradedStored;
        private final int quarantineHits;

        private WindowResult(int pointCount, int candidates,
                             int detectedEvents, int storedEvents,
                             int degradedStored, int quarantineHits) {
            this.pointCount = pointCount;
            this.candidates = candidates;
            this.detectedEvents = detectedEvents;
            this.storedEvents = storedEvents;
            this.degradedStored = degradedStored;
            this.quarantineHits = quarantineHits;
        }
    }
}
