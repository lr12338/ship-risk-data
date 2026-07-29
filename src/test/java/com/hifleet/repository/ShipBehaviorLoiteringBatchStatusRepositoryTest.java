package com.hifleet.repository;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ShipBehaviorLoiteringBatchStatusRepositoryTest {

    @Test
    public void detailedDoneStoresCandidateBreakdownAndNoDataCode() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        ShipBehaviorLoiteringBatchStatusRepository repository =
                new ShipBehaviorLoiteringBatchStatusRepository();
        ReflectionTestUtils.setField(repository,
                "newDataanalysisJdbcTemplate", jdbc);

        repository.markDone("batch", "413000001",
                "2026-01-01 00:00:00", "2026-07-01 00:00:00",
                0, 12, 7, 8, 4, 2,
                ShipBehaviorLoiteringBatchStatusRepository.RESULT_NO_DATA,
                1234L);

        assertTrue(jdbc.sql.contains("candidate_count"));
        assertEquals(2, jdbc.args[6]);
        assertEquals(12, jdbc.args[7]);
        assertEquals(7, jdbc.args[8]);
        assertEquals(8, jdbc.args[9]);
        assertEquals(4, jdbc.args[10]);
        assertEquals(2, jdbc.args[11]);
        assertEquals("NO_DATA", jdbc.args[12]);
        assertEquals(1234L, jdbc.args[13]);
    }

    @Test
    public void failedStatusDoesNotPretendCandidateCountsAreZero() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        ShipBehaviorLoiteringBatchStatusRepository repository =
                new ShipBehaviorLoiteringBatchStatusRepository();
        ReflectionTestUtils.setField(repository,
                "newDataanalysisJdbcTemplate", jdbc);

        repository.markFailed("batch", "413000001",
                "2026-01-01 00:00:00", "2026-07-01 00:00:00",
                "source failed", 5678L);

        assertEquals("FAILED", jdbc.args[4]);
        assertNull(jdbc.args[7]);
        assertNull(jdbc.args[8]);
        assertNull(jdbc.args[9]);
        assertNull(jdbc.args[10]);
        assertEquals(0, jdbc.args[11]);
        assertNull(jdbc.args[12]);
        assertEquals(5678L, jdbc.args[13]);
        assertEquals("source failed", jdbc.args[14]);
    }

    private static class RecordingJdbcTemplate extends JdbcTemplate {
        private String sql;
        private Object[] args;

        @Override
        public int update(String sql, Object... args) {
            this.sql = sql;
            this.args = args;
            return 1;
        }
    }
}
