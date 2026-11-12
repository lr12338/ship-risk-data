package com.hifleet.service;

import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.AlongsideCandidateTarget;
import com.hifleet.domain.ReplayQueryRequest;
import com.hifleet.domain.ReplayQueryResult;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TraceRecipeServiceTest {

    @Test
    public void alongsideRecipeStoresOwnTargetFingerprintsAndReplayRequest() {
        TrajectoryPoint own = point("413000001", 120.0);
        TrajectoryPoint target = point("413000002", 120.001);
        ShipBehaviorEvent event = event(ShipBehaviorEvent.TYPE_ALONGSIDE);
        event.setTargetMmsi("413000002");
        ShipBehaviorTrace trace = new ShipBehaviorTrace(
                event, Collections.singletonList(own),
                Collections.singletonList(target));
        AlongsideCandidateTarget candidate = new AlongsideCandidateTarget();
        candidate.setWindowStart("2026-01-01 00:00:00");
        candidate.setWindowEnd("2026-01-01 01:00:00");
        candidate.setTargetMmsi("413000002");
        ReplayQueryRequest request = new ReplayQueryRequest();
        request.setStartTime("2026-01-01 00:00:00");
        request.setEndTime("2026-01-01 00:30:00");
        request.setMinLon(119.9);
        request.setMaxLon(120.1);
        request.setMinLat(29.9);
        request.setMaxLat(30.1);
        request.setDevSize(50000);
        ReplayQueryResult replayResult = new ReplayQueryResult();
        replayResult.setRequest(request);
        candidate.setReplayQueryResult(replayResult);

        TraceRecipeService.attachAlongside(trace, candidate, 15, "config-hash");

        JSONObject recipe = TraceRecipeService.parseRecipe(event.getScoreDetailJson());
        assertNotNull(recipe);
        assertEquals("config-hash", recipe.getString("ruleConfigHash"));
        assertEquals("OWN", recipe.getJSONArray("roles")
                .getJSONObject(0).getString("role"));
        assertEquals(TraceRecipeService.SOURCE_HBASE, recipe.getJSONArray("roles")
                .getJSONObject(0).getString("trajectorySource"));
        assertEquals("TARGET", recipe.getJSONArray("roles")
                .getJSONObject(1).getString("role"));
        assertEquals(TraceRecipeService.SOURCE_SPATIAL_REPLAY,
                recipe.getJSONArray("roles").getJSONObject(1)
                        .getString("trajectorySource"));
        assertEquals(30L, recipe.getJSONArray("roles").getJSONObject(1)
                .getJSONObject("replay").getLongValue("stepMinutes"));
        assertEquals(50000, recipe.getJSONArray("roles").getJSONObject(1)
                .getJSONObject("replay").getIntValue("devSize"));
    }

    @Test
    public void loiteringRecipeStoresEngineAndOnlyOwnRole() {
        ShipBehaviorEvent event = event(ShipBehaviorEvent.TYPE_LOITERING);
        ShipBehaviorTrace trace = new ShipBehaviorTrace(
                event, Collections.singletonList(point("413000001", 120.0)),
                Collections.emptyList());

        TraceRecipeService.attachLoitering(
                trace, "V2", event.getStartTime(), event.getEndTime(),
                15, "config-hash");

        JSONObject recipe = TraceRecipeService.parseRecipe(event.getScoreDetailJson());
        assertEquals("V2", recipe.getString("engine"));
        assertEquals(event.getStartTime(), recipe.getJSONObject("episodeSelection")
                .getString("startTime"));
        assertEquals(1, recipe.getJSONArray("roles").size());
        assertEquals(1, recipe.getJSONArray("roles")
                .getJSONObject(0).getIntValue("pointCount"));
        assertNotNull(recipe.getJSONArray("roles")
                .getJSONObject(0).getString("fingerprint"));
    }

    private ShipBehaviorEvent event(String behaviorType) {
        ShipBehaviorEvent event = new ShipBehaviorEvent();
        event.setBehaviorType(behaviorType);
        event.setMmsi("413000001");
        event.setStartTime("2026-01-01 00:00:00");
        event.setEndTime("2026-01-01 01:00:00");
        event.setRuleVersion(ShipBehaviorEvent.TYPE_ALONGSIDE.equals(behaviorType)
                ? "ALONGSIDE_V3_INSURANCE" : "LOITERING_V2_INSURANCE");
        return event;
    }

    private TrajectoryPoint point(String mmsi, double lon) {
        return new TrajectoryPoint(mmsi,
                LocalDateTime.of(2026, 1, 1, 0, 10),
                lon, 30.0, 0.2, 90.0, "0");
    }
}
