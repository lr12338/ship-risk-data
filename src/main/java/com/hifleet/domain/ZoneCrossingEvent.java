package com.hifleet.domain;

/**
 * 船舶区域进出事件，进入和离开各一条记录。
 */
public class ZoneCrossingEvent {

    public static final String ENTER = "ENTER";
    public static final String EXIT = "EXIT";

    /** 大风浪区域（PostgreSQL regions_wave） */
    public static final String CATEGORY_STORM_WAVE = "STORM_WAVE";
    /** 大风区域（PostgreSQL regions_wind） */
    public static final String CATEGORY_STORM_WIND = "STORM_WIND";
    /** 海峡（MySQL ais41.alertzones，参考 event 项目） */
    public static final String CATEGORY_STRAIT = "STRAIT";
    /** 海区（MySQL ais41.world_sea，参考 event 项目） */
    public static final String CATEGORY_SEA_AREA = "SEA_AREA";
    /** 桥区（MySQL ais41.alertzones，parent=594504766@qq.com，name 非「风电」开头，如「围栏××大桥」） */
    public static final String CATEGORY_BRIDGE = "BRIDGE";
    /** 风电区（MySQL ais41.alertzones，parent=594504766@qq.com，name 以「风电」开头） */
    public static final String CATEGORY_WIND_FARM = "WIND_FARM";
    /** 高风险警示区（MySQL ais41.alertzones，parent=alertarea） */
    public static final String CATEGORY_HIGH_RISK = "HIGH_RISK";
    /** 冰区（MySQL ais41.alertzones，parent=alertarea） */
    public static final String CATEGORY_ICE = "ICE";
    /** 海洋保护区（MySQL ais41.alertzones，parent=alertarea） */
    public static final String CATEGORY_MARINE_PROTECTED = "MARINE_PROTECTED";
    /** 养殖区（MySQL ais41.alertzones，parent=alertarea） */
    public static final String CATEGORY_AQUACULTURE = "AQUACULTURE";
    /** 排放控制区（MySQL ais41.alertzones，parent=alertarea） */
    public static final String CATEGORY_EMISSION_CONTROL = "EMISSION_CONTROL";
    /** MARPOL 区域（MySQL ais41.alertzones，parent=alertarea） */
    public static final String CATEGORY_MARPOL = "MARPOL";
    /** JWC 区域（MySQL ais41.alertzones，parent=alertarea，name 含 (JWC)） */
    public static final String CATEGORY_JWC = "JWC";

    private String mmsi;
    private int zoneId;
    private String zoneName;
    /** 事件大类：STORM_WAVE / STORM_WIND / STRAIT / SEA_AREA / BRIDGE / WIND_FARM / HIGH_RISK / ICE 等 */
    private String eventCategory;
    /** ENTER 或 EXIT */
    private String eventType;
    private String eventTime;
    private Double lon;
    private Double lat;
    private Integer portId;
    private Integer zoneType;
    /** 数据来源表名 */
    private String zoneSource;

    public String getMmsi() {
        return mmsi;
    }

    public void setMmsi(String mmsi) {
        this.mmsi = mmsi;
    }

    public int getZoneId() {
        return zoneId;
    }

    public void setZoneId(int zoneId) {
        this.zoneId = zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public String getEventCategory() {
        return eventCategory;
    }

    public void setEventCategory(String eventCategory) {
        this.eventCategory = eventCategory;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventTime() {
        return eventTime;
    }

    public void setEventTime(String eventTime) {
        this.eventTime = eventTime;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Integer getPortId() {
        return portId;
    }

    public void setPortId(Integer portId) {
        this.portId = portId;
    }

    public Integer getZoneType() {
        return zoneType;
    }

    public void setZoneType(Integer zoneType) {
        this.zoneType = zoneType;
    }

    public String getZoneSource() {
        return zoneSource;
    }

    public void setZoneSource(String zoneSource) {
        this.zoneSource = zoneSource;
    }

    @Override
    public String toString() {
        return "ZoneCrossingEvent{" +
                "mmsi='" + mmsi + '\'' +
                ", zoneId=" + zoneId +
                ", zoneName='" + zoneName + '\'' +
                ", eventCategory='" + eventCategory + '\'' +
                ", eventType='" + eventType + '\'' +
                ", eventTime='" + eventTime + '\'' +
                '}';
    }
}
