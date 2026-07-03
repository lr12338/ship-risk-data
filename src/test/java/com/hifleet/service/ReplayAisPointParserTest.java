package com.hifleet.service;

import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ReplayAisPointParserTest {

    private final ReplayAisPointParser parser = new ReplayAisPointParser();

    @Test
    public void parsesNormalReplayPoint() {
        TrajectoryPoint point = parser.parse(
                "563161100;2023-03-06 16:07:36;110.80033;4.88386;224.4;9.7;122;229.0;-0.04;1;机动船在航;;202");

        assertNotNull(point);
        assertEquals("563161100", point.getMmsi());
        assertEquals(110.80033, point.getLon(), 0.000001);
        assertEquals(4.88386, point.getLat(), 0.000001);
        assertEquals(224.4, point.getCourse(), 0.000001);
        assertEquals(9.7, point.getSpeed(), 0.000001);
        assertEquals("机动船在航", point.getNavStatus());
    }

    @Test
    public void rejectsInvalidReplayPoint() {
        assertNull(parser.parse("563161100;2023-03-06 16:07:36;110.8"));
        assertNull(parser.parse("563161100;bad-time;110.8;4.8;224.4;9.7"));
        assertNull(parser.parse("563161100;2023-03-06 16:07:36;190.0;4.8;224.4;9.7"));
        assertNull(parser.parse("563161100;2023-03-06 16:07:36;110.8;4.8;224.4;90.0"));
    }
}
