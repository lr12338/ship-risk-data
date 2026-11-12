package com.hifleet.job.services;

import com.hifleet.beans.ShipForm;
import com.hifleet.repository.AIS41GeneralQueryRepository;
import com.hifleet.repository.ShipBehaviorAlongsideBatchStatusRepository;
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
import java.util.List;
import java.util.Set;

@Service
public class AlongsideV3HistoryTestService {

    private static final Logger log = LoggerFactory.getLogger(AlongsideV3HistoryTestService.class);
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${alongside.test.ship-limit:1000}")
    private int defaultShipLimit;

    @Value("${alongside.test.batch-size:100}")
    private int defaultBatchSize;

    @Value("${alongside.test.days:30}")
    private int defaultDays;

    @Value("${alongside.test.window-days:30}")
    private int defaultWindowDays;

    @Value("${alongside.test.max-ship-limit:110000}")
    private int maxShipLimit;

    @Value("${alongside.test.max-batch-size:1000}")
    private int maxBatchSize;

    @Value("${alongside.test.max-window-days:31}")
    private int maxWindowDays;

    @Autowired
    private AIS41GeneralQueryRepository ais41GeneralQueryRepository;

    @Autowired
    private ShipBehaviorAlongsideBatchStatusRepository statusRepository;

    @Autowired
    private ShipBehaviorAnalysisService analysisService;

    public HistoryTestResult run(String rawParams) {
        AlongsideV3HistoryTestParams params = AlongsideV3HistoryTestParams.parse(
                rawParams, defaultShipLimit, defaultBatchSize, defaultDays,
                defaultWindowDays, maxShipLimit, maxBatchSize, maxWindowDays, LocalDateTime.now());
        List<TimeWindow> windows = splitWindows(params);
        HistoryTestResult total = new HistoryTestResult(params.getBatchNo(), params.getStatusBatchNo());
        long startMillis = System.currentTimeMillis();
        String lastMmsi = "0";
        int pageNo = 0;

        logJob("ALONGSIDE_V3_HISTORY_TEST start batchNo=" + params.getBatchNo()
                + " statusBatchNo=" + params.getStatusBatchNo()
                + " shipLimit=" + params.getShipLimit()
                + " batchSize=" + params.getBatchSize()
                + " windowDays=" + params.getWindowDays()
                + " windows=" + windows.size()
                + " startTime=" + params.getStartTime()
                + " endTime=" + params.getEndTime()
                + " enableInsert=" + params.isEnableInsert()
                + " resume=" + params.isResume());

        while (total.shipsTotal < params.getShipLimit()) {
            int pageSize = Math.min(params.getBatchSize(), params.getShipLimit() - total.shipsTotal);
            List<ShipForm> ships = ais41GeneralQueryRepository.selectRecentShipsPage(
                    lastMmsi, pageSize, params.getStartTime());
            if (ships == null || ships.isEmpty()) {
                break;
            }
            Set<String> completed = params.isResume()
                    ? statusRepository.findCompletedWindows(
                            params.getStatusBatchNo(), collectMmsis(ships))
                    : Collections.<String>emptySet();
            pageNo++;
            int pageSuccess = 0;
            int pageFailed = 0;
            for (ShipForm ship : ships) {
                String mmsi = ship == null ? null : StringUtils.trimToNull(ship.getMmsi());
                if (mmsi == null) {
                    throw new IllegalStateException("recent ship page contains blank mmsi");
                }
                ensureCursorMovesForward(lastMmsi, mmsi);
                lastMmsi = mmsi;
                total.shipsTotal++;
                ShipRunResult shipResult = runShip(params, completed, windows, mmsi, total);
                if (shipResult.failed) {
                    total.shipsFailed++;
                    pageFailed++;
                } else if (shipResult.executed) {
                    total.shipsSuccess++;
                    pageSuccess++;
                } else {
                    total.shipsSkipped++;
                }
                if (total.shipsTotal >= params.getShipLimit()) {
                    break;
                }
            }
            logJob("ALONGSIDE_V3_HISTORY_TEST page_summary batchNo=" + params.getBatchNo()
                    + " pageNo=" + pageNo
                    + " pageShips=" + ships.size()
                    + " pageSuccess=" + pageSuccess
                    + " pageFailed=" + pageFailed
                    + " shipsTotal=" + total.shipsTotal
                    + " lastMmsi=" + lastMmsi);
            if (ships.size() < pageSize) {
                break;
            }
        }

        total.durationMillis = System.currentTimeMillis() - startMillis;
        logJob(total.getSummary());
        return total;
    }

    private ShipRunResult runShip(AlongsideV3HistoryTestParams params, Set<String> completed,
                                  List<TimeWindow> windows, String mmsi, HistoryTestResult total) {
        ShipRunResult shipResult = new ShipRunResult();
        for (TimeWindow window : windows) {
            total.windowsTotal++;
            String windowKey = ShipBehaviorAlongsideBatchStatusRepository.windowKey(
                    mmsi, window.startTime, window.endTime);
            if (completed.contains(windowKey)) {
                total.windowsSkipped++;
                continue;
            }
            shipResult.executed = true;
            try {
                ShipBehaviorAnalysisService.AlongsideWindowResult result =
                        analysisService.analysisAlongsideHistoryWindow(
                                mmsi, window.startTime, window.endTime,
                                params.getBatchNo(), params.isEnableInsert());
                if (result.getFailed() > 0 || result.getReplayFailures() > 0) {
                    throw new IllegalStateException("incomplete alongside window, candidateFailures="
                            + result.getFailed() + ", replayFailures=" + result.getReplayFailures());
                }
                statusRepository.markDone(params.getStatusBatchNo(), mmsi,
                        window.startTime, window.endTime,
                        result.getPoints(), result.getLowSpeedWindows(),
                        result.getCandidateTargets(), result.getReplayQueries(),
                        result.getReplayFailures(), result.getEvents());
                total.add(result);
                total.windowsSuccess++;
            } catch (Exception ex) {
                shipResult.failed = true;
                total.windowsFailed++;
                markFailed(params, mmsi, window, ex);
                log.error("alongside V3 history test ship window failed, batchNo={}, mmsi={}, window={}",
                        params.getBatchNo(), mmsi, window, ex);
                logJob("ALONGSIDE_V3_HISTORY_TEST ship_window_failed batchNo=" + params.getBatchNo()
                        + " mmsi=" + mmsi
                        + " startTime=" + window.startTime
                        + " endTime=" + window.endTime
                        + " error=" + throwableMessage(ex));
            }
        }
        return shipResult;
    }

    private Set<String> collectMmsis(List<ShipForm> ships) {
        Set<String> mmsis = new java.util.HashSet<String>();
        for (ShipForm ship : ships) {
            if (ship != null && StringUtils.isNotBlank(ship.getMmsi())) {
                mmsis.add(ship.getMmsi().trim());
            }
        }
        return mmsis;
    }

    private List<TimeWindow> splitWindows(AlongsideV3HistoryTestParams params) {
        LocalDateTime start = LocalDateTime.parse(params.getStartTime(), TIME_FORMATTER);
        LocalDateTime end = LocalDateTime.parse(params.getEndTime(), TIME_FORMATTER);
        List<TimeWindow> windows = new ArrayList<TimeWindow>();
        LocalDateTime cursor = start;
        while (cursor.isBefore(end)) {
            LocalDateTime next = cursor.plusDays(params.getWindowDays());
            if (next.isAfter(end)) {
                next = end;
            }
            windows.add(new TimeWindow(cursor.format(TIME_FORMATTER), next.format(TIME_FORMATTER)));
            cursor = next;
        }
        return windows;
    }

    private void markFailed(AlongsideV3HistoryTestParams params, String mmsi,
                            TimeWindow window, Exception cause) {
        try {
            statusRepository.markFailed(params.getStatusBatchNo(), mmsi,
                    window.startTime, window.endTime, throwableMessage(cause));
        } catch (Exception statusEx) {
            log.error("record alongside V3 history failure status failed, batchNo={}, mmsi={}",
                    params.getBatchNo(), mmsi, statusEx);
        }
    }

    private void ensureCursorMovesForward(String lastMmsi, String currentMmsi) {
        try {
            if (Long.parseLong(currentMmsi) <= Long.parseLong(lastMmsi)) {
                throw new IllegalStateException("recent ship page is not ordered by mmsi");
            }
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("invalid mmsi in recent ship page: " + currentMmsi, ex);
        }
    }

    private String throwableMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return throwable == null ? null : throwable.getClass().getSimpleName()
                + (StringUtils.isBlank(message) ? "" : ": " + message);
    }

    private void logJob(String message) {
        log.info(message);
        XxlJobLogger.log(message);
    }

    public static class HistoryTestResult {
        private final String batchNo;
        private final String statusBatchNo;
        private int shipsTotal;
        private int shipsSuccess;
        private int shipsFailed;
        private int shipsSkipped;
        private int windowsTotal;
        private int windowsSuccess;
        private int windowsFailed;
        private int windowsSkipped;
        private long trajectoryPoints;
        private long candidatePairs;
        private int confirmedEvents;
        private int highRiskEvents;
        private long durationMillis;

        HistoryTestResult(String batchNo, String statusBatchNo) {
            this.batchNo = batchNo;
            this.statusBatchNo = statusBatchNo;
        }

        private void add(ShipBehaviorAnalysisService.AlongsideWindowResult result) {
            trajectoryPoints += result.getPoints();
            candidatePairs += result.getCandidatePairs();
            confirmedEvents += result.getConfirmedEvents();
            highRiskEvents += result.getHighRiskEvents();
        }

        public boolean isSuccess() {
            return shipsFailed == 0;
        }

        public String getSummary() {
            return "Alongside V3 History Test Summary"
                    + " batchNo=" + batchNo
                    + " statusBatchNo=" + statusBatchNo
                    + " shipsTotal=" + shipsTotal
                    + " shipsSuccess=" + shipsSuccess
                    + " shipsFailed=" + shipsFailed
                    + " shipsSkipped=" + shipsSkipped
                    + " windowsTotal=" + windowsTotal
                    + " windowsSuccess=" + windowsSuccess
                    + " windowsFailed=" + windowsFailed
                    + " windowsSkipped=" + windowsSkipped
                    + " trajectoryPoints=" + trajectoryPoints
                    + " candidatePairs=" + candidatePairs
                    + " confirmedEvents=" + confirmedEvents
                    + " highRiskEvents=" + highRiskEvents
                    + " durationSeconds=" + durationMillis / 1000.0;
        }
    }

    private static class ShipRunResult {
        private boolean executed;
        private boolean failed;
    }

    private static class TimeWindow {
        private final String startTime;
        private final String endTime;

        private TimeWindow(String startTime, String endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        @Override
        public String toString() {
            return startTime + "~" + endTime;
        }
    }
}
