package com.hifleet.sts.repository;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StsBackfillTaskRepositoryTest {

    @Test
    public void heartbeatOnlyTouchesRunningTask() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StsBackfillTaskRepository repository = repository(jdbc);
        when(jdbc.update("UPDATE dataanalysis.ship_sts_backfill_task "
                + "SET updated_at=NOW() WHERE id=? AND status='RUNNING'", 7L)).thenReturn(1);

        assertEquals(1, repository.touchRunning(7L));
    }

    @Test
    public void staleRecoveryRetainsCrashTimeoutGuard() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StsBackfillTaskRepository repository = repository(jdbc);

        repository.reclaimStaleRunningTasks("batch-a");

        verify(jdbc).update(eq(StsBackfillTaskRepository.RECLAIM_STALE_SQL),
                eq("batch-a"), eq(StsBackfillTaskRepository.STALE_TIMEOUT_MINUTES),
                eq(StsBackfillTaskRepository.MAX_RETRY));
        assertEquals(180, StsBackfillTaskRepository.STALE_TIMEOUT_MINUTES);
    }

    private StsBackfillTaskRepository repository(JdbcTemplate jdbc) {
        StsBackfillTaskRepository repository = new StsBackfillTaskRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
        return repository;
    }
}
