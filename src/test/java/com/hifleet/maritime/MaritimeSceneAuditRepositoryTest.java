package com.hifleet.maritime;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class MaritimeSceneAuditRepositoryTest {
    @Test
    public void auditKeyIsDeterministicAndChangesWithSnapshot() {
        MaritimeSceneAuditRepository repository = new MaritimeSceneAuditRepository(null, false);
        String first = repository.auditKey("batch", "LOITERING", "123", null,
                "2026-01-01", "2026-01-02", "a");
        String second = repository.auditKey("batch", "LOITERING", "123", null,
                "2026-01-01", "2026-01-02", "a");
        String changed = repository.auditKey("batch", "LOITERING", "123", null,
                "2026-01-01", "2026-01-02", "b");
        assertEquals(first, second);
        if (first.equals(changed)) throw new AssertionError("snapshot must affect audit key");
    }

    @Test
    public void storeWritesEpisodeMetricsToQueryableColumns() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        MaritimeSceneAuditRepository repository = new MaritimeSceneAuditRepository(jdbc, true);
        MaritimeSceneDecision inland = new MaritimeSceneDecision(
                MaritimeSceneClass.INLAND_RIVER, SceneConfidence.HIGH,
                MaritimeSceneEvidence.unavailable("test"), 3.0, null,
                "release", repeat('a', 64), MaritimeScenePolicy.VERSION, false);
        SceneEpisodeAggregator.Summary summary = new SceneEpisodeAggregator().aggregate(
                Arrays.asList(inland, inland), Arrays.asList(0L, 7200000L));

        repository.store("batch", "LOITERING", "123", null,
                "2026-01-01", "2026-01-02", "release", repeat('a', 64),
                summary, "WOULD_REJECT_INLAND", "{}");

        assertEquals(28, jdbc.values.length);
        if (!jdbc.sql.contains("ON DUPLICATE KEY UPDATE") || jdbc.sql.contains("ON CONFLICT")) {
            throw new AssertionError("event database upsert must use MySQL syntax");
        }
        assertEquals(1.0, (Double) jdbc.values[13], 0.000001);
        assertEquals(0, jdbc.values[23]);
        assertEquals(3.0, (Double) jdbc.values[24], 0.000001);
    }

    private static String repeat(char value, int count) {
        return String.join("", Collections.nCopies(count, String.valueOf(value)));
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private String sql;
        private Object[] values;

        @Override
        public int update(String sql, Object... args) {
            this.sql = sql;
            values = args;
            return 1;
        }
    }
}
