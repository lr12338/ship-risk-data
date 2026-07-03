package com.hifleet.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ReplayQueryRequest;
import com.hifleet.domain.ReplayQueryResult;
import com.hifleet.trajectory.TrajectoryPoint;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * getRecentAis 区域回放接口客户端。
 */
@Component
public class SpatialAisReplayClient {

    private static final Logger log = LoggerFactory.getLogger(SpatialAisReplayClient.class);

    @Value("${ship.behavior.alongside.replay.url:https://gm.hifleet.com/spatialais/getRecentAis}")
    private String replayUrl;

    @Value("${ship.behavior.alongside.replay.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${ship.behavior.alongside.replay.read-timeout-ms:8000}")
    private int readTimeoutMs;

    @Value("${ship.behavior.alongside.replay.retry-times:1}")
    private int retryTimes;

    @Value("${ship.behavior.alongside.replay.retry-sleep-ms:500}")
    private long retrySleepMs;

    @Value("${ship.behavior.alongside.replay.large-result-threshold:30000}")
    private int largeResultThreshold;

    @Autowired
    private ReplayAisPointParser replayAisPointParser;

    public ReplayQueryResult query(ReplayQueryRequest request) {
        ReplayQueryResult last = null;
        int attempts = Math.max(0, retryTimes) + 1;
        for (int i = 0; i < attempts; i++) {
            last = doQuery(request);
            if (last.isSuccess()) {
                return last;
            }
            if (i + 1 < attempts) {
                sleepBeforeRetry();
            }
        }
        return last == null ? failed(request, "UNKNOWN_REPLAY_ERROR") : last;
    }

    private ReplayQueryResult doQuery(ReplayQueryRequest request) {
        long start = System.currentTimeMillis();
        ReplayQueryResult result = new ReplayQueryResult();
        result.setRequest(request);
        try {
            URI uri = buildUri(request);
            RequestConfig config = RequestConfig.custom()
                    .setConnectTimeout(connectTimeoutMs)
                    .setSocketTimeout(readTimeoutMs)
                    .setConnectionRequestTimeout(connectTimeoutMs)
                    .build();
            HttpGet httpGet = new HttpGet(uri);
            httpGet.setConfig(config);
            CloseableHttpClient httpClient = HttpClients.createDefault();
            try {
                CloseableHttpResponse response = httpClient.execute(httpGet);
                try {
                    int statusCode = response.getStatusLine().getStatusCode();
                    result.setHttpStatus(statusCode);
                    String body = response.getEntity() == null ? ""
                            : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    if (statusCode != HttpStatus.SC_OK) {
                        result.setErrorMessage("HTTP_STATUS_" + statusCode);
                        return result;
                    }
                    parseBody(body, request, result);
                    result.setSuccess(true);
                    return result;
                } finally {
                    response.close();
                }
            } finally {
                httpClient.close();
            }
        } catch (Exception ex) {
            result.setErrorMessage(ex.getMessage());
            log.warn("getRecentAis query failed, request={}", requestSummary(request), ex);
            return result;
        } finally {
            result.setElapsedMs(System.currentTimeMillis() - start);
        }
    }

    private URI buildUri(ReplayQueryRequest request) throws Exception {
        URIBuilder builder = new URIBuilder(replayUrl);
        builder.addParameter("starttime", request.getStartTime());
        builder.addParameter("endtime", request.getEndTime());
        builder.addParameter("devSize", String.valueOf(request.getDevSize()));
        if (request.getMinLat() != null && request.getMaxLat() != null
                && request.getMinLon() != null && request.getMaxLon() != null) {
            builder.addParameter("minlat", String.valueOf(request.getMinLat()));
            builder.addParameter("maxlat", String.valueOf(request.getMaxLat()));
            builder.addParameter("minlon", String.valueOf(request.getMinLon()));
            builder.addParameter("maxlon", String.valueOf(request.getMaxLon()));
        } else if (request.getCenterLon() != null && request.getCenterLat() != null
                && request.getRadiusNm() != null) {
            builder.addParameter("center", request.getCenterLon() + "," + request.getCenterLat());
            builder.addParameter("radius", String.valueOf(request.getRadiusNm()));
        }
        return builder.build();
    }

    private void parseBody(String body, ReplayQueryRequest request, ReplayQueryResult result) {
        if (StringUtils.isBlank(body)) {
            result.setErrorMessage("EMPTY_RESPONSE");
            return;
        }
        JSONObject root = JSON.parseObject(body);
        if (!"OK".equalsIgnoreCase(root.getString("status"))) {
            result.setErrorMessage("STATUS_" + root.getString("status"));
            return;
        }
        JSONObject data = root.getJSONObject("data");
        if (data == null || data.isEmpty()) {
            result.setTracksByMmsi(new HashMap<String, List<TrajectoryPoint>>());
            return;
        }
        Map<String, List<TrajectoryPoint>> tracks = new HashMap<String, List<TrajectoryPoint>>();
        int rawPoints = 0;
        int validPoints = 0;
        int parseErrors = 0;
        for (String mmsi : data.keySet()) {
            JSONArray values = data.getJSONArray(mmsi);
            if (values == null) {
                continue;
            }
            List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
            for (int i = 0; i < values.size(); i++) {
                rawPoints++;
                TrajectoryPoint point = replayAisPointParser.parse(values.getString(i));
                if (point == null) {
                    parseErrors++;
                    continue;
                }
                validPoints++;
                points.add(point);
            }
            if (!points.isEmpty()) {
                tracks.put(mmsi, points);
            }
        }
        result.setRawVesselCount(data.size());
        result.setRawPointCount(rawPoints);
        result.setValidPointCount(validPoints);
        result.setParseErrorCount(parseErrors);
        result.setLargeResult(rawPoints >= largeResultThreshold);
        result.setTruncated(request.getDevSize() > 0 && rawPoints >= request.getDevSize());
        result.setTracksByMmsi(tracks);
    }

    private ReplayQueryResult failed(ReplayQueryRequest request, String message) {
        ReplayQueryResult result = new ReplayQueryResult();
        result.setRequest(request);
        result.setErrorMessage(message);
        return result;
    }

    private void sleepBeforeRetry() {
        if (retrySleepMs <= 0L) {
            return;
        }
        try {
            Thread.sleep(retrySleepMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String requestSummary(ReplayQueryRequest request) {
        if (request == null) {
            return "";
        }
        return "start=" + request.getStartTime()
                + ", end=" + request.getEndTime()
                + ", minLon=" + request.getMinLon()
                + ", maxLon=" + request.getMaxLon()
                + ", minLat=" + request.getMinLat()
                + ", maxLat=" + request.getMaxLat();
    }
}
