package com.hifleet.analysis.alongside.output.db;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class AlongsideDbTableInitializerTest {

    @Test
    public void shouldBuildEventAndDetailDdl() {
        AlongsideDbTableInitializer initializer = new AlongsideDbTableInitializer();

        String eventDdl = initializer.eventTableDdl("ship_realtime_alongside_event");
        String detailDdl = initializer.detailTableDdl("ship_realtime_alongside_event_detail");

        Assert.assertTrue(eventDdl.contains("CREATE TABLE IF NOT EXISTS ship_realtime_alongside_event"));
        Assert.assertTrue(eventDdl.contains("UNIQUE KEY uk_event_id (event_id)"));
        Assert.assertTrue(eventDdl.contains("score_detail_json JSON"));
        Assert.assertTrue(eventDdl.contains("start_lon DOUBLE"));
        Assert.assertTrue(eventDdl.contains("start_lat DOUBLE"));
        Assert.assertTrue(eventDdl.contains("end_lon DOUBLE"));
        Assert.assertTrue(eventDdl.contains("end_lat DOUBLE"));
        Assert.assertTrue(eventDdl.contains("center_lon DOUBLE"));
        Assert.assertTrue(eventDdl.contains("center_lat DOUBLE"));
        Assert.assertTrue(eventDdl.contains("ais_gap_count INT"));
        Assert.assertTrue(eventDdl.contains("max_ais_gap_minutes BIGINT"));
        Assert.assertTrue(eventDdl.contains("track_coverage_ratio DOUBLE"));
        Assert.assertTrue(eventDdl.contains("KEY idx_center (center_lon, center_lat)"));
        Assert.assertTrue(detailDdl.contains("CREATE TABLE IF NOT EXISTS ship_realtime_alongside_event_detail"));
        Assert.assertTrue(detailDdl.contains("point_role VARCHAR(16) NOT NULL"));
        Assert.assertTrue(detailDdl.contains("KEY idx_event_id (event_id)"));
    }

    @Test
    public void shouldNotCreateDetailTableWhenDetailDisabled() throws Exception {
        RecordingConnection recorder = new RecordingConnection();
        AlongsideDbSinkConfig config = new AlongsideDbSinkConfig();
        config.setEventTable("ship_realtime_alongside_event");
        config.setDetailTable("ship_realtime_alongside_event_detail");
        config.setDetailEnabled(false);

        new AlongsideDbTableInitializer().initialize(recorder.connection(), config);

        Assert.assertFalse(containsSql(recorder.sql, "ship_realtime_alongside_event_detail"));
    }

    private static boolean containsSql(List<String> sqlList, String value) {
        for (String sql : sqlList) {
            if (sql.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static class RecordingConnection implements InvocationHandler {
        private final List<String> sql = new ArrayList<String>();

        Connection connection() {
            return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("createStatement".equals(method.getName())) {
                return statement();
            }
            return null;
        }

        private Statement statement() {
            return (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[] { Statement.class },
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("execute".equals(method.getName()) && args != null && args.length == 1) {
                            sql.add((String) args[0]);
                            return Boolean.TRUE;
                        }
                        return null;
                    }
                });
        }
    }
}
