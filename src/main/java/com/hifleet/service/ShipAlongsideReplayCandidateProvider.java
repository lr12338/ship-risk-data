package com.hifleet.service;

import com.hifleet.domain.AlongsideCandidateTarget;
import com.hifleet.domain.AlongsideReplaySearchResult;
import com.hifleet.domain.ReplayQueryRequest;
import com.hifleet.domain.ReplayQueryResult;
import com.hifleet.repository.AIS41GeneralQueryRepository;
import com.hifleet.trajectory.TrajectoryPoint;
import com.hifleet.trajectory.TrajectoryUtils;
import com.xxl.job.core.log.XxlJobLogger;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 getRecentAis 区域回放的 AUTO 候选目标船召回。
 */
@Component
public class ShipAlongsideReplayCandidateProvider {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${ship.behavior.alongside.replay.enabled:true}")
    private boolean replayEnabled;

    @Value("${ship.behavior.alongside.replay.dev-size:50000}")
    private int replayDevSize;

    @Value("${ship.behavior.alongside.replay.window-minutes:30}")
    private long replayWindowMinutes;

    @Value("${ship.behavior.alongside.replay.bbox-buffer-nm:10.0}")
    private double replayBboxBufferNm;

    @Value("${ship.behavior.alongside.replay.default-radius-nm:0.5}")
    private double replayDefaultRadiusNm;

    @Value("${ship.behavior.alongside.candidate.max-distance-nm:0.27}")
    private double candidateMaxDistanceNm;

    @Value("${ship.behavior.alongside.candidate.max-speed:3.0}")
    private double candidateMaxSpeed;

    @Value("${ship.behavior.alongside.candidate.min-speed:1.0}")
    private double candidateMinSpeed;

    @Value("${ship.behavior.alongside.candidate.max-time-gap-minutes:10}")
    private long candidateMaxTimeGapMinutes;

    @Value("${ship.behavior.alongside.candidate.min-points:3}")
    private int candidateMinPoints;

    @Value("${ship.behavior.alongside.candidate.min-duration-minutes:20}")
    private long candidateMinDurationMinutes;

    @Value("${ship.behavior.alongside.candidate.merchant-filter-enabled:true}")
    private boolean merchantFilterEnabled;

    @Autowired
    private SpatialAisReplayClient spatialAisReplayClient;

    @Autowired
    private AIS41GeneralQueryRepository ais41GeneralQueryRepository;

    @Autowired
    private ShipTypeClassifier shipTypeClassifier;

    void setSpatialAisReplayClient(SpatialAisReplayClient spatialAisReplayClient) {
        this.spatialAisReplayClient = spatialAisReplayClient;
    }

    public AlongsideReplaySearchResult findCandidates(String ownMmsi, List<ShipAlongsideDetector.LowSpeedWindow> windows,
                                                       Map<String, List<TrajectoryPoint>> ownPointsByWindow,
                                                       String batchNo) {
        AlongsideReplaySearchResult result = new AlongsideReplaySearchResult();
        if (!replayEnabled || windows == null || windows.isEmpty()) {
            return result;
        }
        Map<String, AlongsideCandidateTarget> candidatesByTargetAndWindow =
                new HashMap<String, AlongsideCandidateTarget>();
        for (ShipAlongsideDetector.LowSpeedWindow window : windows) {
            List<TrajectoryPoint> ownWindowPoints = ownPointsByWindow.get(windowKey(window));
            if (ownWindowPoints == null || ownWindowPoints.isEmpty()) {
                continue;
            }
            List<ReplayQueryRequest> requests = buildRequests(window, ownWindowPoints, batchNo);
            for (int i = 0; i < requests.size(); i++) {
                ReplayQueryRequest request = requests.get(i);
                ReplayQueryResult queryResult = spatialAisReplayClient.query(request);
                result.addReplayResult(queryResult);
                logReplayQuery(result.getReplayQueryCount(), request, queryResult, batchNo);
                if (queryResult == null || !queryResult.isSuccess()) {
                    continue;
                }
                collectCandidates(ownMmsi, window, ownWindowPoints, queryResult,
                        candidatesByTargetAndWindow, result);
            }
        }
        for (AlongsideCandidateTarget candidate : candidatesByTargetAndWindow.values()) {
            result.addCandidate(candidate);
        }
        return result;
    }

    private List<ReplayQueryRequest> buildRequests(ShipAlongsideDetector.LowSpeedWindow window,
                                                   List<TrajectoryPoint> points, String batchNo) {
        List<ReplayQueryRequest> requests = new ArrayList<ReplayQueryRequest>();
        LocalDateTime start = parseTime(window.getStartTime());
        LocalDateTime end = parseTime(window.getEndTime());
        long step = Math.max(1L, replayWindowMinutes);
        LocalDateTime current = start;
        while (!current.isAfter(end)) {
            LocalDateTime partEnd = current.plusMinutes(step);
            if (partEnd.isAfter(end)) {
                partEnd = end;
            }
            ReplayQueryRequest request = buildRequest(points, current, partEnd, batchNo);
            requests.add(request);
            if (!partEnd.isBefore(end)) {
                break;
            }
            current = partEnd;
        }
        return requests;
    }

    private ReplayQueryRequest buildRequest(List<TrajectoryPoint> points, LocalDateTime start,
                                            LocalDateTime end, String batchNo) {
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
        centerLat = centerLat / Math.max(1, points.size());
        double latBufferDeg = replayBboxBufferNm / 60.0;
        double lonBufferDeg = replayBboxBufferNm / Math.max(1.0, 60.0 * Math.cos(Math.toRadians(centerLat)));
        ReplayQueryRequest request = new ReplayQueryRequest();
        request.setStartTime(formatTime(start));
        request.setEndTime(formatTime(end));
        request.setMinLat(minLat - latBufferDeg);
        request.setMaxLat(maxLat + latBufferDeg);
        request.setMinLon(minLon - lonBufferDeg);
        request.setMaxLon(maxLon + lonBufferDeg);
        request.setRadiusNm(replayDefaultRadiusNm);
        request.setDevSize(replayDevSize);
        request.setBatchNo(batchNo);
        return request;
    }

    private void collectCandidates(String ownMmsi, ShipAlongsideDetector.LowSpeedWindow window,
                                   List<TrajectoryPoint> ownWindowPoints, ReplayQueryResult queryResult,
                                   Map<String, AlongsideCandidateTarget> candidatesByTargetAndWindow,
                                   AlongsideReplaySearchResult searchResult) {
        Map<String, String> shipTypes = loadShipTypes(queryResult.getTracksByMmsi().keySet());
        for (Map.Entry<String, List<TrajectoryPoint>> entry : queryResult.getTracksByMmsi().entrySet()) {
            String targetMmsi = entry.getKey();
            if (StringUtils.isBlank(targetMmsi) || targetMmsi.equals(ownMmsi)) {
                continue;
            }
            if (merchantFilterEnabled && shipTypeClassifier != null
                    && Boolean.FALSE.equals(shipTypeClassifier.isMerchant(shipTypes.get(targetMmsi)))) {
                continue;
            }
            List<TrajectoryPoint> targetPoints = cleanAndSort(entry.getValue());
            CandidateMatchStats stats = matchCandidate(ownWindowPoints, targetPoints);
            if (!stats.isCandidate()) {
                continue;
            }
            searchResult.incrementPrefilteredTargets();
            String key = window.getStartTime() + "|" + window.getEndTime() + "|" + targetMmsi;
            AlongsideCandidateTarget candidate = candidatesByTargetAndWindow.get(key);
            if (candidate == null) {
                candidate = new AlongsideCandidateTarget();
                candidate.setOwnMmsi(ownMmsi);
                candidate.setTargetMmsi(targetMmsi);
                candidate.setWindowStart(window.getStartTime());
                candidate.setWindowEnd(window.getEndTime());
                candidate.setOwnWindowPoints(new ArrayList<TrajectoryPoint>(ownWindowPoints));
                candidate.setTargetPoints(new ArrayList<TrajectoryPoint>());
                candidate.setReplayQueryResult(queryResult);
                candidatesByTargetAndWindow.put(key, candidate);
            }
            candidate.setTargetPoints(mergePoints(candidate.getTargetPoints(), targetPoints));
            candidate.setMatchedPointCount(Math.max(candidate.getMatchedPointCount(), stats.getMatchedPointCount()));
            candidate.setMinDistanceNm(candidate.getMinDistanceNm() == 0.0
                    ? stats.getMinDistanceNm() : Math.min(candidate.getMinDistanceNm(), stats.getMinDistanceNm()));
            candidate.setMaxDistanceNm(Math.max(candidate.getMaxDistanceNm(), stats.getMaxDistanceNm()));
            candidate.setAvgDistanceNm(stats.getAvgDistanceNm());
        }
    }

    private Map<String, String> loadShipTypes(Set<String> mmsis) {
        if (!merchantFilterEnabled || ais41GeneralQueryRepository == null || shipTypeClassifier == null) {
            return Collections.emptyMap();
        }
        try {
            return ais41GeneralQueryRepository.selectShipTypes(mmsis);
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private CandidateMatchStats matchCandidate(List<TrajectoryPoint> ownPoints, List<TrajectoryPoint> targetPoints) {
        CandidateMatchStats stats = new CandidateMatchStats();
        if (targetPoints.size() < candidateMinPoints || !hasLowSpeedPoint(targetPoints)) {
            return stats;
        }
        for (TrajectoryPoint ownPoint : ownPoints) {
            TrajectoryPoint best = findNearestTimePoint(ownPoint, targetPoints);
            if (best == null) {
                continue;
            }
            long gap = Math.abs(Duration.between(ownPoint.getTime(), best.getTime()).toMinutes());
            if (gap > candidateMaxTimeGapMinutes) {
                continue;
            }
            double distanceNm = TrajectoryUtils.distanceNm(ownPoint, best);
            if (distanceNm > candidateMaxDistanceNm) {
                continue;
            }
            stats.addMatch(ownPoint, distanceNm);
        }
        return stats;
    }

    private TrajectoryPoint findNearestTimePoint(TrajectoryPoint ownPoint, List<TrajectoryPoint> targetPoints) {
        TrajectoryPoint best = null;
        long bestGapSeconds = Long.MAX_VALUE;
        for (TrajectoryPoint targetPoint : targetPoints) {
            long gapSeconds = Math.abs(Duration.between(ownPoint.getTime(), targetPoint.getTime()).getSeconds());
            if (gapSeconds < bestGapSeconds) {
                bestGapSeconds = gapSeconds;
                best = targetPoint;
            }
        }
        return best;
    }

    private boolean hasLowSpeedPoint(List<TrajectoryPoint> points) {
        for (TrajectoryPoint point : points) {
            if (point.getSpeed() >= candidateMinSpeed && point.getSpeed() <= candidateMaxSpeed) {
                return true;
            }
        }
        return false;
    }

    private List<TrajectoryPoint> cleanAndSort(List<TrajectoryPoint> points) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        List<TrajectoryPoint> sorted = new ArrayList<TrajectoryPoint>(points);
        Collections.sort(sorted, new Comparator<TrajectoryPoint>() {
            @Override
            public int compare(TrajectoryPoint a, TrajectoryPoint b) {
                return a.getTime().compareTo(b.getTime());
            }
        });
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        Set<String> seen = new HashSet<String>();
        for (TrajectoryPoint point : sorted) {
            if (point == null || point.getTime() == null
                    || point.getLon() < -180.0 || point.getLon() > 180.0
                    || point.getLat() < -90.0 || point.getLat() > 90.0
                    || point.getSpeed() < 0.0 || point.getSpeed() > 80.0) {
                continue;
            }
            String key = point.getMmsi() + "|" + point.getTime() + "|" + point.getLon() + "|" + point.getLat();
            if (seen.add(key)) {
                result.add(point);
            }
        }
        return result;
    }

    private List<TrajectoryPoint> mergePoints(List<TrajectoryPoint> a, List<TrajectoryPoint> b) {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        if (a != null) {
            result.addAll(a);
        }
        if (b != null) {
            result.addAll(b);
        }
        return cleanAndSort(result);
    }

    public String windowKey(ShipAlongsideDetector.LowSpeedWindow window) {
        return window.getStartTime() + "|" + window.getEndTime();
    }

    private LocalDateTime parseTime(String time) {
        return LocalDateTime.parse(time, TIME_FORMATTER);
    }

    private String formatTime(LocalDateTime time) {
        return time.format(TIME_FORMATTER);
    }

    private void logReplayQuery(int index, ReplayQueryRequest request, ReplayQueryResult result, String batchNo) {
        XxlJobLogger.log("ALONGSIDE_V3 replay_query[" + index + "]"
                + " start=" + request.getStartTime()
                + " end=" + request.getEndTime()
                + " minlon=" + request.getMinLon()
                + " maxlon=" + request.getMaxLon()
                + " minlat=" + request.getMinLat()
                + " maxlat=" + request.getMaxLat()
                + " httpStatus=" + (result == null ? 0 : result.getHttpStatus())
                + " responseStatus=" + (result == null ? nullToDash(null) : nullToDash(result.getResponseStatus()))
                + " responseMsg=" + (result == null ? nullToDash(null) : nullToDash(result.getResponseMessage()))
                + " returnedVessels=" + (result == null ? 0 : result.getRawVesselCount())
                + " rawPoints=" + (result == null ? 0 : result.getRawPointCount())
                + " validPoints=" + (result == null ? 0 : result.getValidPointCount())
                + " parseErrors=" + (result == null ? 0 : result.getParseErrorCount())
                + " elapsedMs=" + (result == null ? 0 : result.getElapsedMs())
                + " truncated=" + (result != null && result.isTruncated())
                + " success=" + (result != null && result.isSuccess())
                + " error=" + (result == null ? nullToDash(null) : nullToDash(result.getErrorMessage()))
                + " batchNo=" + batchNo);
    }

    private String nullToDash(String value) {
        return StringUtils.isBlank(value) ? "-" : value;
    }

    private class CandidateMatchStats {
        private int matchedPointCount;
        private double minDistanceNm = Double.MAX_VALUE;
        private double maxDistanceNm;
        private double totalDistanceNm;
        private LocalDateTime firstTime;
        private LocalDateTime lastTime;

        private void addMatch(TrajectoryPoint ownPoint, double distanceNm) {
            matchedPointCount++;
            minDistanceNm = Math.min(minDistanceNm, distanceNm);
            maxDistanceNm = Math.max(maxDistanceNm, distanceNm);
            totalDistanceNm += distanceNm;
            if (firstTime == null || ownPoint.getTime().isBefore(firstTime)) {
                firstTime = ownPoint.getTime();
            }
            if (lastTime == null || ownPoint.getTime().isAfter(lastTime)) {
                lastTime = ownPoint.getTime();
            }
        }

        private boolean isCandidate() {
            return matchedPointCount >= candidateMinPoints
                    && firstTime != null
                    && lastTime != null
                    && Duration.between(firstTime, lastTime).toMinutes() >= candidateMinDurationMinutes;
        }

        private int getMatchedPointCount() {
            return matchedPointCount;
        }

        private double getMinDistanceNm() {
            return minDistanceNm == Double.MAX_VALUE ? 0.0 : minDistanceNm;
        }

        private double getMaxDistanceNm() {
            return maxDistanceNm;
        }

        private double getAvgDistanceNm() {
            return totalDistanceNm / Math.max(1, matchedPointCount);
        }
    }
}
