package com.hifleet.sts.domain;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

public class StsRegionSetJobParams {
    private String seedBatchNo;
    private int expectedShipCount;
    private int shipOffset;
    private int shipLimit;
    private String regionSetCode;
    private String from;
    private String to;
    private String batchNo;

    public static StsRegionSetJobParams parse(String params) {
        StsRegionSetJobParams p = new StsRegionSetJobParams();
        if (StringUtils.isBlank(params)) {
            throw new IllegalArgumentException("params is required");
        }
        JSONObject json = JSONObject.parseObject(params);
        p.seedBatchNo = json.getString("seedBatchNo");
        p.expectedShipCount = json.getIntValue("expectedShipCount");
        p.shipOffset = json.getIntValue("shipOffset");
        p.shipLimit = json.getIntValue("shipLimit");
        p.regionSetCode = json.getString("regionSetCode");
        p.from = json.getString("from");
        p.to = json.getString("to");
        p.batchNo = json.getString("batchNo");
        return p;
    }

    public String getSeedBatchNo() { return seedBatchNo; }
    public void setSeedBatchNo(String v) { this.seedBatchNo = v; }

    public int getExpectedShipCount() { return expectedShipCount; }
    public void setExpectedShipCount(int v) { this.expectedShipCount = v; }

    public int getShipOffset() { return shipOffset; }
    public void setShipOffset(int v) { this.shipOffset = v; }

    public int getShipLimit() { return shipLimit; }
    public void setShipLimit(int v) { this.shipLimit = v; }

    public String getRegionSetCode() { return regionSetCode; }
    public void setRegionSetCode(String v) { this.regionSetCode = v; }

    public String getFrom() { return from; }
    public void setFrom(String v) { this.from = v; }

    public String getTo() { return to; }
    public void setTo(String v) { this.to = v; }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String v) { this.batchNo = v; }
}
