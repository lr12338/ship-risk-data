package com.hifleet.analysis.alongside.output.db;

import org.junit.Assert;
import org.junit.Test;

public class AlongsideDbTableInitializerTest {

    @Test
    public void shouldBuildEventAndDetailDdl() {
        AlongsideDbTableInitializer initializer = new AlongsideDbTableInitializer();

        String eventDdl = initializer.eventTableDdl("ship_realtime_alongside_event");
        String detailDdl = initializer.detailTableDdl("ship_realtime_alongside_event_detail");

        Assert.assertTrue(eventDdl.contains("CREATE TABLE IF NOT EXISTS ship_realtime_alongside_event"));
        Assert.assertTrue(eventDdl.contains("UNIQUE KEY uk_event_id (event_id)"));
        Assert.assertTrue(eventDdl.contains("score_detail_json JSON"));
        Assert.assertTrue(detailDdl.contains("CREATE TABLE IF NOT EXISTS ship_realtime_alongside_event_detail"));
        Assert.assertTrue(detailDdl.contains("point_role VARCHAR(16) NOT NULL"));
        Assert.assertTrue(detailDdl.contains("KEY idx_event_id (event_id)"));
    }
}
