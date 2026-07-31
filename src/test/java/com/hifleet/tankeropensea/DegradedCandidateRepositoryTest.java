package com.hifleet.tankeropensea;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DegradedCandidateRepositoryTest {
    @Test
    public void replaceWindowDeletesThenUpsertsDeterministicCandidateWithTracks() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        DegradedCandidateRepository repository = new DegradedCandidateRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);

        ShipBehaviorTrace trace = trace();
        repository.replaceWindow("ALONGSIDE", "batch-v2", "123456789",
                "2026-01-01 00:00:00", "2026-01-08 00:00:00",
                Collections.singletonList(new DegradedCandidate(
                        trace, "GEOMETRY_QUARANTINE_HIT")),
                snapshot());
        String firstKey = jdbc.candidateKey;
        repository.replaceWindow("ALONGSIDE", "batch-v2", "123456789",
                "2026-01-01 00:00:00", "2026-01-08 00:00:00",
                Collections.singletonList(new DegradedCandidate(
                        trace, "GEOMETRY_QUARANTINE_HIT")),
                snapshot());

        assertEquals(firstKey, jdbc.candidateKey);
        assertTrue(firstKey.matches("^[0-9a-f]{64}$"));
        assertTrue(jdbc.payload.contains("\"ownTrack\""));
        assertTrue(jdbc.payload.contains("\"targetTrack\""));
        assertTrue(jdbc.deleteCalls >= 2);
        assertTrue(jdbc.insertCalls >= 2);
        assertFalse(jdbc.insertSql.contains("review_status='PENDING'"));
    }

    private PortAnchorBerthExclusionService.Snapshot snapshot() {
        return PortAnchorBerthExclusionService.snapshotForTest(
                Collections.singletonList(new PortAnchorBerthExclusionService.SourceRow(
                        "PORT", "ports", "1",
                        "POLYGON((130 30,131 30,131 31,130 31,130 30))",
                        false, false)));
    }

    private ShipBehaviorTrace trace() {
        ShipBehaviorEvent event = new ShipBehaviorEvent();
        event.setBehaviorType("ALONGSIDE");
        event.setMmsi("123456789");
        event.setTargetMmsi("987654321");
        event.setStartTime("2026-01-01 00:00:00");
        event.setEndTime("2026-01-01 01:00:00");
        event.setRuleVersion("ALONGSIDE_LIQUID_CONTAINER_V2");
        event.setBatchNo("batch-v2");
        LocalDateTime time = LocalDateTime.of(2026, 1, 1, 0, 0);
        return new ShipBehaviorTrace(event,
                Arrays.asList(new TrajectoryPoint("123456789", time,
                        120.0, 20.0, 2.0, 90.0, "under way")),
                Arrays.asList(new TrajectoryPoint("987654321", time,
                        120.01, 20.0, 2.0, 90.0, "under way")));
    }

    private static class RecordingJdbcTemplate extends JdbcTemplate {
        private int deleteCalls;
        private int insertCalls;
        private String candidateKey;
        private String payload;
        private String insertSql;

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("DELETE")) {
                deleteCalls++;
            } else if (sql.startsWith("INSERT")) {
                insertCalls++;
                insertSql = sql;
                candidateKey = String.valueOf(args[0]);
                payload = String.valueOf(args[12]);
            }
            return 1;
        }
    }
}
