package com.hifleet.tankeropensea;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TankerOpenSeaTargetShipRepositoryTest {
    @Test
    public void sourceCandidatesUseBoundedMmsiKeysetPage() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        TankerOpenSeaTargetShipRepository repository =
                new TankerOpenSeaTargetShipRepository();
        ReflectionTestUtils.setField(
                repository, "aisReadOnlyJdbcTemplate", jdbc);

        repository.selectSourceCandidatesPage(
                "2026-01-01 00:00:00", 222222222, 5000);

        assertTrue(jdbc.sql.contains("mmsi>?"));
        assertTrue(jdbc.sql.contains("updatetime>=?"));
        assertTrue(jdbc.sql.contains("ORDER BY mmsi LIMIT ?"));
        assertEquals(3, jdbc.args.length);
        assertEquals(222222222, jdbc.args[0]);
        assertEquals("2026-01-01 00:00:00", jdbc.args[1]);
        assertEquals(5000, jdbc.args[2]);
    }

    @Test
    public void sourceCandidatePageRejectsLimitAboveFiveThousand() {
        TankerOpenSeaTargetShipRepository repository =
                new TankerOpenSeaTargetShipRepository();
        try {
            repository.selectSourceCandidatesPage(
                    "2026-01-01 00:00:00", 0, 5001);
            fail("expected bounded page validation");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("5000"));
        }
    }

    @Test
    public void immutableInsertFailureRollsBackTransaction() throws Exception {
        TankerOpenSeaTargetShipRepository repository =
                new TankerOpenSeaTargetShipRepository();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(jdbc.queryForObject(anyString(), any(Object[].class),
                org.mockito.Matchers.eq(Integer.class))).thenReturn(1, 1, 0);
        doThrow(new IllegalStateException("insert failed"))
                .when(jdbc).batchUpdate(anyString(),
                any(org.springframework.jdbc.core.BatchPreparedStatementSetter.class));
        ReflectionTestUtils.setField(repository, "eventJdbcTemplate", jdbc);
        ReflectionTestUtils.setField(repository, "eventDataSource", dataSource);

        try {
            repository.insertImmutable("liquid-container-test-v1",
                    Collections.singletonList(listed()));
            fail("expected insert failure");
        } catch (IllegalStateException expected) {
            assertEquals("insert failed", expected.getMessage());
        }

        verify(connection).rollback();
    }

    private ListedTanker listed() {
        ShipMetadata metadata = new ShipMetadata(
                "111111111", null, "TEST", "油轮", "油轮",
                null, 200.0, 50000L, null);
        return new ListedTanker(1, metadata,
                ShipEligibilityDecision.eligible(StandardShipType.TANKER_OIL,
                        "TEST", "TEST"));
    }

    private static class RecordingJdbcTemplate extends JdbcTemplate {
        private String sql;
        private Object[] args;

        @Override
        public <T> List<T> query(String sql, Object[] args,
                                 RowMapper<T> rowMapper) {
            this.sql = sql;
            this.args = args;
            return Collections.emptyList();
        }
    }
}
