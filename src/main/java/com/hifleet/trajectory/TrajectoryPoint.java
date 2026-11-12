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
    private final double heading;
    private final boolean speedValid;
    private final boolean courseValid;
    private final boolean headingValid;
    private final String navStatus;

    public TrajectoryPoint(String mmsi, LocalDateTime time, double lon, double lat,
                           double speed, double course, String navStatus) {
        this.mmsi = mmsi;
        this.time = time;
        this.lon = lon;
        this.lat = lat;
        this.speed = speed;
        this.course = course;
        this.heading = Double.NaN;
        this.speedValid = isFinite(speed) && speed >= 0.0;
        this.courseValid = isFinite(course) && course >= 0.0 && course <= 360.0;
        this.headingValid = false;
        this.navStatus = navStatus;
    }

    public TrajectoryPoint(String mmsi, LocalDateTime time, double lon, double lat,
                           Double speed, Double course, Double heading, String navStatus) {
        this.mmsi = mmsi;
        this.time = time;
        this.lon = lon;
        this.lat = lat;
        this.speed = speed == null ? Double.NaN : speed;
        this.course = course == null ? Double.NaN : course;
        this.heading = heading == null ? Double.NaN : heading;
        this.speedValid = speed != null && isFinite(speed) && speed >= 0.0;
        this.courseValid = course != null && isFinite(course) && course >= 0.0 && course <= 360.0;
        this.headingValid = heading != null && isFinite(heading) && heading >= 0.0 && heading <= 360.0;
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

    public double getHeading() { return heading; }
    public boolean isSpeedValid() { return speedValid; }
    public boolean isCourseValid() { return courseValid; }
    public boolean isHeadingValid() { return headingValid; }

    public String getNavStatus() {
        return navStatus;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
