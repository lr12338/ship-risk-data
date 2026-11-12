package com.hifleet.service;

import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class TrajectoryFingerprintTest {

    @Test
    public void fingerprintIsStableAndIncludesRoleAndOrder() {
        TrajectoryPoint first = new TrajectoryPoint("413000001",
                LocalDateTime.of(2026, 1, 1, 0, 0), 120.1, 30.2, 1.5, 90.0, "0");
        TrajectoryPoint second = new TrajectoryPoint("413000001",
                LocalDateTime.of(2026, 1, 1, 0, 5), 120.2, 30.3, 1.6, 91.0, null);

        String value = TrajectoryFingerprint.sha256("OWN", Arrays.asList(first, second));

        assertEquals(value, TrajectoryFingerprint.sha256("OWN", Arrays.asList(first, second)));
        assertNotEquals(value, TrajectoryFingerprint.sha256("TARGET", Arrays.asList(first, second)));
        assertNotEquals(value, TrajectoryFingerprint.sha256("OWN", Arrays.asList(second, first)));
    }
}
