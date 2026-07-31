package com.hifleet.tankeropensea;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TankerOpenSeaJobParams {
    public enum Behavior { ALONGSIDE, LOITERING }

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_SHIP_LIMIT = 200;

    private String listBatchNo;
    private int shipLimit;
    private String startTime;
    private String endTime;
    private int windowDays;
    private int windowHours;
    private boolean enableInsert;
    private boolean storeDetails;
    private boolean resume;
    private int parallelism;
    private int retryCount;
    private int logIntervalShips;
    private String ruleVersion;
    private String expectedExclusionFingerprint;
    private String batchNo;

    public static TankerOpenSeaJobParams parse(String raw, Behavior behavior) {
        JSONObject json;
        try {
            json = JSON.parseObject(raw);
        } catch (Exception ex) {
            throw new IllegalArgumentException("params must be a JSON object", ex);
        }
        if (json == null || !"liquid_container_exclusion_test".equals(json.getString("mode"))) {
            throw new IllegalArgumentException("mode must be liquid_container_exclusion_test");
        }
        TankerOpenSeaJobParams params = new TankerOpenSeaJobParams();
        params.listBatchNo = required(json, "listBatchNo");
        params.shipLimit = positive(json, "shipLimit", 0);
        params.startTime = required(json, "startTime");
        params.endTime = required(json, "endTime");
        params.windowDays = positive(json, "windowDays", 7);
        params.windowHours = positive(json, "windowHours", 24);
        params.enableInsert = json.getBooleanValue("enableInsert");
        params.storeDetails = json.getBooleanValue("storeDetails");
        params.resume = !json.containsKey("resume") || json.getBooleanValue("resume");
        params.parallelism = positive(json, "parallelism", 1);
        params.retryCount = nonNegative(json, "retryCount", 1);
        params.logIntervalShips = positive(json, "logIntervalShips", 10);
        params.ruleVersion = required(json, "ruleVersion");
        params.expectedExclusionFingerprint =
                StringUtils.trimToNull(json.getString("expectedExclusionFingerprint"));
        params.batchNo = required(json, "batchNo");
        params.validate(behavior);
        return params;
    }

    private void validate(Behavior behavior) {
        if (!listBatchNo.startsWith("liquid-container-")) {
            throw new IllegalArgumentException("dedicated liquid-container listBatchNo is required");
        }
        if (shipLimit > MAX_SHIP_LIMIT) {
            throw new IllegalArgumentException("shipLimit must be <= " + MAX_SHIP_LIMIT);
        }
        if (parallelism != 1) {
            throw new IllegalArgumentException("parallelism must be 1");
        }
        if (retryCount > 3) {
            throw new IllegalArgumentException("retryCount must be <=3");
        }
        if (listBatchNo.length() > 64 || batchNo.length() > 64) {
            throw new IllegalArgumentException("identifier length must be <=64");
        }
        if (enableInsert && (expectedExclusionFingerprint == null
                || !expectedExclusionFingerprint.matches("^[0-9a-fA-F]{64}$"))) {
            throw new IllegalArgumentException(
                    "expectedExclusionFingerprint must be a SHA-256 for insert");
        }
        if (expectedExclusionFingerprint != null
                && !expectedExclusionFingerprint.matches("^[0-9a-fA-F]{64}$")) {
            throw new IllegalArgumentException(
                    "expectedExclusionFingerprint must be a SHA-256");
        }
        LocalDateTime start = parse(startTime);
        LocalDateTime end = parse(endTime);
        if (!start.isBefore(end) || Duration.between(start, end).toDays() > 31L) {
            throw new IllegalArgumentException("time range must be positive and <=31 days");
        }
        String expectedRule = behavior == Behavior.ALONGSIDE
                ? TankerOpenSeaAlongsideRule.RULE_VERSION
                : TankerOpenSeaLoiteringRule.RULE_VERSION;
        String expectedBatchPrefix = behavior == Behavior.ALONGSIDE
                ? "alongside-liquid-container-" : "loitering-liquid-container-";
        String expectedRangeBatchPrefix = behavior == Behavior.ALONGSIDE
                ? "alongside-lc-" : "loitering-lc-";
        if (!expectedRule.equals(ruleVersion)) {
            throw new IllegalArgumentException("ruleVersion must be " + expectedRule);
        }
        if (!batchNo.startsWith(expectedBatchPrefix)
                && !batchNo.startsWith(expectedRangeBatchPrefix)) {
            throw new IllegalArgumentException("dedicated batchNo prefix is required");
        }
        boolean dryRunBatch = batchNo.toLowerCase().contains("dryrun");
        if (enableInsert && dryRunBatch) {
            throw new IllegalArgumentException("dryrun batch must not enable insert");
        }
        if (!enableInsert && !dryRunBatch) {
            throw new IllegalArgumentException("write-disabled run must use a dryrun batch");
        }
        if (storeDetails && !enableInsert) {
            throw new IllegalArgumentException("storeDetails requires enableInsert=true");
        }
        if (behavior == Behavior.ALONGSIDE && windowDays > 7) {
            throw new IllegalArgumentException("alongside windowDays must be <=7");
        }
        if (behavior == Behavior.LOITERING && windowHours > 24) {
            throw new IllegalArgumentException("loitering windowHours must be <=24");
        }
    }

    private static LocalDateTime parse(String value) {
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

    private static int nonNegative(JSONObject json, String key, int defaultValue) {
        int value = json.containsKey(key) ? json.getIntValue(key) : defaultValue;
        if (value < 0) throw new IllegalArgumentException(key + " must not be negative");
        return value;
    }

    public String getListBatchNo() { return listBatchNo; }
    public int getShipLimit() { return shipLimit; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public int getWindowDays() { return windowDays; }
    public int getWindowHours() { return windowHours; }
    public boolean isEnableInsert() { return enableInsert; }
    public boolean isStoreDetails() { return storeDetails; }
    public boolean isResume() { return resume; }
    public int getParallelism() { return parallelism; }
    public int getRetryCount() { return retryCount; }
    public int getLogIntervalShips() { return logIntervalShips; }
    public String getRuleVersion() { return ruleVersion; }
    public String getExpectedExclusionFingerprint() {
        return expectedExclusionFingerprint;
    }
    public String getBatchNo() { return batchNo; }
}
