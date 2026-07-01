package com.hifleet.service;

import com.hifleet.trajectory.TrajectoryPoint;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 解析 getRecentAis 返回的分号 AIS 字符串。
 */
@Component
public class ReplayAisPointParser {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public TrajectoryPoint parse(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String[] values = raw.split(";", -1);
        if (values.length < 6) {
            return null;
        }
        try {
            String mmsi = values[0].trim();
            LocalDateTime time = parseTime(values[1]);
            Double lon = parseDouble(values[2]);
            Double lat = parseDouble(values[3]);
            Double course = parseDouble(values[4]);
            Double speed = parseDouble(values[5]);
            if (StringUtils.isBlank(mmsi) || time == null || lon == null || lat == null
                    || speed == null || !validLonLat(lon, lat) || speed < 0.0 || speed > 80.0) {
                return null;
            }
            String navStatus = parseNavStatus(values);
            return new TrajectoryPoint(mmsi, time, lon, lat, speed,
                    course == null ? -1.0 : course, navStatus);
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalDateTime parseTime(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() == 16) {
            trimmed = trimmed + ":00";
        }
        return LocalDateTime.parse(trimmed, TIME_FORMATTER);
    }

    private Double parseDouble(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return Double.parseDouble(value.trim());
    }

    private boolean validLonLat(double lon, double lat) {
        return lon >= -180.0 && lon <= 180.0 && lat >= -90.0 && lat <= 90.0;
    }

    private String parseNavStatus(String[] values) {
        for (int i = 10; i < values.length; i++) {
            String value = values[i];
            if (StringUtils.isNotBlank(value) && containsLetterOrChinese(value)) {
                return value.trim();
            }
        }
        return values.length > 10 ? values[10] : null;
    }

    private boolean containsLetterOrChinese(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetter(c) || Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }
}
