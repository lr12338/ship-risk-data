package com.hifleet.sts.domain;

public class StsIdentityRecord {
    private String imo;
    private String mmsi;
    private String firstSeen;
    private String lastSeen;
    private String name;
    private String callsign;
    private int length;
    private int width;
    private String type;
    private String minotype;
    private String identityMode;

    public String getImo() { return imo; }
    public void setImo(String imo) { this.imo = imo; }
    public String getMmsi() { return mmsi; }
    public void setMmsi(String mmsi) { this.mmsi = mmsi; }
    public String getFirstSeen() { return firstSeen; }
    public void setFirstSeen(String firstSeen) { this.firstSeen = firstSeen; }
    public String getLastSeen() { return lastSeen; }
    public void setLastSeen(String lastSeen) { this.lastSeen = lastSeen; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCallsign() { return callsign; }
    public void setCallsign(String callsign) { this.callsign = callsign; }
    public int getLength() { return length; }
    public void setLength(int length) { this.length = length; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMinotype() { return minotype; }
    public void setMinotype(String minotype) { this.minotype = minotype; }
    public String getIdentityMode() { return identityMode; }
    public void setIdentityMode(String identityMode) { this.identityMode = identityMode; }
}
