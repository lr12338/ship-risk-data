package com.hifleet.sts.domain;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

public class StsFleetBackfillJobParams {
    private String targetListBatchNo;
    private int expectedTargetCount;
    private int shipOffset;
    private int shipCount;
    private String regionSetCode;
    private String from;
    private String to;
    private int sliceDays;
    private String batchNo;

    public static StsFleetBackfillJobParams parse(String params) {
        StsFleetBackfillJobParams p = new StsFleetBackfillJobParams();
        if (StringUtils.isBlank(params)) {
            throw new IllegalArgumentException("params is required");
        }
        JSONObject json = JSONObject.parseObject(params);
        p.targetListBatchNo = json.getString("targetListBatchNo");
        p.expectedTargetCount = json.getIntValue("expectedTargetCount");
        p.shipOffset = json.getIntValue("shipOffset");
        p.shipCount = json.getIntValue("shipCount");
        p.regionSetCode = json.getString("regionSetCode");
        p.from = json.getString("from");
        p.to = json.getString("to");
        p.sliceDays = json.getIntValue("sliceDays");
        p.batchNo = json.getString("batchNo");
        return p;
    }

    public String getTargetListBatchNo() { return targetListBatchNo; }
    public void setTargetListBatchNo(String v) { this.targetListBatchNo = v; }

    public int getExpectedTargetCount() { return expectedTargetCount; }
    public void setExpectedTargetCount(int v) { this.expectedTargetCount = v; }

    public int getShipOffset() { return shipOffset; }
    public void setShipOffset(int v) { this.shipOffset = v; }

    public int getShipCount() { return shipCount; }
    public void setShipCount(int v) { this.shipCount = v; }

    public String getRegionSetCode() { return regionSetCode; }
    public void setRegionSetCode(String v) { this.regionSetCode = v; }

    public String getFrom() { return from; }
    public void setFrom(String v) { this.from = v; }

    public String getTo() { return to; }
    public void setTo(String v) { this.to = v; }

    public int getSliceDays() { return sliceDays; }
    public void setSliceDays(int v) { this.sliceDays = v; }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String v) { this.batchNo = v; }
}
