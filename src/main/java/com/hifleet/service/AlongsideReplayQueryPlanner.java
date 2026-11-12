package com.hifleet.service;

import com.hifleet.domain.ReplayQueryRequest;
import com.hifleet.trajectory.TrajectoryPoint;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Single source of truth for replay slicing, BBOX construction and query deduplication. */
public final class AlongsideReplayQueryPlanner {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public AlongsideReplayQueryPlan plan(List<ShipAlongsideDetector.LowSpeedWindow> windows,
                                         Map<String, List<TrajectoryPoint>> ownPointsByWindow,
                                         String batchNo, long replayWindowMinutes,
                                         double replayBboxBufferNm, double replayDefaultRadiusNm,
                                         int replayDevSize) {
        Map<String, AlongsideReplayQueryPlan.PlannedQuery> unique =
                new LinkedHashMap<String, AlongsideReplayQueryPlan.PlannedQuery>();
        if (windows == null || ownPointsByWindow == null) {
            return new AlongsideReplayQueryPlan(new ArrayList<AlongsideReplayQueryPlan.PlannedQuery>());
        }
        for (ShipAlongsideDetector.LowSpeedWindow window : windows) {
            List<TrajectoryPoint> points = ownPointsByWindow.get(windowKey(window));
            if (points == null || points.isEmpty()) continue;
            for (ReplayQueryRequest request : buildRequests(window, points, batchNo,
                    replayWindowMinutes, replayBboxBufferNm, replayDefaultRadiusNm, replayDevSize)) {
                String key = queryKey(request);
                AlongsideReplayQueryPlan.PlannedQuery planned = unique.get(key);
                if (planned == null) {
                    planned = new AlongsideReplayQueryPlan.PlannedQuery(key, request);
                    unique.put(key, planned);
                }
                planned.addContext(window, points);
            }
        }
        return new AlongsideReplayQueryPlan(new ArrayList<AlongsideReplayQueryPlan.PlannedQuery>(unique.values()));
    }

    private List<ReplayQueryRequest> buildRequests(ShipAlongsideDetector.LowSpeedWindow window,
                                                    List<TrajectoryPoint> points, String batchNo,
                                                    long replayWindowMinutes, double replayBboxBufferNm,
                                                    double replayDefaultRadiusNm, int replayDevSize) {
        List<ReplayQueryRequest> requests = new ArrayList<ReplayQueryRequest>();
        LocalDateTime start = LocalDateTime.parse(window.getStartTime(), FORMATTER);
        LocalDateTime end = LocalDateTime.parse(window.getEndTime(), FORMATTER);
        long step = Math.max(1L, replayWindowMinutes);
        LocalDateTime current = start;
        while (!current.isAfter(end)) {
            LocalDateTime partEnd = current.plusMinutes(step);
            if (partEnd.isAfter(end)) partEnd = end;
            requests.add(buildRequest(points, current, partEnd, batchNo,
                    replayBboxBufferNm, replayDefaultRadiusNm, replayDevSize));
            if (!partEnd.isBefore(end)) break;
            current = partEnd;
        }
        return requests;
    }

    private ReplayQueryRequest buildRequest(List<TrajectoryPoint> points, LocalDateTime start,
                                            LocalDateTime end, String batchNo,
                                            double replayBboxBufferNm, double replayDefaultRadiusNm,
                                            int replayDevSize) {
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE, centerLat = 0.0;
        for (TrajectoryPoint point : points) {
            minLon = Math.min(minLon, point.getLon()); maxLon = Math.max(maxLon, point.getLon());
            minLat = Math.min(minLat, point.getLat()); maxLat = Math.max(maxLat, point.getLat());
            centerLat += point.getLat();
        }
        centerLat /= Math.max(1, points.size());
        double latBufferDeg = replayBboxBufferNm / 60.0;
        double lonBufferDeg = replayBboxBufferNm
                / Math.max(1.0, 60.0 * Math.cos(Math.toRadians(centerLat)));
        ReplayQueryRequest request = new ReplayQueryRequest();
        request.setStartTime(start.format(FORMATTER)); request.setEndTime(end.format(FORMATTER));
        request.setMinLat(minLat - latBufferDeg); request.setMaxLat(maxLat + latBufferDeg);
        request.setMinLon(minLon - lonBufferDeg); request.setMaxLon(maxLon + lonBufferDeg);
        request.setRadiusNm(replayDefaultRadiusNm); request.setDevSize(replayDevSize); request.setBatchNo(batchNo);
        return request;
    }

    public String queryKey(ReplayQueryRequest request) {
        return request.getStartTime() + "|" + request.getEndTime()
                + "|" + Double.doubleToLongBits(request.getMinLon())
                + "|" + Double.doubleToLongBits(request.getMaxLon())
                + "|" + Double.doubleToLongBits(request.getMinLat())
                + "|" + Double.doubleToLongBits(request.getMaxLat())
                + "|" + Double.doubleToLongBits(request.getRadiusNm())
                + "|" + request.getDevSize();
    }

    public static String windowKey(ShipAlongsideDetector.LowSpeedWindow window) {
        return window.getStartTime() + "|" + window.getEndTime();
    }
}
