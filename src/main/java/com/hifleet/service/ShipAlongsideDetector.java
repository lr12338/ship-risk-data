package com.hifleet.service;

import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.trajectory.TrajectoryPoint;
import com.hifleet.trajectory.TrajectorySegment;
import com.hifleet.trajectory.TrajectoryUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 船舶搭靠 V2：识别双船低速近距离候选片段，并输出保险风控可解释字段。
 */
public class ShipAlongsideDetector {

    public static final String SUB_TYPE_FORMAL = "ALONGSIDE_CONFIRMED_CANDIDATE";
    public static final String SUB_TYPE_WEAK = "WEAK_ALONGSIDE_CANDIDATE";

    public static final String CONFIDENCE_HIGH = "HIGH";
    public static final String CONFIDENCE_MEDIUM = "MEDIUM";
    public static final String CONFIDENCE_LOW = "LOW";

    public static final String DOWNGRADED_NORMAL_LIKELY = "DOWNGRADED_NORMAL_LIKELY";
    public static final String EXCLUDED_NORMAL = "EXCLUDED_NORMAL";
    public static final String VALID_RISK_EVENT = "VALID_RISK_EVENT";

    private DetectionStats lastStats = DetectionStats.empty();

    public List<ShipBehaviorEvent> detect(String mmsi, List<TrajectoryPoint> ownPoints,
                                          String targetMmsi, List<TrajectoryPoint> targetPoints,
                                          AlongsideRule rule) {
        List<ShipBehaviorTrace> traces = detectWithTrace(mmsi, ownPoints, targetMmsi, targetPoints, rule);
        List<ShipBehaviorEvent> events = new ArrayList<ShipBehaviorEvent>();
        for (ShipBehaviorTrace trace : traces) {
            events.add(trace.getEvent());
        }
        return events;
    }

    public List<ShipBehaviorTrace> detectWithTrace(String mmsi, List<TrajectoryPoint> ownPoints,
                                                   String targetMmsi, List<TrajectoryPoint> targetPoints,
                                                   AlongsideRule rule) {
        return detectWithTrace(mmsi, ownPoints, targetMmsi, targetPoints, rule, AlongsideContext.unknown());
    }

    public List<ShipBehaviorTrace> detectWithTrace(String mmsi, List<TrajectoryPoint> ownPoints,
                                                   String targetMmsi, List<TrajectoryPoint> targetPoints,
                                                   AlongsideRule rule, AlongsideContext context) {
        AlongsideRule effectiveRule = rule == null ? AlongsideRule.defaultRule() : rule;
        List<TrajectoryPoint> cleanOwnPoints = cleanAndSort(ownPoints);
        List<TrajectoryPoint> cleanTargetPoints = cleanAndSort(targetPoints);
        AlongsideContext effectiveContext = context == null ? AlongsideContext.unknown() : context;
        if (cleanOwnPoints.isEmpty() || cleanTargetPoints.isEmpty() || !effectiveContext.isOwnShipEligible()) {
            lastStats = DetectionStats.empty(cleanOwnPoints.size(), cleanTargetPoints.size());
            return Collections.emptyList();
        }
        List<MatchedSegment> rawSegments = detectRawSegments(cleanOwnPoints, cleanTargetPoints, effectiveRule);
        List<MatchedSegment> mergedSegments = mergeSegments(rawSegments, effectiveRule);
        List<ShipBehaviorTrace> traces = new ArrayList<ShipBehaviorTrace>();
        for (MatchedSegment segment : mergedSegments) {
            traces.add(buildTrace(mmsi, targetMmsi, segment, effectiveRule, effectiveContext));
        }
        lastStats = DetectionStats.from(cleanOwnPoints.size(), cleanTargetPoints.size(),
                rawSegments.size(), mergedSegments.size(), traces.size());
        return traces;
    }

    public DetectionStats getLastStats() {
        return lastStats;
    }

    public List<TrajectoryPoint> cleanAndSort(List<TrajectoryPoint> points) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        List<TrajectoryPoint> sorted = new ArrayList<TrajectoryPoint>(points);
        Collections.sort(sorted, new Comparator<TrajectoryPoint>() {
            @Override
            public int compare(TrajectoryPoint a, TrajectoryPoint b) {
                if (a == null && b == null) {
                    return 0;
                }
                if (a == null || a.getTime() == null) {
                    return 1;
                }
                if (b == null || b.getTime() == null) {
                    return -1;
                }
                return a.getTime().compareTo(b.getTime());
            }
        });
        List<TrajectoryPoint> clean = new ArrayList<TrajectoryPoint>();
        String lastKey = null;
        for (TrajectoryPoint point : sorted) {
            if (!isValidPoint(point)) {
                continue;
            }
            String key = point.getTime() + "|" + point.getLon() + "|" + point.getLat();
            if (!key.equals(lastKey)) {
                clean.add(point);
                lastKey = key;
            }
        }
        return clean;
    }

    public List<LowSpeedWindow> extractLowSpeedWindows(String mmsi, List<TrajectoryPoint> points,
                                                       double maxSpeed, long minDurationMinutes,
                                                       long splitGapMinutes) {
        return extractLowSpeedWindows(mmsi, points, 0.0, maxSpeed, minDurationMinutes, splitGapMinutes);
    }

    public List<LowSpeedWindow> extractLowSpeedWindows(String mmsi, List<TrajectoryPoint> points,
                                                       double minSpeed, double maxSpeed,
                                                       long minDurationMinutes, long splitGapMinutes) {
        List<TrajectoryPoint> cleanPoints = cleanAndSort(points);
        List<LowSpeedWindow> windows = new ArrayList<LowSpeedWindow>();
        List<TrajectoryPoint> current = new ArrayList<TrajectoryPoint>();
        for (TrajectoryPoint point : cleanPoints) {
            boolean split = false;
            if (!current.isEmpty()) {
                long gap = Duration.between(current.get(current.size() - 1).getTime(), point.getTime()).toMinutes();
                split = gap > splitGapMinutes;
            }
            if (split || point.getSpeed() < minSpeed || point.getSpeed() > maxSpeed) {
                collectLowSpeedWindow(mmsi, current, minDurationMinutes, windows);
                current.clear();
            }
            if (point.getSpeed() >= minSpeed && point.getSpeed() <= maxSpeed) {
                current.add(point);
            }
        }
        collectLowSpeedWindow(mmsi, current, minDurationMinutes, windows);
        return windows;
    }

    private List<MatchedSegment> detectRawSegments(List<TrajectoryPoint> ownPoints,
                                                   List<TrajectoryPoint> targetPoints,
                                                   AlongsideRule rule) {
        List<MatchedPoint> current = new ArrayList<MatchedPoint>();
        List<MatchedSegment> segments = new ArrayList<MatchedSegment>();
        int targetIndex = 0;
        for (TrajectoryPoint ownPoint : ownPoints) {
            MatchResult match = findNearest(ownPoint, targetPoints, targetIndex, rule.getMaxTimeGapMinutes());
            if (match != null) {
                targetIndex = match.getIndex() + 1;
            }
            if (isAlongsideCandidate(ownPoint, match, rule)) {
                current.add(new MatchedPoint(ownPoint, match.getPoint(), match.getDistanceNm(),
                        match.getTimeGapSeconds(), speedDiff(ownPoint, match.getPoint()),
                        headingParallelDiff(ownPoint, match.getPoint())));
            } else {
                collectSegment(current, rule, segments);
                current.clear();
            }
        }
        collectSegment(current, rule, segments);
        return segments;
    }

    private boolean isAlongsideCandidate(TrajectoryPoint ownPoint, MatchResult match, AlongsideRule rule) {
        if (match == null || !ownPoint.isSpeedValid() || !match.getPoint().isSpeedValid()) {
            return false;
        }
        boolean underway = ownPoint.getSpeed() >= rule.getConfirmMinSpeed()
                && ownPoint.getSpeed() <= rule.getConfirmMaxSpeed();
        boolean stationaryPair = ownPoint.getSpeed() >= 0.0
                && ownPoint.getSpeed() < rule.getConfirmMinSpeed()
                && match.getPoint().getSpeed() >= 0.0
                && match.getPoint().getSpeed() < rule.getConfirmMinSpeed();
        return (underway || stationaryPair)
                && match.getPoint().getSpeed() <= rule.getConfirmMaxSpeed()
                && speedDiff(ownPoint, match.getPoint()) <= rule.getMaxRelativeSpeed()
                && match.getDistanceNm() <= rule.getConfirmMaxDistanceNm()
                && headingAccepted(ownPoint, match.getPoint(), rule);
    }

    private MatchResult findNearest(TrajectoryPoint ownPoint, List<TrajectoryPoint> targetPoints,
                                    int startIndex, long maxTimeGapMinutes) {
        MatchResult best = null;
        for (int i = Math.max(0, startIndex); i < targetPoints.size(); i++) {
            TrajectoryPoint targetPoint = targetPoints.get(i);
            long gapSeconds = Math.abs(Duration.between(ownPoint.getTime(), targetPoint.getTime()).getSeconds());
            long gapMinutes = gapSeconds / 60L;
            if (best != null && targetPoint.getTime().isAfter(ownPoint.getTime()) && gapMinutes > maxTimeGapMinutes) {
                break;
            }
            if (gapMinutes > maxTimeGapMinutes) {
                continue;
            }
            double distanceNm = TrajectoryUtils.distanceNm(ownPoint, targetPoint);
            if (best == null
                    || gapSeconds < best.getTimeGapSeconds()
                    || gapSeconds == best.getTimeGapSeconds() && distanceNm < best.getDistanceNm()) {
                best = new MatchResult(i, targetPoint, distanceNm, gapSeconds);
            }
        }
        return best;
    }

    private void collectSegment(List<MatchedPoint> matches, AlongsideRule rule, List<MatchedSegment> segments) {
        if (matches.size() < rule.getMinPoints()) {
            return;
        }
        MatchedSegment segment = new MatchedSegment(new ArrayList<MatchedPoint>(matches));
        if (segment.getDurationMinutes() < rule.getWeakMinDurationMinutes()) {
            return;
        }
        segments.add(segment);
    }

    private List<MatchedSegment> mergeSegments(List<MatchedSegment> segments, AlongsideRule rule) {
        if (!rule.isMergeEnabled() || segments.size() < 2) {
            return segments;
        }
        List<MatchedSegment> result = new ArrayList<MatchedSegment>();
        MatchedSegment current = segments.get(0);
        for (int i = 1; i < segments.size(); i++) {
            MatchedSegment next = segments.get(i);
            if (shouldMerge(current, next, rule)) {
                current = current.merge(next);
            } else {
                result.add(current);
                current = next;
            }
        }
        result.add(current);
        return result;
    }

    private boolean shouldMerge(MatchedSegment a, MatchedSegment b, AlongsideRule rule) {
        long gap = Duration.between(a.getEndOwnPoint().getTime(), b.getStartOwnPoint().getTime()).toMinutes();
        if (gap < 0 || gap > rule.getMergeMaxGapMinutes()) {
            return false;
        }
        return TrajectoryUtils.distanceNm(a.getCenterPoint(), b.getCenterPoint()) <= rule.getMergeMaxCenterDistanceNm();
    }

    private ShipBehaviorTrace buildTrace(String mmsi, String targetMmsi, MatchedSegment segment,
                                         AlongsideRule rule, AlongsideContext context) {
        List<TrajectoryPoint> ownPoints = segment.getOwnPoints();
        List<TrajectoryPoint> targetPoints = segment.getTargetPoints();
        TrajectorySegment ownSegment = new TrajectorySegment(ownPoints);
        TrajectoryPoint start = ownSegment.getStartPoint();
        TrajectoryPoint end = ownSegment.getEndPoint();
        AlongsideFeature feature = calculateFeature(segment, rule);
        AlongsideScore score = calculateScore(feature, segment, rule, context);

        ShipBehaviorEvent event = new ShipBehaviorEvent();
        event.setBehaviorType(ShipBehaviorEvent.TYPE_ALONGSIDE);
        event.setMmsi(mmsi);
        event.setTargetMmsi(targetMmsi);
        event.setStartTime(TrajectoryUtils.formatTime(start));
        event.setEndTime(TrajectoryUtils.formatTime(end));
        event.setDurationMinutes(feature.getDurationMinutes());
        event.setStartLon(start.getLon());
        event.setStartLat(start.getLat());
        event.setEndLon(end.getLon());
        event.setEndLat(end.getLat());
        event.setMinDistanceNm(feature.getMinDistanceNm());
        event.setMaxDistanceNm(feature.getMaxDistanceNm());
        event.setAvgDistanceNm(feature.getAvgDistanceNm());
        event.setDistanceVarianceNm2(feature.getDistanceVarianceNm2());
        event.setCourseDifferenceDegree(feature.getCourseDifferenceDegree());
        event.setSpeedDifferenceKnots(feature.getAvgRelativeSpeed());
        event.setParallelRatio(feature.getHeadingParallelRatio());
        event.setNearPort(context.isNearPort());
        event.setNearAnchor(context.isNearAnchor());
        event.setOpenSea(context.isOpenSea());
        event.setOwnShipType(context.getOwnShipType());
        event.setTargetShipType(context.getTargetShipType());
        event.setAvgSpeed(TrajectoryUtils.averageSpeed(ownPoints));
        event.setPointCount(segment.getMatches().size());
        event.setRuleVersion(rule.getRuleVersion());
        event.setEventSubType(feature.isWeakCandidate() ? SUB_TYPE_WEAK : SUB_TYPE_FORMAL);
        event.setSeverityLevel(score.getSeverityLevel());
        event.setEventScore(score.getEventScore());
        event.setConfidenceLevel(feature.getConfidenceLevel());
        event.setExclusionFlag(score.getExclusionFlag());
        event.setExclusionReason(score.getExclusionReason());
        event.setAisGapCount(feature.getAisGapCount());
        event.setMaxAisGapMinutes(feature.getMaxAisGapMinutes());
        event.setTrackCoverageRatio(feature.getTrackCoverageRatio());
        event.setCenterLon(feature.getCenterLon());
        event.setCenterLat(feature.getCenterLat());
        event.setRadiusNm(feature.getRadiusNm());
        event.setScoreDetailJson(score.toJson());
        return new ShipBehaviorTrace(event, ownPoints, targetPoints);
    }

    private AlongsideFeature calculateFeature(MatchedSegment segment, AlongsideRule rule) {
        List<MatchedPoint> matches = segment.getMatches();
        long duration = segment.getDurationMinutes();
        double minDistance = Double.MAX_VALUE;
        double maxDistance = 0.0;
        double totalDistance = 0.0;
        double totalDistanceSquare = 0.0;
        double totalRelativeSpeed = 0.0;
        double totalCourseDifference = 0.0;
        List<Double> distances = new ArrayList<Double>();
        List<Long> timeGaps = new ArrayList<Long>();
        long maxTimeGapSeconds = 0L;
        int highTimeGapPoints = 0;
        int headingOkCount = 0;
        int headingKnownCount = 0;
        int aisGapCount = 0;
        long maxAisGapMinutes = 0L;
        for (int i = 0; i < matches.size(); i++) {
            MatchedPoint match = matches.get(i);
            minDistance = Math.min(minDistance, match.getDistanceNm());
            maxDistance = Math.max(maxDistance, match.getDistanceNm());
            totalDistance += match.getDistanceNm();
            totalDistanceSquare += match.getDistanceNm() * match.getDistanceNm();
            distances.add(match.getDistanceNm());
            timeGaps.add(match.getTimeGapSeconds());
            totalRelativeSpeed += match.getRelativeSpeed();
            maxTimeGapSeconds = Math.max(maxTimeGapSeconds, match.getTimeGapSeconds());
            if (match.getTimeGapSeconds() <= rule.getHighConfidenceTimeGapMinutes() * 60L) {
                highTimeGapPoints++;
            }
            if (match.getHeadingParallelDiff() != null) {
                headingKnownCount++;
                totalCourseDifference += match.getHeadingParallelDiff();
                if (match.getHeadingParallelDiff() <= rule.getHeadingParallelThresholdDegree()) {
                    headingOkCount++;
                }
            }
            if (i > 0) {
                long gap = Duration.between(matches.get(i - 1).getOwnPoint().getTime(),
                        match.getOwnPoint().getTime()).toMinutes();
                if (gap > rule.getAisGapMinutes()) {
                    aisGapCount++;
                    maxAisGapMinutes = Math.max(maxAisGapMinutes, gap);
                }
            }
        }
        double avgDistance = totalDistance / Math.max(1, matches.size());
        Collections.sort(distances);
        Collections.sort(timeGaps);
        double medianDistance = percentile(distances, 0.5);
        double p90Distance = percentile(distances, 0.9);
        long medianTimeGapSeconds = percentileLong(timeGaps, 0.5);
        long p90TimeGapSeconds = percentileLong(timeGaps, 0.9);
        double distanceVariance = Math.max(0.0,
                totalDistanceSquare / Math.max(1, matches.size()) - avgDistance * avgDistance);
        double avgRelativeSpeed = totalRelativeSpeed / Math.max(1, matches.size());
        double courseDifference = headingKnownCount == 0 ? -1.0 : totalCourseDifference / headingKnownCount;
        double timeGapHighRatio = highTimeGapPoints / (double) Math.max(1, matches.size());
        double headingParallelRatio = headingOkCount / (double) Math.max(1, matches.size());
        double validHeadingRatio = headingKnownCount / (double) Math.max(1, matches.size());
        double coverage = calculateCoverage(duration, matches.size(), rule.getAisGapMinutes());
        double[] center = calculatePairCenter(matches);
        double radiusNm = calculateRadiusNm(segment.getOwnPoints(), center[0], center[1]);
        String confidence = determineConfidence(matches.size(), coverage, maxTimeGapSeconds,
                headingParallelRatio, aisGapCount, rule);
        boolean weak = duration < rule.getMinDurationMinutes()
                || matches.size() < rule.getFormalMinPoints()
                || rule.isHeadingEnabled() && headingParallelRatio < rule.getMinParallelRatio();
        String behaviorMode = averageMatchedSpeed(matches, true) < rule.getConfirmMinSpeed()
                || averageMatchedSpeed(matches, false) < rule.getConfirmMinSpeed()
                ? "STATIONARY_SIDE_BY_SIDE_REVIEW" : "UNDERWAY_PARALLEL_COMPANIONSHIP";
        return new AlongsideFeature(duration, matches.size(), minDistance, maxDistance, avgDistance,
                medianDistance, p90Distance, distanceVariance, courseDifference, avgRelativeSpeed,
                medianTimeGapSeconds, p90TimeGapSeconds, maxTimeGapSeconds, timeGapHighRatio,
                headingParallelRatio, validHeadingRatio, aisGapCount, maxAisGapMinutes, coverage, confidence,
                behaviorMode,
                center[0], center[1], radiusNm, weak);
    }

    private double percentile(List<Double> values, double percentile) {
        if (values.isEmpty()) { return 0.0; }
        return values.get((int) Math.ceil(percentile * values.size()) - 1);
    }

    private long percentileLong(List<Long> values, double percentile) {
        if (values.isEmpty()) { return 0L; }
        return values.get((int) Math.ceil(percentile * values.size()) - 1);
    }

    private double averageMatchedSpeed(List<MatchedPoint> matches, boolean own) {
        double total = 0.0;
        int count = 0;
        for (MatchedPoint match : matches) {
            TrajectoryPoint point = own ? match.getOwnPoint() : match.getTargetPoint();
            if (point.isSpeedValid()) {
                total += point.getSpeed();
                count++;
            }
        }
        return count == 0 ? Double.NaN : total / count;
    }

    private AlongsideScore calculateScore(AlongsideFeature feature, MatchedSegment segment,
                                          AlongsideRule rule, AlongsideContext context) {
        String exclusionFlag = VALID_RISK_EVENT;
        String exclusionReason = "NO_SCENE_DATA";
        double normalSceneAdjustment = 1.0;
        if (context.isNearPort() && context.getNearbyVesselCount() >= rule.getCrowdedVesselCount()) {
            exclusionFlag = EXCLUDED_NORMAL;
            exclusionReason = "PORT_QUEUE_LIKELY";
            normalSceneAdjustment = 0.15;
        } else if (context.isNearPort()) {
            exclusionFlag = DOWNGRADED_NORMAL_LIKELY;
            exclusionReason = "PORT_OPERATION_LIKELY";
            normalSceneAdjustment = 0.35;
        } else if (context.isNearAnchor()) {
            exclusionFlag = DOWNGRADED_NORMAL_LIKELY;
            exclusionReason = "ANCHORAGE_WAITING_LIKELY";
            normalSceneAdjustment = 0.4;
        } else if (hasAnchoredOrMooredStatus(segment.getOwnPoints())
                || hasAnchoredOrMooredStatus(segment.getTargetPoints())) {
            exclusionFlag = DOWNGRADED_NORMAL_LIKELY;
            exclusionReason = "NAV_STATUS_ANCHORED_OR_MOORED";
            normalSceneAdjustment = 0.5;
        } else if (context.isOutsideKnownPortAndAnchor()) {
            exclusionReason = "OUTSIDE_KNOWN_PORT_AND_ANCHOR";
        }

        int baseEvidenceScore = calculateBaseEvidenceScore(feature, rule);
        int sceneRiskScore = context.isOutsideKnownPortAndAnchor() ? 5 : 0;
        int counterpartyRiskScore = context.isMerchantPair() ? 10 : 0;
        int behaviorRiskScore = feature.getAisGapCount() > 0 ? 5 : 0;
        int durationRiskScore = calculateDurationRiskScore(feature.getDurationMinutes());
        int recurrenceRiskScore = 0;
        double rawScore = baseEvidenceScore + sceneRiskScore + counterpartyRiskScore
                + behaviorRiskScore + durationRiskScore + recurrenceRiskScore;
        double score = rawScore * normalSceneAdjustment;
        if (CONFIDENCE_LOW.equals(feature.getConfidenceLevel())) {
            score *= 0.6;
        }
        if (feature.isWeakCandidate()) {
            score = Math.min(score, 20.0);
        }
        score = Math.max(0.0, Math.min(100.0, score));
        String rawSeverity = determineSeverityLevel(score);
        String severity = rawSeverity;
        String severityCapReason = "";
        if (rule.isHeadingEnabled() && feature.getCourseDifferenceDegree() < 0.0
                && severityRank(severity) > severityRank("L2")) {
            severity = "L2";
            severityCapReason = "HEADING_EVIDENCE_MISSING";
        }
        if (rule.isSeverityCapEnabled() && shouldCapNoSceneData(feature, exclusionReason, rule, context)
                && severityRank(severity) > severityRank(rule.getNoSceneDataMaxLevel())) {
            severity = rule.getNoSceneDataMaxLevel();
            severityCapReason = "NO_SCENE_DATA_NO_EXTERNAL_EVIDENCE";
        }
        if (!rule.isAutoL4Enabled() && "L4".equals(severity)) {
            severity = "L3";
            severityCapReason = "AUTO_L4_DISABLED";
        }
        if (feature.isWeakCandidate()) {
            severity = "L1";
        }
        return new AlongsideScore(baseEvidenceScore, sceneRiskScore, counterpartyRiskScore,
                behaviorRiskScore, durationRiskScore, recurrenceRiskScore, normalSceneAdjustment,
                rawScore, score, rawSeverity, severity, severityCapReason, exclusionFlag, exclusionReason,
                rule.isSceneDataAvailable() || context.isSceneDataAvailable(), rule.getRuleVersion(),
                rule.getCodeCommitSha(), feature, context);
    }

    private int calculateBaseEvidenceScore(AlongsideFeature feature, AlongsideRule rule) {
        int score = 0;
        if (feature.getMinDistanceNm() <= rule.getConfirmMaxDistanceNm() * 0.67) {
            score += 10;
        } else {
            score += 7;
        }
        if (feature.getDistanceVarianceNm2() <= rule.getMaxDistanceVarianceNm2()) {
            score += 10;
        } else {
            score += 4;
        }
        if (feature.getAvgRelativeSpeed() <= rule.getMaxRelativeSpeed() * 0.5) {
            score += 8;
        } else if (feature.getAvgRelativeSpeed() <= rule.getMaxRelativeSpeed()) {
            score += 5;
        }
        if (feature.getCourseDifferenceDegree() >= 0.0
                && feature.getCourseDifferenceDegree() <= rule.getHeadingParallelThresholdDegree()) {
            score += 8;
        }
        if (feature.getHeadingParallelRatio() >= rule.getMinParallelRatio()) {
            score += 8;
        }
        if (feature.getTrackCoverageRatio() >= 0.8 && feature.getTimeGapHighRatio() >= 0.8) {
            score += 6;
        } else {
            score += 3;
        }
        if (feature.getPointCount() >= rule.getFormalMinPoints()) {
            score += 5;
        }
        return Math.min(55, score);
    }

    private int calculateDurationRiskScore(long durationMinutes) {
        if (durationMinutes >= 360) {
            return 15;
        }
        if (durationMinutes >= 120) {
            return 12;
        }
        if (durationMinutes >= 60) {
            return 8;
        }
        if (durationMinutes >= 30) {
            return 5;
        }
        return 0;
    }

    private boolean shouldCapNoSceneData(AlongsideFeature feature, String exclusionReason,
                                         AlongsideRule rule, AlongsideContext context) {
        return "NO_SCENE_DATA".equals(exclusionReason)
                && !rule.isSceneDataAvailable()
                && !context.isSceneDataAvailable()
                && !rule.isCounterpartyRiskAvailable()
                && feature.getAisGapCount() == 0;
    }

    public String determineSeverityLevel(double score) {
        if (score <= 0.0) {
            return "L0";
        }
        if (score <= 25.0) {
            return "L1";
        }
        if (score <= 50.0) {
            return "L2";
        }
        if (score <= 75.0) {
            return "L3";
        }
        return "L4";
    }

    private String determineConfidence(int pointCount, double coverage, long maxTimeGapSeconds,
                                       double headingParallelRatio, int aisGapCount, AlongsideRule rule) {
        if (coverage < 0.6 || pointCount < rule.getMinPoints()
                || maxTimeGapSeconds > rule.getMaxTimeGapMinutes() * 60L) {
            return CONFIDENCE_LOW;
        }
        boolean headingOk = !rule.isHeadingEnabled() || headingParallelRatio >= rule.getMinParallelRatio();
        if (pointCount >= rule.getFormalMinPoints()
                && coverage >= 0.8
                && maxTimeGapSeconds <= rule.getHighConfidenceTimeGapMinutes() * 60L
                && aisGapCount == 0
                && headingOk) {
            return CONFIDENCE_HIGH;
        }
        return CONFIDENCE_MEDIUM;
    }

    private void collectLowSpeedWindow(String mmsi, List<TrajectoryPoint> points, long minDurationMinutes,
                                       List<LowSpeedWindow> windows) {
        if (points == null || points.size() < 2) {
            return;
        }
        TrajectorySegment segment = new TrajectorySegment(points);
        long duration = segment.getDurationMinutes();
        if (duration < minDurationMinutes) {
            return;
        }
        double[] center = calculateCenter(points);
        windows.add(new LowSpeedWindow(mmsi, TrajectoryUtils.formatTime(segment.getStartPoint()),
                TrajectoryUtils.formatTime(segment.getEndPoint()), duration, points.size(), center[0], center[1]));
    }

    private double calculateCoverage(long durationMinutes, int pointCount, long maxGapMinutes) {
        long expected = Math.max(1L, durationMinutes / Math.max(1L, maxGapMinutes) + 1L);
        return Math.min(1.0, pointCount / (double) expected);
    }

    private double[] calculateCenter(List<TrajectoryPoint> points) {
        double lon = 0.0;
        double lat = 0.0;
        for (TrajectoryPoint point : points) {
            lon += point.getLon();
            lat += point.getLat();
        }
        int size = Math.max(1, points.size());
        return new double[]{lon / size, lat / size};
    }

    private double[] calculatePairCenter(List<MatchedPoint> matches) {
        double lon = 0.0;
        double lat = 0.0;
        for (MatchedPoint match : matches) {
            lon += (match.getOwnPoint().getLon() + match.getTargetPoint().getLon()) / 2.0;
            lat += (match.getOwnPoint().getLat() + match.getTargetPoint().getLat()) / 2.0;
        }
        int size = Math.max(1, matches.size());
        return new double[]{lon / size, lat / size};
    }

    private double calculateRadiusNm(List<TrajectoryPoint> points, double centerLon, double centerLat) {
        TrajectoryPoint center = new TrajectoryPoint(null, points.get(0).getTime(), centerLon, centerLat,
                0.0, 0.0, null);
        double radius = 0.0;
        for (TrajectoryPoint point : points) {
            radius = Math.max(radius, TrajectoryUtils.distanceNm(center, point));
        }
        return radius;
    }

    private boolean headingAccepted(TrajectoryPoint ownPoint, TrajectoryPoint targetPoint, AlongsideRule rule) {
        if (!rule.isHeadingEnabled()) {
            return true;
        }
        if (ownPoint.getSpeed() < 0.5 && targetPoint.getSpeed() < 0.5) {
            return true;
        }
        Double diff = headingParallelDiff(ownPoint, targetPoint);
        return diff == null || diff <= rule.getHeadingParallelThresholdDegree();
    }

    private Double headingParallelDiff(TrajectoryPoint ownPoint, TrajectoryPoint targetPoint) {
        if (!isValidCourse(ownPoint.getCourse()) || !isValidCourse(targetPoint.getCourse())) {
            return null;
        }
        return circularDelta(ownPoint.getCourse(), targetPoint.getCourse());
    }

    private double speedDiff(TrajectoryPoint ownPoint, TrajectoryPoint targetPoint) {
        if (!ownPoint.isSpeedValid() || !targetPoint.isSpeedValid()) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.abs(ownPoint.getSpeed() - targetPoint.getSpeed());
    }

    private double circularDelta(double a, double b) {
        double delta = Math.abs(a - b);
        return delta > 180.0 ? 360.0 - delta : delta;
    }

    private boolean isValidCourse(double course) {
        return course >= 0.0 && course <= 360.0;
    }

    private boolean isValidPoint(TrajectoryPoint point) {
        return point != null
                && point.getTime() != null
                && point.getLon() >= -180.0
                && point.getLon() <= 180.0
                && point.getLat() >= -90.0
                && point.getLat() <= 90.0
                && point.isSpeedValid()
                && point.getSpeed() >= 0.0
                && point.getSpeed() <= 80.0;
    }

    private boolean hasAnchoredOrMooredStatus(List<TrajectoryPoint> points) {
        for (TrajectoryPoint point : points) {
            String status = point.getNavStatus();
            if (StringUtils.isBlank(status)) {
                continue;
            }
            String normalized = status.trim().toUpperCase();
            if (normalized.contains("ANCHOR")
                    || normalized.contains("MOORED")
                    || normalized.contains("BERTH")
                    || "1".equals(normalized)
                    || "5".equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private int severityRank(String severity) {
        if ("L4".equals(severity)) {
            return 4;
        }
        if ("L3".equals(severity)) {
            return 3;
        }
        if ("L2".equals(severity)) {
            return 2;
        }
        if ("L1".equals(severity)) {
            return 1;
        }
        return 0;
    }

    public static class AlongsideRule {
        private double confirmMaxDistanceNm = 0.15;
        private double confirmMinSpeed = 0.0;
        private double confirmMaxSpeed = 2.0;
        private double maxRelativeSpeed = 1.0;
        private long maxTimeGapMinutes = 10L;
        private long highConfidenceTimeGapMinutes = 5L;
        private long minDurationMinutes = 30L;
        private long weakMinDurationMinutes = 20L;
        private int minPoints = 3;
        private int formalMinPoints = 6;
        private boolean headingEnabled = true;
        private double headingParallelThresholdDegree = 30.0;
        private double minParallelRatio = 0.8;
        private double maxDistanceVarianceNm2 = 0.0025;
        private int crowdedVesselCount = 5;
        private boolean mergeEnabled = true;
        private long mergeMaxGapMinutes = 30L;
        private double mergeMaxCenterDistanceNm = 1.0;
        private long aisGapMinutes = 30L;
        private boolean severityCapEnabled = true;
        private String noSceneDataMaxLevel = "L2";
        private boolean sceneDataAvailable = false;
        private boolean counterpartyRiskAvailable = false;
        private boolean autoL4Enabled = false;
        private String ruleVersion = "ALONGSIDE_V3_ASYMMETRIC_COUNTERPARTY_V1";
        private String codeCommitSha = "UNKNOWN";

        public AlongsideRule(double maxDistanceNm, double maxSpeed, long maxTimeGapMinutes,
                             long minDurationMinutes, int minPoints, String ruleVersion) {
            this.confirmMaxDistanceNm = maxDistanceNm;
            this.confirmMaxSpeed = maxSpeed;
            this.maxTimeGapMinutes = maxTimeGapMinutes;
            this.weakMinDurationMinutes = minDurationMinutes;
            this.minDurationMinutes = minDurationMinutes;
            this.minPoints = minPoints;
            this.formalMinPoints = Math.max(minPoints, 6);
            this.ruleVersion = ruleVersion;
        }

        public AlongsideRule() {
        }

        public static AlongsideRule defaultRule() {
            return new AlongsideRule();
        }

        public double getConfirmMaxDistanceNm() {
            return confirmMaxDistanceNm;
        }

        public void setConfirmMaxDistanceNm(double confirmMaxDistanceNm) {
            this.confirmMaxDistanceNm = confirmMaxDistanceNm;
        }

        public double getConfirmMaxSpeed() {
            return confirmMaxSpeed;
        }

        public double getConfirmMinSpeed() {
            return confirmMinSpeed;
        }

        public void setConfirmMinSpeed(double confirmMinSpeed) {
            this.confirmMinSpeed = confirmMinSpeed;
        }

        public void setConfirmMaxSpeed(double confirmMaxSpeed) {
            this.confirmMaxSpeed = confirmMaxSpeed;
        }

        public double getMaxRelativeSpeed() {
            return maxRelativeSpeed;
        }

        public void setMaxRelativeSpeed(double maxRelativeSpeed) {
            this.maxRelativeSpeed = maxRelativeSpeed;
        }

        public long getMaxTimeGapMinutes() {
            return maxTimeGapMinutes;
        }

        public void setMaxTimeGapMinutes(long maxTimeGapMinutes) {
            this.maxTimeGapMinutes = maxTimeGapMinutes;
        }

        public long getHighConfidenceTimeGapMinutes() {
            return highConfidenceTimeGapMinutes;
        }

        public void setHighConfidenceTimeGapMinutes(long highConfidenceTimeGapMinutes) {
            this.highConfidenceTimeGapMinutes = highConfidenceTimeGapMinutes;
        }

        public long getMinDurationMinutes() {
            return minDurationMinutes;
        }

        public void setMinDurationMinutes(long minDurationMinutes) {
            this.minDurationMinutes = minDurationMinutes;
        }

        public long getWeakMinDurationMinutes() {
            return weakMinDurationMinutes;
        }

        public void setWeakMinDurationMinutes(long weakMinDurationMinutes) {
            this.weakMinDurationMinutes = weakMinDurationMinutes;
        }

        public int getMinPoints() {
            return minPoints;
        }

        public void setMinPoints(int minPoints) {
            this.minPoints = minPoints;
        }

        public int getFormalMinPoints() {
            return formalMinPoints;
        }

        public void setFormalMinPoints(int formalMinPoints) {
            this.formalMinPoints = formalMinPoints;
        }

        public boolean isHeadingEnabled() {
            return headingEnabled;
        }

        public void setHeadingEnabled(boolean headingEnabled) {
            this.headingEnabled = headingEnabled;
        }

        public double getHeadingParallelThresholdDegree() {
            return headingParallelThresholdDegree;
        }

        public void setHeadingParallelThresholdDegree(double headingParallelThresholdDegree) {
            this.headingParallelThresholdDegree = headingParallelThresholdDegree;
        }

        public double getMinParallelRatio() {
            return minParallelRatio;
        }

        public void setMinParallelRatio(double minParallelRatio) {
            this.minParallelRatio = minParallelRatio;
        }

        public double getMaxDistanceVarianceNm2() {
            return maxDistanceVarianceNm2;
        }

        public void setMaxDistanceVarianceNm2(double maxDistanceVarianceNm2) {
            this.maxDistanceVarianceNm2 = maxDistanceVarianceNm2;
        }

        public int getCrowdedVesselCount() {
            return crowdedVesselCount;
        }

        public void setCrowdedVesselCount(int crowdedVesselCount) {
            this.crowdedVesselCount = crowdedVesselCount;
        }

        public boolean isMergeEnabled() {
            return mergeEnabled;
        }

        public void setMergeEnabled(boolean mergeEnabled) {
            this.mergeEnabled = mergeEnabled;
        }

        public long getMergeMaxGapMinutes() {
            return mergeMaxGapMinutes;
        }

        public void setMergeMaxGapMinutes(long mergeMaxGapMinutes) {
            this.mergeMaxGapMinutes = mergeMaxGapMinutes;
        }

        public double getMergeMaxCenterDistanceNm() {
            return mergeMaxCenterDistanceNm;
        }

        public void setMergeMaxCenterDistanceNm(double mergeMaxCenterDistanceNm) {
            this.mergeMaxCenterDistanceNm = mergeMaxCenterDistanceNm;
        }

        public long getAisGapMinutes() {
            return aisGapMinutes;
        }

        public void setAisGapMinutes(long aisGapMinutes) {
            this.aisGapMinutes = aisGapMinutes;
        }

        public boolean isSeverityCapEnabled() {
            return severityCapEnabled;
        }

        public void setSeverityCapEnabled(boolean severityCapEnabled) {
            this.severityCapEnabled = severityCapEnabled;
        }

        public String getNoSceneDataMaxLevel() {
            return noSceneDataMaxLevel;
        }

        public void setNoSceneDataMaxLevel(String noSceneDataMaxLevel) {
            this.noSceneDataMaxLevel = noSceneDataMaxLevel;
        }

        public boolean isSceneDataAvailable() {
            return sceneDataAvailable;
        }

        public void setSceneDataAvailable(boolean sceneDataAvailable) {
            this.sceneDataAvailable = sceneDataAvailable;
        }

        public boolean isCounterpartyRiskAvailable() {
            return counterpartyRiskAvailable;
        }

        public void setCounterpartyRiskAvailable(boolean counterpartyRiskAvailable) {
            this.counterpartyRiskAvailable = counterpartyRiskAvailable;
        }

        public boolean isAutoL4Enabled() {
            return autoL4Enabled;
        }

        public void setAutoL4Enabled(boolean autoL4Enabled) {
            this.autoL4Enabled = autoL4Enabled;
        }

        public String getRuleVersion() {
            return ruleVersion;
        }

        public void setRuleVersion(String ruleVersion) {
            this.ruleVersion = ruleVersion;
        }

        public String getCodeCommitSha() { return codeCommitSha; }
        public void setCodeCommitSha(String codeCommitSha) { this.codeCommitSha = codeCommitSha; }
    }

    public static class AlongsideContext {
        private final String ownShipType;
        private final String targetShipType;
        private final Boolean merchantPair;
        private final OwnShipEligibility ownShipEligibility;
        private final TargetVesselRole targetVesselRole;
        private final ClassificationConfidence targetTypeConfidence;
        private final List<String> targetRoleReasonCodes;
        private final boolean nearPort;
        private final boolean nearAnchor;
        private final boolean outsideKnownPortAndAnchor;
        private final boolean sceneDataAvailable;
        private final int nearbyVesselCount;

        private AlongsideContext(String ownShipType, String targetShipType, Boolean merchantPair,
                                 OwnShipEligibility ownShipEligibility, TargetVesselRole targetVesselRole,
                                 ClassificationConfidence targetTypeConfidence, List<String> targetRoleReasonCodes,
                                 boolean nearPort, boolean nearAnchor, boolean outsideKnownPortAndAnchor,
                                 boolean sceneDataAvailable, int nearbyVesselCount) {
            this.ownShipType = ownShipType;
            this.targetShipType = targetShipType;
            this.merchantPair = merchantPair;
            this.ownShipEligibility = ownShipEligibility;
            this.targetVesselRole = targetVesselRole;
            this.targetTypeConfidence = targetTypeConfidence;
            this.targetRoleReasonCodes = targetRoleReasonCodes == null
                    ? Collections.<String>emptyList() : new ArrayList<String>(targetRoleReasonCodes);
            this.nearPort = nearPort;
            this.nearAnchor = nearAnchor;
            this.outsideKnownPortAndAnchor = outsideKnownPortAndAnchor;
            this.sceneDataAvailable = sceneDataAvailable;
            this.nearbyVesselCount = Math.max(0, nearbyVesselCount);
        }

        public static AlongsideContext unknown() {
            return new AlongsideContext(null, null, null, OwnShipEligibility.ELIGIBLE_MERCHANT,
                    TargetVesselRole.UNKNOWN, ClassificationConfidence.LOW,
                    Collections.singletonList("TARGET_TYPE_MISSING"), false, false, false, false, 0);
        }

        public static AlongsideContext merchantPair(String ownShipType, String targetShipType,
                                                    boolean nearPort, boolean nearAnchor,
                                                    boolean openSea, int nearbyVesselCount) {
            return new AlongsideContext(ownShipType, targetShipType, true,
                    OwnShipEligibility.ELIGIBLE_MERCHANT, TargetVesselRole.LARGE_MERCHANT,
                    ClassificationConfidence.HIGH, Collections.singletonList("LEGACY_MERCHANT_PAIR"),
                    nearPort, nearAnchor, openSea, true, nearbyVesselCount);
        }

        public static AlongsideContext nonMerchantPair(String ownShipType, String targetShipType) {
            TargetRoleClassification target = new ShipTypeClassifier()
                    .classifyTargetShipWithReason(targetShipType, null);
            return asymmetric(ownShipType, targetShipType, OwnShipEligibility.ELIGIBLE_MERCHANT,
                    target, false, false, false, false, 0);
        }

        public static AlongsideContext of(String ownShipType, String targetShipType, Boolean merchantPair,
                                          boolean nearPort, boolean nearAnchor, boolean openSea,
                                          boolean sceneDataAvailable, int nearbyVesselCount) {
            TargetRoleClassification target = new ShipTypeClassifier()
                    .classifyTargetShipWithReason(targetShipType, null);
            return asymmetric(ownShipType, targetShipType,
                    new ShipTypeClassifier().classifyOwnShip(ownShipType), target,
                    nearPort, nearAnchor, openSea, sceneDataAvailable, nearbyVesselCount);
        }

        public static AlongsideContext asymmetric(String ownShipType, String targetShipType,
                                                   OwnShipEligibility ownShipEligibility,
                                                   TargetRoleClassification target,
                                                   boolean nearPort, boolean nearAnchor,
                                                   boolean outsideKnownPortAndAnchor,
                                                   boolean sceneDataAvailable, int nearbyVesselCount) {
            TargetRoleClassification effective = target == null
                    ? new ShipTypeClassifier().classifyTargetShipWithReason(targetShipType, null) : target;
            return new AlongsideContext(ownShipType, targetShipType, null, ownShipEligibility,
                    effective.getRole(), effective.getConfidence(), effective.getReasonCodes(), nearPort, nearAnchor,
                    outsideKnownPortAndAnchor, sceneDataAvailable, nearbyVesselCount);
        }

        public String getOwnShipType() {
            return ownShipType;
        }

        public String getTargetShipType() {
            return targetShipType;
        }

        public boolean isMerchantPair() {
            return Boolean.TRUE.equals(merchantPair);
        }

        public boolean isOwnShipEligible() { return ownShipEligibility == OwnShipEligibility.ELIGIBLE_MERCHANT; }
        public OwnShipEligibility getOwnShipEligibility() { return ownShipEligibility; }
        public TargetVesselRole getTargetVesselRole() { return targetVesselRole; }
        public ClassificationConfidence getTargetTypeConfidence() { return targetTypeConfidence; }
        public List<String> getTargetRoleReasonCodes() { return Collections.unmodifiableList(targetRoleReasonCodes); }

        public boolean isNearPort() {
            return nearPort;
        }

        public boolean isNearAnchor() {
            return nearAnchor;
        }

        public boolean isOpenSea() {
            return false;
        }

        public boolean isOutsideKnownPortAndAnchor() { return outsideKnownPortAndAnchor; }

        public boolean isSceneDataAvailable() {
            return sceneDataAvailable;
        }

        public int getNearbyVesselCount() {
            return nearbyVesselCount;
        }

        private String riskInterpretation() {
            switch (targetVesselRole) {
                case LARGE_MERCHANT: return "POSSIBLE_STS_LIKE_CONTACT";
                case BUNKER_OR_SUPPLY: return nearPort ? "POSSIBLE_BUNKERING" : "OFF_PORT_BUNKERING_REVIEW";
                case BARGE_OR_LIGHTER: return "POSSIBLE_LIGHTERING";
                case TUG_OR_SERVICE: return nearPort ? "PORT_ASSISTANCE_LIKELY" : "OFF_PORT_SERVICE_CONTACT_REVIEW";
                case FISHING_OR_SMALL_CRAFT: return "SMALL_VESSEL_TRANSFER_REVIEW";
                case PASSENGER_OR_PILOT: return nearPort ? "PORT_ASSISTANCE_LIKELY" : "UNCLASSIFIED_VESSEL_INTERACTION";
                default: return "UNKNOWN_COUNTERPARTY_CONTACT";
            }
        }

        private String interpretationConfidence(AlongsideFeature feature) {
            if (targetTypeConfidence == ClassificationConfidence.LOW || feature.isWeakCandidate()) {
                return "LOW";
            }
            return "HIGH".equals(feature.getConfidenceLevel()) ? "HIGH" : "MEDIUM";
        }

        private String decisionStatus(AlongsideFeature feature) {
            if (feature.isWeakCandidate() || "STATIONARY_SIDE_BY_SIDE_REVIEW".equals(feature.getBehaviorMode())
                    || targetVesselRole == TargetVesselRole.UNKNOWN
                    || targetVesselRole == TargetVesselRole.BUNKER_OR_SUPPLY
                    || targetVesselRole == TargetVesselRole.BARGE_OR_LIGHTER
                    || targetVesselRole == TargetVesselRole.FISHING_OR_SMALL_CRAFT
                    || targetVesselRole == TargetVesselRole.TUG_OR_SERVICE) {
                return "REVIEW";
            }
            return "CONFIRMED";
        }

        private List<String> reasonCodes(AlongsideFeature feature) {
            List<String> reasons = new ArrayList<String>(targetRoleReasonCodes);
            reasons.add(feature.getBehaviorMode());
            reasons.add(nearPort ? "INSIDE_KNOWN_PORT" : nearAnchor ? "INSIDE_KNOWN_ANCHOR"
                    : outsideKnownPortAndAnchor ? "OUTSIDE_KNOWN_PORT_AND_ANCHOR" : "SCENE_CONTEXT_INCOMPLETE");
            if (feature.getValidHeadingRatio() < 0.8) {
                reasons.add("HEADING_EVIDENCE_INCOMPLETE");
            }
            return reasons;
        }
    }

    public static class DetectionStats {
        private final int ownPoints;
        private final int targetPoints;
        private final int rawEvents;
        private final int mergedEvents;
        private final int savedEvents;

        private DetectionStats(int ownPoints, int targetPoints, int rawEvents, int mergedEvents, int savedEvents) {
            this.ownPoints = ownPoints;
            this.targetPoints = targetPoints;
            this.rawEvents = rawEvents;
            this.mergedEvents = mergedEvents;
            this.savedEvents = savedEvents;
        }

        private static DetectionStats empty() {
            return new DetectionStats(0, 0, 0, 0, 0);
        }

        private static DetectionStats empty(int ownPoints, int targetPoints) {
            return new DetectionStats(ownPoints, targetPoints, 0, 0, 0);
        }

        private static DetectionStats from(int ownPoints, int targetPoints, int rawEvents,
                                           int mergedEvents, int savedEvents) {
            return new DetectionStats(ownPoints, targetPoints, rawEvents, mergedEvents, savedEvents);
        }

        public int getOwnPoints() {
            return ownPoints;
        }

        public int getTargetPoints() {
            return targetPoints;
        }

        public int getRawEvents() {
            return rawEvents;
        }

        public int getMergedEvents() {
            return mergedEvents;
        }

        public int getSavedEvents() {
            return savedEvents;
        }
    }

    public static class LowSpeedWindow {
        private final String mmsi;
        private final String startTime;
        private final String endTime;
        private final long durationMinutes;
        private final int pointCount;
        private final double centerLon;
        private final double centerLat;

        private LowSpeedWindow(String mmsi, String startTime, String endTime, long durationMinutes,
                               int pointCount, double centerLon, double centerLat) {
            this.mmsi = mmsi;
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationMinutes = durationMinutes;
            this.pointCount = pointCount;
            this.centerLon = centerLon;
            this.centerLat = centerLat;
        }

        public String getMmsi() {
            return mmsi;
        }

        public String getStartTime() {
            return startTime;
        }

        public String getEndTime() {
            return endTime;
        }

        public long getDurationMinutes() {
            return durationMinutes;
        }

        public int getPointCount() {
            return pointCount;
        }

        public double getCenterLon() {
            return centerLon;
        }

        public double getCenterLat() {
            return centerLat;
        }
    }

    public static class AlongsideScore {
        private final int baseEvidenceScore;
        private final int sceneRiskScore;
        private final int counterpartyRiskScore;
        private final int behaviorRiskScore;
        private final int durationRiskScore;
        private final int recurrenceRiskScore;
        private final double normalSceneAdjustment;
        private final double rawScore;
        private final double eventScore;
        private final String rawSeverityLevel;
        private final String severityLevel;
        private final String severityCapReason;
        private final String exclusionFlag;
        private final String exclusionReason;
        private final boolean sceneDataAvailable;
        private final String ruleVersion;
        private final String codeCommitSha;
        private final AlongsideFeature feature;
        private final AlongsideContext context;

        private AlongsideScore(int baseEvidenceScore, int sceneRiskScore, int counterpartyRiskScore,
                               int behaviorRiskScore, int durationRiskScore, int recurrenceRiskScore,
                               double normalSceneAdjustment, double rawScore, double eventScore,
                               String rawSeverityLevel, String severityLevel, String severityCapReason,
                               String exclusionFlag, String exclusionReason, boolean sceneDataAvailable,
                               String ruleVersion, String codeCommitSha,
                               AlongsideFeature feature, AlongsideContext context) {
            this.baseEvidenceScore = baseEvidenceScore;
            this.sceneRiskScore = sceneRiskScore;
            this.counterpartyRiskScore = counterpartyRiskScore;
            this.behaviorRiskScore = behaviorRiskScore;
            this.durationRiskScore = durationRiskScore;
            this.recurrenceRiskScore = recurrenceRiskScore;
            this.normalSceneAdjustment = normalSceneAdjustment;
            this.rawScore = rawScore;
            this.eventScore = eventScore;
            this.rawSeverityLevel = rawSeverityLevel;
            this.severityLevel = severityLevel;
            this.severityCapReason = severityCapReason;
            this.exclusionFlag = exclusionFlag;
            this.exclusionReason = exclusionReason;
            this.sceneDataAvailable = sceneDataAvailable;
            this.ruleVersion = ruleVersion;
            this.codeCommitSha = codeCommitSha;
            this.feature = feature;
            this.context = context;
        }

        public double getEventScore() {
            return eventScore;
        }

        public String getSeverityLevel() {
            return severityLevel;
        }

        public String getExclusionFlag() {
            return exclusionFlag;
        }

        public String getExclusionReason() {
            return exclusionReason;
        }

        public String toJson() {
            JSONObject json = new JSONObject();
            json.put("baseEvidenceScore", baseEvidenceScore);
            json.put("sceneRiskScore", sceneRiskScore);
            json.put("counterpartyRiskScore", counterpartyRiskScore);
            json.put("behaviorRiskScore", behaviorRiskScore);
            json.put("durationRiskScore", durationRiskScore);
            json.put("recurrenceRiskScore", recurrenceRiskScore);
            json.put("normalSceneAdjustment", normalSceneAdjustment);
            json.put("rawScore", rawScore);
            json.put("eventScore", eventScore);
            json.put("rawSeverityLevel", rawSeverityLevel);
            json.put("severityLevel", severityLevel);
            json.put("severityCapReason", severityCapReason);
            json.put("exclusionFlag", exclusionFlag);
            json.put("exclusionReason", exclusionReason);
            json.put("sceneDataAvailable", sceneDataAvailable);
            json.put("ruleVersion", ruleVersion);
            json.put("codeCommitSha", codeCommitSha);
            json.put("weakCandidate", feature.isWeakCandidate());
            json.put("minDistanceNm", feature.getMinDistanceNm());
            json.put("avgDistanceNm", feature.getAvgDistanceNm());
            json.put("medianDistanceNm", feature.getMedianDistanceNm());
            json.put("p90DistanceNm", feature.getP90DistanceNm());
            json.put("distanceVarianceNm2", feature.getDistanceVarianceNm2());
            json.put("avgRelativeSpeed", feature.getAvgRelativeSpeed());
            json.put("courseDifferenceDegree", feature.getCourseDifferenceDegree());
            json.put("maxTimeGapSeconds", feature.getMaxTimeGapSeconds());
            json.put("medianTimeGapSeconds", feature.getMedianTimeGapSeconds());
            json.put("p90TimeGapSeconds", feature.getP90TimeGapSeconds());
            json.put("matchedPointCount", feature.getPointCount());
            json.put("ownValidPointCount", feature.getPointCount());
            json.put("targetValidPointCount", feature.getPointCount());
            json.put("timeGapHighRatio", feature.getTimeGapHighRatio());
            json.put("headingParallelRatio", feature.getHeadingParallelRatio());
            json.put("validHeadingRatio", feature.getValidHeadingRatio());
            json.put("trackCoverageRatio", feature.getTrackCoverageRatio());
            json.put("behaviorMode", feature.getBehaviorMode());
            json.put("behaviorConfidence", feature.getConfidenceLevel());
            json.put("targetVesselRole", context.getTargetVesselRole().name());
            json.put("targetTypeConfidence", context.getTargetTypeConfidence().name());
            json.put("targetRoleReasonCodes", context.getTargetRoleReasonCodes());
            json.put("interpretationConfidence", context.interpretationConfidence(feature));
            json.put("riskInterpretation", context.riskInterpretation());
            json.put("decisionStatus", context.decisionStatus(feature));
            json.put("reasonCodes", context.reasonCodes(feature));
            json.put("nearPort", context.isNearPort());
            json.put("nearAnchor", context.isNearAnchor());
            json.put("openSea", context.isOpenSea());
            json.put("outsideKnownPortAndAnchor", context.isOutsideKnownPortAndAnchor());
            json.put("sceneContextComplete", context.isSceneDataAvailable());
            json.put("nearbyVesselCount", context.getNearbyVesselCount());
            json.put("ownShipType", context.getOwnShipType());
            json.put("targetShipType", context.getTargetShipType());
            json.put("targetShipTypeRaw", context.getTargetShipType());
            json.put("targetShipMinTypeRaw", null);
            json.put("targetProfileMissing", context.getTargetShipType() == null);
            json.put("targetImoPresent", false);
            json.put("targetLength", null);
            json.put("targetDwt", null);
            json.put("merchantPair", context.isMerchantPair());
            return json.toJSONString();
        }
    }

    private static class AlongsideFeature {
        private final long durationMinutes;
        private final int pointCount;
        private final double minDistanceNm;
        private final double maxDistanceNm;
        private final double avgDistanceNm;
        private final double medianDistanceNm;
        private final double p90DistanceNm;
        private final double distanceVarianceNm2;
        private final double courseDifferenceDegree;
        private final double avgRelativeSpeed;
        private final long maxTimeGapSeconds;
        private final long medianTimeGapSeconds;
        private final long p90TimeGapSeconds;
        private final double timeGapHighRatio;
        private final double headingParallelRatio;
        private final double validHeadingRatio;
        private final int aisGapCount;
        private final long maxAisGapMinutes;
        private final double trackCoverageRatio;
        private final String confidenceLevel;
        private final String behaviorMode;
        private final double centerLon;
        private final double centerLat;
        private final double radiusNm;
        private final boolean weakCandidate;

        private AlongsideFeature(long durationMinutes, int pointCount, double minDistanceNm,
                                 double maxDistanceNm, double avgDistanceNm, double medianDistanceNm,
                                 double p90DistanceNm, double distanceVarianceNm2,
                                 double courseDifferenceDegree, double avgRelativeSpeed,
                                 long medianTimeGapSeconds, long p90TimeGapSeconds, long maxTimeGapSeconds,
                                 double timeGapHighRatio, double headingParallelRatio, double validHeadingRatio,
                                 int aisGapCount,
                                 long maxAisGapMinutes, double trackCoverageRatio, String confidenceLevel,
                                 String behaviorMode, double centerLon, double centerLat, double radiusNm,
                                 boolean weakCandidate) {
            this.durationMinutes = durationMinutes;
            this.pointCount = pointCount;
            this.minDistanceNm = minDistanceNm;
            this.maxDistanceNm = maxDistanceNm;
            this.avgDistanceNm = avgDistanceNm;
            this.medianDistanceNm = medianDistanceNm;
            this.p90DistanceNm = p90DistanceNm;
            this.distanceVarianceNm2 = distanceVarianceNm2;
            this.courseDifferenceDegree = courseDifferenceDegree;
            this.avgRelativeSpeed = avgRelativeSpeed;
            this.maxTimeGapSeconds = maxTimeGapSeconds;
            this.medianTimeGapSeconds = medianTimeGapSeconds;
            this.p90TimeGapSeconds = p90TimeGapSeconds;
            this.timeGapHighRatio = timeGapHighRatio;
            this.headingParallelRatio = headingParallelRatio;
            this.validHeadingRatio = validHeadingRatio;
            this.aisGapCount = aisGapCount;
            this.maxAisGapMinutes = maxAisGapMinutes;
            this.trackCoverageRatio = trackCoverageRatio;
            this.confidenceLevel = confidenceLevel;
            this.behaviorMode = behaviorMode;
            this.centerLon = centerLon;
            this.centerLat = centerLat;
            this.radiusNm = radiusNm;
            this.weakCandidate = weakCandidate;
        }

        public long getDurationMinutes() {
            return durationMinutes;
        }

        public int getPointCount() {
            return pointCount;
        }

        public double getMinDistanceNm() {
            return minDistanceNm;
        }

        public double getMaxDistanceNm() {
            return maxDistanceNm;
        }

        public double getAvgDistanceNm() {
            return avgDistanceNm;
        }

        public double getMedianDistanceNm() { return medianDistanceNm; }
        public double getP90DistanceNm() { return p90DistanceNm; }

        public double getDistanceVarianceNm2() {
            return distanceVarianceNm2;
        }

        public double getCourseDifferenceDegree() {
            return courseDifferenceDegree;
        }

        public double getAvgRelativeSpeed() {
            return avgRelativeSpeed;
        }

        public long getMaxTimeGapSeconds() {
            return maxTimeGapSeconds;
        }

        public long getMedianTimeGapSeconds() { return medianTimeGapSeconds; }
        public long getP90TimeGapSeconds() { return p90TimeGapSeconds; }

        public double getTimeGapHighRatio() {
            return timeGapHighRatio;
        }

        public double getHeadingParallelRatio() {
            return headingParallelRatio;
        }

        public double getValidHeadingRatio() { return validHeadingRatio; }
        public String getBehaviorMode() { return behaviorMode; }

        public int getAisGapCount() {
            return aisGapCount;
        }

        public long getMaxAisGapMinutes() {
            return maxAisGapMinutes;
        }

        public double getTrackCoverageRatio() {
            return trackCoverageRatio;
        }

        public String getConfidenceLevel() {
            return confidenceLevel;
        }

        public double getCenterLon() {
            return centerLon;
        }

        public double getCenterLat() {
            return centerLat;
        }

        public double getRadiusNm() {
            return radiusNm;
        }

        public boolean isWeakCandidate() {
            return weakCandidate;
        }
    }

    private static class MatchedSegment {
        private final List<MatchedPoint> matches;

        private MatchedSegment(List<MatchedPoint> matches) {
            this.matches = matches;
        }

        private List<MatchedPoint> getMatches() {
            return matches;
        }

        private List<TrajectoryPoint> getOwnPoints() {
            List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
            for (MatchedPoint match : matches) {
                points.add(match.getOwnPoint());
            }
            return points;
        }

        private List<TrajectoryPoint> getTargetPoints() {
            List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
            for (MatchedPoint match : matches) {
                points.add(match.getTargetPoint());
            }
            return points;
        }

        private long getDurationMinutes() {
            return Duration.between(getStartOwnPoint().getTime(), getEndOwnPoint().getTime()).toMinutes();
        }

        private TrajectoryPoint getStartOwnPoint() {
            return matches.get(0).getOwnPoint();
        }

        private TrajectoryPoint getEndOwnPoint() {
            return matches.get(matches.size() - 1).getOwnPoint();
        }

        private TrajectoryPoint getCenterPoint() {
            double[] center = new double[]{0.0, 0.0};
            for (MatchedPoint match : matches) {
                center[0] += match.getOwnPoint().getLon();
                center[1] += match.getOwnPoint().getLat();
            }
            int size = Math.max(1, matches.size());
            return new TrajectoryPoint(null, getStartOwnPoint().getTime(), center[0] / size, center[1] / size,
                    0.0, 0.0, null);
        }

        private MatchedSegment merge(MatchedSegment other) {
            List<MatchedPoint> merged = new ArrayList<MatchedPoint>(matches);
            merged.addAll(other.getMatches());
            return new MatchedSegment(merged);
        }
    }

    private static class MatchResult {
        private final int index;
        private final TrajectoryPoint point;
        private final double distanceNm;
        private final long timeGapSeconds;

        private MatchResult(int index, TrajectoryPoint point, double distanceNm, long timeGapSeconds) {
            this.index = index;
            this.point = point;
            this.distanceNm = distanceNm;
            this.timeGapSeconds = timeGapSeconds;
        }

        private int getIndex() {
            return index;
        }

        private TrajectoryPoint getPoint() {
            return point;
        }

        private double getDistanceNm() {
            return distanceNm;
        }

        private long getTimeGapSeconds() {
            return timeGapSeconds;
        }
    }

    private static class MatchedPoint {
        private final TrajectoryPoint ownPoint;
        private final TrajectoryPoint targetPoint;
        private final double distanceNm;
        private final long timeGapSeconds;
        private final double relativeSpeed;
        private final Double headingParallelDiff;

        private MatchedPoint(TrajectoryPoint ownPoint, TrajectoryPoint targetPoint, double distanceNm,
                             long timeGapSeconds, double relativeSpeed, Double headingParallelDiff) {
            this.ownPoint = ownPoint;
            this.targetPoint = targetPoint;
            this.distanceNm = distanceNm;
            this.timeGapSeconds = timeGapSeconds;
            this.relativeSpeed = relativeSpeed;
            this.headingParallelDiff = headingParallelDiff;
        }

        private TrajectoryPoint getOwnPoint() {
            return ownPoint;
        }

        private TrajectoryPoint getTargetPoint() {
            return targetPoint;
        }

        private double getDistanceNm() {
            return distanceNm;
        }

        private long getTimeGapSeconds() {
            return timeGapSeconds;
        }

        private double getRelativeSpeed() {
            return relativeSpeed;
        }

        private Double getHeadingParallelDiff() {
            return headingParallelDiff;
        }
    }
}
