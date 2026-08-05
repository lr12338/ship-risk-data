package com.hifleet.behaviorplatform;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public final class BehaviorJobEnvelope {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> ALLOWED = new HashSet<String>(Arrays.asList(
            "profile", "profileId", "list", "listBatchNo", "from", "startTime", "to", "endTime",
            "startSeq", "limit", "shipLimit", "pageSize", "parallelism", "resume", "dryRun",
            "batch", "batchNo", "sceneModeOverride", "writeModeOverride", "source", "ships",
            "region", "bbox"));

    private BehaviorTargetSourceType sourceType;
    private List<String> ships = Collections.emptyList();
    private String profileId;
    private String listBatchNo;
    private String regionId;
    private double[] bbox;
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
        return parse(raw, 10, 5000, 420);
    }

    public static BehaviorJobEnvelope parse(String raw, int maxInlineShips, int maxListShips,
                                            int maxParamBytes) {
        if (raw == null || raw.getBytes(StandardCharsets.UTF_8).length > maxParamBytes)
            throw new IllegalArgumentException("behavior job params exceed max UTF-8 bytes=" + maxParamBytes);
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
        result.sourceType = source(json);
        result.listBatchNo = trim(alias(json, "list", "listBatchNo"));
        result.regionId = trim(json.getString("region"));
        result.ships = parseShips(json.getString("ships"), maxInlineShips);
        result.bbox = parseBbox(json.getString("bbox"));
        result.startTime = time(required(alias(json, "from", "startTime"), "from"));
        result.endTime = time(required(alias(json, "to", "endTime"), "to"));
        result.batchNo = required(alias(json, "batch", "batchNo"), "batch");
        result.startSeq = integer(json, "startSeq", 0);
        result.shipLimit = integerAlias(json, "limit", "shipLimit",
                result.sourceType == BehaviorTargetSourceType.SHIPS ? result.ships.size() : 100);
        result.pageSize = integer(json, "pageSize", 100);
        result.parallelism = integer(json, "parallelism", 1);
        result.resume = !json.containsKey("resume") || json.getBooleanValue("resume");
        result.dryRun = json.containsKey("dryRun") && json.getBooleanValue("dryRun");
        result.sceneModeOverride = enumValue(SceneMode.class, json.getString("sceneModeOverride"));
        result.writeModeOverride = enumValue(WriteMode.class, json.getString("writeModeOverride"));
        result.validate(json, maxListShips);
        return result;
    }

    private void validate(JSONObject json, int maxListShips) {
        if (profileId.length() > 64 || batchNo.length() > 128)
            throw new IllegalArgumentException("profile/list/batch identifier is too long");
        if (startSeq < 0 || shipLimit <= 0 || pageSize <= 0 || pageSize > 1000
                || parallelism <= 0 || parallelism > 4)
            throw new IllegalArgumentException("invalid paging or parallelism");
        if (sourceType == BehaviorTargetSourceType.SHIPS) {
            requireOnly(ships.size() > 0, listBatchNo == null && regionId == null && bbox == null,
                    "SHIPS requires ships and forbids LIST/REGION fields");
            if (shipLimit != ships.size())
                throw new IllegalArgumentException("SHIPS limit is the deduplicated ship count");
            rejectPresent(json, "startSeq", "pageSize");
        } else if (sourceType == BehaviorTargetSourceType.LIST) {
            requireOnly(listBatchNo != null, ships.isEmpty() && regionId == null && bbox == null,
                    "LIST requires list and forbids SHIPS/REGION fields");
            if (listBatchNo.length() > 128 || shipLimit > maxListShips)
                throw new IllegalArgumentException("LIST identifier or limit exceeds server boundary");
        } else {
            requireOnly((regionId == null) != (bbox == null), ships.isEmpty() && listBatchNo == null,
                    "REGION requires exactly one of region/bbox and forbids SHIPS/LIST fields");
            rejectPresent(json, "startSeq", "pageSize");
        }
        if (!LocalDateTime.parse(startTime, TIME).isBefore(LocalDateTime.parse(endTime, TIME)))
            throw new IllegalArgumentException("from must be before to");
    }

    private static BehaviorTargetSourceType source(JSONObject json) {
        String value = trim(json.getString("source"));
        if (value == null) {
            if (json.containsKey("list") || json.containsKey("listBatchNo")) return BehaviorTargetSourceType.LIST;
            throw new IllegalArgumentException("source is required unless legacy list is supplied");
        }
        try { return BehaviorTargetSourceType.valueOf(value.toUpperCase()); }
        catch (Exception ex) { throw new IllegalArgumentException("unsupported behavior source: " + value); }
    }

    private static List<String> parseShips(String value, int max) {
        value = trim(value);
        if (value == null) return Collections.emptyList();
        LinkedHashSet<String> ordered = new LinkedHashSet<String>();
        for (String item : value.split(",", -1)) {
            String mmsi = trim(item);
            if (mmsi == null || !mmsi.matches("[0-9]{9}"))
                throw new IllegalArgumentException("each MMSI must be exactly 9 digits");
            ordered.add(mmsi);
        }
        if (ordered.isEmpty() || ordered.size() > max)
            throw new IllegalArgumentException("SHIPS count must be 1.." + max);
        return Collections.unmodifiableList(new ArrayList<String>(ordered));
    }

    private static double[] parseBbox(String value) {
        value = trim(value);
        if (value == null) return null;
        String[] parts = value.split(",", -1);
        if (parts.length != 4) throw new IllegalArgumentException("bbox must contain four coordinates");
        double[] result = new double[4];
        try { for (int i = 0; i < parts.length; i++) result[i] = Double.parseDouble(parts[i].trim()); }
        catch (Exception ex) { throw new IllegalArgumentException("bbox coordinates must be numbers", ex); }
        if (!Double.isFinite(result[0]) || !Double.isFinite(result[1])
                || !Double.isFinite(result[2]) || !Double.isFinite(result[3])
                || result[0] < -180 || result[2] > 180 || result[1] < -90 || result[3] > 90
                || result[0] >= result[2] || result[1] >= result[3]
                || result[2] - result[0] > 180)
            throw new IllegalArgumentException("bbox longitude/latitude order is invalid or crosses date line");
        return result;
    }

    private static void requireOnly(boolean required, boolean exclusive, String message) {
        if (!required || !exclusive) throw new IllegalArgumentException(message);
    }

    private static void rejectPresent(JSONObject json, String... names) {
        for (String name : names) if (json.containsKey(name))
            throw new IllegalArgumentException(name + " is not valid for source=" + json.getString("source"));
    }

    private static String trim(String value) { return StringUtils.trimToNull(value); }

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
    public BehaviorTargetSourceType getSourceType() { return sourceType; }
    public List<String> getShips() { return ships; }
    public String getProfileId() { return profileId; }
    public String getListBatchNo() { return listBatchNo; }
    public String getRegionId() { return regionId; }
    public double[] getBbox() { return bbox == null ? null : bbox.clone(); }
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
