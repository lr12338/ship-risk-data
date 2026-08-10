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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(ShipAlongsideReplayCandidateProvider.class);
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static volatile boolean legacyWarningLogged;

    @Value("${ship.behavior.alongside.replay.enabled:true}")
    private boolean replayEnabled;

    @Value("${ship.behavior.alongside.replay.dev-size:50000}")
    private int replayDevSize;

    @Value("${ship.behavior.alongside.replay.window-minutes:30}")
    private long replayWindowMinutes;

    @Value("${ship.behavior.alongside.replay.min-window-minutes:10}")
    private long replayMinWindowMinutes;

    @Value("${ship.behavior.alongside.replay.split-on-truncated:true}")
    private boolean splitOnTruncated;

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

    @Value("${ship.behavior.alongside.target-ship.filter-mode:NONE}")
    private String targetShipFilterMode;

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
        return findCandidates(ownMmsi, windows, ownPointsByWindow, batchNo, true);
    }

    public AlongsideReplaySearchResult findCandidates(String ownMmsi,
                                                       List<ShipAlongsideDetector.LowSpeedWindow> windows,
                                                       Map<String, List<TrajectoryPoint>> ownPointsByWindow,
                                                       String batchNo,
                                                       boolean logQueries) {
        AlongsideReplaySearchResult result = new AlongsideReplaySearchResult();
        warnLegacyMerchantFilterOnce();
        if (!replayEnabled || windows == null || windows.isEmpty()) {
            return result;
        }
        Map<String, AlongsideCandidateTarget> candidatesByTargetAndWindow =
                new HashMap<String, AlongsideCandidateTarget>();
        AlongsideReplayQueryPlan plan = planQueries(windows, ownPointsByWindow, batchNo);
        for (AlongsideReplayQueryPlan.PlannedQuery planned : plan.getQueries()) {
            ReplayQueryRequest request = planned.getRequest();
            ReplayExecution execution = executeReplay(request, result, batchNo, logQueries, 0);
            if (!execution.complete) continue;
            ReplayQueryResult queryResult = execution.result;
            result.recordSuccessfulReplayQueries(execution.leafQueries);
            result.addReplayData(queryResult);
            for (AlongsideReplayQueryPlan.QueryContext context : planned.getContexts()) {
                collectCandidates(ownMmsi, context.getWindow(), context.getOwnWindowPoints(), queryResult,
                        candidatesByTargetAndWindow, result);
            }
        }
        for (AlongsideCandidateTarget candidate : candidatesByTargetAndWindow.values()) {
            result.addCandidate(candidate);
        }
        return result;
    }

    public AlongsideReplayQueryPlan planQueries(List<ShipAlongsideDetector.LowSpeedWindow> windows,
                                                Map<String, List<TrajectoryPoint>> ownPointsByWindow,
                                                String batchNo) {
        if (!replayEnabled) {
            return new AlongsideReplayQueryPlan(Collections.<AlongsideReplayQueryPlan.PlannedQuery>emptyList());
        }
        return new AlongsideReplayQueryPlanner().plan(windows, ownPointsByWindow, batchNo,
                replayWindowMinutes, replayBboxBufferNm, replayDefaultRadiusNm, replayDevSize);
    }

    private ReplayExecution executeReplay(ReplayQueryRequest request,
                                          AlongsideReplaySearchResult searchResult,
                                          String batchNo, boolean logQueries, int depth) {
        ReplayQueryResult queryResult = spatialAisReplayClient.query(request);
        if (queryResult != null && queryResult.isSuccess() && queryResult.isTruncated()
                && splitOnTruncated && canSplit(request)) {
            searchResult.recordSplitReplayResult(queryResult);
            if (logQueries) {
                logReplayQuery(searchResult.getReplayQueryCount(), request, queryResult, batchNo);
            }
            List<ReplayQueryRequest> children = split(request);
            XxlJobLogger.log("ALONGSIDE_V3 replay_split depth=" + depth
                    + " parentStart=" + request.getStartTime()
                    + " parentEnd=" + request.getEndTime()
                    + " child1Start=" + children.get(0).getStartTime()
                    + " child1End=" + children.get(0).getEndTime()
                    + " child2Start=" + children.get(1).getStartTime()
                    + " child2End=" + children.get(1).getEndTime()
                    + " rawPoints=" + queryResult.getRawPointCount()
                    + " batchNo=" + batchNo);
            ReplayExecution first = executeReplay(children.get(0), searchResult, batchNo, logQueries, depth + 1);
            if (!first.complete) {
                return ReplayExecution.incomplete();
            }
            ReplayExecution second = executeReplay(children.get(1), searchResult, batchNo, logQueries, depth + 1);
            if (!second.complete) {
                return ReplayExecution.incomplete();
            }
            ReplayQueryResult merged = merge(request, first.result, second.result);
            XxlJobLogger.log("ALONGSIDE_V3 replay_split_recovered depth=" + depth
                    + " start=" + request.getStartTime()
                    + " end=" + request.getEndTime()
                    + " leafQueries=" + (first.leafQueries + second.leafQueries)
                    + " vessels=" + merged.getRawVesselCount()
                    + " points=" + merged.getValidPointCount()
                    + " batchNo=" + batchNo);
            return ReplayExecution.complete(merged, first.leafQueries + second.leafQueries);
        }

        searchResult.recordTerminalReplayResult(queryResult);
        if (logQueries) {
            logReplayQuery(searchResult.getReplayQueryCount(), request, queryResult, batchNo);
        }
        if (queryResult == null || !queryResult.isSuccess()) {
            return ReplayExecution.incomplete();
        }
        if (queryResult.isTruncated()) {
            XxlJobLogger.log("ALONGSIDE_V3 replay_split_terminal depth=" + depth
                    + " start=" + request.getStartTime()
                    + " end=" + request.getEndTime()
                    + " rawPoints=" + queryResult.getRawPointCount()
                    + " minWindowMinutes=" + Math.max(1L, replayMinWindowMinutes)
                    + " splitEnabled=" + splitOnTruncated
                    + " batchNo=" + batchNo);
            return ReplayExecution.incomplete();
        }
        return ReplayExecution.complete(queryResult, 1);
    }

    private boolean canSplit(ReplayQueryRequest request) {
        LocalDateTime start = LocalDateTime.parse(request.getStartTime(), TIME_FORMATTER);
        LocalDateTime end = LocalDateTime.parse(request.getEndTime(), TIME_FORMATTER);
        return Duration.between(start, end).getSeconds()
                > Math.max(1L, replayMinWindowMinutes) * 60L;
    }

    private List<ReplayQueryRequest> split(ReplayQueryRequest request) {
        LocalDateTime start = LocalDateTime.parse(request.getStartTime(), TIME_FORMATTER);
        LocalDateTime end = LocalDateTime.parse(request.getEndTime(), TIME_FORMATTER);
        long minSeconds = Math.max(1L, replayMinWindowMinutes) * 60L;
        LocalDateTime firstEnd = start.plusSeconds(minSeconds);
        LocalDateTime secondStart = firstEnd;
        if (Duration.between(firstEnd, end).getSeconds() < minSeconds) {
            secondStart = end.minusSeconds(minSeconds);
        }
        List<ReplayQueryRequest> children = new ArrayList<ReplayQueryRequest>();
        children.add(copyWithTime(request, start, firstEnd));
        children.add(copyWithTime(request, secondStart, end));
        return children;
    }

    private ReplayQueryRequest copyWithTime(ReplayQueryRequest source,
                                            LocalDateTime start, LocalDateTime end) {
        ReplayQueryRequest copy = new ReplayQueryRequest();
        copy.setStartTime(start.format(TIME_FORMATTER));
        copy.setEndTime(end.format(TIME_FORMATTER));
        copy.setMinLat(source.getMinLat());
        copy.setMaxLat(source.getMaxLat());
        copy.setMinLon(source.getMinLon());
        copy.setMaxLon(source.getMaxLon());
        copy.setCenterLon(source.getCenterLon());
        copy.setCenterLat(source.getCenterLat());
        copy.setRadiusNm(source.getRadiusNm());
        copy.setDevSize(source.getDevSize());
        copy.setBatchNo(source.getBatchNo());
        return copy;
    }

    private ReplayQueryResult merge(ReplayQueryRequest request,
                                    ReplayQueryResult first, ReplayQueryResult second) {
        Map<String, List<TrajectoryPoint>> tracks = new HashMap<String, List<TrajectoryPoint>>();
        mergeTracks(tracks, first.getTracksByMmsi());
        mergeTracks(tracks, second.getTracksByMmsi());
        int validPoints = 0;
        for (Map.Entry<String, List<TrajectoryPoint>> entry : tracks.entrySet()) {
            List<TrajectoryPoint> clean = cleanAndSort(entry.getValue());
            entry.setValue(clean);
            validPoints += clean.size();
        }
        ReplayQueryResult merged = new ReplayQueryResult();
        merged.setRequest(request);
        merged.setSuccess(true);
        merged.setTruncated(false);
        merged.setLargeResult(first.isLargeResult() || second.isLargeResult());
        merged.setRawVesselCount(tracks.size());
        merged.setValidPointCount(validPoints);
        merged.setParseErrorCount(first.getParseErrorCount() + second.getParseErrorCount());
        merged.setRawPointCount(validPoints + merged.getParseErrorCount());
        merged.setElapsedMs(first.getElapsedMs() + second.getElapsedMs());
        merged.setTracksByMmsi(tracks);
        return merged;
    }

    private void mergeTracks(Map<String, List<TrajectoryPoint>> target,
                             Map<String, List<TrajectoryPoint>> source) {
        if (source == null) {
            return;
        }
        for (Map.Entry<String, List<TrajectoryPoint>> entry : source.entrySet()) {
            List<TrajectoryPoint> points = target.get(entry.getKey());
            if (points == null) {
                points = new ArrayList<TrajectoryPoint>();
                target.put(entry.getKey(), points);
            }
            if (entry.getValue() != null) {
                points.addAll(entry.getValue());
            }
        }
    }

    private void collectCandidates(String ownMmsi, ShipAlongsideDetector.LowSpeedWindow window,
                                   List<TrajectoryPoint> ownWindowPoints, ReplayQueryResult queryResult,
                                   Map<String, AlongsideCandidateTarget> candidatesByTargetAndWindow,
                                   AlongsideReplaySearchResult searchResult) {
        for (Map.Entry<String, List<TrajectoryPoint>> entry : queryResult.getTracksByMmsi().entrySet()) {
            String targetMmsi = entry.getKey();
            if (!isValidMmsi(targetMmsi) || targetMmsi.equals(ownMmsi)) {
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

    private boolean isValidMmsi(String mmsi) {
        return StringUtils.isNotBlank(mmsi) && mmsi.matches("\\d{7,9}");
    }

    private void warnLegacyMerchantFilterOnce() {
        if (merchantFilterEnabled && !legacyWarningLogged) {
            synchronized (ShipAlongsideReplayCandidateProvider.class) {
                if (!legacyWarningLogged) {
                    LOG.warn("ship.behavior.alongside.candidate.merchant-filter-enabled is deprecated; "
                            + "target vessels are no longer filtered by merchant type; use target-ship.filter-mode={}",
                            targetShipFilterMode);
                    legacyWarningLogged = true;
                }
            }
        }
    }

    private CandidateMatchStats matchCandidate(List<TrajectoryPoint> ownPoints, List<TrajectoryPoint> targetPoints) {
        CandidateMatchStats stats = new CandidateMatchStats();
        if (targetPoints.size() < candidateMinPoints || !hasLowSpeedPoint(targetPoints)) {
            return stats;
        }
        int targetIndex = 0;
        for (TrajectoryPoint ownPoint : ownPoints) {
            IndexedPoint best = findNearestTimePoint(ownPoint, targetPoints, targetIndex);
            if (best == null) {
                continue;
            }
            targetIndex = best.index + 1;
            long gap = Math.abs(Duration.between(ownPoint.getTime(), best.point.getTime()).toMinutes());
            if (gap > candidateMaxTimeGapMinutes) {
                continue;
            }
            double distanceNm = TrajectoryUtils.distanceNm(ownPoint, best.point);
            if (distanceNm > candidateMaxDistanceNm) {
                continue;
            }
            stats.addMatch(ownPoint, distanceNm);
        }
        return stats;
    }

    private IndexedPoint findNearestTimePoint(TrajectoryPoint ownPoint, List<TrajectoryPoint> targetPoints,
                                              int startIndex) {
        IndexedPoint best = null;
        long bestGapSeconds = Long.MAX_VALUE;
        for (int i = Math.max(0, startIndex); i < targetPoints.size(); i++) {
            TrajectoryPoint targetPoint = targetPoints.get(i);
            long gapSeconds = Math.abs(Duration.between(ownPoint.getTime(), targetPoint.getTime()).getSeconds());
            if (gapSeconds < bestGapSeconds) {
                bestGapSeconds = gapSeconds;
                best = new IndexedPoint(i, targetPoint);
            }
        }
        return best;
    }

    private static class IndexedPoint {
        private final int index;
        private final TrajectoryPoint point;
        private IndexedPoint(int index, TrajectoryPoint point) { this.index = index; this.point = point; }
    }

    private boolean hasLowSpeedPoint(List<TrajectoryPoint> points) {
        for (TrajectoryPoint point : points) {
            if (point.isSpeedValid() && point.getSpeed() >= 0.0 && point.getSpeed() <= candidateMaxSpeed) {
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
                    || !point.isSpeedValid() || point.getSpeed() < 0.0 || point.getSpeed() > 80.0) {
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
        return AlongsideReplayQueryPlanner.windowKey(window);
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

    private static class ReplayExecution {
        private final boolean complete;
        private final ReplayQueryResult result;
        private final int leafQueries;

        private ReplayExecution(boolean complete, ReplayQueryResult result, int leafQueries) {
            this.complete = complete;
            this.result = result;
            this.leafQueries = leafQueries;
        }

        private static ReplayExecution complete(ReplayQueryResult result, int leafQueries) {
            return new ReplayExecution(true, result, leafQueries);
        }

        private static ReplayExecution incomplete() {
            return new ReplayExecution(false, null, 0);
        }
    }
}
