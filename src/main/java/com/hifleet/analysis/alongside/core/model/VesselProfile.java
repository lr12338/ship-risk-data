package com.hifleet.analysis.alongside.core.model;

import java.io.Serializable;

public class VesselProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long mmsi;
    private VesselType vesselType;
    private VesselClass vesselClass;
    private Integer navigationStatus;

    public VesselProfile() {
    }

    public VesselProfile(Long mmsi, VesselType vesselType, VesselClass vesselClass, Integer navigationStatus) {
        this.mmsi = mmsi;
        this.vesselType = vesselType;
        this.vesselClass = vesselClass;
        this.navigationStatus = navigationStatus;
    }

    public Long getMmsi() {
        return mmsi;
    }

    public void setMmsi(Long mmsi) {
        this.mmsi = mmsi;
    }

    public VesselType getVesselType() {
        return vesselType;
    }

    public void setVesselType(VesselType vesselType) {
        this.vesselType = vesselType;
    }

    public VesselClass getVesselClass() {
        return vesselClass;
    }

    public void setVesselClass(VesselClass vesselClass) {
        this.vesselClass = vesselClass;
    }

    public Integer getNavigationStatus() {
        return navigationStatus;
    }

    public void setNavigationStatus(Integer navigationStatus) {
        this.navigationStatus = navigationStatus;
    }
}
