package com.hifleet.tankeropensea;

import java.time.LocalDateTime;

public class ShipMetadata {
    private final String mmsi;
    private final String imo;
    private final String shipName;
    private final String rawType;
    private final String rawMinorType;
    private final Integer aisShipTypeCode;
    private final Double lengthMeters;
    private final Long dwt;
    private final LocalDateTime metadataUpdatedAt;

    public ShipMetadata(String mmsi, String imo, String shipName,
                        String rawType, String rawMinorType, Integer aisShipTypeCode,
                        Double lengthMeters, Long dwt, LocalDateTime metadataUpdatedAt) {
        this.mmsi = mmsi;
        this.imo = imo;
        this.shipName = shipName;
        this.rawType = rawType;
        this.rawMinorType = rawMinorType;
        this.aisShipTypeCode = aisShipTypeCode;
        this.lengthMeters = lengthMeters;
        this.dwt = dwt;
        this.metadataUpdatedAt = metadataUpdatedAt;
    }

    public String getMmsi() { return mmsi; }
    public String getImo() { return imo; }
    public String getShipName() { return shipName; }
    public String getRawType() { return rawType; }
    public String getRawMinorType() { return rawMinorType; }
    public Integer getAisShipTypeCode() { return aisShipTypeCode; }
    public Double getLengthMeters() { return lengthMeters; }
    public Long getDwt() { return dwt; }
    public LocalDateTime getMetadataUpdatedAt() { return metadataUpdatedAt; }
}
