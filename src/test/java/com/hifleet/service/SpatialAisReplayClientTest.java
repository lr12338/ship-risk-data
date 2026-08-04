package com.hifleet.service;

import com.hifleet.domain.ReplayQueryRequest;
import com.hifleet.domain.ReplayQueryResult;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.SocketTimeoutException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SpatialAisReplayClientTest {

    @Test
    public void readTimeoutIsDetectedThroughNestedCause() {
        assertTrue(SpatialAisReplayClient.isReadTimeout(
                new RuntimeException(new SocketTimeoutException("Read timed out"))));
        assertFalse(SpatialAisReplayClient.isReadTimeout(
                new RuntimeException("Read timed out")));
    }

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

    @Test
    public void parseBodyAcceptsStatusOneAndLoadsTracks() throws Exception {
        SpatialAisReplayClient client = new SpatialAisReplayClient();
        ReflectionTestUtils.setField(client, "largeResultThreshold", 30000);
        ReflectionTestUtils.setField(client, "replayAisPointParser", new ReplayAisPointParser());

        ReplayQueryRequest request = new ReplayQueryRequest();
        request.setDevSize(50000);
        ReplayQueryResult result = new ReplayQueryResult();
        result.setRequest(request);

        String body = "{\"status\":\"1\",\"msg\":\"OK\",\"data\":{\"413193000\":["
                + "\"413193000;2026-06-28 18:43:06;122.2104;29.88851;35.6;0.1;50272;139.0;0.0;1;锚泊;;2026-06-27 20:00:00;CN ZOS;8.1;NORTH;9599810;D5UF3;散货船;散货船;292;45;45;0;LR\","
                + "\"413193000;2026-06-28 18:50:06;122.2105;29.88855;36.0;0.1;50272;139.0;0.0;1;锚泊;;2026-06-27 20:00:00;CN ZOS;8.1;NORTH;9599810;D5UF3;散货船;散货船;292;45;45;0;LR\"]}}";

        Method parseBody = SpatialAisReplayClient.class.getDeclaredMethod(
                "parseBody", String.class, ReplayQueryRequest.class, ReplayQueryResult.class);
        parseBody.setAccessible(true);
        parseBody.invoke(client, body, request, result);

        assertTrue(result.isSuccess());
        assertEquals("1", result.getResponseStatus());
        assertEquals("OK", result.getResponseMessage());
        assertEquals(1, result.getRawVesselCount());
        assertEquals(2, result.getRawPointCount());
        assertEquals(2, result.getValidPointCount());
        assertNotNull(result.getTracksByMmsi().get("413193000"));
        assertEquals(2, result.getTracksByMmsi().get("413193000").size());
    }

    @Test
    public void parseBodyKeepsFailureWhenStatusIsZero() throws Exception {
        SpatialAisReplayClient client = new SpatialAisReplayClient();
        ReplayQueryRequest request = new ReplayQueryRequest();
        ReplayQueryResult result = new ReplayQueryResult();
        result.setRequest(request);

        Method parseBody = SpatialAisReplayClient.class.getDeclaredMethod(
                "parseBody", String.class, ReplayQueryRequest.class, ReplayQueryResult.class);
        parseBody.setAccessible(true);
        parseBody.invoke(client, "{\"status\":\"0\",\"msg\":\"starttime or endtime is empty\",\"data\":null}",
                request, result);

        assertFalse(result.isSuccess());
        assertEquals("0", result.getResponseStatus());
        assertEquals("starttime or endtime is empty", result.getResponseMessage());
        assertTrue(result.getErrorMessage().contains("STATUS_0"));
    }
}
