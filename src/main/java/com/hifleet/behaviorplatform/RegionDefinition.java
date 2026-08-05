package com.hifleet.behaviorplatform;

import com.hifleet.regionbehavior.RegionGeometry;

public final class RegionDefinition {
    private final String regionId;
    private final int version;
    private final String regionName;
    private final String polygonWkt;
    private final double regionBufferNm;
    private final int discoveryTimeSliceMinutes;
    private final int maxCandidateVessels;
    private final String fingerprint;
    private final double areaSqKm;

    RegionDefinition(String regionId, int version, String regionName, String polygonWkt,
                     double regionBufferNm, int discoveryTimeSliceMinutes,
                     int maxCandidateVessels, RegionGeometry geometry) {
        this.regionId = regionId;
        this.version = version;
        this.regionName = regionName;
        this.polygonWkt = polygonWkt;
        this.regionBufferNm = regionBufferNm;
        this.discoveryTimeSliceMinutes = discoveryTimeSliceMinutes;
        this.maxCandidateVessels = maxCandidateVessels;
        this.fingerprint = geometry.getFingerprint();
        this.areaSqKm = geometry.getAreaSqKm();
    }
    public String getRegionId() { return regionId; }
    public int getVersion() { return version; }
    public String getRegionName() { return regionName; }
    public String getPolygonWkt() { return polygonWkt; }
    public double getRegionBufferNm() { return regionBufferNm; }
    public int getDiscoveryTimeSliceMinutes() { return discoveryTimeSliceMinutes; }
    public int getMaxCandidateVessels() { return maxCandidateVessels; }
    public String getFingerprint() { return fingerprint; }
    public double getAreaSqKm() { return areaSqKm; }
}
