package com.hifleet.sts.xxljob;

import com.hifleet.sts.domain.StsAlongsideHistoryJobParams;
import com.hifleet.sts.service.StsAlongsideHistoryService;
import com.xxl.job.core.biz.model.ReturnT;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StsJobsTest {

    @Test
    public void partialShipCoverageReturnsCode200WithExplicitPartialMessage() {
        StsAlongsideHistoryService service = mock(StsAlongsideHistoryService.class);
        StsAlongsideHistoryService.RunResult result = new StsAlongsideHistoryService.RunResult();
        result.success = true;
        result.partial = true;
        result.summary = "[STS][JOB_RETURN] outcome=PARTIAL completedShips=784 failedShips=2 partial=true";
        when(service.runOneInvocation(any(StsAlongsideHistoryJobParams.class))).thenReturn(result);
        StsJobs jobs = new StsJobs();
        ReflectionTestUtils.setField(jobs, "stsAlongsideHistoryService", service);

        ReturnT<String> returned = jobs.shipStsAlongsideHistoryJobHandler(params());

        assertEquals(ReturnT.SUCCESS_CODE, returned.getCode());
        assertTrue(returned.getMsg().contains("outcome=PARTIAL"));
        assertTrue(returned.getMsg().contains("failedShips=2"));
    }

    @Test
    public void systemicFailureReturnsCode500WithFatalStage() {
        StsAlongsideHistoryService service = mock(StsAlongsideHistoryService.class);
        when(service.runOneInvocation(any(StsAlongsideHistoryJobParams.class)))
                .thenThrow(new IllegalStateException("FATAL_STAGE=WORLD_SEA ROOT_CAUSE=snapshot unavailable"));
        StsJobs jobs = new StsJobs();
        ReflectionTestUtils.setField(jobs, "stsAlongsideHistoryService", service);

        ReturnT<String> returned = jobs.shipStsAlongsideHistoryJobHandler(params());

        assertEquals(ReturnT.FAIL_CODE, returned.getCode());
        assertTrue(returned.getMsg().contains("outcome=FATAL"));
        assertTrue(returned.getMsg().contains("FATAL_STAGE=WORLD_SEA"));
    }

    private String params() {
        return "{\"mode\":\"STS_ALONGSIDE_HISTORY_V1\","
                + "\"seedBatchNo\":\"seed\",\"regionSetCode\":\"regions\","
                + "\"from\":\"2025-09-02 00:00:00\",\"to\":\"2025-09-09 00:00:00\","
                + "\"batchNo\":\"batch\",\"shipCount\":786}";
    }
}
