package com.hifleet.service;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.trajectory.TrajectoryPoint;
import com.hifleet.trajectory.TrajectorySegment;
import com.hifleet.trajectory.TrajectoryUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 船舶徘徊 V2-lite：轨迹清洗、AIS gap 切分、复合徘徊识别、置信度与单事件评分。
 */
public class ShipLoiteringDetector {

    public static final String SUB_TYPE_SLOW_LOITERING = "SLOW_LOITERING";
    public static final String SUB_TYPE_MANEUVER_LOITERING = "MANEUVER_LOITERING";
    public static final String SUB_TYPE_HIGH_RISK_WAITING = "HIGH_RISK_WAITING";
    public static final String SUB_TYPE_COMPOSITE_LOITERING = "COMPOSITE_LOITERING";
    public static final String SUB_TYPE_WEAK_LOITERING_CANDIDATE = "WEAK_LOITERING_CANDIDATE";

    public static final String CONFIDENCE_HIGH = "HIGH";
    public static final String CONFIDENCE_MEDIUM = "MEDIUM";
    public static final String CONFIDENCE_LOW = "LOW";

    public static final String EXCLUDED_NORMAL = "EXCLUDED_NORMAL";
    public static final String DOWNGRADED_NORMAL_LIKELY = "DOWNGRADED_NORMAL_LIKELY";
    public static final String VALID_RISK_EVENT = "VALID_RISK_EVENT";

    private DetectionStats lastStats = DetectionStats.empty();

    public List<ShipBehaviorEvent> detect(String mmsi, List<TrajectoryPoint> points, LoiteringRule rule) {
        List<ShipBehaviorTrace> traces = detectWithTrace(mmsi, points, LoiteringConfig.fromRule(rule));
        List<ShipBehaviorEvent> events = new ArrayList<ShipBehaviorEvent>();
        for (ShipBehaviorTrace trace : traces) {
            events.add(trace.getEvent());
        }
        return events;
    }

    public List<ShipBehaviorTrace> detectWithTrace(List<TrajectoryPoint> points) {
        return detectWithTrace(null, points, LoiteringConfig.defaultConfig());
    }

    public List<ShipBehaviorTrace> detectWithTrace(String mmsi, List<TrajectoryPoint> points, LoiteringRule rule) {
        return detectWithTrace(mmsi, points, LoiteringConfig.fromRule(rule));
    }

    public List<ShipBehaviorTrace> detectWithTrace(String mmsi, List<TrajectoryPoint> points, LoiteringConfig config) {
        if (points == null || points.isEmpty()) {
            lastStats = new DetectionStats(0, 0, 0, 0, 0, 0);
            return Collections.emptyList();
        }
        LoiteringConfig effectiveConfig = config == null ? LoiteringConfig.defaultConfig() : config;
        List<TrajectoryPoint> cleanPoints = cleanAndSort(points, effectiveConfig);
        List<SegmentWithQuality> segments = splitByGapAndQuality(cleanPoints, effectiveConfig);
        List<LoiteringCandidate> candidates = new ArrayList<LoiteringCandidate>();
        RejectSummary rejectSummary = new RejectSummary();
        for (SegmentWithQuality segment : segments) {
            List<LoiteringCandidate> segmentCandidates = new ArrayList<LoiteringCandidate>();
            segmentCandidates.addAll(detectSlowLoitering(mmsi, segment, effectiveConfig));
            segmentCandidates.addAll(detectManeuverLoitering(mmsi, segment, effectiveConfig));
            segmentCandidates.addAll(detectHighRiskWaiting(mmsi, segment, effectiveConfig));
            if (segmentCandidates.isEmpty()) {
                collectRejectAndNearMiss(segment, effectiveConfig, rejectSummary);
                segmentCandidates.addAll(detectWeakLoitering(mmsi, segment, effectiveConfig));
            }
            candidates.addAll(segmentCandidates);
        }
        int rawCandidates = candidates.size();
        List<LoiteringCandidate> mergedCandidates = mergeCandidates(candidates, effectiveConfig);
        applyNormalSceneExclusion(mergedCandidates);
        scoreCandidates(mergedCandidates, effectiveConfig);
        List<ShipBehaviorTrace> traces = buildTraces(filterStoredCandidates(mergedCandidates, effectiveConfig));
        lastStats = DetectionStats.from(points.size(), cleanPoints.size(), segments.size(), rawCandidates,
                mergedCandidates, traces.size(), rejectSummary, effectiveConfig);
        return traces;
    }

    public DetectionStats getLastStats() {
        return lastStats;
    }

    public List<TrajectoryPoint> cleanAndSort(List<TrajectoryPoint> points, LoiteringConfig config) {
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
                if (a == null) {
                    return 1;
                }
                if (b == null) {
                    return -1;
                }
                if (a.getTime() == null && b.getTime() == null) {
                    return 0;
                }
                if (a.getTime() == null) {
                    return 1;
                }
                if (b.getTime() == null) {
                    return -1;
                }
                return a.getTime().compareTo(b.getTime());
            }
        });
        List<TrajectoryPoint> clean = new ArrayList<TrajectoryPoint>();
        Set<String> seen = new HashSet<String>();
        for (TrajectoryPoint point : sorted) {
            if (!isValidPoint(point, config)) {
                continue;
            }
            String key = point.getTime() + "|" + point.getLon() + "|" + point.getLat();
            if (seen.contains(key)) {
                continue;
            }
            seen.add(key);
            clean.add(point);
        }
        return clean;
    }

    public List<SegmentWithQuality> splitByGapAndQuality(List<TrajectoryPoint> points, LoiteringConfig config) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        List<SegmentWithQuality> segments = new ArrayList<SegmentWithQuality>();
        List<TrajectoryPoint> current = new ArrayList<TrajectoryPoint>();
        int aisGapCount = 0;
        long maxGap = 0L;
        current.add(points.get(0));
        for (int i = 1; i < points.size(); i++) {
            TrajectoryPoint previous = points.get(i - 1);
            TrajectoryPoint point = points.get(i);
            long gap = Duration.between(previous.getTime(), point.getTime()).toMinutes();
            if (gap > config.getEventSplitGapMinutes()) {
                addSegment(segments, current, aisGapCount, maxGap);
                current = new ArrayList<TrajectoryPoint>();
                aisGapCount = 0;
                maxGap = 0L;
            } else if (gap > config.getMaxGapMinutes()) {
                aisGapCount++;
                maxGap = Math.max(maxGap, gap);
            }
            current.add(point);
        }
        addSegment(segments, current, aisGapCount, maxGap);
        return segments;
    }

    public List<LoiteringCandidate> detectSlowLoitering(String mmsi, SegmentWithQuality segment,
                                                         LoiteringConfig config) {
        if (segment == null || segment.getPoints().size() < config.getSlowMinPoints()) {
            return Collections.emptyList();
        }
        LoiteringEventFeature feature = calculateFeature(segment, config);
        if (feature.getAvgSog() <= config.getSlowMaxSpeed()
                && feature.getDurationMinutes() >= config.getSlowMinDurationMinutes()
                && feature.getRadiusNm() <= config.getSlowMaxRadiusNm()
                && feature.getPointCount() >= config.getSlowMinPoints()) {
            return Collections.singletonList(new LoiteringCandidate(mmsi, SUB_TYPE_SLOW_LOITERING, segment, feature));
        }
        return Collections.emptyList();
    }

    public List<LoiteringCandidate> detectManeuverLoitering(String mmsi, SegmentWithQuality segment,
                                                             LoiteringConfig config) {
        if (segment == null || segment.getPoints().size() < config.getMinWarningPoints()) {
            return Collections.emptyList();
        }
        LoiteringEventFeature feature = calculateFeature(segment, config);
        boolean maneuver = feature.getTurnCountPerHour() >= config.getManeuverMinTurnCountPerHour()
                || feature.getCogChangePerHour() >= config.getManeuverMinCogChangePerHour()
                || feature.getTortuosity() >= config.getManeuverMinTortuosity();
        if (feature.getAvgSog() >= config.getManeuverMinSpeed()
                && feature.getAvgSog() <= config.getManeuverMaxSpeed()
                && feature.getDurationMinutes() >= config.getManeuverMinDurationMinutes()
                && feature.getRadiusNm() <= config.getManeuverMaxRadiusNm()
                && maneuver) {
            return Collections.singletonList(new LoiteringCandidate(mmsi, SUB_TYPE_MANEUVER_LOITERING, segment, feature));
        }
        return Collections.emptyList();
    }

    public List<LoiteringCandidate> detectHighRiskWaiting(String mmsi, SegmentWithQuality segment,
                                                           LoiteringConfig config) {
        if (!config.isHighRiskEnabled() || segment == null) {
            return Collections.emptyList();
        }
        LoiteringEventFeature feature = calculateFeature(segment, config);
        List<String> riskAreaTags = resolveRiskAreaTags(feature);
        boolean waitingEvidence = feature.getAvgSog() <= 3.0
                || feature.getTurnCountPerHour() >= config.getManeuverMinTurnCountPerHour()
                || feature.getAisGapCount() > 0;
        if (!riskAreaTags.isEmpty()
                && feature.getDurationMinutes() >= config.getHighRiskMinDurationMinutes()
                && feature.getRadiusNm() <= config.getHighRiskMaxRadiusNm()
                && waitingEvidence) {
            LoiteringCandidate candidate = new LoiteringCandidate(mmsi, SUB_TYPE_HIGH_RISK_WAITING, segment, feature);
            candidate.setRiskAreaTags(riskAreaTags);
            return Collections.singletonList(candidate);
        }
        return Collections.emptyList();
    }

    public List<LoiteringCandidate> detectWeakLoitering(String mmsi, SegmentWithQuality segment,
                                                         LoiteringConfig config) {
        if (!config.isWeakEnabled() || segment == null || segment.getPoints().size() < config.getWeakMinPoints()) {
            return Collections.emptyList();
        }
        LoiteringEventFeature feature = calculateFeature(segment, config);
        if (feature.getDurationMinutes() >= config.getWeakMinDurationMinutes()
                && feature.getAvgSog() <= config.getWeakMaxSpeed()
                && feature.getRadiusNm() <= config.getWeakMaxRadiusNm()
                && feature.getPointCount() >= config.getWeakMinPoints()
                && feature.getMaxAisGapMinutes() <= config.getWeakMaxGapMinutes()) {
            LoiteringCandidate candidate = new LoiteringCandidate(mmsi, SUB_TYPE_WEAK_LOITERING_CANDIDATE, segment, feature);
            candidate.setWeakCandidate(true);
            return Collections.singletonList(candidate);
        }
        return Collections.emptyList();
    }

    public double calculateRadiusNm(List<TrajectoryPoint> points, double centerLon, double centerLat) {
        if (points == null || points.isEmpty()) {
            return 0.0;
        }
        TrajectoryPoint center = new TrajectoryPoint(null, points.get(0).getTime(), centerLon, centerLat, 0.0, 0.0, null);
        double max = 0.0;
        for (TrajectoryPoint point : points) {
            max = Math.max(max, TrajectoryUtils.distanceNm(center, point));
        }
        return max;
    }

    public double[] calculateCenter(List<TrajectoryPoint> points) {
        double lon = 0.0;
        double lat = 0.0;
        for (TrajectoryPoint point : points) {
            lon += point.getLon();
            lat += point.getLat();
        }
        return new double[]{lon / points.size(), lat / points.size()};
    }

    public double calculateTurnCountPerHour(List<TrajectoryPoint> points, long durationMinutes, double turnThresholdDegree) {
        if (points == null || points.size() < 2 || durationMinutes <= 0) {
            return 0.0;
        }
        int turns = 0;
        Double previousCog = null;
        for (TrajectoryPoint point : points) {
            if (!isValidCourse(point.getCourse())) {
                continue;
            }
            if (previousCog != null && circularCogDelta(previousCog, point.getCourse()) >= turnThresholdDegree) {
                turns++;
            }
            previousCog = point.getCourse();
        }
        return turns / (durationMinutes / 60.0);
    }

    public double calculateCogChangePerHour(List<TrajectoryPoint> points, long durationMinutes) {
        if (points == null || points.size() < 2 || durationMinutes <= 0) {
            return 0.0;
        }
        double total = 0.0;
        Double previousCog = null;
        for (TrajectoryPoint point : points) {
            if (!isValidCourse(point.getCourse())) {
                continue;
            }
            if (previousCog != null) {
                total += circularCogDelta(previousCog, point.getCourse());
            }
            previousCog = point.getCourse();
        }
        return total / (durationMinutes / 60.0);
    }

    public double calculateTortuosity(List<TrajectoryPoint> points, double radiusNm) {
        if (points == null || points.size() < 2) {
            return 0.0;
        }
        double actualDistanceNm = 0.0;
        for (int i = 1; i < points.size(); i++) {
            actualDistanceNm += TrajectoryUtils.distanceNm(points.get(i - 1), points.get(i));
        }
        double directDistanceNm = TrajectoryUtils.distanceNm(points.get(0), points.get(points.size() - 1));
        if (directDistanceNm < 0.5) {
            return actualDistanceNm / Math.max(radiusNm, 0.1);
        }
        return actualDistanceNm / directDistanceNm;
    }

    public double calculateTrackCoverageRatio(long durationMinutes, int pointCount, LoiteringConfig config) {
        long expectedPointCount = durationMinutes / Math.max(1L, config.getMaxGapMinutes()) + 1L;
        if (expectedPointCount <= 0) {
            return 1.0;
        }
        return Math.min(1.0, pointCount / (double) expectedPointCount);
    }

    public void applyNormalSceneExclusion(List<LoiteringCandidate> candidates) {
        for (LoiteringCandidate candidate : candidates) {
            if (hasAnchoredOrMooredStatus(candidate.getSegment().getPoints())) {
                candidate.setExclusionFlag(DOWNGRADED_NORMAL_LIKELY);
                candidate.setExclusionReason("NAV_STATUS_ANCHORED_OR_MOORED");
            } else {
                candidate.setExclusionFlag(VALID_RISK_EVENT);
                candidate.setExclusionReason("NO_SCENE_DATA");
            }
        }
    }

    public LoiteringEventScore calculateEventScore(LoiteringCandidate candidate, LoiteringConfig config) {
        LoiteringEventFeature feature = candidate.getFeature();
        int durationScore = calculateDurationScore(feature.getDurationMinutes());
        int trajectoryScore = calculateTrajectoryScore(feature, config);
        int areaScore = candidate.getRiskAreaTags().isEmpty() ? 0 : 10;
        int evidenceScore = calculateEvidenceScore(feature);
        double confidenceAdjustment = CONFIDENCE_LOW.equals(feature.getConfidenceLevel()) ? 0.6 : 1.0;
        double normalSceneAdjustment = DOWNGRADED_NORMAL_LIKELY.equals(candidate.getExclusionFlag()) ? 0.5 : 1.0;
        double rawScore = durationScore + trajectoryScore + areaScore + evidenceScore;
        double score = rawScore;
        if (EXCLUDED_NORMAL.equals(candidate.getExclusionFlag())) {
            score = 0.0;
        } else {
            score = score * normalSceneAdjustment * confidenceAdjustment;
        }
        if (candidate.isWeakCandidate()) {
            score = Math.min(score, config.getWeakMaxScore());
        }
        score = Math.max(0.0, Math.min(100.0, score));
        String rawSeverity = determineSeverityLevel(rawScore);
        String finalSeverity = determineSeverityLevel(score);
        String severityCapReason = "";
        if (CONFIDENCE_LOW.equals(feature.getConfidenceLevel()) && severityRank(finalSeverity) > severityRank("L2")) {
            finalSeverity = "L2";
            severityCapReason = "LOW_CONFIDENCE_MAX_L2";
        }
        if (config.isSeverityCapEnabled() && config.isRequireExternalEvidenceForL3()
                && shouldCapNoSceneData(candidate, config)
                && severityRank(finalSeverity) > severityRank(config.getSeverityCapNoSceneDataMaxLevel())) {
            finalSeverity = config.getSeverityCapNoSceneDataMaxLevel();
            severityCapReason = "NO_SCENE_DATA_NO_EXTERNAL_EVIDENCE";
        }
        if (!config.isAutoL4Enabled() && "L4".equals(finalSeverity)) {
            finalSeverity = "L3";
            severityCapReason = "AUTO_L4_DISABLED";
        }
        if (candidate.isWeakCandidate()) {
            finalSeverity = "L1";
        }
        return new LoiteringEventScore(durationScore, trajectoryScore, areaScore, evidenceScore,
                confidenceAdjustment, normalSceneAdjustment, rawScore, score, rawSeverity, finalSeverity,
                severityCapReason, config.isSceneDataAvailable(), config.isHighRiskEnabled(),
                candidate.isWeakCandidate(), !candidate.isWeakCandidate(), candidate.getMatchedSubTypes(),
                candidate.getMergeReason(), candidate.getRawCandidateCount());
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

    public ShipBehaviorTrace buildTrace(LoiteringCandidate candidate) {
        ShipBehaviorEvent event = new ShipBehaviorEvent();
        LoiteringEventFeature feature = candidate.getFeature();
        List<TrajectoryPoint> points = candidate.getSegment().getPoints();
        TrajectorySegment segment = new TrajectorySegment(points);
        TrajectoryPoint start = segment.getStartPoint();
        TrajectoryPoint end = segment.getEndPoint();
        event.setBehaviorType(ShipBehaviorEvent.TYPE_LOITERING);
        event.setMmsi(candidate.getMmsi());
        event.setStartTime(TrajectoryUtils.formatTime(start));
        event.setEndTime(TrajectoryUtils.formatTime(end));
        event.setDurationMinutes(feature.getDurationMinutes());
        event.setStartLon(start.getLon());
        event.setStartLat(start.getLat());
        event.setEndLon(end.getLon());
        event.setEndLat(end.getLat());
        event.setMaxDistanceNm(feature.getRadiusNm());
        event.setAvgSpeed(feature.getAvgSog());
        event.setPointCount(feature.getPointCount());
        event.setRuleVersion(candidate.getRuleVersion());
        event.setEventSubType(candidate.getSubType());
        event.setSeverityLevel(candidate.getScore().getSeverityLevel());
        event.setEventScore(candidate.getScore().getEventScore());
        event.setConfidenceLevel(feature.getConfidenceLevel());
        event.setExclusionFlag(candidate.getExclusionFlag());
        event.setExclusionReason(candidate.getExclusionReason());
        event.setAisGapCount(feature.getAisGapCount());
        event.setMaxAisGapMinutes(feature.getMaxAisGapMinutes());
        event.setTrackCoverageRatio(feature.getTrackCoverageRatio());
        event.setTurnCountPerHour(feature.getTurnCountPerHour());
        event.setCogChangePerHour(feature.getCogChangePerHour());
        event.setTortuosity(feature.getTortuosity());
        event.setCenterLon(feature.getCenterLon());
        event.setCenterLat(feature.getCenterLat());
        event.setRadiusNm(feature.getRadiusNm());
        event.setScoreDetailJson(candidate.getScore().toJson());
        return new ShipBehaviorTrace(event, new ArrayList<TrajectoryPoint>(points), Collections.<TrajectoryPoint>emptyList());
    }

    private List<LoiteringCandidate> mergeCandidates(List<LoiteringCandidate> candidates, LoiteringConfig config) {
        if (!config.isMergeEnabled() || candidates == null || candidates.size() < 2) {
            return candidates == null ? Collections.<LoiteringCandidate>emptyList() : candidates;
        }
        List<LoiteringCandidate> merged = new ArrayList<LoiteringCandidate>();
        boolean[] used = new boolean[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            if (used[i]) {
                continue;
            }
            LoiteringCandidate current = candidates.get(i);
            used[i] = true;
            for (int j = i + 1; j < candidates.size(); j++) {
                if (!used[j] && shouldMerge(current, candidates.get(j), config)) {
                    current = mergeTwoCandidates(current, candidates.get(j), config);
                    used[j] = true;
                }
            }
            merged.add(current);
        }
        return merged;
    }

    private boolean shouldMerge(LoiteringCandidate a, LoiteringCandidate b, LoiteringConfig config) {
        if (a == null || b == null || !same(a.getMmsi(), b.getMmsi())) {
            return false;
        }
        boolean timeOverlap = timeOverlapRatio(a, b) >= config.getMergeMinOverlapRatio()
                || sameWindow(a, b);
        boolean pointOverlap = pointOverlapRatio(a.getSegment().getPoints(), b.getSegment().getPoints())
                >= config.getMergeMinOverlapRatio();
        boolean spaceClose = centerDistanceNm(a, b) <= config.getMergeMaxCenterDistanceNm();
        return (timeOverlap && spaceClose) || pointOverlap || sameWindow(a, b);
    }

    private LoiteringCandidate mergeTwoCandidates(LoiteringCandidate a, LoiteringCandidate b, LoiteringConfig config) {
        List<TrajectoryPoint> points = mergePoints(a.getSegment().getPoints(), b.getSegment().getPoints());
        SegmentWithQuality segment = recalculateSegmentQuality(points, config);
        LoiteringEventFeature feature = calculateFeature(segment, config);
        LoiteringCandidate merged = new LoiteringCandidate(a.getMmsi(), config.getMergeCompositeSubtype(), segment, feature);
        merged.setMatchedSubTypes(unionSubTypes(a, b));
        merged.setMergeReason("OVERLAP_TIME_AND_SPACE");
        merged.setRawCandidateCount(a.getRawCandidateCount() + b.getRawCandidateCount());
        merged.setWeakCandidate(a.isWeakCandidate() && b.isWeakCandidate());
        return merged;
    }

    private List<LoiteringCandidate> filterStoredCandidates(List<LoiteringCandidate> candidates, LoiteringConfig config) {
        List<LoiteringCandidate> stored = new ArrayList<LoiteringCandidate>();
        for (LoiteringCandidate candidate : candidates) {
            if (!candidate.isWeakCandidate() || config.isWeakStoreEnabled()) {
                stored.add(candidate);
            }
        }
        return stored;
    }

    private void collectRejectAndNearMiss(SegmentWithQuality segment, LoiteringConfig config, RejectSummary summary) {
        if (segment == null || segment.getPoints().isEmpty()) {
            summary.rejectByUnknown++;
            return;
        }
        LoiteringEventFeature feature = calculateFeature(segment, config);
        String failedRules = failedRules(feature, config);
        summary.addReject(feature, config, failedRules);
        if (isNearMiss(feature)) {
            summary.addNearMiss(new NearMiss(segment, feature, failedRules));
        }
    }

    private String failedRules(LoiteringEventFeature feature, LoiteringConfig config) {
        List<String> failed = new ArrayList<String>();
        if (feature.getDurationMinutes() < config.getWeakMinDurationMinutes()) {
            failed.add("duration");
        }
        if (feature.getPointCount() < config.getWeakMinPoints()) {
            failed.add("pointCount");
        }
        if (feature.getRadiusNm() > config.getWeakMaxRadiusNm()) {
            failed.add("radius");
        }
        if (feature.getAvgSog() > config.getWeakMaxSpeed()) {
            failed.add("speed");
        }
        if (feature.getTurnCountPerHour() < config.getManeuverMinTurnCountPerHour()
                && feature.getCogChangePerHour() < config.getManeuverMinCogChangePerHour()
                && feature.getTortuosity() < config.getManeuverMinTortuosity()) {
            failed.add("turnFeature");
        }
        if (feature.getMaxAisGapMinutes() > config.getWeakMaxGapMinutes()) {
            failed.add("gap");
        }
        if (feature.getTrackCoverageRatio() < 0.6) {
            failed.add("coverage");
        }
        return join(failed, "|");
    }

    private boolean isNearMiss(LoiteringEventFeature feature) {
        return feature.getDurationMinutes() >= 60 && feature.getAvgSog() <= 3.0
                || feature.getDurationMinutes() >= 120 && feature.getRadiusNm() <= 8.0
                || feature.getPointCount() >= 6 && feature.getAvgSog() <= 2.0
                || feature.getRadiusNm() <= 5.0 && feature.getAvgSog() <= 3.0
                || feature.getDurationMinutes() >= 60 && feature.getPointCount() >= 4
                && feature.getMaxAisGapMinutes() <= 180;
    }

    private boolean shouldCapNoSceneData(LoiteringCandidate candidate, LoiteringConfig config) {
        LoiteringEventFeature feature = candidate.getFeature();
        return "NO_SCENE_DATA".equals(candidate.getExclusionReason())
                && candidate.getRiskAreaTags().isEmpty()
                && feature.getDurationMinutes() < config.getSeverityCapNoSceneDataMaxDurationMinutes()
                && !CONFIDENCE_HIGH.equals(feature.getConfidenceLevel())
                && !hasSeriousAisGap(feature);
    }

    private boolean hasSeriousAisGap(LoiteringEventFeature feature) {
        return feature.getAisGapCount() > 0 && feature.getMaxAisGapMinutes() >= 120;
    }

    private double timeOverlapRatio(LoiteringCandidate a, LoiteringCandidate b) {
        long start = Math.max(firstMillis(a), firstMillis(b));
        long end = Math.min(lastMillis(a), lastMillis(b));
        if (end <= start) {
            return 0.0;
        }
        long duration = Math.min(Math.max(1L, lastMillis(a) - firstMillis(a)),
                Math.max(1L, lastMillis(b) - firstMillis(b)));
        return (end - start) / (double) duration;
    }

    private boolean sameWindow(LoiteringCandidate a, LoiteringCandidate b) {
        return firstMillis(a) == firstMillis(b) && lastMillis(a) == lastMillis(b);
    }

    private long firstMillis(LoiteringCandidate candidate) {
        return java.sql.Timestamp.valueOf(candidate.getSegment().getPoints().get(0).getTime()).getTime();
    }

    private long lastMillis(LoiteringCandidate candidate) {
        List<TrajectoryPoint> points = candidate.getSegment().getPoints();
        return java.sql.Timestamp.valueOf(points.get(points.size() - 1).getTime()).getTime();
    }

    private double pointOverlapRatio(List<TrajectoryPoint> a, List<TrajectoryPoint> b) {
        Set<String> aKeys = pointKeys(a);
        Set<String> bKeys = pointKeys(b);
        int overlap = 0;
        for (String key : aKeys) {
            if (bKeys.contains(key)) {
                overlap++;
            }
        }
        return overlap / (double) Math.max(1, Math.min(aKeys.size(), bKeys.size()));
    }

    private Set<String> pointKeys(List<TrajectoryPoint> points) {
        Set<String> keys = new HashSet<String>();
        for (TrajectoryPoint point : points) {
            keys.add(point.getTime() + "|" + point.getLon() + "|" + point.getLat());
        }
        return keys;
    }

    private double centerDistanceNm(LoiteringCandidate a, LoiteringCandidate b) {
        TrajectoryPoint pa = new TrajectoryPoint(a.getMmsi(), a.getSegment().getPoints().get(0).getTime(),
                a.getFeature().getCenterLon(), a.getFeature().getCenterLat(), 0.0, 0.0, null);
        TrajectoryPoint pb = new TrajectoryPoint(b.getMmsi(), b.getSegment().getPoints().get(0).getTime(),
                b.getFeature().getCenterLon(), b.getFeature().getCenterLat(), 0.0, 0.0, null);
        return TrajectoryUtils.distanceNm(pa, pb);
    }

    private List<TrajectoryPoint> mergePoints(List<TrajectoryPoint> a, List<TrajectoryPoint> b) {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        Set<String> seen = new HashSet<String>();
        appendUniquePoints(result, seen, a);
        appendUniquePoints(result, seen, b);
        Collections.sort(result, new Comparator<TrajectoryPoint>() {
            @Override
            public int compare(TrajectoryPoint a, TrajectoryPoint b) {
                return a.getTime().compareTo(b.getTime());
            }
        });
        return result;
    }

    private void appendUniquePoints(List<TrajectoryPoint> result, Set<String> seen, List<TrajectoryPoint> points) {
        for (TrajectoryPoint point : points) {
            String key = point.getTime() + "|" + point.getLon() + "|" + point.getLat();
            if (!seen.contains(key)) {
                seen.add(key);
                result.add(point);
            }
        }
    }

    private SegmentWithQuality recalculateSegmentQuality(List<TrajectoryPoint> points, LoiteringConfig config) {
        int gapCount = 0;
        long maxGap = 0L;
        for (int i = 1; i < points.size(); i++) {
            long gap = Duration.between(points.get(i - 1).getTime(), points.get(i).getTime()).toMinutes();
            if (gap > config.getMaxGapMinutes()) {
                gapCount++;
                maxGap = Math.max(maxGap, gap);
            }
        }
        return new SegmentWithQuality(points, gapCount, maxGap);
    }

    private List<String> unionSubTypes(LoiteringCandidate a, LoiteringCandidate b) {
        LinkedHashSet<String> subTypes = new LinkedHashSet<String>();
        subTypes.addAll(a.getMatchedSubTypes());
        subTypes.addAll(b.getMatchedSubTypes());
        return new ArrayList<String>(subTypes);
    }

    private boolean same(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private String join(List<String> values, String delimiter) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(delimiter);
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private void addSegment(List<SegmentWithQuality> segments, List<TrajectoryPoint> points,
                            int aisGapCount, long maxGap) {
        if (points != null && !points.isEmpty()) {
            segments.add(new SegmentWithQuality(new ArrayList<TrajectoryPoint>(points), aisGapCount, maxGap));
        }
    }

    private boolean isValidPoint(TrajectoryPoint point, LoiteringConfig config) {
        return point != null
                && point.getTime() != null
                && point.getLon() >= -180.0
                && point.getLon() <= 180.0
                && point.getLat() >= -90.0
                && point.getLat() <= 90.0
                && point.getSpeed() >= 0.0
                && point.getSpeed() <= config.getMaxValidSpeed();
    }

    private LoiteringEventFeature calculateFeature(SegmentWithQuality segment, LoiteringConfig config) {
        List<TrajectoryPoint> points = segment.getPoints();
        TrajectorySegment trajectorySegment = new TrajectorySegment(points);
        long durationMinutes = Math.max(0L, trajectorySegment.getDurationMinutes());
        double[] center = calculateCenter(points);
        double radiusNm = calculateRadiusNm(points, center[0], center[1]);
        double turnCountPerHour = calculateTurnCountPerHour(points, durationMinutes, config.getTurnThresholdDegree());
        double cogChangePerHour = calculateCogChangePerHour(points, durationMinutes);
        double tortuosity = calculateTortuosity(points, radiusNm);
        double coverage = calculateTrackCoverageRatio(durationMinutes, points.size(), config);
        String confidence = determineConfidence(coverage, segment.getMaxAisGapMinutes(), points.size());
        return new LoiteringEventFeature(durationMinutes, points.size(), TrajectoryUtils.averageSpeed(points),
                center[0], center[1], radiusNm, segment.getAisGapCount(), segment.getMaxAisGapMinutes(),
                coverage, confidence, turnCountPerHour, cogChangePerHour, tortuosity);
    }

    private String determineConfidence(double coverage, long maxGapMinutes, int pointCount) {
        if (coverage < 0.6 || pointCount < 12 || maxGapMinutes > 120) {
            return CONFIDENCE_LOW;
        }
        if (coverage >= 0.8 && maxGapMinutes <= 30 && pointCount >= 12) {
            return CONFIDENCE_HIGH;
        }
        if (coverage >= 0.6 && maxGapMinutes <= 120) {
            return CONFIDENCE_MEDIUM;
        }
        return CONFIDENCE_LOW;
    }

    private List<String> resolveRiskAreaTags(LoiteringEventFeature feature) {
        return Collections.emptyList();
    }

    private void scoreCandidates(List<LoiteringCandidate> candidates, LoiteringConfig config) {
        for (LoiteringCandidate candidate : candidates) {
            candidate.setRuleVersion(config.getRuleVersion());
            candidate.setScore(calculateEventScore(candidate, config));
        }
    }

    private List<ShipBehaviorTrace> buildTraces(List<LoiteringCandidate> candidates) {
        List<ShipBehaviorTrace> traces = new ArrayList<ShipBehaviorTrace>();
        for (LoiteringCandidate candidate : candidates) {
            traces.add(buildTrace(candidate));
        }
        return traces;
    }

    private int countExcluded(List<LoiteringCandidate> candidates) {
        int count = 0;
        for (LoiteringCandidate candidate : candidates) {
            if (EXCLUDED_NORMAL.equals(candidate.getExclusionFlag())
                    || DOWNGRADED_NORMAL_LIKELY.equals(candidate.getExclusionFlag())) {
                count++;
            }
        }
        return count;
    }

    private int countLowConfidence(List<LoiteringCandidate> candidates) {
        int count = 0;
        for (LoiteringCandidate candidate : candidates) {
            if (CONFIDENCE_LOW.equals(candidate.getFeature().getConfidenceLevel())) {
                count++;
            }
        }
        return count;
    }

    private int calculateDurationScore(long durationMinutes) {
        if (durationMinutes < 60) {
            return 0;
        }
        if (durationMinutes < 120) {
            return 5;
        }
        if (durationMinutes < 240) {
            return 10;
        }
        if (durationMinutes < 720) {
            return 18;
        }
        return 25;
    }

    private int calculateTrajectoryScore(LoiteringEventFeature feature, LoiteringConfig config) {
        int score = 0;
        if (feature.getAvgSog() <= config.getSlowMaxSpeed()) {
            score += 8;
        }
        if (feature.getRadiusNm() <= config.getSlowMaxRadiusNm()) {
            score += 7;
        }
        if (feature.getTurnCountPerHour() >= config.getManeuverMinTurnCountPerHour()) {
            score += 5;
        }
        if (feature.getCogChangePerHour() >= config.getManeuverMinCogChangePerHour()) {
            score += 5;
        }
        if (feature.getTortuosity() >= config.getManeuverMinTortuosity()) {
            score += 5;
        }
        return Math.min(25, score);
    }

    private int calculateEvidenceScore(LoiteringEventFeature feature) {
        int score = 0;
        if (feature.getAisGapCount() > 0) {
            score += 5;
        }
        if (feature.getMaxAisGapMinutes() > 30) {
            score += 5;
        }
        return Math.min(25, score);
    }

    private boolean hasAnchoredOrMooredStatus(List<TrajectoryPoint> points) {
        for (TrajectoryPoint point : points) {
            String status = point.getNavStatus();
            if (status == null) {
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

    public double circularCogDelta(double cog1, double cog2) {
        double delta = Math.abs(cog2 - cog1);
        return delta > 180.0 ? 360.0 - delta : delta;
    }

    private boolean isValidCourse(double course) {
        return course >= 0.0 && course <= 360.0;
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

    public static class DetectionStats {
        private final int points;
        private final int cleanPoints;
        private final int segments;
        private final int candidates;
        private final int rawCandidates;
        private final int mergedCandidates;
        private final int savedEvents;
        private final int validRiskEvents;
        private final int excludedNormalEvents;
        private final int downgradedNormalLikelyEvents;
        private final int excludedEvents;
        private final int lowConfidenceEvents;
        private final int weakCandidates;
        private final int duplicateMerged;
        private final boolean sceneDataAvailable;
        private final boolean riskAreaEnabled;
        private final RejectSummary rejectSummary;

        private DetectionStats(int points, int cleanPoints, int segments, int candidates,
                               int excludedEvents, int lowConfidenceEvents) {
            this.points = points;
            this.cleanPoints = cleanPoints;
            this.segments = segments;
            this.candidates = candidates;
            this.rawCandidates = candidates;
            this.mergedCandidates = candidates;
            this.savedEvents = candidates;
            this.validRiskEvents = 0;
            this.excludedNormalEvents = excludedEvents;
            this.downgradedNormalLikelyEvents = 0;
            this.excludedEvents = excludedEvents;
            this.lowConfidenceEvents = lowConfidenceEvents;
            this.weakCandidates = 0;
            this.duplicateMerged = 0;
            this.sceneDataAvailable = false;
            this.riskAreaEnabled = false;
            this.rejectSummary = new RejectSummary();
        }

        private DetectionStats(int points, int cleanPoints, int segments, int rawCandidates,
                               int mergedCandidates, int savedEvents, int validRiskEvents,
                               int excludedNormalEvents, int downgradedNormalLikelyEvents,
                               int lowConfidenceEvents, int weakCandidates, boolean sceneDataAvailable,
                               boolean riskAreaEnabled, RejectSummary rejectSummary) {
            this.points = points;
            this.cleanPoints = cleanPoints;
            this.segments = segments;
            this.candidates = rawCandidates;
            this.rawCandidates = rawCandidates;
            this.mergedCandidates = mergedCandidates;
            this.savedEvents = savedEvents;
            this.validRiskEvents = validRiskEvents;
            this.excludedNormalEvents = excludedNormalEvents;
            this.downgradedNormalLikelyEvents = downgradedNormalLikelyEvents;
            this.excludedEvents = excludedNormalEvents;
            this.lowConfidenceEvents = lowConfidenceEvents;
            this.weakCandidates = weakCandidates;
            this.duplicateMerged = Math.max(0, rawCandidates - mergedCandidates);
            this.sceneDataAvailable = sceneDataAvailable;
            this.riskAreaEnabled = riskAreaEnabled;
            this.rejectSummary = rejectSummary == null ? new RejectSummary() : rejectSummary;
        }

        private static DetectionStats empty() {
            return new DetectionStats(0, 0, 0, 0, 0, 0);
        }

        private static DetectionStats from(int points, int cleanPoints, int segments, int rawCandidates,
                                           List<LoiteringCandidate> mergedCandidates, int savedEvents,
                                           RejectSummary rejectSummary, LoiteringConfig config) {
            int validRisk = 0;
            int excludedNormal = 0;
            int downgraded = 0;
            int lowConfidence = 0;
            int weak = 0;
            for (LoiteringCandidate candidate : mergedCandidates) {
                if (VALID_RISK_EVENT.equals(candidate.getExclusionFlag())) {
                    validRisk++;
                } else if (EXCLUDED_NORMAL.equals(candidate.getExclusionFlag())) {
                    excludedNormal++;
                } else if (DOWNGRADED_NORMAL_LIKELY.equals(candidate.getExclusionFlag())) {
                    downgraded++;
                }
                if (CONFIDENCE_LOW.equals(candidate.getFeature().getConfidenceLevel())) {
                    lowConfidence++;
                }
                if (candidate.isWeakCandidate()) {
                    weak++;
                }
            }
            return new DetectionStats(points, cleanPoints, segments, rawCandidates, mergedCandidates.size(),
                    savedEvents, validRisk, excludedNormal, downgraded, lowConfidence, weak,
                    config.isSceneDataAvailable(), config.isHighRiskEnabled(), rejectSummary);
        }

        public int getPoints() {
            return points;
        }

        public int getCleanPoints() {
            return cleanPoints;
        }

        public int getSegments() {
            return segments;
        }

        public int getCandidates() {
            return candidates;
        }

        public int getRawCandidates() {
            return rawCandidates;
        }

        public int getMergedCandidates() {
            return mergedCandidates;
        }

        public int getSavedEvents() {
            return savedEvents;
        }

        public int getValidRiskEvents() {
            return validRiskEvents;
        }

        public int getExcludedNormalEvents() {
            return excludedNormalEvents;
        }

        public int getDowngradedNormalLikelyEvents() {
            return downgradedNormalLikelyEvents;
        }

        public int getExcludedEvents() {
            return excludedEvents;
        }

        public int getLowConfidenceEvents() {
            return lowConfidenceEvents;
        }

        public int getWeakCandidates() {
            return weakCandidates;
        }

        public int getDuplicateMerged() {
            return duplicateMerged;
        }

        public boolean isSceneDataAvailable() {
            return sceneDataAvailable;
        }

        public boolean isRiskAreaEnabled() {
            return riskAreaEnabled;
        }

        public RejectSummary getRejectSummary() {
            return rejectSummary;
        }
    }

    public static class LoiteringConfig {
        private String ruleVersion = "LOITERING_V2_INSURANCE";
        private long maxGapMinutes = 30L;
        private long eventSplitGapMinutes = 120L;
        private double maxValidSpeed = 80.0;
        private int minWarningPoints = 6;
        private double slowMaxSpeed = 2.0;
        private long slowMinDurationMinutes = 240L;
        private double slowMaxRadiusNm = 3.0;
        private int slowMinPoints = 12;
        private double maneuverMinSpeed = 0.5;
        private double maneuverMaxSpeed = 5.0;
        private long maneuverMinDurationMinutes = 120L;
        private double maneuverMaxRadiusNm = 5.0;
        private double maneuverMinTurnCountPerHour = 3.0;
        private double maneuverMinCogChangePerHour = 120.0;
        private double maneuverMinTortuosity = 3.0;
        private double turnThresholdDegree = 45.0;
        private boolean highRiskEnabled = false;
        private long highRiskMinDurationMinutes = 60L;
        private double highRiskMaxRadiusNm = 5.0;
        private boolean mergeEnabled = true;
        private double mergeMinOverlapRatio = 0.8;
        private double mergeMaxCenterDistanceNm = 1.0;
        private String mergeCompositeSubtype = SUB_TYPE_COMPOSITE_LOITERING;
        private boolean severityCapEnabled = true;
        private String severityCapNoSceneDataMaxLevel = "L2";
        private long severityCapNoSceneDataMaxDurationMinutes = 720L;
        private boolean requireExternalEvidenceForL3 = true;
        private boolean autoL4Enabled = false;
        private boolean sceneDataAvailable = false;
        private boolean diagnosticEnabled = true;
        private int nearMissLimit = 10;
        private boolean weakEnabled = true;
        private boolean weakStoreEnabled = true;
        private long weakMinDurationMinutes = 60L;
        private double weakMaxSpeed = 3.0;
        private double weakMaxRadiusNm = 8.0;
        private int weakMinPoints = 6;
        private long weakMaxGapMinutes = 180L;
        private double weakMaxScore = 20.0;

        public static LoiteringConfig defaultConfig() {
            return new LoiteringConfig();
        }

        public static LoiteringConfig fromRule(LoiteringRule rule) {
            LoiteringConfig config = defaultConfig();
            if (rule != null) {
                config.setSlowMaxSpeed(rule.getMaxSpeed());
                config.setSlowMinDurationMinutes(rule.getMinDurationMinutes());
                config.setSlowMaxRadiusNm(rule.getMaxRadiusNm());
                config.setSlowMinPoints(rule.getMinPoints());
                config.setRuleVersion(rule.getRuleVersion());
            }
            return config;
        }

        public String getRuleVersion() {
            return ruleVersion;
        }

        public void setRuleVersion(String ruleVersion) {
            this.ruleVersion = ruleVersion;
        }

        public long getMaxGapMinutes() {
            return maxGapMinutes;
        }

        public void setMaxGapMinutes(long maxGapMinutes) {
            this.maxGapMinutes = maxGapMinutes;
        }

        public long getEventSplitGapMinutes() {
            return eventSplitGapMinutes;
        }

        public void setEventSplitGapMinutes(long eventSplitGapMinutes) {
            this.eventSplitGapMinutes = eventSplitGapMinutes;
        }

        public double getMaxValidSpeed() {
            return maxValidSpeed;
        }

        public void setMaxValidSpeed(double maxValidSpeed) {
            this.maxValidSpeed = maxValidSpeed;
        }

        public int getMinWarningPoints() {
            return minWarningPoints;
        }

        public void setMinWarningPoints(int minWarningPoints) {
            this.minWarningPoints = minWarningPoints;
        }

        public double getSlowMaxSpeed() {
            return slowMaxSpeed;
        }

        public void setSlowMaxSpeed(double slowMaxSpeed) {
            this.slowMaxSpeed = slowMaxSpeed;
        }

        public long getSlowMinDurationMinutes() {
            return slowMinDurationMinutes;
        }

        public void setSlowMinDurationMinutes(long slowMinDurationMinutes) {
            this.slowMinDurationMinutes = slowMinDurationMinutes;
        }

        public double getSlowMaxRadiusNm() {
            return slowMaxRadiusNm;
        }

        public void setSlowMaxRadiusNm(double slowMaxRadiusNm) {
            this.slowMaxRadiusNm = slowMaxRadiusNm;
        }

        public int getSlowMinPoints() {
            return slowMinPoints;
        }

        public void setSlowMinPoints(int slowMinPoints) {
            this.slowMinPoints = slowMinPoints;
        }

        public double getManeuverMinSpeed() {
            return maneuverMinSpeed;
        }

        public void setManeuverMinSpeed(double maneuverMinSpeed) {
            this.maneuverMinSpeed = maneuverMinSpeed;
        }

        public double getManeuverMaxSpeed() {
            return maneuverMaxSpeed;
        }

        public void setManeuverMaxSpeed(double maneuverMaxSpeed) {
            this.maneuverMaxSpeed = maneuverMaxSpeed;
        }

        public long getManeuverMinDurationMinutes() {
            return maneuverMinDurationMinutes;
        }

        public void setManeuverMinDurationMinutes(long maneuverMinDurationMinutes) {
            this.maneuverMinDurationMinutes = maneuverMinDurationMinutes;
        }

        public double getManeuverMaxRadiusNm() {
            return maneuverMaxRadiusNm;
        }

        public void setManeuverMaxRadiusNm(double maneuverMaxRadiusNm) {
            this.maneuverMaxRadiusNm = maneuverMaxRadiusNm;
        }

        public double getManeuverMinTurnCountPerHour() {
            return maneuverMinTurnCountPerHour;
        }

        public void setManeuverMinTurnCountPerHour(double maneuverMinTurnCountPerHour) {
            this.maneuverMinTurnCountPerHour = maneuverMinTurnCountPerHour;
        }

        public double getManeuverMinCogChangePerHour() {
            return maneuverMinCogChangePerHour;
        }

        public void setManeuverMinCogChangePerHour(double maneuverMinCogChangePerHour) {
            this.maneuverMinCogChangePerHour = maneuverMinCogChangePerHour;
        }

        public double getManeuverMinTortuosity() {
            return maneuverMinTortuosity;
        }

        public void setManeuverMinTortuosity(double maneuverMinTortuosity) {
            this.maneuverMinTortuosity = maneuverMinTortuosity;
        }

        public double getTurnThresholdDegree() {
            return turnThresholdDegree;
        }

        public void setTurnThresholdDegree(double turnThresholdDegree) {
            this.turnThresholdDegree = turnThresholdDegree;
        }

        public boolean isHighRiskEnabled() {
            return highRiskEnabled;
        }

        public void setHighRiskEnabled(boolean highRiskEnabled) {
            this.highRiskEnabled = highRiskEnabled;
        }

        public long getHighRiskMinDurationMinutes() {
            return highRiskMinDurationMinutes;
        }

        public void setHighRiskMinDurationMinutes(long highRiskMinDurationMinutes) {
            this.highRiskMinDurationMinutes = highRiskMinDurationMinutes;
        }

        public double getHighRiskMaxRadiusNm() {
            return highRiskMaxRadiusNm;
        }

        public void setHighRiskMaxRadiusNm(double highRiskMaxRadiusNm) {
            this.highRiskMaxRadiusNm = highRiskMaxRadiusNm;
        }

        public boolean isMergeEnabled() {
            return mergeEnabled;
        }

        public void setMergeEnabled(boolean mergeEnabled) {
            this.mergeEnabled = mergeEnabled;
        }

        public double getMergeMinOverlapRatio() {
            return mergeMinOverlapRatio;
        }

        public void setMergeMinOverlapRatio(double mergeMinOverlapRatio) {
            this.mergeMinOverlapRatio = mergeMinOverlapRatio;
        }

        public double getMergeMaxCenterDistanceNm() {
            return mergeMaxCenterDistanceNm;
        }

        public void setMergeMaxCenterDistanceNm(double mergeMaxCenterDistanceNm) {
            this.mergeMaxCenterDistanceNm = mergeMaxCenterDistanceNm;
        }

        public String getMergeCompositeSubtype() {
            return mergeCompositeSubtype;
        }

        public void setMergeCompositeSubtype(String mergeCompositeSubtype) {
            this.mergeCompositeSubtype = mergeCompositeSubtype;
        }

        public boolean isSeverityCapEnabled() {
            return severityCapEnabled;
        }

        public void setSeverityCapEnabled(boolean severityCapEnabled) {
            this.severityCapEnabled = severityCapEnabled;
        }

        public String getSeverityCapNoSceneDataMaxLevel() {
            return severityCapNoSceneDataMaxLevel;
        }

        public void setSeverityCapNoSceneDataMaxLevel(String severityCapNoSceneDataMaxLevel) {
            this.severityCapNoSceneDataMaxLevel = severityCapNoSceneDataMaxLevel;
        }

        public long getSeverityCapNoSceneDataMaxDurationMinutes() {
            return severityCapNoSceneDataMaxDurationMinutes;
        }

        public void setSeverityCapNoSceneDataMaxDurationMinutes(long severityCapNoSceneDataMaxDurationMinutes) {
            this.severityCapNoSceneDataMaxDurationMinutes = severityCapNoSceneDataMaxDurationMinutes;
        }

        public boolean isRequireExternalEvidenceForL3() {
            return requireExternalEvidenceForL3;
        }

        public void setRequireExternalEvidenceForL3(boolean requireExternalEvidenceForL3) {
            this.requireExternalEvidenceForL3 = requireExternalEvidenceForL3;
        }

        public boolean isAutoL4Enabled() {
            return autoL4Enabled;
        }

        public void setAutoL4Enabled(boolean autoL4Enabled) {
            this.autoL4Enabled = autoL4Enabled;
        }

        public boolean isSceneDataAvailable() {
            return sceneDataAvailable;
        }

        public void setSceneDataAvailable(boolean sceneDataAvailable) {
            this.sceneDataAvailable = sceneDataAvailable;
        }

        public boolean isDiagnosticEnabled() {
            return diagnosticEnabled;
        }

        public void setDiagnosticEnabled(boolean diagnosticEnabled) {
            this.diagnosticEnabled = diagnosticEnabled;
        }

        public int getNearMissLimit() {
            return nearMissLimit;
        }

        public void setNearMissLimit(int nearMissLimit) {
            this.nearMissLimit = nearMissLimit;
        }

        public boolean isWeakEnabled() {
            return weakEnabled;
        }

        public void setWeakEnabled(boolean weakEnabled) {
            this.weakEnabled = weakEnabled;
        }

        public boolean isWeakStoreEnabled() {
            return weakStoreEnabled;
        }

        public void setWeakStoreEnabled(boolean weakStoreEnabled) {
            this.weakStoreEnabled = weakStoreEnabled;
        }

        public long getWeakMinDurationMinutes() {
            return weakMinDurationMinutes;
        }

        public void setWeakMinDurationMinutes(long weakMinDurationMinutes) {
            this.weakMinDurationMinutes = weakMinDurationMinutes;
        }

        public double getWeakMaxSpeed() {
            return weakMaxSpeed;
        }

        public void setWeakMaxSpeed(double weakMaxSpeed) {
            this.weakMaxSpeed = weakMaxSpeed;
        }

        public double getWeakMaxRadiusNm() {
            return weakMaxRadiusNm;
        }

        public void setWeakMaxRadiusNm(double weakMaxRadiusNm) {
            this.weakMaxRadiusNm = weakMaxRadiusNm;
        }

        public int getWeakMinPoints() {
            return weakMinPoints;
        }

        public void setWeakMinPoints(int weakMinPoints) {
            this.weakMinPoints = weakMinPoints;
        }

        public long getWeakMaxGapMinutes() {
            return weakMaxGapMinutes;
        }

        public void setWeakMaxGapMinutes(long weakMaxGapMinutes) {
            this.weakMaxGapMinutes = weakMaxGapMinutes;
        }

        public double getWeakMaxScore() {
            return weakMaxScore;
        }

        public void setWeakMaxScore(double weakMaxScore) {
            this.weakMaxScore = weakMaxScore;
        }
    }

    public static class LoiteringRule {
        private final double maxSpeed;
        private final long minDurationMinutes;
        private final double maxRadiusNm;
        private final int minPoints;
        private final String ruleVersion;

        public LoiteringRule(double maxSpeed, long minDurationMinutes, double maxRadiusNm,
                             int minPoints, String ruleVersion) {
            this.maxSpeed = maxSpeed;
            this.minDurationMinutes = minDurationMinutes;
            this.maxRadiusNm = maxRadiusNm;
            this.minPoints = minPoints;
            this.ruleVersion = ruleVersion;
        }

        public double getMaxSpeed() {
            return maxSpeed;
        }

        public long getMinDurationMinutes() {
            return minDurationMinutes;
        }

        public double getMaxRadiusNm() {
            return maxRadiusNm;
        }

        public int getMinPoints() {
            return minPoints;
        }

        public String getRuleVersion() {
            return ruleVersion;
        }
    }

    public static class SegmentWithQuality {
        private final List<TrajectoryPoint> points;
        private final int aisGapCount;
        private final long maxAisGapMinutes;

        private SegmentWithQuality(List<TrajectoryPoint> points, int aisGapCount, long maxAisGapMinutes) {
            this.points = points;
            this.aisGapCount = aisGapCount;
            this.maxAisGapMinutes = maxAisGapMinutes;
        }

        public List<TrajectoryPoint> getPoints() {
            return points;
        }

        public int getAisGapCount() {
            return aisGapCount;
        }

        public long getMaxAisGapMinutes() {
            return maxAisGapMinutes;
        }
    }

    private static class LoiteringCandidate {
        private final String mmsi;
        private final String subType;
        private final SegmentWithQuality segment;
        private final LoiteringEventFeature feature;
        private String ruleVersion;
        private String exclusionFlag;
        private String exclusionReason;
        private List<String> riskAreaTags = Collections.emptyList();
        private LoiteringEventScore score;
        private List<String> matchedSubTypes = new ArrayList<String>();
        private String mergeReason = "";
        private int rawCandidateCount = 1;
        private boolean weakCandidate;

        private LoiteringCandidate(String mmsi, String subType, SegmentWithQuality segment,
                                   LoiteringEventFeature feature) {
            this.mmsi = mmsi;
            this.subType = subType;
            this.segment = segment;
            this.feature = feature;
            this.matchedSubTypes.add(subType);
        }

        private String getMmsi() {
            return mmsi;
        }

        private String getSubType() {
            return subType;
        }

        private SegmentWithQuality getSegment() {
            return segment;
        }

        private LoiteringEventFeature getFeature() {
            return feature;
        }

        private String getRuleVersion() {
            return ruleVersion;
        }

        private void setRuleVersion(String ruleVersion) {
            this.ruleVersion = ruleVersion;
        }

        private String getExclusionFlag() {
            return exclusionFlag;
        }

        private void setExclusionFlag(String exclusionFlag) {
            this.exclusionFlag = exclusionFlag;
        }

        private String getExclusionReason() {
            return exclusionReason;
        }

        private void setExclusionReason(String exclusionReason) {
            this.exclusionReason = exclusionReason;
        }

        private List<String> getRiskAreaTags() {
            return riskAreaTags;
        }

        private void setRiskAreaTags(List<String> riskAreaTags) {
            this.riskAreaTags = riskAreaTags == null ? Collections.<String>emptyList() : riskAreaTags;
        }

        private LoiteringEventScore getScore() {
            return score;
        }

        private void setScore(LoiteringEventScore score) {
            this.score = score;
        }

        private List<String> getMatchedSubTypes() {
            return matchedSubTypes;
        }

        private void setMatchedSubTypes(List<String> matchedSubTypes) {
            this.matchedSubTypes = matchedSubTypes == null ? new ArrayList<String>() : matchedSubTypes;
        }

        private String getMergeReason() {
            return mergeReason;
        }

        private void setMergeReason(String mergeReason) {
            this.mergeReason = mergeReason == null ? "" : mergeReason;
        }

        private int getRawCandidateCount() {
            return rawCandidateCount;
        }

        private void setRawCandidateCount(int rawCandidateCount) {
            this.rawCandidateCount = rawCandidateCount;
        }

        private boolean isWeakCandidate() {
            return weakCandidate;
        }

        private void setWeakCandidate(boolean weakCandidate) {
            this.weakCandidate = weakCandidate;
        }
    }

    public static class LoiteringEventFeature {
        private final long durationMinutes;
        private final int pointCount;
        private final double avgSog;
        private final double centerLon;
        private final double centerLat;
        private final double radiusNm;
        private final int aisGapCount;
        private final long maxAisGapMinutes;
        private final double trackCoverageRatio;
        private final String confidenceLevel;
        private final double turnCountPerHour;
        private final double cogChangePerHour;
        private final double tortuosity;

        private LoiteringEventFeature(long durationMinutes, int pointCount, double avgSog,
                                      double centerLon, double centerLat, double radiusNm,
                                      int aisGapCount, long maxAisGapMinutes, double trackCoverageRatio,
                                      String confidenceLevel, double turnCountPerHour,
                                      double cogChangePerHour, double tortuosity) {
            this.durationMinutes = durationMinutes;
            this.pointCount = pointCount;
            this.avgSog = avgSog;
            this.centerLon = centerLon;
            this.centerLat = centerLat;
            this.radiusNm = radiusNm;
            this.aisGapCount = aisGapCount;
            this.maxAisGapMinutes = maxAisGapMinutes;
            this.trackCoverageRatio = trackCoverageRatio;
            this.confidenceLevel = confidenceLevel;
            this.turnCountPerHour = turnCountPerHour;
            this.cogChangePerHour = cogChangePerHour;
            this.tortuosity = tortuosity;
        }

        public long getDurationMinutes() {
            return durationMinutes;
        }

        public int getPointCount() {
            return pointCount;
        }

        public double getAvgSog() {
            return avgSog;
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

        public double getTurnCountPerHour() {
            return turnCountPerHour;
        }

        public double getCogChangePerHour() {
            return cogChangePerHour;
        }

        public double getTortuosity() {
            return tortuosity;
        }
    }

    public static class LoiteringEventScore {
        private final int durationScore;
        private final int trajectoryScore;
        private final int areaScore;
        private final int evidenceScore;
        private final double confidenceAdjustment;
        private final double normalSceneAdjustment;
        private final double rawScore;
        private final double eventScore;
        private final String rawSeverity;
        private final String severityLevel;
        private final String severityCapReason;
        private final boolean sceneDataAvailable;
        private final boolean riskAreaEnabled;
        private final boolean weakCandidate;
        private final boolean includeInAnnualScore;
        private final List<String> matchedSubTypes;
        private final String mergeReason;
        private final int rawCandidateCount;

        private LoiteringEventScore(int durationScore, int trajectoryScore, int areaScore, int evidenceScore,
                                    double confidenceAdjustment, double normalSceneAdjustment,
                                    double rawScore, double eventScore, String rawSeverity, String severityLevel,
                                    String severityCapReason, boolean sceneDataAvailable, boolean riskAreaEnabled,
                                    boolean weakCandidate, boolean includeInAnnualScore, List<String> matchedSubTypes,
                                    String mergeReason, int rawCandidateCount) {
            this.durationScore = durationScore;
            this.trajectoryScore = trajectoryScore;
            this.areaScore = areaScore;
            this.evidenceScore = evidenceScore;
            this.confidenceAdjustment = confidenceAdjustment;
            this.normalSceneAdjustment = normalSceneAdjustment;
            this.rawScore = rawScore;
            this.eventScore = eventScore;
            this.rawSeverity = rawSeverity;
            this.severityLevel = severityLevel;
            this.severityCapReason = severityCapReason == null ? "" : severityCapReason;
            this.sceneDataAvailable = sceneDataAvailable;
            this.riskAreaEnabled = riskAreaEnabled;
            this.weakCandidate = weakCandidate;
            this.includeInAnnualScore = includeInAnnualScore;
            this.matchedSubTypes = matchedSubTypes == null ? Collections.<String>emptyList() : matchedSubTypes;
            this.mergeReason = mergeReason == null ? "" : mergeReason;
            this.rawCandidateCount = rawCandidateCount;
        }

        public double getEventScore() {
            return eventScore;
        }

        public String getSeverityLevel() {
            return severityLevel;
        }

        private String toJson() {
            return "{"
                    + "\"durationScore\":" + durationScore
                    + ",\"trajectoryScore\":" + trajectoryScore
                    + ",\"areaScore\":" + areaScore
                    + ",\"evidenceScore\":" + evidenceScore
                    + ",\"rawScore\":" + rawScore
                    + ",\"finalScore\":" + eventScore
                    + ",\"rawSeverity\":\"" + rawSeverity + "\""
                    + ",\"finalSeverity\":\"" + severityLevel + "\""
                    + ",\"matchedSubTypes\":" + toJsonArray(matchedSubTypes)
                    + ",\"mergeReason\":\"" + mergeReason + "\""
                    + ",\"rawCandidateCount\":" + rawCandidateCount
                    + ",\"confidenceAdjustment\":" + confidenceAdjustment
                    + ",\"normalSceneAdjustment\":" + normalSceneAdjustment
                    + ",\"severityCapReason\":\"" + severityCapReason + "\""
                    + ",\"sceneDataAvailable\":" + sceneDataAvailable
                    + ",\"riskAreaEnabled\":" + riskAreaEnabled
                    + ",\"weakCandidate\":" + weakCandidate
                    + ",\"includeInAnnualScore\":" + includeInAnnualScore
                    + "}";
        }

        private String toJsonArray(List<String> values) {
            StringBuilder builder = new StringBuilder("[");
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    builder.append(",");
                }
                builder.append("\"").append(values.get(i)).append("\"");
            }
            builder.append("]");
            return builder.toString();
        }
    }

    public static class RejectSummary {
        private int rejectByDuration;
        private int rejectByPointCount;
        private int rejectByRadius;
        private int rejectBySpeed;
        private int rejectByTurnFeature;
        private int rejectByGapSplit;
        private int rejectByCoverage;
        private int rejectByNormalScene;
        private int rejectByUnknown;
        private final List<NearMiss> nearMisses = new ArrayList<NearMiss>();

        private void addReject(LoiteringEventFeature feature, LoiteringConfig config, String failedRules) {
            boolean any = false;
            if (failedRules.contains("duration")) {
                rejectByDuration++;
                any = true;
            }
            if (failedRules.contains("pointCount")) {
                rejectByPointCount++;
                any = true;
            }
            if (failedRules.contains("radius")) {
                rejectByRadius++;
                any = true;
            }
            if (failedRules.contains("speed")) {
                rejectBySpeed++;
                any = true;
            }
            if (failedRules.contains("turnFeature")) {
                rejectByTurnFeature++;
                any = true;
            }
            if (failedRules.contains("gap")) {
                rejectByGapSplit++;
                any = true;
            }
            if (failedRules.contains("coverage")) {
                rejectByCoverage++;
                any = true;
            }
            if (!any) {
                rejectByUnknown++;
            }
        }

        private void addNearMiss(NearMiss nearMiss) {
            nearMisses.add(nearMiss);
            Collections.sort(nearMisses, new Comparator<NearMiss>() {
                @Override
                public int compare(NearMiss a, NearMiss b) {
                    int byDuration = Long.compare(b.getFeature().getDurationMinutes(), a.getFeature().getDurationMinutes());
                    if (byDuration != 0) {
                        return byDuration;
                    }
                    int bySpeed = Double.compare(a.getFeature().getAvgSog(), b.getFeature().getAvgSog());
                    if (bySpeed != 0) {
                        return bySpeed;
                    }
                    int byRadius = Double.compare(a.getFeature().getRadiusNm(), b.getFeature().getRadiusNm());
                    if (byRadius != 0) {
                        return byRadius;
                    }
                    return Integer.compare(b.getFeature().getPointCount(), a.getFeature().getPointCount());
                }
            });
        }

        public int getRejectByDuration() {
            return rejectByDuration;
        }

        public int getRejectByPointCount() {
            return rejectByPointCount;
        }

        public int getRejectByRadius() {
            return rejectByRadius;
        }

        public int getRejectBySpeed() {
            return rejectBySpeed;
        }

        public int getRejectByTurnFeature() {
            return rejectByTurnFeature;
        }

        public int getRejectByGapSplit() {
            return rejectByGapSplit;
        }

        public int getRejectByCoverage() {
            return rejectByCoverage;
        }

        public int getRejectByNormalScene() {
            return rejectByNormalScene;
        }

        public int getRejectByUnknown() {
            return rejectByUnknown;
        }

        public List<NearMiss> getNearMisses() {
            return nearMisses;
        }
    }

    public static class NearMiss {
        private final SegmentWithQuality segment;
        private final LoiteringEventFeature feature;
        private final String failedRules;

        private NearMiss(SegmentWithQuality segment, LoiteringEventFeature feature, String failedRules) {
            this.segment = segment;
            this.feature = feature;
            this.failedRules = failedRules;
        }

        public SegmentWithQuality getSegment() {
            return segment;
        }

        public LoiteringEventFeature getFeature() {
            return feature;
        }

        public String getFailedRules() {
            return failedRules;
        }

        public String getStartTime() {
            return TrajectoryUtils.formatTime(segment.getPoints().get(0));
        }

        public String getEndTime() {
            return TrajectoryUtils.formatTime(segment.getPoints().get(segment.getPoints().size() - 1));
        }
    }
}
