package com.hifleet.analysis.alongside.output.db;

import com.hifleet.analysis.alongside.core.model.AlongsideEvent;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class AlongsideEventDbMapperTest {

    @Test
    public void shouldBuildUpsertSqlWithStableUniqueKeyFields() {
        String sql = new AlongsideEventDbMapper().upsertSql("ship_realtime_alongside_event");

        Assert.assertTrue(sql.contains("INSERT INTO ship_realtime_alongside_event"));
        Assert.assertTrue(sql.contains("event_id"));
        Assert.assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
        Assert.assertTrue(sql.contains("start_lon"));
        Assert.assertTrue(sql.contains("center_lat"));
        Assert.assertTrue(sql.contains("ais_gap_count"));
        Assert.assertTrue(sql.contains("max_ais_gap_minutes"));
        Assert.assertTrue(sql.contains("track_coverage_ratio"));
        Assert.assertTrue(sql.contains("center_lon=VALUES(center_lon)"));
        Assert.assertTrue(sql.contains("score_detail_json=VALUES(score_detail_json)"));
    }

    @Test
    public void shouldBindTrajectoryLookupFields() throws Exception {
        AlongsideEvent event = new AlongsideEvent();
        event.setRuleVersion("ALONGSIDE_V2_INSURANCE");
        event.setPairId("413193000-413433610");
        event.setMmsiA(413193000L);
        event.setMmsiB(413433610L);
        event.setEventSubType("ALONGSIDE_CONFIRMED_CANDIDATE");
        event.setStartTime(Instant.parse("2026-06-29T00:00:00Z"));
        event.setEndTime(Instant.parse("2026-06-29T00:20:00Z"));
        event.setStartLon(122.10d);
        event.setStartLat(30.10d);
        event.setEndLon(122.20d);
        event.setEndLat(30.20d);
        event.setCenterLon(122.15d);
        event.setCenterLat(30.15d);
        event.setAisGapCount(2);
        event.setMaxAisGapMinutes(35L);
        event.setTrackCoverageRatio(0.8d);

        RecordingStatement recorder = new RecordingStatement();
        PreparedStatement statement = recorder.statement();

        new AlongsideEventDbMapper().bind(statement, event);

        Assert.assertEquals(122.10d, (Double) recorder.values.get(14), 0.001d);
        Assert.assertEquals(30.10d, (Double) recorder.values.get(15), 0.001d);
        Assert.assertEquals(122.20d, (Double) recorder.values.get(16), 0.001d);
        Assert.assertEquals(30.20d, (Double) recorder.values.get(17), 0.001d);
        Assert.assertEquals(122.15d, (Double) recorder.values.get(18), 0.001d);
        Assert.assertEquals(30.15d, (Double) recorder.values.get(19), 0.001d);
        Assert.assertEquals(2, recorder.values.get(37));
        Assert.assertEquals(35L, recorder.values.get(38));
        Assert.assertEquals(0.8d, (Double) recorder.values.get(39), 0.001d);
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

    private static class RecordingStatement implements InvocationHandler {
        private final Map<Integer, Object> values = new HashMap<Integer, Object>();

        PreparedStatement statement() {
            return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[] { PreparedStatement.class },
                this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if (name.startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer) {
                values.put((Integer) args[0], args[1]);
            }
            return defaultValue(method.getReturnType());
        }

        private Object defaultValue(Class<?> type) {
            if (Boolean.TYPE.equals(type)) {
                return Boolean.FALSE;
            }
            if (Integer.TYPE.equals(type)) {
                return Integer.valueOf(0);
            }
            if (Long.TYPE.equals(type)) {
                return Long.valueOf(0L);
            }
            if (Double.TYPE.equals(type)) {
                return Double.valueOf(0d);
            }
            return null;
        }
    }
}
