package com.hifleet.sts.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class StsFleetShipIdentity {
    private String physicalShipKey;
    private String imo;
    private Set<String> mmsis;
    private String primaryMmsi;
    private String shipName;
    private String shipType;
    private int minSeqNo;
    private String identitySource;

    public StsFleetShipIdentity() {
        this.mmsis = new TreeSet<String>();
    }

    public String getPhysicalShipKey() { return physicalShipKey; }
    public void setPhysicalShipKey(String v) { this.physicalShipKey = v; }

    public String getImo() { return imo; }
    public void setImo(String v) { this.imo = v; }

    public Set<String> getMmsis() { return Collections.unmodifiableSet(mmsis); }
    public void addMmsi(String mmsi) { this.mmsis.add(mmsi); }

    public String getPrimaryMmsi() { return primaryMmsi; }
    public void setPrimaryMmsi(String v) { this.primaryMmsi = v; }

    public String getShipName() { return shipName; }
    public void setShipName(String v) { this.shipName = v; }

    public String getShipType() { return shipType; }
    public void setShipType(String v) { this.shipType = v; }

    public int getMinSeqNo() { return minSeqNo; }
    public void setMinSeqNo(int v) { this.minSeqNo = v; }

    public String getIdentitySource() { return identitySource; }
    public void setIdentitySource(String v) { this.identitySource = v; }

    public String toFingerprintLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(physicalShipKey).append('|');
        sb.append(imo == null ? "" : imo).append('|');
        List<String> sortedMmsis = new ArrayList<String>(mmsis);
        Collections.sort(sortedMmsis);
        for (int i = 0; i < sortedMmsis.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(sortedMmsis.get(i));
        }
        sb.append('|');
        sb.append(shipType == null ? "" : shipType);
        return sb.toString();
    }
}
