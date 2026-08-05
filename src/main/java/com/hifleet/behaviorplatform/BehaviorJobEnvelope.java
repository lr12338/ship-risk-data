package com.hifleet.behaviorplatform;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class BehaviorJobEnvelope {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> ALLOWED = new HashSet<String>(Arrays.asList(
            "profile", "profileId", "list", "listBatchNo", "from", "startTime", "to", "endTime",
            "startSeq", "limit", "shipLimit", "pageSize", "parallelism", "resume", "dryRun",
            "batch", "batchNo", "sceneModeOverride", "writeModeOverride"));

    private String profileId;
    private String listBatchNo;
    private String startTime;
    private String endTime;
    private int startSeq;
    private int shipLimit;
    private int pageSize;
    private int parallelism;
    private boolean resume;
    private boolean dryRun;
    private String batchNo;
    private SceneMode sceneModeOverride;
    private WriteMode writeModeOverride;

    public static BehaviorJobEnvelope parse(String raw) {
        JSONObject json;
        try {
            json = JSON.parseObject(raw);
        } catch (Exception ex) {
            throw new IllegalArgumentException("behavior job params must be complete JSON", ex);
        }
        if (json == null) throw new IllegalArgumentException("behavior job params are required");
        for (String key : json.keySet()) {
            if (!ALLOWED.contains(key)) throw new IllegalArgumentException("unknown behavior job field: " + key);
        }
        BehaviorJobEnvelope result = new BehaviorJobEnvelope();
        result.profileId = required(alias(json, "profile", "profileId"), "profile");
        result.listBatchNo = required(alias(json, "list", "listBatchNo"), "list");
        result.startTime = time(required(alias(json, "from", "startTime"), "from"));
        result.endTime = time(required(alias(json, "to", "endTime"), "to"));
        result.batchNo = required(alias(json, "batch", "batchNo"), "batch");
        result.startSeq = integer(json, "startSeq", 0);
        result.shipLimit = integerAlias(json, "limit", "shipLimit", 100);
        result.pageSize = integer(json, "pageSize", 100);
        result.parallelism = integer(json, "parallelism", 1);
        result.resume = !json.containsKey("resume") || json.getBooleanValue("resume");
        result.dryRun = json.containsKey("dryRun") && json.getBooleanValue("dryRun");
        result.sceneModeOverride = enumValue(SceneMode.class, json.getString("sceneModeOverride"));
        result.writeModeOverride = enumValue(WriteMode.class, json.getString("writeModeOverride"));
        result.validate();
        return result;
    }

    private void validate() {
        if (profileId.length() > 64 || listBatchNo.length() > 128 || batchNo.length() > 128)
            throw new IllegalArgumentException("profile/list/batch identifier is too long");
        if (startSeq < 0 || shipLimit <= 0 || pageSize <= 0 || pageSize > 1000
                || parallelism <= 0 || parallelism > 4)
            throw new IllegalArgumentException("invalid paging or parallelism");
        if (!LocalDateTime.parse(startTime, TIME).isBefore(LocalDateTime.parse(endTime, TIME)))
            throw new IllegalArgumentException("from must be before to");
    }

    private static String alias(JSONObject json, String shortName, String longName) {
        if (json.containsKey(shortName) && json.containsKey(longName))
            throw new IllegalArgumentException("use only one of " + shortName + " and " + longName);
        return json.containsKey(shortName) ? json.getString(shortName) : json.getString(longName);
    }

    private static int integerAlias(JSONObject json, String shortName, String longName, int fallback) {
        if (json.containsKey(shortName) && json.containsKey(longName))
            throw new IllegalArgumentException("use only one of " + shortName + " and " + longName);
        return json.containsKey(shortName) ? json.getIntValue(shortName)
                : json.containsKey(longName) ? json.getIntValue(longName) : fallback;
    }

    private static int integer(JSONObject json, String key, int fallback) {
        return json.containsKey(key) ? json.getIntValue(key) : fallback;
    }

    private static String required(String value, String name) {
        value = StringUtils.trimToNull(value);
        if (value == null) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static String time(String value) {
        if (value.length() == 10) value += " 00:00:00";
        try { return LocalDateTime.parse(value, TIME).format(TIME); }
        catch (Exception ex) { throw new IllegalArgumentException("invalid time: " + value, ex); }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return StringUtils.isBlank(value) ? null : Enum.valueOf(type, value.trim().toUpperCase());
    }

    public int utf8Length(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }
    public String getProfileId() { return profileId; }
    public String getListBatchNo() { return listBatchNo; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public int getStartSeq() { return startSeq; }
    public int getShipLimit() { return shipLimit; }
    public int getPageSize() { return pageSize; }
    public int getParallelism() { return parallelism; }
    public boolean isResume() { return resume; }
    public boolean isDryRun() { return dryRun; }
    public String getBatchNo() { return batchNo; }
    public SceneMode getSceneModeOverride() { return sceneModeOverride; }
    public WriteMode getWriteModeOverride() { return writeModeOverride; }
}
