package com.hifleet.sts.domain;

public class StsRiskRegionRow {
    private Long id;
    private String regionCode;
    private String regionVersion;
    private String regionName;
    private String geometryWkt;
    private double analysisBufferNm;
    private int priority;
    private String status;
    private int enabled;
    private String geometryFingerprint;
    private String sourceType;
    private String sourceNote;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String v) { this.regionCode = v; }

    public String getRegionVersion() { return regionVersion; }
    public void setRegionVersion(String v) { this.regionVersion = v; }

    public String getRegionName() { return regionName; }
    public void setRegionName(String v) { this.regionName = v; }

    public String getGeometryWkt() { return geometryWkt; }
    public void setGeometryWkt(String v) { this.geometryWkt = v; }

    public double getAnalysisBufferNm() { return analysisBufferNm; }
    public void setAnalysisBufferNm(double v) { this.analysisBufferNm = v; }

    public int getPriority() { return priority; }
    public void setPriority(int v) { this.priority = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public int getEnabled() { return enabled; }
    public void setEnabled(int v) { this.enabled = v; }

    public String getGeometryFingerprint() { return geometryFingerprint; }
    public void setGeometryFingerprint(String v) { this.geometryFingerprint = v; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String v) { this.sourceType = v; }

    public String getSourceNote() { return sourceNote; }
    public void setSourceNote(String v) { this.sourceNote = v; }
}
