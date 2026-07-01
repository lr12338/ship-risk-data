package com.hifleet.domain;

/**
 * getRecentAis 区域回放查询请求。
 */
public class ReplayQueryRequest {

    private String startTime;
    private String endTime;
    private Double minLat;
    private Double maxLat;
    private Double minLon;
    private Double maxLon;
    private Double centerLon;
    private Double centerLat;
    private Double radiusNm;
    private int devSize;
    private String batchNo;

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public Double getMinLat() {
        return minLat;
    }

    public void setMinLat(Double minLat) {
        this.minLat = minLat;
    }

    public Double getMaxLat() {
        return maxLat;
    }

    public void setMaxLat(Double maxLat) {
        this.maxLat = maxLat;
    }

    public Double getMinLon() {
        return minLon;
    }

    public void setMinLon(Double minLon) {
        this.minLon = minLon;
    }

    public Double getMaxLon() {
        return maxLon;
    }

    public void setMaxLon(Double maxLon) {
        this.maxLon = maxLon;
    }

    public Double getCenterLon() {
        return centerLon;
    }

    public void setCenterLon(Double centerLon) {
        this.centerLon = centerLon;
    }

    public Double getCenterLat() {
        return centerLat;
    }

    public void setCenterLat(Double centerLat) {
        this.centerLat = centerLat;
    }

    public Double getRadiusNm() {
        return radiusNm;
    }

    public void setRadiusNm(Double radiusNm) {
        this.radiusNm = radiusNm;
    }

    public int getDevSize() {
        return devSize;
    }

    public void setDevSize(int devSize) {
        this.devSize = devSize;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }
}
