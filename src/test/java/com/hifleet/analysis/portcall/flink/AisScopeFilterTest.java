package com.hifleet.analysis.portcall.flink;

import com.hifleet.analysis.portcall.model.AisPoint;
import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AisScopeFilterTest {

    @Test
    public void shouldPassAllWhenNoScopeConfigured() throws Exception {
        AisScopeFilter filter = new AisScopeFilter(Collections.<Long>emptySet(), null, null, null, null);

        Assert.assertTrue(filter.filter(point(413193000L, 121.0d, 31.0d)));
    }

    @Test
    public void shouldFilterByMmsiWhitelistAndBoundingBox() throws Exception {
        Set<Long> whitelist = new HashSet<Long>();
        whitelist.add(413193000L);
        AisScopeFilter filter = new AisScopeFilter(whitelist, 120.5d, 122.0d, 30.5d, 31.5d);

        Assert.assertTrue(filter.filter(point(413193000L, 121.0d, 31.0d)));
        Assert.assertFalse(filter.filter(point(413433610L, 121.0d, 31.0d)));
        Assert.assertFalse(filter.filter(point(413193000L, 123.0d, 31.0d)));
    }

    private AisPoint point(Long mmsi, Double lon, Double lat) {
        AisPoint point = new AisPoint();
        point.setMmsi(mmsi);
        point.setUpdateTime(Instant.parse("2026-06-29T00:00:00Z"));
        point.setLon(lon);
        point.setLat(lat);
        point.setDataState("normal");
        return point;
    }
}
