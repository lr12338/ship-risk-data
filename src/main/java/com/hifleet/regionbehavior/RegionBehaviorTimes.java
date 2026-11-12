package com.hifleet.regionbehavior;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class RegionBehaviorTimes {
    static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private RegionBehaviorTimes() {
    }

    static LocalDateTime parse(String value) {
        return LocalDateTime.parse(value, FORMATTER);
    }

    static String format(LocalDateTime value) {
        return value.format(FORMATTER);
    }
}
