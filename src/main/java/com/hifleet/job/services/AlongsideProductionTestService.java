package com.hifleet.job.services;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.domain.TargetShip;
import com.hifleet.repository.ShipBehaviorAlongsideBatchStatusRepository;
import com.hifleet.repository.TargetShipRepository;
import com.hifleet.service.BehaviorEventTransactionStore;
import com.hifleet.service.ShipAlongsideDetector;
import com.xxl.job.core.log.XxlJobLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class AlongsideProductionTestService {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private TargetShipRepository targetShipRepository;

    @Autowired
    private ShipBehaviorAlongsideBatchStatusRepository statusRepository;

    @Autowired
    private ShipBehaviorAnalysisService analysisService;

    @Autowired
    private BehaviorEventTransactionStore eventStore;

    @Value("${ship.behavior.alongside.rule-version:ALONGSIDE_V3_INSURANCE}")
    private String ruleVersion;

    public Result run(String rawParams) {
        final ProductionTestParams params = ProductionTestParams.parse(rawParams);
        params.validateAlongside();
        targetShipRepository.validate(params.getListBatchNo(), params.getExpectedShipCount());
        final List<Window> windows = windows(params);
        Result total = new Result(params.getBatchNo());
        XxlJobLogger.log("Alongside Production Start batchNo=" + params.getBatchNo()
                + " listBatchNo=" + params.getListBatchNo()
                + " shipLimit=" + params.getShipLimit()
                + " windows=" + windows.size()
                + " parallelism=" + params.getParallelism()
                + " enableInsert=" + params.isEnableInsert());
        int afterSeq = 0;
        while (total.shipsSeen < params.getShipLimit()) {
            int limit = Math.min(params.getPageSize(), params.getShipLimit() - total.shipsSeen);
            List<TargetShip> page = targetShipRepository.selectPage(
                    params.getListBatchNo(), afterSeq, limit);
            if (page.isEmpty()) {
                break;
            }
            afterSeq = page.get(page.size() - 1).getSeqNo();
            Set<String> completed = params.isResume()
                    ? statusRepository.findCompletedWindows(
                    params.getBatchNo(), mmsis(page))
                    : Collections.<String>emptySet();
            total.add(runPage(params, windows, page, completed));
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
                           List<TargetShip> page, final Set<String> completed) {
        ExecutorService executor = Executors.newFixedThreadPool(params.getParallelism());
        List<Future<Result>> futures = new ArrayList<Future<Result>>();
        try {
            for (final TargetShip ship : page) {
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
                        || result.shipsSeen == page.size()) {
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
        String statusBatchNo = params.getBatchNo();
        boolean failed = false;
        boolean executed = false;
        for (Window window : windows) {
            String key = ShipBehaviorAlongsideBatchStatusRepository.windowKey(
                    ship.getMmsi(), window.start, window.end);
            if (completed.contains(key)) {
                result.windowsSkipped++;
                continue;
            }
            executed = true;
            try {
                ShipBehaviorAnalysisService.AlongsideWindowResult computed =
                        executeWithRetry(params, ship.getMmsi(), window);
                List<ShipBehaviorTrace> strict = strict(computed.getTraces());
                if (params.isEnableInsert()) {
                    eventStore.replace(ShipBehaviorEvent.TYPE_ALONGSIDE,
                            params.getBatchNo(), ship.getMmsi(), window.start, window.end,
                            Collections.singletonList(ruleVersion), strict, false);
                }
                statusRepository.markDone(statusBatchNo, ship.getMmsi(), window.start, window.end,
                        computed.getPoints(), computed.getLowSpeedWindows(),
                        computed.getCandidateTargets(), computed.getReplayQueries(),
                        computed.getReplayFailures(), strict.size());
                result.windowsDone++;
                result.events += strict.size();
            } catch (Exception ex) {
                failed = true;
                result.windowsFailed++;
                statusRepository.markFailed(statusBatchNo, ship.getMmsi(),
                        window.start, window.end, ex.getMessage());
                XxlJobLogger.log("Alongside Production FAILED batchNo="
                        + params.getBatchNo() + " mmsi=" + ship.getMmsi()
                        + " startTime=" + window.start + " endTime=" + window.end
                        + " error=" + ex.getMessage());
            }
        }
        if (failed) {
            result.shipsFailed = 1;
        } else if (executed) {
            result.shipsDone = 1;
        } else {
            result.shipsSkipped = 1;
        }
        return result;
    }

    private ShipBehaviorAnalysisService.AlongsideWindowResult executeWithRetry(
            ProductionTestParams params, String mmsi, Window window) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= params.getRetryCount(); attempt++) {
            try {
                ShipBehaviorAnalysisService.AlongsideWindowResult result =
                        analysisService.analysisAlongsideProductionWindow(
                                mmsi, window.start, window.end, params.getBatchNo());
                if (result.getFailed() > 0 || result.getReplayFailures() > 0
                        || result.getTruncatedQueries() > 0) {
                    throw new IllegalStateException("incomplete alongside window failed="
                            + result.getFailed() + " replayFailures="
                            + result.getReplayFailures() + " truncated="
                            + result.getTruncatedQueries());
                }
                return result;
            } catch (RuntimeException ex) {
                last = ex;
            }
        }
        throw last == null ? new IllegalStateException("alongside window failed") : last;
    }

    private List<ShipBehaviorTrace> strict(List<ShipBehaviorTrace> traces) {
        List<ShipBehaviorTrace> result = new ArrayList<ShipBehaviorTrace>();
        for (ShipBehaviorTrace trace : traces) {
            ShipBehaviorEvent event = trace == null ? null : trace.getEvent();
            if (event != null
                    && !ShipAlongsideDetector.SUB_TYPE_WEAK.equals(event.getEventSubType())
                    && ShipAlongsideDetector.VALID_RISK_EVENT.equals(event.getExclusionFlag())) {
                result.add(trace);
            }
        }
        return result;
    }

    private List<Window> windows(ProductionTestParams params) {
        LocalDateTime start = LocalDateTime.parse(params.getStartTime(), TIME_FORMATTER);
        LocalDateTime end = LocalDateTime.parse(params.getEndTime(), TIME_FORMATTER);
        List<Window> values = new ArrayList<Window>();
        while (start.isBefore(end)) {
            LocalDateTime next = start.plusDays(params.getWindowDays());
            if (next.isAfter(end)) {
                next = end;
            }
            values.add(new Window(start.format(TIME_FORMATTER), next.format(TIME_FORMATTER)));
            start = next;
        }
        return values;
    }

    private Set<String> mmsis(List<TargetShip> page) {
        Set<String> values = new HashSet<String>();
        for (TargetShip ship : page) {
            values.add(ship.getMmsi());
        }
        return values;
    }

    private static class Window {
        private final String start;
        private final String end;

        private Window(String start, String end) {
            this.start = start;
            this.end = end;
        }
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
            events += other.events;
        }

        public boolean isSuccess() { return shipsFailed == 0 && windowsFailed == 0; }

        public String summary() {
            return "Alongside Production Summary batchNo=" + batchNo
                    + " shipsSeen=" + shipsSeen + " shipsDone=" + shipsDone
                    + " shipsFailed=" + shipsFailed + " shipsSkipped=" + shipsSkipped
                    + " windowsDone=" + windowsDone + " windowsFailed=" + windowsFailed
                    + " windowsSkipped=" + windowsSkipped + " events=" + events;
        }
    }
}
