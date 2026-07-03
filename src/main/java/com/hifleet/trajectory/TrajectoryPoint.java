package com.hifleet.trajectory;

import java.time.LocalDateTime;

/**
 * 轨迹类业务的统一点模型，避免各业务直接依赖 HBase 返回对象的字符串字段。
 */
public class TrajectoryPoint {

    private final String mmsi;
    private final LocalDateTime time;
    private final double lon;
    private final double lat;
    private final double speed;
    private final double course;
    private final String navStatus;

    public TrajectoryPoint(String mmsi, LocalDateTime time, double lon, double lat,
                           double speed, double course, String navStatus) {
        this.mmsi = mmsi;
        this.time = time;
        this.lon = lon;
        this.lat = lat;
        this.speed = speed;
        this.course = course;
        this.navStatus = navStatus;
    }

    public String getMmsi() {
        return mmsi;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public double getLon() {
        return lon;
    }

    public double getLat() {
        return lat;
    }

    public double getSpeed() {
        return speed;
    }

    public double getCourse() {
        return course;
    }

    public String getNavStatus() {
        return navStatus;
    }
}
