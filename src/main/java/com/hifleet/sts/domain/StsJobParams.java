package com.hifleet.sts.domain;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StsJobParams {
    private String mode;
    private String seedFile;
    private List<String> imos = Collections.emptyList();
    private String from;
    private String to;
    private int shipLimit;
    private int windowHours;
    private double activityMinSpeedKn;
    private double activityMaxSpeedKn;
    private int parallelism;
    private String writeMode;

    private String regionId;
    private String regionVersion;
    private String regionWkt;
    private double analysisBufferNm;
    private String batchNo;

    public static StsJobParams parse(String params) {
        StsJobParams p = new StsJobParams();
        if (StringUtils.isBlank(params)) {
            return p;
        }
        JSONObject json = JSONObject.parseObject(params);
        p.mode = json.getString("mode");
        p.seedFile = json.getString("seedFile");
        p.from = json.getString("from");
        p.to = json.getString("to");

        JSONArray imosArr = json.getJSONArray("imos");
        if (imosArr != null && !imosArr.isEmpty()) {
            List<String> list = new ArrayList<String>();
            for (int i = 0; i < imosArr.size(); i++) {
                String imo = imosArr.getString(i);
                if (StringUtils.isNotBlank(imo)) {
                    list.add(imo.trim());
                }
            }
            p.imos = list;
        }

        p.shipLimit = json.getIntValue("shipLimit");
        p.windowHours = json.getIntValue("windowHours");
        if (p.windowHours <= 0) p.windowHours = 24;
        p.activityMinSpeedKn = json.getDoubleValue("activityMinSpeedKn");
        p.activityMaxSpeedKn = json.getDoubleValue("activityMaxSpeedKn");
        if (p.activityMaxSpeedKn <= 0) p.activityMaxSpeedKn = 4.0;
        p.parallelism = json.getIntValue("parallelism");
        if (p.parallelism <= 0) p.parallelism = 1;
        p.writeMode = json.getString("writeMode");
        if (StringUtils.isBlank(p.writeMode)) p.writeMode = "LOG_ONLY";

        p.regionId = json.getString("regionId");
        p.regionVersion = json.getString("regionVersion");
        p.regionWkt = json.getString("regionWkt");
        p.analysisBufferNm = json.getDoubleValue("analysisBufferNm");
        if (p.analysisBufferNm <= 0.0) p.analysisBufferNm = 10.0;
        p.batchNo = json.getString("batchNo");

        return p;
    }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getSeedFile() { return seedFile; }
    public void setSeedFile(String seedFile) { this.seedFile = seedFile; }

    public List<String> getImos() { return imos; }
    public void setImos(List<String> imos) { this.imos = imos != null ? imos : Collections.<String>emptyList(); }
    public boolean hasExplicitImos() { return imos != null && !imos.isEmpty(); }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }
    public int getShipLimit() { return shipLimit; }
    public void setShipLimit(int shipLimit) { this.shipLimit = shipLimit; }
    public int getWindowHours() { return windowHours; }
    public void setWindowHours(int windowHours) { this.windowHours = windowHours; }
    public double getActivityMinSpeedKn() { return activityMinSpeedKn; }
    public void setActivityMinSpeedKn(double v) { this.activityMinSpeedKn = v; }
    public double getActivityMaxSpeedKn() { return activityMaxSpeedKn; }
    public void setActivityMaxSpeedKn(double v) { this.activityMaxSpeedKn = v; }
    public int getParallelism() { return parallelism; }
    public void setParallelism(int parallelism) { this.parallelism = parallelism; }
    public String getWriteMode() { return writeMode; }
    public void setWriteMode(String writeMode) { this.writeMode = writeMode; }

    public String getRegionId() { return regionId; }
    public void setRegionId(String v) { this.regionId = v; }
    public String getRegionVersion() { return regionVersion; }
    public void setRegionVersion(String v) { this.regionVersion = v; }
    public String getRegionWkt() { return regionWkt; }
    public void setRegionWkt(String v) { this.regionWkt = v; }
    public double getAnalysisBufferNm() { return analysisBufferNm; }
    public void setAnalysisBufferNm(double v) { this.analysisBufferNm = v; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String v) { this.batchNo = v; }
}
