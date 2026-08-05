package com.hifleet.regionbehavior;

public final class RegionShipMetadataSummary {
    private int discoveredVessels;
    private int validMmsiVessels;
    private int metadataFound;
    private int eligibleMainShips;
    private int excludedByType;
    private int unknownShipType;
    private int metadataQueryFailures;

    public int getDiscoveredVessels() { return discoveredVessels; }
    public int getValidMmsiVessels() { return validMmsiVessels; }
    public int getMetadataFound() { return metadataFound; }
    public int getEligibleMainShips() { return eligibleMainShips; }
    public int getExcludedByType() { return excludedByType; }
    public int getUnknownShipType() { return unknownShipType; }
    public int getMetadataQueryFailures() { return metadataQueryFailures; }
    void discovered() { discoveredVessels++; }
    void validMmsi() { validMmsiVessels++; }
    void metadataFound() { metadataFound++; }
    void eligible() { eligibleMainShips++; }
    void excluded() { excludedByType++; }
    void unknown() { unknownShipType++; }
    void metadataQueryFailure() { metadataQueryFailures++; }
}
