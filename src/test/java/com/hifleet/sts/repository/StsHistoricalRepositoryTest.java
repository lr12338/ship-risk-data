package com.hifleet.sts.repository;

import com.hifleet.sts.domain.StsShadowEvent;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import static org.junit.Assert.*;

public class StsHistoricalRepositoryTest {
    @Test public void historicalSeedReadRetainsDisabledAliases() {
        RecordingJdbc jdbc = new RecordingJdbc();
        StsSeedIdentityRepository repository = new StsSeedIdentityRepository();
        ReflectionTestUtils.setField(repository,"jdbcTemplate",jdbc);
        repository.selectHistoricalCandidateRows("batch");
        assertTrue(jdbc.sql.contains("WHERE seed_batch_no=? ORDER BY ship_ordinal,imo,mmsi"));
        assertFalse(jdbc.sql.contains("enabled"));
        repository.findHistoricalImoByMmsi("batch","422222000");
        assertFalse(jdbc.sql.contains("enabled"));
        repository.findImoByMmsi("batch","422222000");
        assertTrue(jdbc.sql.contains("enabled = 1"));
    }
    @Test public void historicalOverlapChecksBothDirectionsAcrossRegions() {
        RecordingJdbc jdbc = new RecordingJdbc();
        StsShadowEventRepository repository = new StsShadowEventRepository();
        ReflectionTestUtils.setField(repository,"jdbcTemplate",jdbc);
        StsShadowEvent event = new StsShadowEvent();
        event.setBatchNo("batch"); event.setOwnImo("9000001"); event.setTargetImo("9000002");
        event.setStartTime("2024-01-01 00:00:00"); event.setEndTime("2024-01-01 01:00:00");
        assertTrue((Boolean)ReflectionTestUtils.invokeMethod(repository,"existsHistoricalPairOverlap",event));
        assertFalse(jdbc.sql.contains("region_id"));
        assertTrue(jdbc.sql.contains("OR (own_imo=? AND target_imo=?)"));
        assertEquals("9000001",jdbc.args[1]); assertEquals("9000002",jdbc.args[2]);
        assertEquals("9000002",jdbc.args[3]); assertEquals("9000001",jdbc.args[4]);
        event.setTargetImo(null); event.setTargetMmsi("413000002");
        ReflectionTestUtils.invokeMethod(repository,"existsHistoricalPairOverlap",event);
        assertTrue(jdbc.sql.contains("target_imo IS NULL AND target_mmsi=?"));
        assertEquals("413000002",jdbc.args[2]);
    }
    @Test public void batchSummarySeparatesPhysicalFromStsPromotions() {
        RecordingJdbc jdbc = new RecordingJdbc();
        StsShadowEventRepository repository = new StsShadowEventRepository();
        ReflectionTestUtils.setField(repository,"jdbcTemplate",jdbc);

        StsShadowEventRepository.BatchSummary result = repository.summarizeBatch("batch");

        assertEquals(26, result.physicalObserved);
        assertEquals(0, result.stsCandidates);
        assertEquals(0, result.stsReviewRequired);
        assertEquals(14, result.serviceSuppressed);
        assertEquals(25, result.weakSuppressed);
        assertTrue(jdbc.sql.contains("stsClassification"));
        assertTrue(jdbc.sql.contains("physicalSupportLevel"));
        assertEquals("batch", jdbc.args[0]);
    }
    private static class RecordingJdbc extends JdbcTemplate {
        String sql; Object[] args;
        @Override public <T> List<T> query(String sql,Object[] args,RowMapper<T> mapper) {
            this.sql=sql; this.args=args; return Collections.emptyList();
        }
        @Override public <T> List<T> queryForList(String sql,Class<T> type,Object... args) {
            this.sql=sql; this.args=args; return Collections.emptyList();
        }
        @Override public <T> T queryForObject(String sql,Object[] args,Class<T> type) {
            this.sql=sql; this.args=args; return type.cast(Integer.valueOf(1));
        }
        @Override public Map<String,Object> queryForMap(String sql,Object... args) {
            this.sql=sql; this.args=args;
            Map<String,Object> row = new HashMap<String,Object>();
            row.put("physical_observed",26); row.put("sts_candidates",0);
            row.put("sts_review_required",0); row.put("service_suppressed",14);
            row.put("weak_suppressed",25);
            return row;
        }
    }
}
