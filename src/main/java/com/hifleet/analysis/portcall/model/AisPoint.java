package com.hifleet.analysis.portcall.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * Raw AIS point from the real-time Kafka stream.
 */
public class AisPoint implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long mmsi;
    private Instant updateTime;
    private Instant serverReceiveTime;
    private Double lon;
    private Double lat;
    private Double speed;
    private Double course;
    private Double heading;
    private Integer navigationStatus;
    private String dataSourceId;
    private String messageId;
    private String dataState;
    private Double draught;
    private Instant eta;
    private String destination;
    private String shipName;
    private String callSign;
    private String imoNumber;
    private Double shipLength;
    private Double shipWidth;

    /**
     * Checks whether lon/lat are both present and in valid geographic range.
     *
     * @return true when the point has valid coordinates
     */
    public boolean hasValidPosition() {
        return lon != null
            && lat != null
            && lon >= -180.0
            && lon <= 180.0
            && lat >= -90.0
            && lat <= 90.0;
    }

    /**
     * Generates a deterministic key candidate for duplicate filtering.
     *
     * @return dedup key string
     */
    public String buildDedupKey() {
        return String.valueOf(mmsi)
            + "|" + String.valueOf(updateTime);
    }

    public Long getMmsi() {
        return mmsi;
    }

    public void setMmsi(Long mmsi) {
        this.mmsi = mmsi;
    }

    public Instant getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Instant updateTime) {
        this.updateTime = updateTime;
    }

    public Instant getServerReceiveTime() {
        return serverReceiveTime;
    }

    public void setServerReceiveTime(Instant serverReceiveTime) {
        this.serverReceiveTime = serverReceiveTime;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getSpeed() {
        return speed;
    }

    public void setSpeed(Double speed) {
        this.speed = speed;
    }

    public Double getCourse() {
        return course;
    }

    public void setCourse(Double course) {
        this.course = course;
    }

    public Double getHeading() {
        return heading;
    }

    public void setHeading(Double heading) {
        this.heading = heading;
    }

    public Integer getNavigationStatus() {
        return navigationStatus;
    }

    public void setNavigationStatus(Integer navigationStatus) {
        this.navigationStatus = navigationStatus;
    }

    public String getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getDataState() {
        return dataState;
    }

    public void setDataState(String dataState) {
        this.dataState = dataState;
    }

    public Double getDraught() {
        return draught;
    }

    public void setDraught(Double draught) {
        this.draught = draught;
    }

    public Instant getEta() {
        return eta;
    }

    public void setEta(Instant eta) {
        this.eta = eta;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getShipName() {
        return shipName;
    }

    public void setShipName(String shipName) {
        this.shipName = shipName;
    }

    public String getCallSign() {
        return callSign;
    }

    public void setCallSign(String callSign) {
        this.callSign = callSign;
    }

    public String getImoNumber() {
        return imoNumber;
    }

    public void setImoNumber(String imoNumber) {
        this.imoNumber = imoNumber;
    }

    public Double getShipLength() {
        return shipLength;
    }

    public void setShipLength(Double shipLength) {
        this.shipLength = shipLength;
    }

    public Double getShipWidth() {
        return shipWidth;
    }

    public void setShipWidth(Double shipWidth) {
        this.shipWidth = shipWidth;
    }
}
