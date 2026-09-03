package com.hifleet.sts.domain;

public class StsRegionSetItemRow {
    private Long id;
    private String regionSetCode;
    private String regionCode;
    private String regionVersion;
    private int priority;
    private int enabled;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }

    public String getRegionSetCode() { return regionSetCode; }
    public void setRegionSetCode(String v) { this.regionSetCode = v; }

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String v) { this.regionCode = v; }

    public String getRegionVersion() { return regionVersion; }
    public void setRegionVersion(String v) { this.regionVersion = v; }

    public int getPriority() { return priority; }
    public void setPriority(int v) { this.priority = v; }

    public int getEnabled() { return enabled; }
    public void setEnabled(int v) { this.enabled = v; }
}
