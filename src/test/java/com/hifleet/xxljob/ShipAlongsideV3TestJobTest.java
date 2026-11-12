package com.hifleet.xxljob;

import com.hifleet.beans.ShipForm;
import com.hifleet.job.services.AlongsideV3HistoryTestService;
import com.hifleet.job.services.ShipBehaviorAnalysisService;
import com.hifleet.repository.AIS41GeneralQueryRepository;
import com.hifleet.repository.ShipBehaviorAlongsideBatchStatusRepository;
import com.xxl.job.core.biz.model.ReturnT;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShipAlongsideV3TestJobTest {

    @Test
    public void handlerRunsPagedHistoryChainAndContinuesAfterOneShipFailure() {
        FakeShipRepository shipRepository = new FakeShipRepository("413000001", "413000002", "413000003");
        FakeStatusRepository statusRepository = new FakeStatusRepository();
        FakeAnalysisService analysisService = new FakeAnalysisService("413000002");
        AlongsideV3HistoryTestService historyService = historyService(
                shipRepository, statusRepository, analysisService);
        ShipAlongsideV3TestJob job = new ShipAlongsideV3TestJob();
        ReflectionTestUtils.setField(job, "historyTestService", historyService);

        ReturnT<String> result = job.shipAlongsideV3TestJobHandler(
                "{\"shipLimit\":3,\"batchSize\":2,\"days\":7,"
                        + "\"enableInsert\":true,\"batchNo\":\"handler-chain-test\"}");

        assertEquals(ReturnT.FAIL_CODE, result.getCode());
        assertEquals(2, shipRepository.pageCalls);
        assertEquals(3, analysisService.processedMmsis.size());
        assertEquals(2, statusRepository.doneMmsis.size());
        assertEquals(Collections.singletonList("413000002"), statusRepository.failedMmsis);
        assertTrue(analysisService.enableInsertValues.get(0));
        assertTrue(result.getMsg().contains("shipsSuccess=2"));
        assertTrue(result.getMsg().contains("shipsFailed=1"));
    }

    @Test
    public void handlerDryRunSkipsCompletedShipsAndReturnsSuccess() {
        FakeShipRepository shipRepository = new FakeShipRepository("413000001", "413000002");
        FakeStatusRepository statusRepository = new FakeStatusRepository();
        statusRepository.completed.add(ShipBehaviorAlongsideBatchStatusRepository.windowKey(
                "413000001", "2025-01-01 00:00:00", "2025-01-31 23:59:59"));
        FakeAnalysisService analysisService = new FakeAnalysisService(null);
        AlongsideV3HistoryTestService historyService = historyService(
                shipRepository, statusRepository, analysisService);
        ShipAlongsideV3TestJob job = new ShipAlongsideV3TestJob();
        ReflectionTestUtils.setField(job, "historyTestService", historyService);

        ReturnT<String> result = job.shipAlongsideV3TestJobHandler(
                "{\"shipLimit\":2,\"batchSize\":1,"
                        + "\"startTime\":\"2025-01-01 00:00:00\","
                        + "\"endTime\":\"2025-01-31 23:59:59\","
                        + "\"windowDays\":31,"
                        + "\"batchNo\":\"resume-test\"}");

        assertEquals(ReturnT.SUCCESS_CODE, result.getCode());
        assertEquals(Collections.singletonList("413000002"), analysisService.processedMmsis);
        assertEquals("2025-01-01 00:00:00", shipRepository.updatedAfter);
        assertFalse(analysisService.enableInsertValues.get(0));
        assertTrue(result.getMsg().contains("shipsSkipped=1"));
    }

    @Test
    public void handlerSplitsLongRangeIntoRetryableTimeWindows() {
        FakeShipRepository shipRepository = new FakeShipRepository("413000001");
        FakeStatusRepository statusRepository = new FakeStatusRepository();
        FakeAnalysisService analysisService = new FakeAnalysisService(null);
        AlongsideV3HistoryTestService historyService = historyService(
                shipRepository, statusRepository, analysisService);
        ShipAlongsideV3TestJob job = new ShipAlongsideV3TestJob();
        ReflectionTestUtils.setField(job, "historyTestService", historyService);

        ReturnT<String> result = job.shipAlongsideV3TestJobHandler(
                "{\"shipLimit\":1,\"batchSize\":1,\"windowDays\":1,"
                        + "\"startTime\":\"2025-01-01 00:00:00\","
                        + "\"endTime\":\"2025-01-04 00:00:00\","
                        + "\"batchNo\":\"window-split-test\"}");

        assertEquals(ReturnT.SUCCESS_CODE, result.getCode());
        assertEquals(3, analysisService.processedMmsis.size());
        assertEquals(3, statusRepository.doneMmsis.size());
        assertTrue(result.getMsg().contains("windowsTotal=3"));
        assertTrue(result.getMsg().contains("windowsSuccess=3"));
    }

    @Test
    public void handlerMarksReplayIncompleteWindowFailed() {
        FakeStatusRepository statusRepository = new FakeStatusRepository();
        FakeAnalysisService analysisService = new FakeAnalysisService(null, "413000001");
        AlongsideV3HistoryTestService historyService = historyService(
                new FakeShipRepository("413000001"), statusRepository, analysisService);
        ShipAlongsideV3TestJob job = new ShipAlongsideV3TestJob();
        ReflectionTestUtils.setField(job, "historyTestService", historyService);

        ReturnT<String> result = job.shipAlongsideV3TestJobHandler(
                "{\"shipLimit\":1,\"days\":7,\"batchNo\":\"incomplete-replay-test\"}");

        assertEquals(ReturnT.FAIL_CODE, result.getCode());
        assertEquals(Collections.singletonList("413000001"), statusRepository.failedMmsis);
        assertTrue(statusRepository.doneMmsis.isEmpty());
        assertTrue(result.getMsg().contains("windowsFailed=1"));
    }

    private AlongsideV3HistoryTestService historyService(
            AIS41GeneralQueryRepository shipRepository,
            ShipBehaviorAlongsideBatchStatusRepository statusRepository,
            ShipBehaviorAnalysisService analysisService) {
        AlongsideV3HistoryTestService service = new AlongsideV3HistoryTestService();
        ReflectionTestUtils.setField(service, "ais41GeneralQueryRepository", shipRepository);
        ReflectionTestUtils.setField(service, "statusRepository", statusRepository);
        ReflectionTestUtils.setField(service, "analysisService", analysisService);
        ReflectionTestUtils.setField(service, "defaultShipLimit", 1000);
        ReflectionTestUtils.setField(service, "defaultBatchSize", 100);
        ReflectionTestUtils.setField(service, "defaultDays", 30);
        ReflectionTestUtils.setField(service, "defaultWindowDays", 30);
        ReflectionTestUtils.setField(service, "maxShipLimit", 110000);
        ReflectionTestUtils.setField(service, "maxBatchSize", 1000);
        ReflectionTestUtils.setField(service, "maxWindowDays", 31);
        return service;
    }

    private static class FakeShipRepository extends AIS41GeneralQueryRepository {
        private final List<String> mmsis;
        private int pageCalls;
        private String updatedAfter;

        FakeShipRepository(String... mmsis) {
            this.mmsis = new ArrayList<String>();
            Collections.addAll(this.mmsis, mmsis);
        }

        @Override
        public List<ShipForm> selectRecentShipsPage(String lastMmsi, int limit, String updatedAfter) {
            pageCalls++;
            this.updatedAfter = updatedAfter;
            List<ShipForm> result = new ArrayList<ShipForm>();
            long cursor = Long.parseLong(lastMmsi);
            for (String mmsi : mmsis) {
                if (Long.parseLong(mmsi) <= cursor) {
                    continue;
                }
                ShipForm ship = new ShipForm();
                ship.setMmsi(mmsi);
                result.add(ship);
                if (result.size() == limit) {
                    break;
                }
            }
            return result;
        }
    }

    private static class FakeStatusRepository extends ShipBehaviorAlongsideBatchStatusRepository {
        private final Set<String> completed = new HashSet<String>();
        private final List<String> doneMmsis = new ArrayList<String>();
        private final List<String> failedMmsis = new ArrayList<String>();

        @Override
        public Set<String> findCompletedWindows(String batchNo) {
            return completed;
        }

        @Override
        public Set<String> findCompletedWindows(String batchNo, Set<String> mmsis) {
            return completed;
        }

        @Override
        public void markDone(String batchNo, String mmsi, String windowStartTime, String windowEndTime,
                             int pointCount, int lowSpeedWindowCount, int candidateTargetCount,
                             int replayQueryCount, int replayFailureCount, int eventCount) {
            doneMmsis.add(mmsi);
        }

        @Override
        public void markFailed(String batchNo, String mmsi, String windowStartTime, String windowEndTime,
                               String errorMessage) {
            failedMmsis.add(mmsi);
        }
    }

    private static class FakeAnalysisService extends ShipBehaviorAnalysisService {
        private final String failingMmsi;
        private final String incompleteMmsi;
        private final List<String> processedMmsis = new ArrayList<String>();
        private final List<Boolean> enableInsertValues = new ArrayList<Boolean>();

        FakeAnalysisService(String failingMmsi) {
            this(failingMmsi, null);
        }

        FakeAnalysisService(String failingMmsi, String incompleteMmsi) {
            this.failingMmsi = failingMmsi;
            this.incompleteMmsi = incompleteMmsi;
        }

        @Override
        public AlongsideWindowResult analysisAlongsideHistoryWindow(
                String mmsi, String startTime, String endTime, String batchNo, boolean enableInsert) {
            processedMmsis.add(mmsi);
            enableInsertValues.add(enableInsert);
            if (mmsi.equals(failingMmsi)) {
                throw new IllegalStateException("simulated trajectory failure");
            }
            if (mmsi.equals(incompleteMmsi)) {
                return new AlongsideWindowResult(10, 1, 2, 3, 4, 1, 1, 1, 0, 0);
            }
            return new AlongsideWindowResult(10, 1, 2, 3, 4, 0, 1, 1, 0, 0);
        }
    }
}
