package com.hifleet.analysis.alongside.core.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

public class AlongsideEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    private String eventType;
    private String eventSubType;
    private String sourceMode;
    private String ruleVersion;
    private String pairId;
    private Long mmsiA;
    private Long mmsiB;
    private Instant startTime;
    private Instant endTime;
    private Long durationMinutes;
    private Double startLon;
    private Double startLat;
    private Double endLon;
    private Double endLat;
    private Double centerLon;
    private Double centerLat;
    private Double minDistanceNm;
    private Double maxDistanceNm;
    private Double avgDistanceNm;
    private Double avgSpeedKnots;
    private Double avgRelativeSpeedKnots;
    private Double headingParallelRatio;
    private Integer pointCount;
    private String pairPriority;
    private String shipClassA;
    private String shipClassB;
    private String severityLevel;
    private Double eventScore;
    private String confidenceLevel;
    private String exclusionFlag;
    private String exclusionReason;
    private String scoreDetailJson;
    private Integer aisGapCount;
    private Long maxAisGapMinutes;
    private Double trackCoverageRatio;
    private Instant emitTime;
    private List<PairProximityEvidence> evidencePoints = Collections.emptyList();

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventSubType() {
        return eventSubType;
    }

    public void setEventSubType(String eventSubType) {
        this.eventSubType = eventSubType;
    }

    public String getSourceMode() {
        return sourceMode;
    }

    public void setSourceMode(String sourceMode) {
        this.sourceMode = sourceMode;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public String getPairId() {
        return pairId;
    }

    public void setPairId(String pairId) {
        this.pairId = pairId;
    }

    public Long getMmsiA() {
        return mmsiA;
    }

    public void setMmsiA(Long mmsiA) {
        this.mmsiA = mmsiA;
    }

    public Long getMmsiB() {
        return mmsiB;
    }

    public void setMmsiB(Long mmsiB) {
        this.mmsiB = mmsiB;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public Long getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Long durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Double getStartLon() {
        return startLon;
    }

    public void setStartLon(Double startLon) {
        this.startLon = startLon;
    }

    public Double getStartLat() {
        return startLat;
    }

    public void setStartLat(Double startLat) {
        this.startLat = startLat;
    }

    public Double getEndLon() {
        return endLon;
    }

    public void setEndLon(Double endLon) {
        this.endLon = endLon;
    }

    public Double getEndLat() {
        return endLat;
    }

    public void setEndLat(Double endLat) {
        this.endLat = endLat;
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

    public Double getMinDistanceNm() {
        return minDistanceNm;
    }

    public void setMinDistanceNm(Double minDistanceNm) {
        this.minDistanceNm = minDistanceNm;
    }

    public Double getMaxDistanceNm() {
        return maxDistanceNm;
    }

    public void setMaxDistanceNm(Double maxDistanceNm) {
        this.maxDistanceNm = maxDistanceNm;
    }

    public Double getAvgDistanceNm() {
        return avgDistanceNm;
    }

    public void setAvgDistanceNm(Double avgDistanceNm) {
        this.avgDistanceNm = avgDistanceNm;
    }

    public Double getAvgSpeedKnots() {
        return avgSpeedKnots;
    }

    public void setAvgSpeedKnots(Double avgSpeedKnots) {
        this.avgSpeedKnots = avgSpeedKnots;
    }

    public Double getAvgRelativeSpeedKnots() {
        return avgRelativeSpeedKnots;
    }

    public void setAvgRelativeSpeedKnots(Double avgRelativeSpeedKnots) {
        this.avgRelativeSpeedKnots = avgRelativeSpeedKnots;
    }

    public Double getHeadingParallelRatio() {
        return headingParallelRatio;
    }

    public void setHeadingParallelRatio(Double headingParallelRatio) {
        this.headingParallelRatio = headingParallelRatio;
    }

    public Integer getPointCount() {
        return pointCount;
    }

    public void setPointCount(Integer pointCount) {
        this.pointCount = pointCount;
    }

    public String getPairPriority() {
        return pairPriority;
    }

    public void setPairPriority(String pairPriority) {
        this.pairPriority = pairPriority;
    }

    public String getShipClassA() {
        return shipClassA;
    }

    public void setShipClassA(String shipClassA) {
        this.shipClassA = shipClassA;
    }

    public String getShipClassB() {
        return shipClassB;
    }

    public void setShipClassB(String shipClassB) {
        this.shipClassB = shipClassB;
    }

    public String getSeverityLevel() {
        return severityLevel;
    }

    public void setSeverityLevel(String severityLevel) {
        this.severityLevel = severityLevel;
    }

    public Double getEventScore() {
        return eventScore;
    }

    public void setEventScore(Double eventScore) {
        this.eventScore = eventScore;
    }

    public String getConfidenceLevel() {
        return confidenceLevel;
    }

    public void setConfidenceLevel(String confidenceLevel) {
        this.confidenceLevel = confidenceLevel;
    }

    public String getExclusionFlag() {
        return exclusionFlag;
    }

    public void setExclusionFlag(String exclusionFlag) {
        this.exclusionFlag = exclusionFlag;
    }

    public String getExclusionReason() {
        return exclusionReason;
    }

    public void setExclusionReason(String exclusionReason) {
        this.exclusionReason = exclusionReason;
    }

    public String getScoreDetailJson() {
        return scoreDetailJson;
    }

    public void setScoreDetailJson(String scoreDetailJson) {
        this.scoreDetailJson = scoreDetailJson;
    }

    public Integer getAisGapCount() {
        return aisGapCount;
    }

    public void setAisGapCount(Integer aisGapCount) {
        this.aisGapCount = aisGapCount;
    }

    public Long getMaxAisGapMinutes() {
        return maxAisGapMinutes;
    }

    public void setMaxAisGapMinutes(Long maxAisGapMinutes) {
        this.maxAisGapMinutes = maxAisGapMinutes;
    }

    public Double getTrackCoverageRatio() {
        return trackCoverageRatio;
    }

    public void setTrackCoverageRatio(Double trackCoverageRatio) {
        this.trackCoverageRatio = trackCoverageRatio;
    }

    public Instant getEmitTime() {
        return emitTime;
    }

    public void setEmitTime(Instant emitTime) {
        this.emitTime = emitTime;
    }

    public List<PairProximityEvidence> getEvidencePoints() {
        return evidencePoints;
    }

    public void setEvidencePoints(List<PairProximityEvidence> evidencePoints) {
        this.evidencePoints = evidencePoints == null ? Collections.<PairProximityEvidence>emptyList() : evidencePoints;
    }
}
