package com.hifleet.tankeropensea;

import com.hifleet.domain.ReplayQueryRequest;
import com.hifleet.domain.ReplayQueryResult;
import com.hifleet.service.SpatialAisReplayClient;
import com.hifleet.trajectory.TrajectoryPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TankerOpenSeaReplayProvider {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Logger log =
            LoggerFactory.getLogger(TankerOpenSeaReplayProvider.class);

    @Autowired
    private SpatialAisReplayClient client;

    @Value("${ship.behavior.tanker-open-sea.replay.dev-size:50000}")
    private int devSize;

    @Value("${ship.behavior.tanker-open-sea.replay.window-minutes:30}")
    private int initialWindowMinutes;

    @Value("${ship.behavior.tanker-open-sea.replay.min-split-minutes:5}")
    private int minSplitMinutes;

    @Value("${ship.behavior.tanker-open-sea.replay.bbox-buffer-nm:0.5}")
    private double bboxBufferNm;

    public SearchResult search(String ownMmsi, List<TrajectoryPoint> ownPoints,
                               String batchNo) {
        SearchResult result = new SearchResult();
        if (ownPoints == null || ownPoints.isEmpty()) return result;
        List<TrajectoryPoint> sorted = new ArrayList<TrajectoryPoint>(ownPoints);
        Collections.sort(sorted, Comparator.comparing(TrajectoryPoint::getTime));
        LocalDateTime start = sorted.get(0).getTime();
        LocalDateTime end = sorted.get(sorted.size() - 1).getTime();
        LocalDateTime cursor = start;
        while (cursor.isBefore(end)) {
            LocalDateTime next = cursor.plusMinutes(Math.max(1, initialWindowMinutes));
            if (next.isAfter(end)) next = end;
            queryPart(ownMmsi, sorted, cursor, next, batchNo, result);
            cursor = next;
        }
        if (result.nonTimeoutFailures > 0) {
            result.completeness = ReplayCompleteness.FAILED;
        } else if (result.unresolvedTruncations > 0) {
            result.completeness = ReplayCompleteness.TRUNCATED_UNRESOLVED;
        } else if (result.timeoutSkippedQueries > 0) {
            result.completeness = ReplayCompleteness.PARTIAL_TIMEOUT;
        } else if (result.splitRetries > 0) {
            result.completeness = ReplayCompleteness.TRUNCATED_RETRY_SPLIT;
        } else {
            result.completeness = ReplayCompleteness.COMPLETE;
        }
        result.tracks.remove(ownMmsi);
        return result;
    }

    private void queryPart(String ownMmsi, List<TrajectoryPoint> allPoints,
                           LocalDateTime start, LocalDateTime end, String batchNo,
                           SearchResult target) {
        List<TrajectoryPoint> points = between(allPoints, start, end);
        if (points.isEmpty()) return;
        ReplayQueryResult query = client.query(request(points, start, end, batchNo));
        target.queryCount++;
        if (query == null || !query.isSuccess()) {
            target.failedQueries++;
            if (query != null && query.isTimeout()) {
                target.timeoutSkippedQueries++;
                log.warn("REPLAY_TIMEOUT_SKIPPED batchNo={} ownMmsi={} start={} end={} "
                                + "continue=true",
                        batchNo, ownMmsi, start.format(FORMATTER),
                        end.format(FORMATTER));
            } else {
                target.nonTimeoutFailures++;
            }
            return;
        }
        if (query.isTruncated()) {
            long minutes = Duration.between(start, end).toMinutes();
            if (minutes <= minSplitMinutes) {
                target.unresolvedTruncations++;
                return;
            }
            target.splitRetries++;
            LocalDateTime middle = start.plusSeconds(
                    Math.max(60L, Duration.between(start, end).getSeconds() / 2L));
            queryPart(ownMmsi, allPoints, start, middle, batchNo, target);
            queryPart(ownMmsi, allPoints, middle, end, batchNo, target);
            return;
        }
        merge(target.tracks, query.getTracksByMmsi());
    }

    private ReplayQueryRequest request(List<TrajectoryPoint> points,
                                       LocalDateTime start, LocalDateTime end,
                                       String batchNo) {
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;
        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double centerLat = 0.0;
        for (TrajectoryPoint point : points) {
            minLon = Math.min(minLon, point.getLon());
            maxLon = Math.max(maxLon, point.getLon());
            minLat = Math.min(minLat, point.getLat());
            maxLat = Math.max(maxLat, point.getLat());
            centerLat += point.getLat();
        }
        centerLat /= points.size();
        double latBuffer = bboxBufferNm / 60.0;
        double lonBuffer = bboxBufferNm
                / Math.max(1.0, 60.0 * Math.cos(Math.toRadians(centerLat)));
        ReplayQueryRequest request = new ReplayQueryRequest();
        request.setStartTime(start.format(FORMATTER));
        request.setEndTime(end.format(FORMATTER));
        request.setMinLon(minLon - lonBuffer);
        request.setMaxLon(maxLon + lonBuffer);
        request.setMinLat(minLat - latBuffer);
        request.setMaxLat(maxLat + latBuffer);
        request.setRadiusNm(0.5);
        request.setDevSize(devSize);
        request.setBatchNo(batchNo);
        return request;
    }

    private List<TrajectoryPoint> between(List<TrajectoryPoint> points,
                                          LocalDateTime start, LocalDateTime end) {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        for (TrajectoryPoint point : points) {
            if (!point.getTime().isBefore(start) && !point.getTime().isAfter(end)) {
                result.add(point);
            }
        }
        return result;
    }

    private void merge(Map<String, List<TrajectoryPoint>> target,
                       Map<String, List<TrajectoryPoint>> source) {
        if (source == null) return;
        for (Map.Entry<String, List<TrajectoryPoint>> entry : source.entrySet()) {
            List<TrajectoryPoint> points = target.get(entry.getKey());
            if (points == null) {
                points = new ArrayList<TrajectoryPoint>();
                target.put(entry.getKey(), points);
            }
            if (entry.getValue() != null) points.addAll(entry.getValue());
            Collections.sort(points, Comparator.comparing(TrajectoryPoint::getTime));
        }
    }

    public static class SearchResult {
        private ReplayCompleteness completeness = ReplayCompleteness.COMPLETE;
        private final Map<String, List<TrajectoryPoint>> tracks =
                new LinkedHashMap<String, List<TrajectoryPoint>>();
        private int queryCount;
        private int failedQueries;
        private int timeoutSkippedQueries;
        private int nonTimeoutFailures;
        private int splitRetries;
        private int unresolvedTruncations;

        public ReplayCompleteness getCompleteness() { return completeness; }
        public Map<String, List<TrajectoryPoint>> getTracks() { return tracks; }
        public int getQueryCount() { return queryCount; }
        public int getFailedQueries() { return failedQueries; }
        public int getTimeoutSkippedQueries() { return timeoutSkippedQueries; }
        public int getSplitRetries() { return splitRetries; }
        public int getUnresolvedTruncations() { return unresolvedTruncations; }
    }
}
