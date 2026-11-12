package com.hifleet.regionbehavior;

import com.hifleet.trajectory.TrajectoryPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RegionShipCandidate {
    private final String mmsi;
    private final List<TrajectoryPoint> discoveryPoints = new ArrayList<TrajectoryPoint>();
    private String shipType;
    private boolean metadataFound;
    private boolean eligible;
    private String eligibilityStatus = "UNFILTERED";

    public RegionShipCandidate(String mmsi) {
        this.mmsi = mmsi;
    }

    void addPoint(TrajectoryPoint point) {
        discoveryPoints.add(point);
    }

    public String getMmsi() { return mmsi; }
    public List<TrajectoryPoint> getDiscoveryPoints() {
        return Collections.unmodifiableList(discoveryPoints);
    }
    public String getShipType() { return shipType; }
    public boolean isMetadataFound() { return metadataFound; }
    public boolean isEligible() { return eligible; }
    public String getEligibilityStatus() { return eligibilityStatus; }
    public void setMetadata(String shipType, boolean found, boolean eligible, String status) {
        this.shipType = shipType;
        this.metadataFound = found;
        this.eligible = eligible;
        this.eligibilityStatus = status;
    }
}
