package com.hifleet.sts.repository;

import com.hifleet.sts.domain.StsBackfillTaskRow;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
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

    @Test
    public void failedTaskIsClaimableOnNextInvocationAndIncrementsRetry() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);

        StsBackfillTaskRow failed = task(7L, "FAILED", 0);
        StsBackfillTaskRow running = task(7L, "RUNNING", 1);
        when(jdbc.queryForObject(eq(StsBackfillTaskRepository.SELECT_CLAIMABLE_SQL),
                any(Object[].class), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(failed);
        when(jdbc.update(StsBackfillTaskRepository.CLAIM_SQL, 7L,
                StsBackfillTaskRepository.MAX_RETRY,
                StsBackfillTaskRepository.MAX_RETRY)).thenReturn(1);
        when(jdbc.queryForObject(eq("SELECT * FROM dataanalysis.ship_sts_backfill_task WHERE id=?"),
                any(Object[].class), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(running);

        StsBackfillTaskRow claimed = repository(jdbc, dataSource).claimNextTask("batch-a");

        assertNotNull(claimed);
        assertEquals("RUNNING", claimed.getStatus());
        assertEquals(1, claimed.getRetryCount());
        verify(jdbc).update(StsBackfillTaskRepository.CLAIM_SQL, 7L,
                StsBackfillTaskRepository.MAX_RETRY,
                StsBackfillTaskRepository.MAX_RETRY);
    }

    @Test
    public void retryExhaustedFailedTaskIsExcludedFromClaim() {
        assertEquals(3, StsBackfillTaskRepository.MAX_RETRY);
        assertTrue(StsBackfillTaskRepository.SELECT_CLAIMABLE_SQL
                .contains("status='FAILED' AND retry_count < ?"));
        assertTrue(StsBackfillTaskRepository.CLAIM_SQL
                .contains("status='FAILED' AND retry_count < ?"));
    }

    @Test
    public void exhaustedStaleAttemptBecomesTerminalFailed() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StsBackfillTaskRepository repository = repository(jdbc);

        repository.failExhaustedStaleRunningTasks("batch-a");

        verify(jdbc).update(eq(StsBackfillTaskRepository.FAIL_EXHAUSTED_STALE_SQL),
                eq("batch-a"), eq(StsBackfillTaskRepository.STALE_TIMEOUT_MINUTES),
                eq(StsBackfillTaskRepository.MAX_RETRY));
    }

    private StsBackfillTaskRepository repository(JdbcTemplate jdbc) {
        StsBackfillTaskRepository repository = new StsBackfillTaskRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
        return repository;
    }

    private StsBackfillTaskRepository repository(JdbcTemplate jdbc, DataSource dataSource) {
        StsBackfillTaskRepository repository = repository(jdbc);
        ReflectionTestUtils.setField(repository, "dataSource", dataSource);
        return repository;
    }

    private StsBackfillTaskRow task(Long id, String status, int retryCount) {
        StsBackfillTaskRow task = new StsBackfillTaskRow();
        task.setId(id);
        task.setStatus(status);
        task.setRetryCount(retryCount);
        return task;
    }
}
