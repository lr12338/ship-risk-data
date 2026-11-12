package com.hifleet.worldsea;

public final class WorldSeaMatch {
    private final int areaId;
    private final String areaName;
    private final String areaCnName;

    WorldSeaMatch(int areaId, String areaName, String areaCnName) {
        this.areaId = areaId;
        this.areaName = areaName;
        this.areaCnName = areaCnName;
    }

    public int getAreaId() { return areaId; }
    public String getAreaName() { return areaName; }
    public String getAreaCnName() { return areaCnName; }
}
