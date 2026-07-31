package com.hifleet.tankeropensea;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.repository.ShipBehaviorAlongsideBatchStatusRepository;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TankerOpenSeaAlongsideService {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private TankerOpenSeaTargetShipRepository targetRepository;
    @Autowired
    private PortAnchorBerthExclusionService exclusionService;
    @Autowired
    private TankerOpenSeaReplayProvider replayProvider;
    @Autowired
    private TrajectoryLoaderService trajectoryLoaderService;
    @Autowired
    private ShipBehaviorAlongsideBatchStatusRepository statusRepository;
    @Autowired
    private BehaviorEventTransactionStore eventStore;
    @Autowired
    private DegradedCandidateRepository degradedRepository;

    private final ShipEligibilityPolicy eligibilityPolicy = new ShipEligibilityPolicy();
    private final TankerOpenSeaAlongsideRule rule =
            new TankerOpenSeaAlongsideRule(TankerOpenSeaAlongsideRule.Config.defaults());

    public TankerOpenSeaBatchResult run(String rawParams) {
        TankerOpenSeaJobParams params = TankerOpenSeaJobParams.parse(
                rawParams, TankerOpenSeaJobParams.Behavior.ALONGSIDE);
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
        TankerOpenSeaBatchResult total = new TankerOpenSeaBatchResult(
                "ALONGSIDE_TANKER", params.getBatchNo());
        total.setExclusionSnapshot(exclusionSnapshot);
        Set<String> completed = params.isResume()
                ? statusRepository.findCompletedWindows(params.getBatchNo())
                : Collections.<String>emptySet();
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
                    String key = ShipBehaviorAlongsideBatchStatusRepository.windowKey(
                            ship.getMetadata().getMmsi(), window.start, window.end);
                    if (completed.contains(key)) {
                        total.windowSkipped();
                        continue;
                    }
                    try {
                        WindowResult result = executeWithRetry(
                                params, ship, window, exclusionSnapshot);
                        statusRepository.markDone(params.getBatchNo(),
                                ship.getMetadata().getMmsi(), window.start, window.end,
                                result.pointCount, result.lowSpeedWindows,
                                result.candidateTargets, result.replayQueries,
                                result.replayFailures, result.storedEvents);
                        total.windowDone();
                        total.addCandidates(result.candidateTargets);
                        total.addDetected(result.detectedEvents);
                        total.addStored(result.storedEvents);
                        total.addDegradedStored(result.degradedStored);
                        total.addQuarantineHits(result.quarantineHits);
                        XxlJobLogger.log("LIQUID_CONTAINER ALONGSIDE window done mmsi="
                                + ship.getMetadata().getMmsi() + " start=" + window.start
                                + " end=" + window.end + " event saved="
                                + result.storedEvents);
                    } catch (RuntimeException ex) {
                        shipFailed = true;
                        total.windowFailed();
                        statusRepository.markFailed(params.getBatchNo(),
                                ship.getMetadata().getMmsi(), window.start, window.end,
                                ex.getMessage());
                        XxlJobLogger.log("LIQUID_CONTAINER ALONGSIDE window failed mmsi="
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
                        1.0, 3.0, 45L, 30L);
        List<ShipBehaviorTrace> accepted = new ArrayList<ShipBehaviorTrace>();
        List<ShipBehaviorTrace> formal = new ArrayList<ShipBehaviorTrace>();
        List<DegradedCandidate> degraded = new ArrayList<DegradedCandidate>();
        Set<String> candidateTargets = new LinkedHashSet<String>();
        int replayQueries = 0;
        int replayFailures = 0;
        int quarantineHits = 0;
        for (List<TrajectoryPoint> episode : episodes) {
            ExclusionDecision ownExclusion =
                    exclusionService.classify(episode, snapshot);
            if (ownExclusion.isExcluded()) {
                XxlJobLogger.log("LIQUID_CONTAINER exclusion rejected ownMmsi="
                        + ship.getMetadata().getMmsi() + " reason="
                        + ownExclusion.getReason());
                continue;
            }
            if (ownExclusion.isQuarantined()) quarantineHits++;
            TankerOpenSeaReplayProvider.SearchResult replay = replayProvider.search(
                    ship.getMetadata().getMmsi(), episode, params.getBatchNo());
            replayQueries += replay.getQueryCount();
            replayFailures += replay.getFailedQueries();
            if (replay.getCompleteness() == ReplayCompleteness.TRUNCATED_UNRESOLVED) {
                throw new IllegalStateException("replay truncated unresolved");
            }
            if (replay.getCompleteness() == ReplayCompleteness.FAILED) {
                throw new IllegalStateException("replay failed count="
                        + replay.getFailedQueries());
            }
            Set<String> targetMmsis = replay.getTracks().keySet();
            Map<String, ShipMetadata> metadata;
            try {
                metadata = targetRepository.findSourceByMmsis(targetMmsis);
            } catch (RuntimeException ex) {
                throw new IllegalStateException("target ship metadata lookup failed", ex);
            }
            for (Map.Entry<String, List<TrajectoryPoint>> entry
                    : replay.getTracks().entrySet()) {
                candidateTargets.add(entry.getKey());
                ShipMetadata target = metadata.get(entry.getKey());
                ShipEligibilityDecision targetEligibility = target == null
                        ? eligibilityPolicy.lookupFailed("metadata missing")
                        : eligibilityPolicy.evaluate(target);
                if (!targetEligibility.isEligible()) {
                    XxlJobLogger.log("LIQUID_CONTAINER ship type rejected targetMmsi="
                            + entry.getKey() + " reason="
                            + targetEligibility.getDecisionReason());
                    continue;
                }
                ExclusionDecision targetExclusion =
                        exclusionService.classify(entry.getValue(), snapshot);
                if (targetExclusion.isExcluded()) {
                    XxlJobLogger.log("LIQUID_CONTAINER exclusion rejected targetMmsi="
                            + entry.getKey() + " reason="
                            + targetExclusion.getReason());
                    continue;
                }
                if (targetExclusion.isQuarantined()) quarantineHits++;
                RuleResult decision = rule.evaluate(episode, entry.getValue(),
                        ship.getEligibility(), targetEligibility,
                        ship.getMetadata(), target, ownExclusion,
                        replay.getCompleteness());
                if (decision.isAccepted()) {
                    ShipBehaviorTrace trace = TankerOpenSeaEventFactory.alongside(
                            params.getBatchNo(), ship, target, targetEligibility, episode,
                            entry.getValue(), decision);
                    accepted.add(trace);
                    String degradationReason = DegradationPolicy.reason(
                            snapshot, ownExclusion, targetExclusion);
                    if (degradationReason != null) {
                        degraded.add(new DegradedCandidate(
                                trace, degradationReason));
                    } else {
                        formal.add(trace);
                    }
                }
            }
        }
        int storedEvents = params.isEnableInsert() ? formal.size() : 0;
        int degradedStored = params.isEnableInsert() ? degraded.size() : 0;
        if (params.isEnableInsert()) {
            eventStore.replace(ShipBehaviorEvent.TYPE_ALONGSIDE,
                    params.getBatchNo(), ship.getMetadata().getMmsi(),
                    window.start, window.end,
                    Collections.singletonList(TankerOpenSeaAlongsideRule.RULE_VERSION),
                    formal, params.isStoreDetails());
            degradedRepository.replaceWindow(ShipBehaviorEvent.TYPE_ALONGSIDE,
                    params.getBatchNo(), ship.getMetadata().getMmsi(),
                    window.start, window.end, degraded, snapshot);
        }
        return new WindowResult(points.size(), episodes.size(),
                candidateTargets.size(), replayQueries, replayFailures,
                accepted.size(), storedEvents, degradedStored, quarantineHits);
    }

    private List<Window> windows(TankerOpenSeaJobParams params) {
        List<Window> result = new ArrayList<Window>();
        LocalDateTime cursor = LocalDateTime.parse(params.getStartTime(), FORMATTER);
        LocalDateTime end = LocalDateTime.parse(params.getEndTime(), FORMATTER);
        while (cursor.isBefore(end)) {
            LocalDateTime next = cursor.plusDays(params.getWindowDays());
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
        private final int lowSpeedWindows;
        private final int candidateTargets;
        private final int replayQueries;
        private final int replayFailures;
        private final int detectedEvents;
        private final int storedEvents;
        private final int degradedStored;
        private final int quarantineHits;

        private WindowResult(int pointCount, int lowSpeedWindows,
                             int candidateTargets, int replayQueries,
                             int replayFailures, int detectedEvents,
                             int storedEvents, int degradedStored,
                             int quarantineHits) {
            this.pointCount = pointCount;
            this.lowSpeedWindows = lowSpeedWindows;
            this.candidateTargets = candidateTargets;
            this.replayQueries = replayQueries;
            this.replayFailures = replayFailures;
            this.detectedEvents = detectedEvents;
            this.storedEvents = storedEvents;
            this.degradedStored = degradedStored;
            this.quarantineHits = quarantineHits;
        }
    }
}
