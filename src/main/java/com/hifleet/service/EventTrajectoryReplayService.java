package com.hifleet.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ReplayQueryRequest;
import com.hifleet.domain.ReplayQueryResult;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.job.services.ShipBehaviorAnalysisService;
import com.hifleet.job.services.ShipLoiteringV3BatchService;
import com.hifleet.trajectory.TrajectoryLoaderService;
import com.hifleet.trajectory.TrajectoryPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Service
public class EventTrajectoryReplayService {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TrajectoryLoaderService trajectoryLoaderService;

    @Autowired
    private SpatialAisReplayClient spatialAisReplayClient;

    @Autowired
    private ShipBehaviorAnalysisService analysisService;

    @Autowired
    private ShipLoiteringV3BatchService v3Service;

    @Value("${ship.behavior.trace.replay.max-days:7}")
    private int maxDays;

    @Value("${ship.behavior.trace.replay.max-points:100000}")
    private int maxPoints;

    @Value("${ship.behavior.trace.replay.concurrent-requests:2}")
    private int concurrentRequests;

    @Value("${ship.behavior.trace.replay.max-display-points:2000}")
    private int maxDisplayPoints;

    private volatile Semaphore semaphore;

    public Map<String, Object> replay(long eventId) {
        return replay(eventId, "FULL");
    }

    public Map<String, Object> replay(long eventId, String projection) {
        Semaphore gate = gate();
        if (!gate.tryAcquire()) {
            throw new ReplayException(503, "QUERY_BUSY", "trajectory replay concurrency limit reached");
        }
        try {
            return doReplay(eventId, normalizeProjection(projection));
        } finally {
            gate.release();
        }
    }

    private Map<String, Object> doReplay(long eventId, String projection) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id,behavior_type,mmsi,target_mmsi,start_time,end_time,"
                        + "rule_version,score_detail_json FROM dataanalysis.ship_behavior_event WHERE id=?",
                eventId);
        if (rows.isEmpty()) {
            throw new ReplayException(404, "EVENT_NOT_FOUND", "event not found");
        }
        Map<String, Object> event = rows.get(0);
        JSONObject recipe = TraceRecipeService.parseRecipe(
                string(event.get("score_detail_json")));
        if (recipe == null || recipe.getIntValue("recipeVersion")
                != TraceRecipeService.RECIPE_VERSION) {
            throw new ReplayException(404, "RECIPE_UNSUPPORTED",
                    "event trace recipe is missing or unsupported");
        }
        String behaviorType = string(event.get("behavior_type"));
        validateRuleConfig(recipe, behaviorType);
        JSONArray recipeRoles = recipe.getJSONArray("roles");
        if (recipeRoles == null || recipeRoles.isEmpty()) {
            throw new ReplayException(404, "RECIPE_UNSUPPORTED", "trace roles are missing");
        }
        List<RoleTrace> traces = "ALONGSIDE".equals(behaviorType)
                ? replayAlongside(event, recipeRoles)
                : replayLoitering(event, recipe, recipeRoles.getJSONObject(0));
        int pointCount = 0;
        boolean match = true;
        List<Map<String, Object>> responseRoles = new ArrayList<Map<String, Object>>();
        for (RoleTrace trace : traces) {
            pointCount += trace.points.size();
            if (pointCount > maxPoints) {
                throw new ReplayException(422, "TRACE_POINT_LIMIT",
                        "trajectory point count exceeds " + maxPoints);
            }
            String actual = TrajectoryFingerprint.sha256(trace.role, trace.points);
            match = match && actual.equals(trace.expectedFingerprint)
                    && trace.points.size() == trace.expectedPointCount;
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("role", trace.role);
            value.put("mmsi", trace.mmsi);
            value.put("trajectorySource", trace.source);
            value.put("expectedPointCount", trace.expectedPointCount);
            value.put("actualPointCount", trace.points.size());
            value.put("expectedFingerprint", trace.expectedFingerprint);
            value.put("actualFingerprint", actual);
            List<TrajectoryPoint> displayPoints = "PREVIEW".equals(projection)
                    ? samplePoints(trace.points, maxDisplayPoints)
                    : trace.points;
            value.put("displayPointCount", displayPoints.size());
            value.put("sampled", displayPoints.size() < trace.points.size());
            value.put("points", pointMaps(displayPoints));
            responseRoles.add(value);
        }
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("eventId", eventId);
        response.put("eventKey", recipe.getString("eventKey"));
        response.put("behaviorType", behaviorType);
        response.put("recipeVersion", recipe.getIntValue("recipeVersion"));
        response.put("status", match ? "MATCH" : "SOURCE_CHANGED");
        response.put("roles", responseRoles);
        return response;
    }

    private List<RoleTrace> replayLoitering(Map<String, Object> event,
                                            JSONObject recipe,
                                            JSONObject roleRecipe) {
        validateWindow(roleRecipe);
        List<TrajectoryPoint> raw = loadHbase(roleRecipe);
        if (raw.isEmpty()) {
            throw new ReplayException(422, "NO_DATA", "no HBase trajectory data");
        }
        String engine = recipe.getString("engine");
        List<ShipBehaviorTrace> candidates;
        if ("V3".equals(engine)) {
            candidates = v3Service.analyzeV3Production(
                    new ShipLoiteringV3BatchService.TargetShipAdapter(
                            string(event.get("mmsi")), null, null, null),
                    raw, "trace-replay", roleRecipe.getIntValue("simplifyZoom"));
        } else if ("V2".equals(engine)) {
            candidates = analysisService.analysisLoiteringV2Production(
                    string(event.get("mmsi")), raw, "trace-replay");
        } else {
            throw new ReplayException(422, "RECIPE_UNSUPPORTED",
                    "unknown loitering engine");
        }
        ShipBehaviorTrace selected = select(candidates,
                string(event.get("start_time")), string(event.get("end_time")),
                string(event.get("target_mmsi")));
        List<TrajectoryPoint> points = selected == null ? raw : selected.getOwnPoints();
        return Collections.singletonList(role(roleRecipe, points));
    }

    private void validateRuleConfig(JSONObject recipe, String behaviorType) {
        String expected = recipe.getString("ruleConfigHash");
        String actual;
        if ("ALONGSIDE".equals(behaviorType)) {
            actual = analysisService.alongsideRuleConfigHash();
        } else if ("V3".equals(recipe.getString("engine"))) {
            actual = v3Service.productionRuleConfigHash();
        } else {
            actual = analysisService.loiteringV2RuleConfigHash();
        }
        if (expected == null || !expected.equals(actual)) {
            throw new ReplayException(422, "RULE_CONFIG_MISMATCH",
                    "current rule configuration does not match the event recipe");
        }
    }

    private List<RoleTrace> replayAlongside(Map<String, Object> event,
                                            JSONArray roleRecipes) {
        JSONObject ownRecipe = null;
        JSONObject targetRecipe = null;
        for (int i = 0; i < roleRecipes.size(); i++) {
            JSONObject value = roleRecipes.getJSONObject(i);
            if ("OWN".equals(value.getString("role"))) {
                ownRecipe = value;
            } else if ("TARGET".equals(value.getString("role"))) {
                targetRecipe = value;
            }
        }
        if (ownRecipe == null || targetRecipe == null) {
            throw new ReplayException(404, "RECIPE_UNSUPPORTED",
                    "alongside OWN/TARGET recipe is incomplete");
        }
        validateWindow(ownRecipe);
        validateWindow(targetRecipe);
        List<TrajectoryPoint> ownRaw = loadHbase(ownRecipe);
        List<TrajectoryPoint> targetRaw = replayTarget(targetRecipe);
        if (ownRaw.isEmpty() || targetRaw.isEmpty()) {
            throw new ReplayException(422, "NO_DATA", "alongside replay returned no data");
        }
        List<ShipBehaviorTrace> candidates = analysisService.analysisAlongsideReplay(
                string(event.get("mmsi")), string(event.get("target_mmsi")),
                ownRaw, targetRaw);
        ShipBehaviorTrace selected = select(candidates,
                string(event.get("start_time")), string(event.get("end_time")),
                string(event.get("target_mmsi")));
        List<RoleTrace> roles = new ArrayList<RoleTrace>();
        roles.add(role(ownRecipe, selected == null ? ownRaw : selected.getOwnPoints()));
        roles.add(role(targetRecipe, selected == null ? targetRaw : selected.getTargetPoints()));
        return roles;
    }

    private List<TrajectoryPoint> replayTarget(JSONObject roleRecipe) {
        JSONObject replay = roleRecipe.getJSONObject("replay");
        if (replay == null) {
            throw new ReplayException(422, "RECIPE_UNSUPPORTED",
                    "spatial replay request is missing");
        }
        LocalDateTime start = parse(replay.getString("startTime"));
        LocalDateTime end = parse(replay.getString("endTime"));
        long step = Math.max(1L, replay.getLongValue("stepMinutes"));
        Map<String, TrajectoryPoint> merged = new HashMap<String, TrajectoryPoint>();
        while (start.isBefore(end)) {
            LocalDateTime partEnd = start.plusMinutes(step);
            if (partEnd.isAfter(end)) {
                partEnd = end;
            }
            ReplayQueryRequest request = new ReplayQueryRequest();
            request.setStartTime(start.format(TIME_FORMATTER));
            request.setEndTime(partEnd.format(TIME_FORMATTER));
            request.setMinLon(replay.getDouble("minLon"));
            request.setMaxLon(replay.getDouble("maxLon"));
            request.setMinLat(replay.getDouble("minLat"));
            request.setMaxLat(replay.getDouble("maxLat"));
            request.setDevSize(replay.getIntValue("devSize"));
            ReplayQueryResult result = spatialAisReplayClient.query(request);
            if (result == null || !result.isSuccess() || result.isTruncated()) {
                throw new ReplayException(503, "QUERY_FAILED",
                        "spatial AIS replay failed or truncated");
            }
            List<TrajectoryPoint> points = result.getTracksByMmsi()
                    .get(roleRecipe.getString("mmsi"));
            if (points != null) {
                for (TrajectoryPoint point : points) {
                    merged.put(key(point), point);
                    if (maxPoints > 0 && merged.size() > maxPoints) {
                        throw new ReplayException(422, "TRACE_POINT_LIMIT",
                                "trajectory point count exceeds " + maxPoints);
                    }
                }
            }
            start = partEnd;
        }
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>(merged.values());
        Collections.sort(result, Comparator.comparing(TrajectoryPoint::getTime));
        return result;
    }

    private List<TrajectoryPoint> loadHbase(JSONObject roleRecipe) {
        return trajectoryLoaderService.loadSafely(roleRecipe.getString("mmsi"),
                roleRecipe.getString("queryStartTime"),
                roleRecipe.getString("queryEndTime"),
                roleRecipe.getIntValue("simplifyZoom"), 6, maxPoints);
    }

    private void validateWindow(JSONObject roleRecipe) {
        LocalDateTime start = parse(roleRecipe.getString("queryStartTime"));
        LocalDateTime end = parse(roleRecipe.getString("queryEndTime"));
        Duration duration = Duration.between(start, end);
        if (!start.isBefore(end)
                || duration.compareTo(Duration.ofDays(maxDays)) > 0) {
            throw new ReplayException(422, "TRACE_WINDOW_LIMIT",
                    "trajectory query window exceeds " + maxDays + " days");
        }
    }

    private ShipBehaviorTrace select(List<ShipBehaviorTrace> traces, String start,
                                     String end, String targetMmsi) {
        for (ShipBehaviorTrace trace : traces) {
            if (trace != null && trace.getEvent() != null
                    && start.equals(trace.getEvent().getStartTime())
                    && end.equals(trace.getEvent().getEndTime())
                    && (targetMmsi == null
                    || targetMmsi.equals(trace.getEvent().getTargetMmsi()))) {
                return trace;
            }
        }
        return null;
    }

    private RoleTrace role(JSONObject recipe, List<TrajectoryPoint> points) {
        return new RoleTrace(recipe.getString("role"), recipe.getString("mmsi"),
                recipe.getString("trajectorySource"), recipe.getIntValue("pointCount"),
                recipe.getString("fingerprint"), points);
    }

    private List<Map<String, Object>> pointMaps(List<TrajectoryPoint> points) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < points.size(); i++) {
            TrajectoryPoint point = points.get(i);
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("index", i);
            value.put("time", point.getTime().format(TIME_FORMATTER));
            value.put("lon", point.getLon());
            value.put("lat", point.getLat());
            value.put("speed", point.getSpeed());
            value.put("course", point.getCourse());
            value.put("navStatus", point.getNavStatus());
            result.add(value);
        }
        return result;
    }

    private List<TrajectoryPoint> samplePoints(List<TrajectoryPoint> points, int limit) {
        if (points == null || points.isEmpty() || limit <= 0 || points.size() <= limit) {
            return points == null ? Collections.<TrajectoryPoint>emptyList() : points;
        }
        int sampleSize = Math.min(limit, points.size());
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>(sampleSize);
        if (sampleSize == 1) {
            result.add(points.get(0));
            return result;
        }
        for (int index = 0; index < sampleSize; index++) {
            int sourceIndex = (int) (((long) index * (points.size() - 1)) / (sampleSize - 1));
            result.add(points.get(sourceIndex));
        }
        return result;
    }

    private String normalizeProjection(String projection) {
        String value = projection == null ? "FULL" : projection.trim().toUpperCase();
        if (!"FULL".equals(value) && !"PREVIEW".equals(value)) {
            throw new ReplayException(400, "INVALID_PROJECTION",
                    "projection must be FULL or PREVIEW");
        }
        return value;
    }

    private synchronized Semaphore gate() {
        if (semaphore == null) {
            semaphore = new Semaphore(Math.max(1, concurrentRequests));
        }
        return semaphore;
    }

    private LocalDateTime parse(String value) {
        try {
            return LocalDateTime.parse(value, TIME_FORMATTER);
        } catch (Exception ex) {
            throw new ReplayException(422, "RECIPE_UNSUPPORTED",
                    "invalid recipe time");
        }
    }

    private String key(TrajectoryPoint point) {
        return point.getMmsi() + "|" + point.getTime() + "|"
                + point.getLon() + "|" + point.getLat();
    }

    private String string(Object value) {
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime().format(TIME_FORMATTER);
        }
        return value == null ? null : String.valueOf(value);
    }

    private static class RoleTrace {
        private final String role;
        private final String mmsi;
        private final String source;
        private final int expectedPointCount;
        private final String expectedFingerprint;
        private final List<TrajectoryPoint> points;

        private RoleTrace(String role, String mmsi, String source,
                          int expectedPointCount, String expectedFingerprint,
                          List<TrajectoryPoint> points) {
            this.role = role;
            this.mmsi = mmsi;
            this.source = source;
            this.expectedPointCount = expectedPointCount;
            this.expectedFingerprint = expectedFingerprint;
            this.points = points;
        }
    }

    public static class ReplayException extends RuntimeException {
        private final int httpStatus;
        private final String code;

        public ReplayException(int httpStatus, String code, String message) {
            super(message);
            this.httpStatus = httpStatus;
            this.code = code;
        }

        public int getHttpStatus() { return httpStatus; }
        public String getCode() { return code; }
    }
}
