package com.hifleet.sts.domain;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StsAlongsideHistoryJobParams {
    public static final String MODE = "STS_ALONGSIDE_HISTORY_V1";
    public static final int DEFAULT_MAX_SLICES_PER_INVOCATION = 8;
    public static final int DEFAULT_MAX_RUNTIME_MINUTES = 90;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String mode;
    private String seedBatchNo;
    private String regionSetCode;
    private List<String> imos = Collections.emptyList();
    private int shipOffset;
    private int shipCount = 20;
    // Local date/time in the executor zone (242: Asia/Shanghai), range [from, to).
    private String from;
    private String to;
    private int sliceDays = 1;
    private String batchNo;
    private int parallelism = 1;
    private String writeMode = "SHADOW_ONLY";
    private boolean resume = true;
    private boolean autoContinue;
    private int maxSlicesPerInvocation;
    private int maxRuntimeMinutes;

    public static StsAlongsideHistoryJobParams parse(String value) {
        if (StringUtils.isBlank(value)) throw new IllegalArgumentException("params is required");
        JSONObject json = JSONObject.parseObject(value);
        StsAlongsideHistoryJobParams p = new StsAlongsideHistoryJobParams();
        p.mode = json.getString("mode");
        p.seedBatchNo = json.getString("seedBatchNo");
        p.regionSetCode = json.getString("regionSetCode");
        JSONArray values = json.getJSONArray("imos");
        if (values != null) {
            List<String> imos = new ArrayList<String>();
            for (Object item : values) if (item != null) imos.add(String.valueOf(item));
            p.imos = Collections.unmodifiableList(imos);
        }
        if (json.containsKey("shipOffset")) p.shipOffset = json.getIntValue("shipOffset");
        if (json.containsKey("shipCount")) p.shipCount = json.getIntValue("shipCount");
        p.from = json.getString("from");
        p.to = json.getString("to");
        if (json.containsKey("sliceDays")) p.sliceDays = json.getIntValue("sliceDays");
        p.batchNo = json.getString("batchNo");
        if (json.containsKey("parallelism")) p.parallelism = json.getIntValue("parallelism");
        if (json.containsKey("writeMode")) p.writeMode = json.getString("writeMode");
        if (json.containsKey("resume")) p.resume = json.getBooleanValue("resume");
        if (json.containsKey("autoContinue")) p.autoContinue = json.getBooleanValue("autoContinue");
        if (json.containsKey("maxSlicesPerInvocation")) {
            p.maxSlicesPerInvocation = json.getIntValue("maxSlicesPerInvocation");
        }
        if (json.containsKey("maxRuntimeMinutes")) {
            p.maxRuntimeMinutes = json.getIntValue("maxRuntimeMinutes");
        }
        return p;
    }

    public void validate() {
        if (!MODE.equals(mode)) throw new IllegalArgumentException("unsupported mode: " + mode);
        if (StringUtils.isBlank(seedBatchNo)) throw new IllegalArgumentException("seedBatchNo is required");
        if (StringUtils.isBlank(regionSetCode)) throw new IllegalArgumentException("regionSetCode is required");
        if (shipOffset < 0) throw new IllegalArgumentException("shipOffset must be >= 0");
        if (imos.isEmpty() && shipCount < 1) {
            throw new IllegalArgumentException("shipCount must be positive");
        }
        if (StringUtils.isBlank(from) || StringUtils.isBlank(to)) {
            throw new IllegalArgumentException("from and to are required");
        }
        LocalDateTime start = LocalDateTime.parse(from, TIME_FMT);
        LocalDateTime end = LocalDateTime.parse(to, TIME_FMT);
        if (!start.isBefore(end)) throw new IllegalArgumentException("from must be before to");
        if (Duration.between(start, end).toDays() > 366L * 5L) {
            throw new IllegalArgumentException("parent span exceeds 5 years");
        }
        if (sliceDays < 1 || sliceDays > 7) throw new IllegalArgumentException("sliceDays must be 1-7");
        if (StringUtils.isBlank(batchNo)) throw new IllegalArgumentException("batchNo is required");
        if (parallelism != 1) throw new IllegalArgumentException("parallelism must be 1");
        if (!"SHADOW_ONLY".equals(writeMode)) {
            throw new IllegalArgumentException("writeMode must be SHADOW_ONLY");
        }
        if (!resume) throw new IllegalArgumentException("resume must be true");
        if (maxSlicesPerInvocation < 0) {
            throw new IllegalArgumentException("maxSlicesPerInvocation must be >= 0");
        }
        if (maxRuntimeMinutes < 0) {
            throw new IllegalArgumentException("maxRuntimeMinutes must be >= 0");
        }
    }

    public String getMode() { return mode; }
    public String getSeedBatchNo() { return seedBatchNo; }
    public String getRegionSetCode() { return regionSetCode; }
    public List<String> getImos() { return imos; }
    public int getShipOffset() { return shipOffset; }
    public int getShipCount() { return shipCount; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public int getSliceDays() { return sliceDays; }
    public String getBatchNo() { return batchNo; }
    public int getParallelism() { return parallelism; }
    public String getWriteMode() { return writeMode; }
    public boolean isResume() { return resume; }
    public boolean isAutoContinue() { return autoContinue; }
    public int getMaxSlicesPerInvocation() {
        return maxSlicesPerInvocation == 0 ? DEFAULT_MAX_SLICES_PER_INVOCATION : maxSlicesPerInvocation;
    }
    public int getMaxRuntimeMinutes() {
        return maxRuntimeMinutes == 0 ? DEFAULT_MAX_RUNTIME_MINUTES : maxRuntimeMinutes;
    }
}
