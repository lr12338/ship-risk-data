package com.hifleet.xxljob;

import com.hifleet.job.services.ZonePassageAnalysisService;
import com.xxl.job.core.biz.model.ReturnT;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ZonePassageJobTest {

    @Test
    public void dayHandlerDelegatesToDailyCatchup() {
        ZonePassageJob job = new ZonePassageJob();
        ZonePassageAnalysisService service = mock(ZonePassageAnalysisService.class);
        ReflectionTestUtils.setField(job, "zonePassageAnalysisService", service);
        ZonePassageAnalysisService.BackfillResult result =
                ZonePassageAnalysisService.BackfillResult.allComplete("done");
        when(service.analysisZonePassageDailyCatchup("date=2026-08-10")).thenReturn(result);

        ReturnT<String> response = job.zonePassageDayJobHandler("date=2026-08-10");

        assertEquals(ReturnT.SUCCESS_CODE, response.getCode());
        assertEquals(result.getSummary(), response.getMsg());
        verify(service).analysisZonePassageDailyCatchup("date=2026-08-10");
    }
}
