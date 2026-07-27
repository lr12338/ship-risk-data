package com.hifleet.job.services;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class AlongsideV3HistoryTestParams {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter BATCH_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private String mode;
    private int shipLimit;
    private int batchSize;
    private int windowDays;
    private String startTime;
    private String endTime;
    private String batchNo;
    private boolean enableInsert;
    private boolean resume;

    public static AlongsideV3HistoryTestParams parse(
            String raw, int defaultShipLimit, int defaultBatchSize, int defaultDays,
            int defaultWindowDays, int maxShipLimit, int maxBatchSize,
            int maxWindowDays, LocalDateTime now) {
        JSONObject json;
        try {
            json = StringUtils.isBlank(raw) ? new JSONObject() : JSON.parseObject(raw.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("history test params must be a JSON object", ex);
        }
        if (json == null) {
            json = new JSONObject();
        }

        AlongsideV3HistoryTestParams params = new AlongsideV3HistoryTestParams();
        params.mode = StringUtils.defaultIfBlank(json.getString("mode"), "history_test").toLowerCase();
        params.shipLimit = json.containsKey("shipLimit") ? json.getIntValue("shipLimit") : defaultShipLimit;
        params.batchSize = json.containsKey("batchSize") ? json.getIntValue("batchSize") : defaultBatchSize;
        params.windowDays = json.containsKey("windowDays")
                ? json.getIntValue("windowDays") : defaultWindowDays;
        params.enableInsert = json.containsKey("enableInsert") && json.getBooleanValue("enableInsert");
        params.resume = !json.containsKey("resume") || json.getBooleanValue("resume");

        String explicitStart = json.getString("startTime");
        String explicitEnd = json.getString("endTime");
        if (StringUtils.isBlank(explicitStart) != StringUtils.isBlank(explicitEnd)) {
            throw new IllegalArgumentException("startTime and endTime must be provided together");
        }
        if (StringUtils.isNotBlank(explicitStart)) {
            params.startTime = explicitStart.trim();
            params.endTime = explicitEnd.trim();
        } else {
            int days = json.containsKey("days") ? json.getIntValue("days") : defaultDays;
            if (days < 1 || days > 366) {
                throw new IllegalArgumentException("days must be between 1 and 366");
            }
            params.startTime = now.minusDays(days).format(TIME_FORMATTER);
            params.endTime = now.format(TIME_FORMATTER);
        }

        params.batchNo = StringUtils.defaultIfBlank(json.getString("batchNo"),
                "alongside-v3-history-test-" + now.format(BATCH_FORMATTER));
        params.validate(maxShipLimit, maxBatchSize, maxWindowDays);
        return params;
    }

    private void validate(int maxShipLimit, int maxBatchSize, int maxWindowDays) {
        if (!"history_test".equals(mode)) {
            throw new IllegalArgumentException("mode must be history_test");
        }
        if (shipLimit < 1 || shipLimit > maxShipLimit) {
            throw new IllegalArgumentException("shipLimit must be between 1 and " + maxShipLimit);
        }
        if (batchSize < 1 || batchSize > maxBatchSize) {
            throw new IllegalArgumentException("batchSize must be between 1 and " + maxBatchSize);
        }
        if (windowDays < 1 || windowDays > maxWindowDays) {
            throw new IllegalArgumentException("windowDays must be between 1 and " + maxWindowDays);
        }
        if (batchNo.length() > 64) {
            throw new IllegalArgumentException("batchNo must not exceed 64 characters");
        }
        try {
            LocalDateTime start = LocalDateTime.parse(startTime, TIME_FORMATTER);
            LocalDateTime end = LocalDateTime.parse(endTime, TIME_FORMATTER);
            if (!start.isBefore(end)) {
                throw new IllegalArgumentException("startTime must be before endTime");
            }
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("time format must be yyyy-MM-dd HH:mm:ss", ex);
        }
    }

    public String getMode() {
        return mode;
    }

    public int getShipLimit() {
        return shipLimit;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getWindowDays() {
        return windowDays;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public String getStatusBatchNo() {
        String prefix = enableInsert ? "write-" : "dry-";
        String value = prefix + batchNo;
        if (value.length() <= 64) {
            return value;
        }
        String hash = Integer.toHexString(batchNo.hashCode());
        return value.substring(0, 63 - hash.length()) + "-" + hash;
    }

    public boolean isEnableInsert() {
        return enableInsert;
    }

    public boolean isResume() {
        return resume;
    }
}
