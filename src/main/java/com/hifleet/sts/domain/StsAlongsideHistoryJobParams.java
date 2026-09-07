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
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String mode;
    private String seedBatchNo;
    private String regionSetCode;
    private List<String> imos = Collections.emptyList();
    private int shipOffset;
    private int shipCount = 20;
    private String from;
    private String to;
    private int sliceDays = 1;
    private String batchNo;
    private int parallelism = 1;
    private String writeMode = "SHADOW_ONLY";
    private boolean resume = true;

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
        return p;
    }

    public void validate() {
        if (!MODE.equals(mode)) throw new IllegalArgumentException("unsupported mode: " + mode);
        if (StringUtils.isBlank(seedBatchNo)) throw new IllegalArgumentException("seedBatchNo is required");
        if (StringUtils.isBlank(regionSetCode)) throw new IllegalArgumentException("regionSetCode is required");
        if (shipOffset < 0) throw new IllegalArgumentException("shipOffset must be >= 0");
        if (imos.isEmpty() && (shipCount < 1 || shipCount > 757)) {
            throw new IllegalArgumentException("shipCount must be 1-757");
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
}
