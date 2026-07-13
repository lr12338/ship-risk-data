package com.hifleet.analysis.portcall.flink;

import com.hifleet.analysis.portcall.model.AisPoint;
import org.junit.Assert;
import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;

public class ValidAisPointFilterTest {

    private static final Instant NOW = Instant.parse("2026-07-07T06:00:00Z");

    @Test
    public void shouldAllowCurrentAndSmallFutureSkew() throws Exception {
        ValidAisPointFilter filter = filter();

        Assert.assertTrue(filter.filter(point(NOW)));
        Assert.assertTrue(filter.filter(point(NOW.plus(Duration.ofMinutes(5)))));
    }

    @Test
    public void shouldRejectClearlyFuturePoint() throws Exception {
        ValidAisPointFilter filter = filter();

        Assert.assertFalse(filter.filter(point(NOW.plus(Duration.ofMinutes(6)))));
    }

    @Test
    public void shouldKeepExistingValidityChecks() throws Exception {
        ValidAisPointFilter filter = filter();
        AisPoint nullTime = point(NOW);
        nullTime.setUpdateTime(null);
        AisPoint invalidPosition = point(NOW);
        invalidPosition.setLon(null);
        AisPoint rejectedState = point(NOW);
        rejectedState.setDataState("deleted");

        Assert.assertFalse(filter.filter(null));
        Assert.assertFalse(filter.filter(nullTime));
        Assert.assertFalse(filter.filter(invalidPosition));
        Assert.assertFalse(filter.filter(rejectedState));
    }

    private ValidAisPointFilter filter() {
        return new ValidAisPointFilter(
            Collections.singleton("normal"),
            Clock.fixed(NOW, ZoneOffset.UTC),
            Duration.ofMinutes(5));
    }

    private AisPoint point(Instant updateTime) {
        AisPoint point = new AisPoint();
        point.setMmsi(244150076L);
        point.setUpdateTime(updateTime);
        point.setLon(113.1d);
        point.setLat(22.2d);
        point.setDataState("normal");
        return point;
    }
}
