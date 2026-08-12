package com.hifleet.job.services;

import com.hifleet.repository.ZonePassageStatusRepository;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ZonePassageDailyCatchupTest {

    @Test
    public void explicitCompletedDayUsesNaturalDayWindowAndSkipsExecution() {
        ZonePassageAnalysisService service = new ZonePassageAnalysisService();
        ZonePassageStatusRepository statusRepository = mock(ZonePassageStatusRepository.class);
        ReflectionTestUtils.setField(service, "zonePassageStatusRepository", statusRepository);
        String date = LocalDate.now().minusDays(1).toString();
        String start = date + " 00:00:00";
        String end = LocalDate.now().toString() + " 00:00:00";
        when(statusRepository.findCompletedMmsi(date, start, end))
                .thenReturn(Collections.singleton("413000001"));

        ZonePassageAnalysisService.BackfillResult result = service.analysisZonePassageDailyCatchup(
                "{\"date\":\"" + date + "\",\"mmsi\":\"413000001\"}");

        assertTrue(result.isSuccess());
        assertTrue(result.isAllComplete());
        verify(statusRepository).findCompletedMmsi(date, start, end);
    }
}
