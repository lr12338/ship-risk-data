package com.hifleet.analysis.alongside.output.db;

import com.hifleet.analysis.alongside.core.model.AlongsideEvent;
import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;

public class AlongsideEventDbMapperTest {

    @Test
    public void shouldBuildUpsertSqlWithStableUniqueKeyFields() {
        String sql = new AlongsideEventDbMapper().upsertSql("ship_realtime_alongside_event");

        Assert.assertTrue(sql.contains("INSERT INTO ship_realtime_alongside_event"));
        Assert.assertTrue(sql.contains("event_id"));
        Assert.assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
        Assert.assertTrue(sql.contains("score_detail_json=VALUES(score_detail_json)"));
    }

    @Test
    public void shouldEnsureEventIdBeforeMapping() {
        AlongsideEvent event = new AlongsideEvent();
        event.setRuleVersion("ALONGSIDE_V2_INSURANCE");
        event.setPairId("413193000-413433610");
        event.setEventSubType("WEAK_ALONGSIDE_CANDIDATE");
        event.setStartTime(Instant.parse("2026-06-29T00:00:00Z"));
        event.setEndTime(Instant.parse("2026-06-29T00:20:00Z"));

        String id = AlongsideEventIdGenerator.ensureEventId(event);

        Assert.assertEquals(id, event.getEventId());
        Assert.assertEquals(32, id.length());
    }
}
