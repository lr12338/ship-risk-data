package com.hifleet.maritime;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MaritimeSceneShadowAuditParams {
    private static final String SHADOW_V2_POLICY = "MARITIME_SCENE_SHADOW_V2";
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_SHIP_LIMIT = 5000;

    private String listBatchNo;
    private int startSeq;
    private int shipLimit;
    private String startTime;
    private String endTime;
    private String batchNo;
    private String sceneSnapshotVersion;
    private String expectedSceneFingerprint;
    private String scenePolicyVersion;
    private List<String> behaviorTypes;
    private boolean dryRun;
    private boolean resume;

    public static MaritimeSceneShadowAuditParams parse(String raw) {
        JSONObject json;
        try {
            json = JSON.parseObject(raw);
        } catch (Exception ex) {
            throw new IllegalArgumentException("params must be a JSON object", ex);
        }
        if (json == null) throw new IllegalArgumentException("params must be a JSON object");
        MaritimeSceneShadowAuditParams params = new MaritimeSceneShadowAuditParams();
        params.listBatchNo = required(json, "listBatchNo");
        params.startSeq = positive(json, "startSeq", 1);
        params.shipLimit = positive(json, "shipLimit", 100);
        params.startTime = required(json, "startTime");
        params.endTime = required(json, "endTime");
        params.batchNo = required(json, "batchNo");
        params.sceneSnapshotVersion = required(json, "sceneSnapshotVersion");
        params.expectedSceneFingerprint = required(json, "expectedSceneFingerprint");
        params.scenePolicyVersion = required(json, "scenePolicyVersion");
        params.behaviorTypes = behaviors(json.getJSONArray("behaviorTypes"));
        params.dryRun = !json.containsKey("dryRun") || json.getBooleanValue("dryRun");
        params.resume = !json.containsKey("resume") || json.getBooleanValue("resume");
        params.validate();
        return params;
    }

    private void validate() {
        if (!listBatchNo.equals("liquid-container-5000-2025h2-2026h1-v1")) {
            throw new IllegalArgumentException("listBatchNo must be the liquid-container-5000 target list");
        }
        if (shipLimit > MAX_SHIP_LIMIT) {
            throw new IllegalArgumentException("shipLimit must be <= " + MAX_SHIP_LIMIT);
        }
        if (!batchNo.startsWith("maritime-scene-shadow-")) {
            throw new IllegalArgumentException("batchNo must use the maritime-scene-shadow prefix");
        }
        if (!expectedSceneFingerprint.matches("^[0-9a-fA-F]{64}$")) {
            throw new IllegalArgumentException("expectedSceneFingerprint must be a SHA-256");
        }
        if (!MaritimeScenePolicy.VERSION.equals(scenePolicyVersion)
                && !SHADOW_V2_POLICY.equals(scenePolicyVersion)) {
            throw new IllegalArgumentException("unsupported scenePolicyVersion: " + scenePolicyVersion);
        }
        LocalDateTime start = parseTime(startTime);
        LocalDateTime end = parseTime(endTime);
        if (!start.isBefore(end) || Duration.between(start, end).toDays() > 366L) {
            throw new IllegalArgumentException("time range must be positive and <=366 days");
        }
    }

    private static List<String> behaviors(JSONArray array) {
        if (array == null || array.isEmpty()) {
            List<String> defaults = new ArrayList<String>();
            defaults.add("LOITERING");
            defaults.add("ALONGSIDE");
            return Collections.unmodifiableList(defaults);
        }
        List<String> result = new ArrayList<String>();
        for (Object value : array) {
            String behavior = String.valueOf(value).toUpperCase();
            if (!"LOITERING".equals(behavior) && !"ALONGSIDE".equals(behavior)) {
                throw new IllegalArgumentException("behaviorTypes only supports LOITERING and ALONGSIDE");
            }
            if (!result.contains(behavior)) result.add(behavior);
        }
        return Collections.unmodifiableList(result);
    }

    private static LocalDateTime parseTime(String value) {
        try {
            return LocalDateTime.parse(value, FORMATTER);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid time: " + value, ex);
        }
    }

    private static String required(JSONObject json, String key) {
        String value = StringUtils.trimToNull(json.getString(key));
        if (value == null) throw new IllegalArgumentException(key + " is required");
        return value;
    }

    private static int positive(JSONObject json, String key, int defaultValue) {
        int value = json.containsKey(key) ? json.getIntValue(key) : defaultValue;
        if (value <= 0) throw new IllegalArgumentException(key + " must be positive");
        return value;
    }

    public String getListBatchNo() { return listBatchNo; }
    public int getStartSeq() { return startSeq; }
    public int getShipLimit() { return shipLimit; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public String getBatchNo() { return batchNo; }
    public String getSceneSnapshotVersion() { return sceneSnapshotVersion; }
    public String getExpectedSceneFingerprint() { return expectedSceneFingerprint; }
    public String getScenePolicyVersion() { return scenePolicyVersion; }
    public List<String> getBehaviorTypes() { return behaviorTypes; }
    public boolean isDryRun() { return dryRun; }
    public boolean isResume() { return resume; }
}
