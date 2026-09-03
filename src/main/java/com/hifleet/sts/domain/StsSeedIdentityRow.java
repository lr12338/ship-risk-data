package com.hifleet.sts.domain;

public class StsSeedIdentityRow {
    private Long id;
    private String seedBatchNo;
    private int shipOrdinal;
    private String imo;
    private String mmsi;
    private String firstSeen;
    private String lastSeen;
    private String identityMode;
    private String name;
    private String callsign;
    private int length;
    private int width;
    private String type;
    private String minotype;
    private int enabled;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }

    public String getSeedBatchNo() { return seedBatchNo; }
    public void setSeedBatchNo(String v) { this.seedBatchNo = v; }

    public int getShipOrdinal() { return shipOrdinal; }
    public void setShipOrdinal(int v) { this.shipOrdinal = v; }

    public String getImo() { return imo; }
    public void setImo(String v) { this.imo = v; }

    public String getMmsi() { return mmsi; }
    public void setMmsi(String v) { this.mmsi = v; }

    public String getFirstSeen() { return firstSeen; }
    public void setFirstSeen(String v) { this.firstSeen = v; }

    public String getLastSeen() { return lastSeen; }
    public void setLastSeen(String v) { this.lastSeen = v; }

    public String getIdentityMode() { return identityMode; }
    public void setIdentityMode(String v) { this.identityMode = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getCallsign() { return callsign; }
    public void setCallsign(String v) { this.callsign = v; }

    public int getLength() { return length; }
    public void setLength(int v) { this.length = v; }

    public int getWidth() { return width; }
    public void setWidth(int v) { this.width = v; }

    public String getType() { return type; }
    public void setType(String v) { this.type = v; }

    public String getMinotype() { return minotype; }
    public void setMinotype(String v) { this.minotype = v; }

    public int getEnabled() { return enabled; }
    public void setEnabled(int v) { this.enabled = v; }

    public StsIdentityRecord toIdentityRecord() {
        StsIdentityRecord r = new StsIdentityRecord();
        r.setImo(imo);
        r.setMmsi(mmsi);
        r.setFirstSeen(firstSeen);
        r.setLastSeen(lastSeen);
        r.setName(name);
        r.setCallsign(callsign);
        r.setLength(length);
        r.setWidth(width);
        r.setType(type);
        r.setMinotype(minotype);
        r.setIdentityMode(identityMode);
        return r;
    }
}
