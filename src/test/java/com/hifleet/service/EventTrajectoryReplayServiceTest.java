package com.hifleet.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ReplayQueryResult;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.job.services.ShipBehaviorAnalysisService;
import com.hifleet.job.services.ShipLoiteringV3BatchService;
import com.hifleet.trajectory.TrajectoryLoaderService;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EventTrajectoryReplayServiceTest {

    @Test
    public void replaysLoiteringThroughOriginalV2Detector() {
        TrajectoryPoint own = point("413000001", 120.0);
        JSONObject recipe = recipe("V2", role(
                "OWN", "413000001", TraceRecipeService.SOURCE_HBASE,
                Collections.singletonList(own), null));
        Fixture fixture = fixture(eventRow(
                ShipBehaviorEvent.TYPE_LOITERING, null, recipe));
        when(fixture.analysis.loiteringV2RuleConfigHash()).thenReturn("config");
        when(fixture.loader.loadSafely(anyString(), anyString(), anyString(),
                anyInt(), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(own));
        when(fixture.analysis.analysisLoiteringV2Production(
                anyString(), anyList(), anyString()))
                .thenReturn(Collections.singletonList(trace(
                        ShipBehaviorEvent.TYPE_LOITERING, null,
                        Collections.singletonList(own), Collections.emptyList())));

        Map<String, Object> result = fixture.service.replay(1L);

        assertEquals("MATCH", result.get("status"));
        assertEquals(1, ((List<?>) result.get("roles")).size());
    }

    @Test
    public void replaysAlongsideOwnAndSpatialTargetRoles() {
        TrajectoryPoint own = point("413000001", 120.0);
        TrajectoryPoint target = point("413000002", 120.001);
        JSONObject replay = new JSONObject(true);
        replay.put("startTime", "2026-01-01 00:00:00");
        replay.put("endTime", "2026-01-01 01:00:00");
        replay.put("stepMinutes", 60);
        replay.put("minLon", 119.9);
        replay.put("maxLon", 120.1);
        replay.put("minLat", 29.9);
        replay.put("maxLat", 30.1);
        replay.put("devSize", 50000);
        JSONObject recipe = recipe(null,
                role("OWN", "413000001", TraceRecipeService.SOURCE_HBASE,
                        Collections.singletonList(own), null),
                role("TARGET", "413000002",
                        TraceRecipeService.SOURCE_SPATIAL_REPLAY,
                        Collections.singletonList(target), replay));
        Fixture fixture = fixture(eventRow(
                ShipBehaviorEvent.TYPE_ALONGSIDE, "413000002", recipe));
        when(fixture.analysis.alongsideRuleConfigHash()).thenReturn("config");
        when(fixture.loader.loadSafely(anyString(), anyString(), anyString(),
                anyInt(), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(own));
        ReplayQueryResult queryResult = new ReplayQueryResult();
        queryResult.setSuccess(true);
        queryResult.setTracksByMmsi(Collections.singletonMap(
                "413000002", Collections.singletonList(target)));
        when(fixture.spatial.query(any(com.hifleet.domain.ReplayQueryRequest.class)))
                .thenReturn(queryResult);
        when(fixture.analysis.analysisAlongsideReplay(
                eq("413000001"), eq("413000002"), anyList(), anyList()))
                .thenReturn(Collections.singletonList(trace(
                        ShipBehaviorEvent.TYPE_ALONGSIDE, "413000002",
                        Collections.singletonList(own),
                        Collections.singletonList(target))));

        Map<String, Object> result = fixture.service.replay(1L);

        assertEquals("MATCH", result.get("status"));
        assertEquals(2, ((List<?>) result.get("roles")).size());
    }

    @Test
    public void previewProjectionReturnsDisplayMetadata() {
        TrajectoryPoint own = point("413000001", 120.0);
        JSONObject recipe = recipe("V2", role(
                "OWN", "413000001", TraceRecipeService.SOURCE_HBASE,
                Collections.singletonList(own), null));
        Fixture fixture = fixture(eventRow(
                ShipBehaviorEvent.TYPE_LOITERING, null, recipe));
        when(fixture.analysis.loiteringV2RuleConfigHash()).thenReturn("config");
        when(fixture.loader.loadSafely(anyString(), anyString(), anyString(),
                anyInt(), anyInt(), anyInt())).thenReturn(Collections.singletonList(own));
        when(fixture.analysis.analysisLoiteringV2Production(
                anyString(), anyList(), anyString())).thenReturn(Collections.singletonList(trace(
                ShipBehaviorEvent.TYPE_LOITERING, null,
                Collections.singletonList(own), Collections.emptyList())));

        Map<String, Object> result = fixture.service.replay(1L, "PREVIEW");
        Map<?, ?> role = (Map<?, ?>) ((List<?>) result.get("roles")).get(0);

        assertEquals(1, role.get("actualPointCount"));
        assertEquals(1, role.get("displayPointCount"));
        assertEquals(false, role.get("sampled"));
    }

    private Fixture fixture(Map<String, Object> row) {
        Fixture fixture = new Fixture();
        fixture.service = new EventTrajectoryReplayService();
        fixture.loader = mock(TrajectoryLoaderService.class);
        fixture.spatial = mock(SpatialAisReplayClient.class);
        fixture.analysis = mock(ShipBehaviorAnalysisService.class);
        fixture.v3 = mock(ShipLoiteringV3BatchService.class);
        ReflectionTestUtils.setField(fixture.service, "jdbcTemplate",
                new OneRowJdbcTemplate(row));
        ReflectionTestUtils.setField(fixture.service, "trajectoryLoaderService", fixture.loader);
        ReflectionTestUtils.setField(fixture.service, "spatialAisReplayClient", fixture.spatial);
        ReflectionTestUtils.setField(fixture.service, "analysisService", fixture.analysis);
        ReflectionTestUtils.setField(fixture.service, "v3Service", fixture.v3);
        ReflectionTestUtils.setField(fixture.service, "maxDays", 7);
        ReflectionTestUtils.setField(fixture.service, "maxPoints", 100000);
        ReflectionTestUtils.setField(fixture.service, "concurrentRequests", 2);
        ReflectionTestUtils.setField(fixture.service, "maxDisplayPoints", 2000);
        return fixture;
    }

    private Map<String, Object> eventRow(
            String behaviorType, String targetMmsi, JSONObject recipe) {
        JSONObject root = new JSONObject(true);
        root.put("traceRecipe", recipe);
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", 1L);
        row.put("behavior_type", behaviorType);
        row.put("mmsi", "413000001");
        row.put("target_mmsi", targetMmsi);
        row.put("start_time", "2026-01-01 00:00:00");
        row.put("end_time", "2026-01-01 01:00:00");
        row.put("rule_version", ShipBehaviorEvent.TYPE_ALONGSIDE.equals(behaviorType)
                ? "ALONGSIDE_V3_INSURANCE" : "LOITERING_V2_INSURANCE");
        row.put("score_detail_json", root.toJSONString());
        return row;
    }

    private JSONObject recipe(String engine, JSONObject... roles) {
        JSONObject recipe = new JSONObject(true);
        recipe.put("recipeVersion", TraceRecipeService.RECIPE_VERSION);
        recipe.put("eventKey", "event-key");
        recipe.put("ruleConfigHash", "config");
        if (engine != null) {
            recipe.put("engine", engine);
        }
        JSONArray values = new JSONArray();
        Collections.addAll(values, roles);
        recipe.put("roles", values);
        return recipe;
    }

    private JSONObject role(String role, String mmsi, String source,
                            List<TrajectoryPoint> points, JSONObject replay) {
        JSONObject value = new JSONObject(true);
        value.put("role", role);
        value.put("mmsi", mmsi);
        value.put("trajectorySource", source);
        value.put("queryStartTime", "2026-01-01 00:00:00");
        value.put("queryEndTime", "2026-01-01 01:00:00");
        value.put("simplifyZoom", 15);
        value.put("pointCount", points.size());
        value.put("fingerprint", TrajectoryFingerprint.sha256(role, points));
        if (replay != null) {
            value.put("replay", replay);
        }
        return value;
    }

    private ShipBehaviorTrace trace(
            String behaviorType, String targetMmsi,
            List<TrajectoryPoint> own, List<TrajectoryPoint> target) {
        ShipBehaviorEvent event = new ShipBehaviorEvent();
        event.setBehaviorType(behaviorType);
        event.setMmsi("413000001");
        event.setTargetMmsi(targetMmsi);
        event.setStartTime("2026-01-01 00:00:00");
        event.setEndTime("2026-01-01 01:00:00");
        return new ShipBehaviorTrace(event, own, target);
    }

    private TrajectoryPoint point(String mmsi, double lon) {
        return new TrajectoryPoint(mmsi,
                LocalDateTime.of(2026, 1, 1, 0, 10),
                lon, 30.0, 0.2, 90.0, "0");
    }

    private static class OneRowJdbcTemplate extends JdbcTemplate {
        private final Map<String, Object> row;

        private OneRowJdbcTemplate(Map<String, Object> row) {
            this.row = row;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return Collections.singletonList(row);
        }
    }

    private static class Fixture {
        private EventTrajectoryReplayService service;
        private TrajectoryLoaderService loader;
        private SpatialAisReplayClient spatial;
        private ShipBehaviorAnalysisService analysis;
        private ShipLoiteringV3BatchService v3;
    }
}
