package com.hifleet.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Loitering V3 跨扫描事件对账。只决定事件键和时间边界，不读写数据库。
 */
public class LoiteringV3EventReconciler {

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_MERGE = "MERGE";

    private static final double MIN_OVERLAP_RATIO = 0.50;
    private static final long MAX_EVENT_GAP_SECONDS = 30L * 60L;
    private static final double MAX_CENTER_DISTANCE_NM = 1.0;
    private static final double EARTH_RADIUS_NM = 3440.065;

    public Reconciliation reconcile(Candidate candidate, List<ExistingEvent> existingEvents) {
        validate(candidate);
        List<ExistingEvent> events = existingEvents == null
                ? Collections.<ExistingEvent>emptyList() : existingEvents;

        Match best = null;
        for (ExistingEvent existing : events) {
            Match match = match(candidate, existing);
            if (match != null && (best == null || match.isBetterThan(best))) {
                best = match;
            }
        }

        if (best == null) {
            return new Reconciliation(ACTION_CREATE, createEventKey(candidate),
                    candidate.getStartTime(), candidate.getEndTime());
        }
        return new Reconciliation(ACTION_MERGE, best.event.getEventKey(),
                earlier(candidate.getStartTime(), best.event.getStartTime()),
                later(candidate.getEndTime(), best.event.getEndTime()));
    }

    private Match match(Candidate candidate, ExistingEvent existing) {
        if (existing == null || isBlank(existing.getEventKey())
                || !candidate.getMmsi().equals(existing.getMmsi())
                || !candidate.getRuleVersion().equals(existing.getRuleVersion())
                || existing.getStartTime() == null || existing.getEndTime() == null
                || existing.getEndTime().isBefore(existing.getStartTime())
                || !isValidCoordinate(existing.getCenterLon(), existing.getCenterLat())) {
            return null;
        }

        double overlapRatio = overlapRatio(candidate.getStartTime(), candidate.getEndTime(),
                existing.getStartTime(), existing.getEndTime());
        long gapSeconds = gapSeconds(candidate.getStartTime(), candidate.getEndTime(),
                existing.getStartTime(), existing.getEndTime());
        boolean temporalMatch = overlapRatio >= MIN_OVERLAP_RATIO
                || (overlapRatio == 0.0 && gapSeconds <= MAX_EVENT_GAP_SECONDS);
        if (!temporalMatch) {
            return null;
        }

        double centerDistanceNm = distanceNm(candidate.getCenterLon(), candidate.getCenterLat(),
                existing.getCenterLon(), existing.getCenterLat());
        if (centerDistanceNm > MAX_CENTER_DISTANCE_NM) {
            return null;
        }
        return new Match(existing, overlapRatio, gapSeconds, centerDistanceNm);
    }

    private void validate(Candidate candidate) {
        if (candidate == null || isBlank(candidate.getMmsi())
                || isBlank(candidate.getRuleVersion())
                || candidate.getStartTime() == null || candidate.getEndTime() == null
                || candidate.getEndTime().isBefore(candidate.getStartTime())
                || !isValidCoordinate(candidate.getCenterLon(), candidate.getCenterLat())) {
            throw new IllegalArgumentException("candidate identity and time range must be valid");
        }
    }

    private double overlapRatio(LocalDateTime firstStart, LocalDateTime firstEnd,
                                LocalDateTime secondStart, LocalDateTime secondEnd) {
        LocalDateTime overlapStart = later(firstStart, secondStart);
        LocalDateTime overlapEnd = earlier(firstEnd, secondEnd);
        if (!overlapStart.isBefore(overlapEnd)) {
            return 0.0;
        }
        long overlapSeconds = Duration.between(overlapStart, overlapEnd).getSeconds();
        long firstSeconds = Math.max(1L, Duration.between(firstStart, firstEnd).getSeconds());
        long secondSeconds = Math.max(1L, Duration.between(secondStart, secondEnd).getSeconds());
        return overlapSeconds / (double) Math.min(firstSeconds, secondSeconds);
    }

    private long gapSeconds(LocalDateTime firstStart, LocalDateTime firstEnd,
                            LocalDateTime secondStart, LocalDateTime secondEnd) {
        if (!firstEnd.isAfter(secondStart)) {
            return Duration.between(firstEnd, secondStart).getSeconds();
        }
        if (!secondEnd.isAfter(firstStart)) {
            return Duration.between(secondEnd, firstStart).getSeconds();
        }
        return Long.MAX_VALUE;
    }

    private double distanceNm(double firstLon, double firstLat,
                              double secondLon, double secondLat) {
        double lat1 = Math.toRadians(firstLat);
        double lat2 = Math.toRadians(secondLat);
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(normalizeLongitudeDelta(secondLon - firstLon));
        double sinLat = Math.sin(deltaLat / 2.0);
        double sinLon = Math.sin(deltaLon / 2.0);
        double a = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        return EARTH_RADIUS_NM * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
    }

    private double normalizeLongitudeDelta(double delta) {
        if (delta > 180.0) {
            return delta - 360.0;
        }
        if (delta < -180.0) {
            return delta + 360.0;
        }
        return delta;
    }

    private String createEventKey(Candidate candidate) {
        String identity = candidate.getMmsi() + "|" + candidate.getRuleVersion()
                + "|" + candidate.getStartTime();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.substring(0, 32);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private LocalDateTime earlier(LocalDateTime first, LocalDateTime second) {
        return first.isBefore(second) ? first : second;
    }

    private LocalDateTime later(LocalDateTime first, LocalDateTime second) {
        return first.isAfter(second) ? first : second;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isValidCoordinate(double lon, double lat) {
        return !Double.isNaN(lon) && !Double.isInfinite(lon)
                && !Double.isNaN(lat) && !Double.isInfinite(lat)
                && lon >= -180.0 && lon <= 180.0
                && lat >= -90.0 && lat <= 90.0;
    }

    public static class Candidate {
        private final String mmsi;
        private final String ruleVersion;
        private final LocalDateTime startTime;
        private final LocalDateTime endTime;
        private final double centerLon;
        private final double centerLat;

        public Candidate(String mmsi, String ruleVersion, LocalDateTime startTime,
                         LocalDateTime endTime, double centerLon, double centerLat) {
            this.mmsi = mmsi;
            this.ruleVersion = ruleVersion;
            this.startTime = startTime;
            this.endTime = endTime;
            this.centerLon = centerLon;
            this.centerLat = centerLat;
        }

        public String getMmsi() {
            return mmsi;
        }

        public String getRuleVersion() {
            return ruleVersion;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public LocalDateTime getEndTime() {
            return endTime;
        }

        public double getCenterLon() {
            return centerLon;
        }

        public double getCenterLat() {
            return centerLat;
        }
    }

    public static class ExistingEvent extends Candidate {
        private final String eventKey;

        public ExistingEvent(String eventKey, String mmsi, String ruleVersion,
                             LocalDateTime startTime, LocalDateTime endTime,
                             double centerLon, double centerLat) {
            super(mmsi, ruleVersion, startTime, endTime, centerLon, centerLat);
            this.eventKey = eventKey;
        }

        public String getEventKey() {
            return eventKey;
        }
    }

    public static class Reconciliation {
        private final String action;
        private final String eventKey;
        private final LocalDateTime startTime;
        private final LocalDateTime endTime;

        private Reconciliation(String action, String eventKey,
                               LocalDateTime startTime, LocalDateTime endTime) {
            this.action = action;
            this.eventKey = eventKey;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public String getAction() {
            return action;
        }

        public String getEventKey() {
            return eventKey;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public LocalDateTime getEndTime() {
            return endTime;
        }
    }

    private static class Match {
        private final ExistingEvent event;
        private final double overlapRatio;
        private final long gapSeconds;
        private final double centerDistanceNm;

        private Match(ExistingEvent event, double overlapRatio,
                      long gapSeconds, double centerDistanceNm) {
            this.event = event;
            this.overlapRatio = overlapRatio;
            this.gapSeconds = gapSeconds;
            this.centerDistanceNm = centerDistanceNm;
        }

        private boolean isBetterThan(Match other) {
            int overlapCompare = Double.compare(overlapRatio, other.overlapRatio);
            if (overlapCompare != 0) {
                return overlapCompare > 0;
            }
            int gapCompare = Long.compare(gapSeconds, other.gapSeconds);
            if (gapCompare != 0) {
                return gapCompare < 0;
            }
            int distanceCompare = Double.compare(centerDistanceNm, other.centerDistanceNm);
            if (distanceCompare != 0) {
                return distanceCompare < 0;
            }
            return event.getEventKey().compareTo(other.event.getEventKey()) < 0;
        }
    }
}
