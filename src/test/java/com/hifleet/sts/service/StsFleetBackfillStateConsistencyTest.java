package com.hifleet.sts.service;

import com.hifleet.sts.domain.StsBackfillBatchRow;
import com.hifleet.sts.domain.StsBackfillTaskRow;
import com.hifleet.sts.domain.StsFleetBackfillJobParams;
import com.hifleet.sts.domain.StsFleetShipIdentity;
import com.hifleet.sts.domain.StsRiskRegionRow;
import com.hifleet.sts.repository.StsBackfillBatchRepository;
import com.hifleet.sts.repository.StsBackfillTaskRepository;
import com.hifleet.sts.repository.StsRiskRegionRepository;
import com.hifleet.sts.repository.StsTargetFleetRepository;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anySet;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StsFleetBackfillStateConsistencyTest {

    @Test
    public void runnerMarksTaskThenRefreshesAndReturnsCompleteOneOfOne() {
        StsTargetFleetRepository targets = mock(StsTargetFleetRepository.class);
        StsRiskRegionRepository regions = mock(StsRiskRegionRepository.class);
        StsBackfillBatchRepository batches = mock(StsBackfillBatchRepository.class);
        StsBackfillTaskRepository tasks = mock(StsBackfillTaskRepository.class);
        StsFleetRegionPreselector preselector = mock(StsFleetRegionPreselector.class);

        StsFleetShipIdentity ship = new StsFleetShipIdentity();
        ship.setPhysicalShipKey("IMO:1234567");
        ship.setImo("1234567");
        ship.addMmsi("123456789");
        ship.setPrimaryMmsi("123456789");
        ship.setIdentitySource("IMO");

        StsRiskRegionRow region = new StsRiskRegionRow();
        region.setRegionCode("TEST_REGION");
        region.setRegionVersion("v1");
        region.setGeometryWkt("POLYGON((0 0,1 0,1 1,0 1,0 0))");
        region.setGeometryFingerprint("region-fingerprint");

        StsBackfillTaskRow task = new StsBackfillTaskRow();
        task.setId(1L);
        task.setBatchNo("batch-a");
        task.setSliceFrom(Timestamp.valueOf("2024-01-01 00:00:00"));
        task.setSliceTo(Timestamp.valueOf("2024-01-02 00:00:00"));

        when(targets.countPhysicalShips("fleet-a")).thenReturn(1);
        when(targets.selectPhysicalShips("fleet-a", 0, 1)).thenReturn(Arrays.asList(ship));
        when(regions.loadRegionSet("regions-a")).thenReturn(Arrays.asList(region));
        when(batches.findByBatchNo("batch-a")).thenReturn(null, batch("COMPLETE", 1, 1, 0));
        when(tasks.claimNextTask("batch-a")).thenReturn(task);

        StsFleetRegionPreselector.PreselectResult preselect =
                new StsFleetRegionPreselector.PreselectResult();
        when(preselector.preselect(anyList(), any(java.time.LocalDateTime.class),
                any(java.time.LocalDateTime.class), anySet(), eq("batch-a"), any(String.class)))
                .thenReturn(preselect);

        StsBackfillTaskHeartbeat heartbeat = new StsBackfillTaskHeartbeat(tasks);
        ReflectionTestUtils.setField(heartbeat, "heartbeatMinutes", 60L);

        StsFleetBackfillService service = new StsFleetBackfillService();
        ReflectionTestUtils.setField(service, "targetFleetRepository", targets);
        ReflectionTestUtils.setField(service, "riskRegionRepository", regions);
        ReflectionTestUtils.setField(service, "batchRepository", batches);
        ReflectionTestUtils.setField(service, "taskRepository", tasks);
        ReflectionTestUtils.setField(service, "regionPreselector", preselector);
        ReflectionTestUtils.setField(service, "taskHeartbeat", heartbeat);

        StsFleetBackfillService.BackfillRunResult result =
                service.runOneInvocation(params());

        assertEquals("COMPLETE", result.batchStatus);
        assertEquals(1, result.completedTasks);
        assertTrue(result.summary.contains("status=COMPLETE, 1/1 tasks"));
        org.mockito.InOrder order = inOrder(tasks, batches);
        order.verify(batches).findByBatchNo("batch-a");
        order.verify(tasks).markComplete(eq(1L), anyInt(), anyInt(), anyInt(), anyInt(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                anyInt(), anyInt());
        order.verify(batches).refreshProgressFromTasks("batch-a");
        order.verify(batches).findByBatchNo("batch-a");
    }

    @Test
    public void singleTaskSmokeReturnsCompleteInsteadOfStaleRunning() {
        Fixture fixture = fixture(batch("COMPLETE", 1, 1, 0));

        fixture.service.refreshResultFromTasks("batch-a", fixture.result);
        fixture.result.summary = fixture.service.buildSummary(fixture.result);

        assertEquals("COMPLETE", fixture.result.batchStatus);
        assertEquals(1, fixture.result.completedTasks);
        assertEquals(0, fixture.result.failedTasks);
        assertTrue(fixture.result.summary.contains("status=COMPLETE, 1/1 tasks"));
        verify(fixture.repository).refreshProgressFromTasks("batch-a");
    }

    @Test
    public void threeTasksAdvanceOneTwoThenCompleteThreeOfThree() {
        Fixture fixture = fixture(
                batch("RUNNING", 3, 1, 0),
                batch("RUNNING", 3, 2, 0),
                batch("COMPLETE", 3, 3, 0));

        fixture.service.refreshResultFromTasks("batch-a", fixture.result);
        assertEquals(1, fixture.result.completedTasks);
        fixture.service.refreshResultFromTasks("batch-a", fixture.result);
        assertEquals(2, fixture.result.completedTasks);
        fixture.service.refreshResultFromTasks("batch-a", fixture.result);

        assertEquals("COMPLETE", fixture.result.batchStatus);
        assertEquals(3, fixture.result.completedTasks);
        verify(fixture.repository, times(3)).refreshProgressFromTasks("batch-a");
    }

    @Test
    public void retryableFailureRemainsRunningAndRetrySuccessRemovesFailedCount() {
        Fixture fixture = fixture(
                batch("RUNNING", 3, 1, 1),
                batch("COMPLETE", 3, 3, 0));

        fixture.service.refreshResultFromTasks("batch-a", fixture.result);
        assertEquals("RUNNING", fixture.result.batchStatus);
        assertEquals(1, fixture.result.failedTasks);

        fixture.service.refreshResultFromTasks("batch-a", fixture.result);
        assertEquals("COMPLETE", fixture.result.batchStatus);
        assertEquals(3, fixture.result.completedTasks);
        assertEquals(0, fixture.result.failedTasks);
    }

    @Test
    public void exhaustedFailureMakesParentPartialFailed() {
        Fixture fixture = fixture(batch("PARTIAL_FAILED", 3, 2, 1));

        fixture.service.refreshResultFromTasks("batch-a", fixture.result);

        assertEquals("PARTIAL_FAILED", fixture.result.batchStatus);
        assertEquals(2, fixture.result.completedTasks);
        assertEquals(1, fixture.result.failedTasks);
    }

    private Fixture fixture(StsBackfillBatchRow... rows) {
        Fixture fixture = new Fixture();
        fixture.repository = mock(StsBackfillBatchRepository.class);
        when(fixture.repository.findByBatchNo("batch-a")).thenReturn(rows[0], remaining(rows));
        fixture.service = new StsFleetBackfillService();
        ReflectionTestUtils.setField(fixture.service, "batchRepository", fixture.repository);
        fixture.result = new StsFleetBackfillService.BackfillRunResult();
        fixture.result.batchNo = "batch-a";
        fixture.result.currentTaskId = 1L;
        return fixture;
    }

    private StsBackfillBatchRow[] remaining(StsBackfillBatchRow[] rows) {
        if (rows.length <= 1) return new StsBackfillBatchRow[0];
        StsBackfillBatchRow[] remaining = new StsBackfillBatchRow[rows.length - 1];
        System.arraycopy(rows, 1, remaining, 0, remaining.length);
        return remaining;
    }

    private StsBackfillBatchRow batch(String status, int total, int complete, int failed) {
        StsBackfillBatchRow row = new StsBackfillBatchRow();
        row.setStatus(status);
        row.setTotalTasks(total);
        row.setCompletedTasks(complete);
        row.setFailedTasks(failed);
        return row;
    }

    private StsFleetBackfillJobParams params() {
        StsFleetBackfillJobParams params = new StsFleetBackfillJobParams();
        params.setTargetListBatchNo("fleet-a");
        params.setExpectedTargetCount(1);
        params.setShipOffset(0);
        params.setShipCount(1);
        params.setRegionSetCode("regions-a");
        params.setFrom("2024-01-01 00:00:00");
        params.setTo("2024-01-02 00:00:00");
        params.setSliceDays(1);
        params.setBatchNo("batch-a");
        return params;
    }

    private static class Fixture {
        StsFleetBackfillService service;
        StsBackfillBatchRepository repository;
        StsFleetBackfillService.BackfillRunResult result;
    }
}
