package com.hifleet.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.AlongsideCandidateTarget;
import com.hifleet.domain.ReplayQueryRequest;
import com.hifleet.domain.ReplayQueryResult;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.trajectory.TrajectoryPoint;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class TraceRecipeService {

    public static final int RECIPE_VERSION = 1;
    public static final String SOURCE_HBASE = "HBASE_HISTORY";
    public static final String SOURCE_SPATIAL_REPLAY = "SPATIAL_AIS_REPLAY";
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TraceRecipeService() {
    }

    public static void attachLoitering(ShipBehaviorTrace trace, String engine,
                                       String queryStartTime, String queryEndTime,
                                       int simplifyZoom, String ruleConfigHash) {
        if (trace == null || trace.getEvent() == null) {
            return;
        }
        ShipBehaviorEvent event = trace.getEvent();
        JSONObject recipe = baseRecipe(event, ruleConfigHash);
        recipe.put("engine", engine);
        JSONObject episode = new JSONObject(true);
        episode.put("startTime", event.getStartTime());
        episode.put("endTime", event.getEndTime());
        episode.put("eventSubType", event.getEventSubType());
        episode.put("pointCount", trace.getOwnPoints() == null
                ? 0 : trace.getOwnPoints().size());
        recipe.put("episodeSelection", episode);
        JSONArray roles = new JSONArray();
        roles.add(role("OWN", event.getMmsi(), SOURCE_HBASE, queryStartTime,
                queryEndTime, simplifyZoom, trace.getOwnPoints(), null));
        recipe.put("roles", roles);
        mergeRecipe(event, recipe);
    }

    public static void attachAlongside(ShipBehaviorTrace trace,
                                       AlongsideCandidateTarget candidate,
                                       int simplifyZoom, String ruleConfigHash) {
        if (trace == null || trace.getEvent() == null || candidate == null) {
            return;
        }
        ShipBehaviorEvent event = trace.getEvent();
        JSONObject recipe = baseRecipe(event, ruleConfigHash);
        recipe.put("candidateWindowStart", candidate.getWindowStart());
        recipe.put("candidateWindowEnd", candidate.getWindowEnd());
        JSONArray roles = new JSONArray();
        roles.add(role("OWN", event.getMmsi(), SOURCE_HBASE,
                candidate.getWindowStart(), candidate.getWindowEnd(),
                simplifyZoom, trace.getOwnPoints(), null));

        JSONObject replay = replayRecipe(candidate.getReplayQueryResult(),
                candidate.getWindowStart(), candidate.getWindowEnd());
        roles.add(role("TARGET", event.getTargetMmsi(), SOURCE_SPATIAL_REPLAY,
                candidate.getWindowStart(), candidate.getWindowEnd(),
                0, trace.getTargetPoints(), replay));
        recipe.put("roles", roles);
        mergeRecipe(event, recipe);
    }

    public static JSONObject parseRecipe(String scoreDetailJson) {
        if (StringUtils.isBlank(scoreDetailJson)) {
            return null;
        }
        JSONObject root = JSON.parseObject(scoreDetailJson);
        return root == null ? null : root.getJSONObject("traceRecipe");
    }

    public static String configHash(Object config) {
        return sha256(config == null ? "" : JSON.toJSONString(config));
    }

    private static JSONObject baseRecipe(ShipBehaviorEvent event,
                                         String ruleConfigHash) {
        JSONObject recipe = new JSONObject(true);
        recipe.put("recipeVersion", RECIPE_VERSION);
        recipe.put("eventKey", eventKey(event));
        recipe.put("behaviorType", event.getBehaviorType());
        recipe.put("ruleVersion", event.getRuleVersion());
        recipe.put("ruleConfigHash", ruleConfigHash);
        Package typePackage = TraceRecipeService.class.getPackage();
        recipe.put("buildVersion", typePackage == null
                || typePackage.getImplementationVersion() == null
                ? "DEV" : typePackage.getImplementationVersion());
        return recipe;
    }

    private static JSONObject role(String role, String mmsi, String source,
                                   String queryStartTime, String queryEndTime,
                                   int simplifyZoom, List<TrajectoryPoint> points,
                                   JSONObject replay) {
        JSONObject value = new JSONObject(true);
        value.put("role", role);
        value.put("mmsi", mmsi);
        value.put("trajectorySource", source);
        value.put("queryStartTime", queryStartTime);
        value.put("queryEndTime", queryEndTime);
        value.put("simplifyZoom", simplifyZoom);
        value.put("pointCount", points == null ? 0 : points.size());
        value.put("fingerprint", TrajectoryFingerprint.sha256(role, points));
        if (replay != null) {
            value.put("replay", replay);
        }
        return value;
    }

    private static JSONObject replayRecipe(ReplayQueryResult result,
                                           String windowStart, String windowEnd) {
        JSONObject replay = new JSONObject(true);
        ReplayQueryRequest request = result == null ? null : result.getRequest();
        if (request != null) {
            replay.put("minLon", request.getMinLon());
            replay.put("maxLon", request.getMaxLon());
            replay.put("minLat", request.getMinLat());
            replay.put("maxLat", request.getMaxLat());
            replay.put("devSize", request.getDevSize());
            try {
                long minutes = Duration.between(
                        LocalDateTime.parse(request.getStartTime(), TIME_FORMATTER),
                        LocalDateTime.parse(request.getEndTime(), TIME_FORMATTER)).toMinutes();
                replay.put("stepMinutes", Math.max(1L, minutes));
            } catch (Exception ignored) {
                replay.put("stepMinutes", 30L);
            }
        }
        replay.put("startTime", windowStart);
        replay.put("endTime", windowEnd);
        return replay;
    }

    private static void mergeRecipe(ShipBehaviorEvent event, JSONObject recipe) {
        JSONObject root;
        try {
            root = StringUtils.isBlank(event.getScoreDetailJson())
                    ? new JSONObject(true) : JSON.parseObject(event.getScoreDetailJson());
        } catch (Exception ex) {
            root = new JSONObject(true);
        }
        root.put("traceRecipe", recipe);
        event.setScoreDetailJson(root.toJSONString());
    }

    private static String eventKey(ShipBehaviorEvent event) {
        return sha256(value(event.getBehaviorType()) + "|" + value(event.getMmsi())
                + "|" + value(event.getTargetMmsi()) + "|" + value(event.getRuleVersion())
                + "|" + value(event.getStartTime()) + "|" + value(event.getEndTime()))
                .substring(0, 32);
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
