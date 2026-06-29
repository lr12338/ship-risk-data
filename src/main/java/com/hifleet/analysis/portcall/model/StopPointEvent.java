package com.hifleet.analysis.portcall.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * Vessel stop-state event emitted to Kafka for direct downstream persistence.
 */
public class StopPointEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String messageId;
    private EventType eventType;
    private String dbAction = "UPSERT";
    private Long mmsi;
    private String imoNumber;
    private String shipName;
    private VesselState vesselState;
    private String stopId;
    private Instant stopStartTime;
    private Instant stableStartTime;
    private Instant stopEndTime;
    private Instant resumeSailingTime;
    private Instant lastAisUpdateTime;
    private Instant lastServerReceiveTime;
    private Instant emitTime;
    private Double stopLon;
    private Double stopLat;
    private PositionStatus positionStatus;
    private String positionMethod = "STABLE_WINDOW_MEAN_WITH_OUTLIER_FILTER";
    private int positionPointCount;
    private double swingRadiusMeters;
    private long durationSeconds;
    private double averageSpeedKnots;
    private double maxSpeedKnots;
    private double confidence;
    private String dataState;
    private String sourceMode = "ONLINE";

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public String getDbAction() {
        return dbAction;
    }

    public void setDbAction(String dbAction) {
        this.dbAction = dbAction;
    }

    public Long getMmsi() {
        return mmsi;
    }

    public void setMmsi(Long mmsi) {
        this.mmsi = mmsi;
    }

    public String getImoNumber() {
        return imoNumber;
    }

    public void setImoNumber(String imoNumber) {
        this.imoNumber = imoNumber;
    }

    public String getShipName() {
        return shipName;
    }

    public void setShipName(String shipName) {
        this.shipName = shipName;
    }

    public VesselState getVesselState() {
        return vesselState;
    }

    public void setVesselState(VesselState vesselState) {
        this.vesselState = vesselState;
    }

    public String getStopId() {
        return stopId;
    }

    public void setStopId(String stopId) {
        this.stopId = stopId;
    }

    public Instant getStopStartTime() {
        return stopStartTime;
    }

    public void setStopStartTime(Instant stopStartTime) {
        this.stopStartTime = stopStartTime;
    }

    public Instant getStableStartTime() {
        return stableStartTime;
    }

    public void setStableStartTime(Instant stableStartTime) {
        this.stableStartTime = stableStartTime;
    }

    public Instant getStopEndTime() {
        return stopEndTime;
    }

    public void setStopEndTime(Instant stopEndTime) {
        this.stopEndTime = stopEndTime;
    }

    public Instant getResumeSailingTime() {
        return resumeSailingTime;
    }

    public void setResumeSailingTime(Instant resumeSailingTime) {
        this.resumeSailingTime = resumeSailingTime;
    }

    public Instant getLastAisUpdateTime() {
        return lastAisUpdateTime;
    }

    public void setLastAisUpdateTime(Instant lastAisUpdateTime) {
        this.lastAisUpdateTime = lastAisUpdateTime;
    }

    public Instant getLastServerReceiveTime() {
        return lastServerReceiveTime;
    }

    public void setLastServerReceiveTime(Instant lastServerReceiveTime) {
        this.lastServerReceiveTime = lastServerReceiveTime;
    }

    public Instant getEmitTime() {
        return emitTime;
    }

    public void setEmitTime(Instant emitTime) {
        this.emitTime = emitTime;
    }

    public Double getStopLon() {
        return stopLon;
    }

    public void setStopLon(Double stopLon) {
        this.stopLon = stopLon;
    }

    public Double getStopLat() {
        return stopLat;
    }

    public void setStopLat(Double stopLat) {
        this.stopLat = stopLat;
    }

    public PositionStatus getPositionStatus() {
        return positionStatus;
    }

    public void setPositionStatus(PositionStatus positionStatus) {
        this.positionStatus = positionStatus;
    }

    public String getPositionMethod() {
        return positionMethod;
    }

    public void setPositionMethod(String positionMethod) {
        this.positionMethod = positionMethod;
    }

    public int getPositionPointCount() {
        return positionPointCount;
    }

    public void setPositionPointCount(int positionPointCount) {
        this.positionPointCount = positionPointCount;
    }

    public double getSwingRadiusMeters() {
        return swingRadiusMeters;
    }

    public void setSwingRadiusMeters(double swingRadiusMeters) {
        this.swingRadiusMeters = swingRadiusMeters;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public double getAverageSpeedKnots() {
        return averageSpeedKnots;
    }

    public void setAverageSpeedKnots(double averageSpeedKnots) {
        this.averageSpeedKnots = averageSpeedKnots;
    }

    public double getMaxSpeedKnots() {
        return maxSpeedKnots;
    }

    public void setMaxSpeedKnots(double maxSpeedKnots) {
        this.maxSpeedKnots = maxSpeedKnots;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getDataState() {
        return dataState;
    }

    public void setDataState(String dataState) {
        this.dataState = dataState;
    }

    public String getSourceMode() {
        return sourceMode;
    }

    public void setSourceMode(String sourceMode) {
        this.sourceMode = sourceMode;
    }

    public enum EventType {
        STOP_CANDIDATE_START,
        STOP_START,
        STOP_UPDATE,
        STOP_END,
        SAILING_START,
        UNKNOWN
    }

    public enum VesselState {
        SAILING,
        STOP_CANDIDATE,
        STOPPED,
        UNKNOWN
    }

    public enum PositionStatus {
        STABILIZING,
        STABLE,
        FINAL
    }
}
