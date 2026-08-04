package com.hifleet.domain;

import java.time.LocalDateTime;

public class TargetShip {

    private int seqNo;
    private final String mmsi;
    private final String imo;
    private final String shipName;
    private final String shipType;
    private final LocalDateTime sourceUpdateTime;

    public TargetShip(String mmsi, String imo, String shipName, String shipType,
                      LocalDateTime sourceUpdateTime) {
        this.mmsi = mmsi;
        this.imo = imo;
        this.shipName = shipName;
        this.shipType = shipType;
        this.sourceUpdateTime = sourceUpdateTime;
    }

    public int getSeqNo() {
        return seqNo;
    }

    public void setSeqNo(int seqNo) {
        this.seqNo = seqNo;
    }

    public String getMmsi() {
        return mmsi;
    }

    public String getImo() {
        return imo;
    }

    public String getShipName() {
        return shipName;
    }

    public String getShipType() {
        return shipType;
    }

    public LocalDateTime getSourceUpdateTime() {
        return sourceUpdateTime;
    }
}
