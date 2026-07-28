package com.hifleet.job.services;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.domain.TargetShip;
import com.hifleet.repository.ShipBehaviorLoiteringBatchStatusRepository;
import com.hifleet.repository.TargetShipRepository;
import com.hifleet.service.BehaviorEventTransactionStore;
import com.hifleet.trajectory.TrajectoryLoaderService;
import com.hifleet.trajectory.TrajectoryPoint;
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

@Service
public class LoiteringProductionTestService {

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

    @Value("${ship.behavior.trajectory.simplify-zoom:15}")
    private int simplifyZoom;

    @Value("${ship.behavior.loitering.production.max-points-per-ship:200000}")
    private int maxPointsPerShip;

    public Result run(String rawParams) {
        final ProductionTestParams params = ProductionTestParams.parse(rawParams);
        params.validateLoitering();
        targetShipRepository.validate(params.getListBatchNo(), params.getExpectedShipCount());
        if (params.getEngines().contains("V3")) {
            v3Service.assertProductionReady();
        }
        final Set<String> completed = params.isResume()
                ? statusRepository.findCompletedWindows(params.getBatchNo())
                : java.util.Collections.<String>emptySet();
        Result total = new Result(params.getBatchNo());
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
            total.add(runPage(params, completed, page));
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

    private Result runPage(final ProductionTestParams params, final Set<String> completed,
                           List<TargetShip> ships) {
        ExecutorService executor = Executors.newFixedThreadPool(params.getParallelism());
        List<Future<Result>> futures = new ArrayList<Future<Result>>();
        try {
            for (final TargetShip ship : ships) {
                futures.add(executor.submit(new Callable<Result>() {
                    @Override
                    public Result call() {
                        return runShip(params, completed, ship);
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

    private Result runShip(ProductionTestParams params, Set<String> completed, TargetShip ship) {
        Result result = new Result(params.getBatchNo());
        result.shipsSeen = 1;
        String key = ShipBehaviorLoiteringBatchStatusRepository.windowKey(
                ship.getMmsi(), params.getStartTime(), params.getEndTime());
        if (completed.contains(key)) {
            result.shipsSkipped = 1;
            return result;
        }
        RuntimeException last = null;
        for (int attempt = 0; attempt <= params.getRetryCount(); attempt++) {
            try {
                List<TrajectoryPoint> points = trajectoryLoaderService.loadSafely(
                        ship.getMmsi(), params.getStartTime(), params.getEndTime(),
                        simplifyZoom, params.getTrajectorySliceHours(), maxPointsPerShip);
                List<ShipBehaviorTrace> traces = new ArrayList<ShipBehaviorTrace>();
                if (params.getEngines().contains("V2")) {
                    traces.addAll(analysisService.analysisLoiteringV2Production(
                            ship.getMmsi(), points, params.getBatchNo()));
                }
                if (params.getEngines().contains("V3")) {
                    traces.addAll(v3Service.analyzeV3Production(
                            new ShipLoiteringV3BatchService.TargetShipAdapter(
                                    ship.getMmsi(), ship.getImo(),
                                    ship.getShipName(), ship.getShipType()),
                            points, params.getBatchNo(), simplifyZoom));
                }
                eventStore.replace(ShipBehaviorEvent.TYPE_LOITERING,
                        params.getBatchNo(), ship.getMmsi(),
                        params.getStartTime(), params.getEndTime(),
                        selectedVersions(params), traces, params.isStoreDetails());
                statusRepository.markDone(params.getBatchNo(), ship.getMmsi(),
                        params.getStartTime(), params.getEndTime(),
                        points.size(), traces.size());
                result.shipsDone = 1;
                result.points = points.size();
                result.events = traces.size();
                return result;
            } catch (RuntimeException ex) {
                last = ex;
            }
        }
        result.shipsFailed = 1;
        statusRepository.markFailed(params.getBatchNo(), ship.getMmsi(),
                params.getStartTime(), params.getEndTime(),
                last == null ? "unknown failure" : last.getMessage());
        XxlJobLogger.log("Loitering Production FAILED batchNo="
                + params.getBatchNo() + " mmsi=" + ship.getMmsi()
                + " error=" + (last == null ? "unknown failure" : last.getMessage()));
        return result;
    }

    private List<String> selectedVersions(ProductionTestParams params) {
        List<String> versions = new ArrayList<String>();
        if (params.getEngines().contains("V2")) {
            versions.add("LOITERING_V2_INSURANCE");
        }
        if (params.getEngines().contains("V3")) {
            versions.add("LOITERING_V3_PROD_TEST_2026H1");
        }
        return versions;
    }

    public static class Result {
        private final String batchNo;
        private int shipsSeen;
        private int shipsDone;
        private int shipsFailed;
        private int shipsSkipped;
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
            points += other.points;
            events += other.events;
        }

        public boolean isSuccess() { return shipsFailed == 0; }

        public String summary() {
            return "Loitering Production Summary batchNo=" + batchNo
                    + " shipsSeen=" + shipsSeen + " shipsDone=" + shipsDone
                    + " shipsFailed=" + shipsFailed + " shipsSkipped=" + shipsSkipped
                    + " points=" + points + " events=" + events;
        }
    }
}
