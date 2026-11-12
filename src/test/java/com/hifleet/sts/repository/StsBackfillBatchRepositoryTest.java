package com.hifleet.sts.repository;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class StsBackfillBatchRepositoryTest {

    @Test
    public void oneAndThreeTaskProgressIsTaskDerived() {
        String sql = StsBackfillBatchRepository.REFRESH_PROGRESS_SQL;

        assertTrue(sql.contains("COUNT(*) AS total_tasks"));
        assertTrue(sql.contains("b.completed_tasks=t.completed_tasks"));
        assertTrue(sql.contains("t.completed_tasks=t.total_tasks"));
        assertFalse(sql.contains("completed_tasks=completed_tasks+"));
        assertTrue(sql.contains("status IN ('COMPLETE','COMPLETE_WITH_WARNINGS')"));
        assertTrue(sql.contains("status='COMPLETE_WITH_WARNINGS'"));
        assertTrue(sql.contains("THEN 'COMPLETE_WITH_WARNINGS'"));
    }

    @Test
    public void retryableFailureKeepsParentRunningUntilRetryIsExhausted() {
        String sql = StsBackfillBatchRepository.REFRESH_PROGRESS_SQL;
        int runningRule = sql.indexOf("t.retryable_failed_tasks > 0 THEN 'RUNNING'");
        int failedRule = sql.indexOf("t.failed_tasks > 0 THEN 'PARTIAL_FAILED'");

        assertTrue(runningRule >= 0);
        assertTrue(failedRule > runningRule);
        assertTrue(sql.contains("status='FAILED' AND retry_count < ?"));
    }

    @Test
    public void taskMetricsAreSummedWithoutIncrementingParent() {
        String sql = StsBackfillBatchRepository.REFRESH_PROGRESS_SQL;

        assertTrue(sql.contains("SUM(candidate_ship_count)"));
        assertTrue(sql.contains("SUM(processed_ship_count)"));
        assertTrue(sql.contains("SUM(baseline_events)"));
        assertTrue(sql.contains("SUM(shadow_inserted)"));
        assertTrue(sql.contains("SUM(shadow_duplicate_skipped)"));
        assertFalse(sql.contains("total_candidate_ships+"));
    }

    @Test
    public void repeatedRefreshIsTheSameIdempotentStatement() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StsBackfillBatchRepository repository = new StsBackfillBatchRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);

        for (int i = 0; i < 10; i++) {
            repository.refreshProgressFromTasks("batch-a");
        }

        verify(jdbc, times(10)).update(StsBackfillBatchRepository.REFRESH_PROGRESS_SQL,
                StsBackfillTaskRepository.MAX_RETRY, "batch-a", "batch-a");
    }

    @Test
    public void finishedAtIsOnlySetForTerminalParentState() {
        String sql = StsBackfillBatchRepository.REFRESH_PROGRESS_SQL;

        assertTrue(sql.contains("t.running_tasks=0 AND t.pending_tasks=0"));
        assertTrue(sql.contains("t.retryable_failed_tasks=0"));
        assertTrue(sql.contains("THEN COALESCE(b.finished_at,NOW()) ELSE NULL END"));
    }
}
