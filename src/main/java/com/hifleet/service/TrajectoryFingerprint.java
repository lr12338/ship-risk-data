package com.hifleet.service;

import com.hifleet.trajectory.TrajectoryPoint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

public final class TrajectoryFingerprint {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TrajectoryFingerprint() {
    }

    public static String sha256(String role, List<TrajectoryPoint> points) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<TrajectoryPoint> safePoints = points == null
                    ? Collections.<TrajectoryPoint>emptyList() : points;
            for (int i = 0; i < safePoints.size(); i++) {
                TrajectoryPoint point = safePoints.get(i);
                if (point == null || point.getTime() == null) {
                    continue;
                }
                String line = value(role) + "|" + i + "|" + value(point.getMmsi())
                        + "|" + point.getTime().format(TIME_FORMATTER)
                        + "|" + Double.toString(point.getLon())
                        + "|" + Double.toString(point.getLat())
                        + "|" + Double.toString(point.getSpeed())
                        + "|" + Double.toString(point.getCourse())
                        + "|" + value(point.getNavStatus()) + "\n";
                digest.update(line.getBytes(StandardCharsets.UTF_8));
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            value.append(String.format("%02x", item & 0xff));
        }
        return value.toString();
    }
}
