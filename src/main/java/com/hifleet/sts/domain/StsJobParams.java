package com.hifleet.sts.domain;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

public class StsJobParams {
    private String mode;
    private String seedFile;
    private String from;
    private String to;
    private int shipLimit;
    private int windowHours;
    private double activityMinSpeedKn;
    private double activityMaxSpeedKn;
    private int parallelism;
    private String writeMode;

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
        return p;
    }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getSeedFile() { return seedFile; }
    public void setSeedFile(String seedFile) { this.seedFile = seedFile; }
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
}
