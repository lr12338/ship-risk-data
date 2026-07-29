package com.hifleet.job.services;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProductionTestParams {

    public static final String EVENT_FILTER_VALID_NON_WEAK = "VALID_NON_WEAK";
    private static final String FIXED_START = "2026-01-01 00:00:00";
    private static final String FIXED_END = "2026-07-01 00:00:00";
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String listBatchNo;
    private int expectedShipCount;
    private int shipLimit;
    private int pageSize;
    private int parallelism;
    private String startTime;
    private String endTime;
    private boolean resume;
    private int retryCount;
    private int logIntervalShips;
    private String batchNo;
    private boolean enableInsert;
    private boolean storeDetails;
    private int windowDays;
    private int trajectorySliceHours;
    private String eventFilterMode;
    private final List<String> engines = new ArrayList<String>();

    public static ProductionTestParams parse(String raw) {
        JSONObject json;
        try {
            json = JSON.parseObject(raw);
        } catch (Exception ex) {
            throw new IllegalArgumentException("production params must be a JSON object", ex);
        }
        if (json == null || !"production_test".equals(json.getString("mode"))) {
            throw new IllegalArgumentException("mode must be production_test");
        }
        ProductionTestParams params = new ProductionTestParams();
        params.listBatchNo = required(json, "listBatchNo");
        params.expectedShipCount = positive(json, "expectedShipCount", 50000);
        params.shipLimit = positive(json, "shipLimit", params.expectedShipCount);
        params.pageSize = positive(json, "pageSize", 200);
        params.parallelism = positive(json, "parallelism", 1);
        params.startTime = required(json, "startTime");
        params.endTime = required(json, "endTime");
        params.resume = !json.containsKey("resume") || json.getBooleanValue("resume");
        params.retryCount = nonNegative(json, "retryCount", 2);
        params.logIntervalShips = positive(json, "logIntervalShips", 50);
        params.batchNo = required(json, "batchNo");
        params.enableInsert = json.containsKey("enableInsert")
                && json.getBooleanValue("enableInsert");
        params.storeDetails = json.containsKey("storeDetails")
                && json.getBooleanValue("storeDetails");
        params.windowDays = positive(json, "windowDays", 30);
        params.trajectorySliceHours = positive(json, "trajectorySliceHours", 6);
        params.eventFilterMode = StringUtils.trimToNull(json.getString("eventFilterMode"));
        if (params.eventFilterMode != null) {
            params.eventFilterMode = params.eventFilterMode.toUpperCase();
        }
        if (json.getJSONArray("engines") == null) {
            params.engines.add("V2");
            params.engines.add("V3");
        } else {
            for (Object value : json.getJSONArray("engines")) {
                String engine = value == null ? null : String.valueOf(value).toUpperCase();
                if (!"V2".equals(engine) && !"V3".equals(engine)) {
                    throw new IllegalArgumentException("engines only supports V2 and V3");
                }
                if (!params.engines.contains(engine)) {
                    params.engines.add(engine);
                }
            }
        }
        params.validate();
        return params;
    }

    private void validate() {
        if (shipLimit > expectedShipCount) {
            throw new IllegalArgumentException("shipLimit must not exceed expectedShipCount");
        }
        if (pageSize > 1000 || parallelism > 2) {
            throw new IllegalArgumentException("pageSize must be <=1000 and parallelism must be <=2");
        }
        if (listBatchNo.length() > 64 || batchNo.length() > 64) {
            throw new IllegalArgumentException("batch identifiers must not exceed 64 characters");
        }
        if (engines.isEmpty()) {
            throw new IllegalArgumentException("engines must not be empty");
        }
        LocalDateTime start = LocalDateTime.parse(startTime, TIME_FORMATTER);
        LocalDateTime end = LocalDateTime.parse(endTime, TIME_FORMATTER);
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
    }

    public void validateAlongside() {
        validateFixedRange();
        if (windowDays != 30) {
            throw new IllegalArgumentException("alongside windowDays must be 30");
        }
        if (storeDetails) {
            throw new IllegalArgumentException("alongside production test is event-only");
        }
    }

    public void validateLoitering() {
        validateFixedRange();
        if (trajectorySliceHours != 6) {
            throw new IllegalArgumentException("loitering trajectorySliceHours must be 6");
        }
        if (!EVENT_FILTER_VALID_NON_WEAK.equals(eventFilterMode)) {
            throw new IllegalArgumentException(
                    "loitering eventFilterMode must be VALID_NON_WEAK");
        }
        if (storeDetails) {
            throw new IllegalArgumentException("loitering production test is event-only");
        }
    }

    private void validateFixedRange() {
        if (!FIXED_START.equals(startTime) || !FIXED_END.equals(endTime)) {
            throw new IllegalArgumentException(
                    "production test range must be [2026-01-01 00:00:00, 2026-07-01 00:00:00)");
        }
    }

    private static String required(JSONObject json, String key) {
        String value = StringUtils.trimToNull(json.getString(key));
        if (value == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private static int positive(JSONObject json, String key, int defaultValue) {
        int value = json.containsKey(key) ? json.getIntValue(key) : defaultValue;
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be positive");
        }
        return value;
    }

    private static int nonNegative(JSONObject json, String key, int defaultValue) {
        int value = json.containsKey(key) ? json.getIntValue(key) : defaultValue;
        if (value < 0) {
            throw new IllegalArgumentException(key + " must not be negative");
        }
        return value;
    }

    public String getListBatchNo() { return listBatchNo; }
    public int getExpectedShipCount() { return expectedShipCount; }
    public int getShipLimit() { return shipLimit; }
    public int getPageSize() { return pageSize; }
    public int getParallelism() { return parallelism; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public boolean isResume() { return resume; }
    public int getRetryCount() { return retryCount; }
    public int getLogIntervalShips() { return logIntervalShips; }
    public String getBatchNo() { return batchNo; }
    public boolean isEnableInsert() { return enableInsert; }
    public boolean isStoreDetails() { return storeDetails; }
    public int getWindowDays() { return windowDays; }
    public int getTrajectorySliceHours() { return trajectorySliceHours; }
    public String getEventFilterMode() { return eventFilterMode; }
    public List<String> getEngines() { return Collections.unmodifiableList(engines); }
}
