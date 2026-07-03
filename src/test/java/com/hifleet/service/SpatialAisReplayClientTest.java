package com.hifleet.service;

import com.hifleet.domain.ReplayQueryRequest;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpatialAisReplayClientTest {

    @Test
    public void buildUriUsesStarttimeParameter() throws Exception {
        SpatialAisReplayClient client = new SpatialAisReplayClient();
        ReflectionTestUtils.setField(client, "replayUrl", "https://gm.hifleet.com/spatialais/getRecentAis");

        ReplayQueryRequest request = new ReplayQueryRequest();
        request.setStartTime("2026-06-28 18:15:32");
        request.setEndTime("2026-06-28 19:35:46");
        request.setDevSize(50000);
        request.setMinLon(122.19858830700687);
        request.setMaxLon(122.22091169299313);
        request.setMinLat(29.879026666666668);
        request.setMaxLat(29.897003333333334);

        Method buildUri = SpatialAisReplayClient.class.getDeclaredMethod("buildUri", ReplayQueryRequest.class);
        buildUri.setAccessible(true);
        URI uri = (URI) buildUri.invoke(client, request);

        String query = URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8.name());
        assertTrue(query.contains("starttime=2026-06-28 18:15:32"));
        assertFalse(query.contains("startime="));
    }
}
