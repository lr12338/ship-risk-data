package com.hifleet.tankeropensea;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.xxl.job.core.log.XxlJobLogger;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class TankerOpenSeaRangeService {
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter BATCH_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    @Autowired
    private TankerOpenSeaAlongsideService alongsideService;
    @Autowired
    private TankerOpenSeaLoiteringService loiteringService;

    public TankerOpenSeaRangeResult run(
            String rawParams, TankerOpenSeaJobParams.Behavior behavior) {
        RangeParams params = RangeParams.parse(rawParams);
        TankerOpenSeaRangeResult total = new TankerOpenSeaRangeResult(
                behavior.name(), params.start.format(TIME_FORMATTER),
                params.end.format(TIME_FORMATTER));
        LocalDateTime cursor = params.start;
        while (cursor.isBefore(params.end)) {
            LocalDateTime nextMonth = cursor.toLocalDate()
                    .withDayOfMonth(1).plusMonths(1).atStartOfDay();
            LocalDateTime segmentEnd = nextMonth.isBefore(params.end)
                    ? nextMonth : params.end;
            String batchNo = batchNo(behavior, params, cursor, segmentEnd);
            String childParams = childParams(
                    behavior, params, cursor, segmentEnd, batchNo);
            XxlJobLogger.log("LIQUID_CONTAINER " + behavior
                    + " range segment start batchNo=" + batchNo
                    + " startTime=" + cursor.format(TIME_FORMATTER)
                    + " endTime=" + segmentEnd.format(TIME_FORMATTER));
            TankerOpenSeaBatchResult result = behavior
                    == TankerOpenSeaJobParams.Behavior.ALONGSIDE
                    ? alongsideService.run(childParams)
                    : loiteringService.run(childParams);
            XxlJobLogger.log(result.summary());
            if (!result.isSuccess()) {
                total.segmentFailed(batchNo);
                XxlJobLogger.log(total.summary());
                return total;
            }
            total.segmentCompleted();
            cursor = segmentEnd;
        }
        XxlJobLogger.log(total.summary());
        return total;
    }

    private String childParams(TankerOpenSeaJobParams.Behavior behavior,
                               RangeParams params,
                               LocalDateTime start,
                               LocalDateTime end,
                               String batchNo) {
        JSONObject json = new JSONObject();
        json.put("mode", "liquid_container_exclusion_test");
        json.put("listBatchNo", params.listBatchNo);
        json.put("shipLimit", params.shipLimit);
        json.put("startTime", start.format(TIME_FORMATTER));
        json.put("endTime", end.format(TIME_FORMATTER));
        if (behavior == TankerOpenSeaJobParams.Behavior.ALONGSIDE) {
            json.put("windowDays", 7);
            json.put("ruleVersion", TankerOpenSeaAlongsideRule.RULE_VERSION);
        } else {
            json.put("windowHours", 24);
            json.put("ruleVersion", TankerOpenSeaLoiteringRule.RULE_VERSION);
        }
        json.put("enableInsert", params.enableInsert);
        json.put("storeDetails", params.storeDetails);
        json.put("resume", params.resume);
        json.put("parallelism", 1);
        json.put("retryCount", params.retryCount);
        json.put("logIntervalShips", params.logIntervalShips);
        if (params.expectedExclusionFingerprint != null) {
            json.put("expectedExclusionFingerprint",
                    params.expectedExclusionFingerprint);
        }
        json.put("batchNo", batchNo);
        return json.toJSONString();
    }

    private String batchNo(TankerOpenSeaJobParams.Behavior behavior,
                           RangeParams params,
                           LocalDateTime start,
                           LocalDateTime end) {
        String prefix = behavior == TankerOpenSeaJobParams.Behavior.ALONGSIDE
                ? "alongside-lc-" : "loitering-lc-";
        String dryRun = params.enableInsert ? "" : "dryrun-";
        return prefix + dryRun + "s" + params.shipLimit + "-"
                + start.format(BATCH_FORMATTER) + "-"
                + end.format(BATCH_FORMATTER) + "-"
                + params.batchVersion;
    }

    private static class RangeParams {
        private String listBatchNo;
        private int shipLimit;
        private LocalDateTime start;
        private LocalDateTime end;
        private boolean enableInsert;
        private boolean storeDetails;
        private boolean resume;
        private int retryCount;
        private int logIntervalShips;
        private String expectedExclusionFingerprint;
        private String batchVersion;

        private static RangeParams parse(String raw) {
            JSONObject json;
            try {
                json = JSON.parseObject(raw);
            } catch (Exception ex) {
                throw new IllegalArgumentException(
                        "params must be a JSON object", ex);
            }
            if (json == null || !"liquid_container_exclusion_range".equals(
                    json.getString("mode"))) {
                throw new IllegalArgumentException(
                        "mode must be liquid_container_exclusion_range");
            }
            RangeParams result = new RangeParams();
            result.listBatchNo = required(json, "listBatchNo");
            if (!result.listBatchNo.startsWith("liquid-container-")
                    || result.listBatchNo.length() > 64) {
                throw new IllegalArgumentException(
                        "dedicated liquid-container listBatchNo is required");
            }
            result.shipLimit = positive(json, "shipLimit", 0);
            if (result.shipLimit > TankerOpenSeaJobParams.MAX_SHIP_LIMIT) {
                throw new IllegalArgumentException("shipLimit must be <= "
                        + TankerOpenSeaJobParams.MAX_SHIP_LIMIT);
            }
            result.start = parseTime(required(json, "startTime"));
            result.end = parseTime(required(json, "endTime"));
            if (!result.start.isBefore(result.end)) {
                throw new IllegalArgumentException("time range must be positive");
            }
            result.enableInsert = json.getBooleanValue("enableInsert");
            result.storeDetails = json.getBooleanValue("storeDetails");
            if (result.storeDetails && !result.enableInsert) {
                throw new IllegalArgumentException(
                        "storeDetails requires enableInsert=true");
            }
            result.resume = !json.containsKey("resume")
                    || json.getBooleanValue("resume");
            if (json.containsKey("parallelism")
                    && json.getIntValue("parallelism") != 1) {
                throw new IllegalArgumentException("parallelism must be 1");
            }
            result.retryCount = nonNegative(json, "retryCount", 1);
            if (result.retryCount > 3) {
                throw new IllegalArgumentException("retryCount must be <=3");
            }
            result.logIntervalShips = positive(
                    json, "logIntervalShips", 10);
            result.expectedExclusionFingerprint = StringUtils.trimToNull(
                    json.getString("expectedExclusionFingerprint"));
            if (result.enableInsert
                    && (result.expectedExclusionFingerprint == null
                    || !result.expectedExclusionFingerprint.matches(
                    "^[0-9a-fA-F]{64}$"))) {
                throw new IllegalArgumentException(
                        "expectedExclusionFingerprint must be a SHA-256 for insert");
            }
            if (result.expectedExclusionFingerprint != null
                    && !result.expectedExclusionFingerprint.matches(
                    "^[0-9a-fA-F]{64}$")) {
                throw new IllegalArgumentException(
                        "expectedExclusionFingerprint must be a SHA-256");
            }
            result.batchVersion = required(json, "batchVersion");
            if (!result.batchVersion.matches("^v[0-9]{1,3}$")) {
                throw new IllegalArgumentException(
                        "batchVersion must match v<number>");
            }
            return result;
        }

        private static String required(JSONObject json, String key) {
            String value = StringUtils.trimToNull(json.getString(key));
            if (value == null) {
                throw new IllegalArgumentException(key + " is required");
            }
            return value;
        }

        private static int positive(JSONObject json, String key,
                                    int defaultValue) {
            int value = json.containsKey(key)
                    ? json.getIntValue(key) : defaultValue;
            if (value <= 0) {
                throw new IllegalArgumentException(key + " must be positive");
            }
            return value;
        }

        private static int nonNegative(JSONObject json, String key,
                                       int defaultValue) {
            int value = json.containsKey(key)
                    ? json.getIntValue(key) : defaultValue;
            if (value < 0) {
                throw new IllegalArgumentException(
                        key + " must not be negative");
            }
            return value;
        }

        private static LocalDateTime parseTime(String value) {
            try {
                return LocalDateTime.parse(value, TIME_FORMATTER);
            } catch (Exception ex) {
                throw new IllegalArgumentException(
                        "invalid time: " + value, ex);
            }
        }
    }
}
