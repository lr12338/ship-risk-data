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
import java.util.List;
import java.util.Set;

/**
 * 船舶徘徊 V2-lite：轨迹清洗、AIS gap 切分、复合徘徊识别、置信度与单事件评分。
 */
public class ShipLoiteringDetector {

    public static final String SUB_TYPE_SLOW_LOITERING = "SLOW_LOITERING";
    public static final String SUB_TYPE_MANEUVER_LOITERING = "MANEUVER_LOITERING";
    public static final String SUB_TYPE_HIGH_RISK_WAITING = "HIGH_RISK_WAITING";

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
        for (SegmentWithQuality segment : segments) {
            candidates.addAll(detectSlowLoitering(mmsi, segment, effectiveConfig));
            candidates.addAll(detectManeuverLoitering(mmsi, segment, effectiveConfig));
            candidates.addAll(detectHighRiskWaiting(mmsi, segment, effectiveConfig));
        }
        applyNormalSceneExclusion(candidates);
        scoreCandidates(candidates, effectiveConfig);
        List<ShipBehaviorTrace> traces = buildTraces(candidates);
        lastStats = new DetectionStats(points.size(), cleanPoints.size(), segments.size(), candidates.size(),
                countExcluded(candidates), countLowConfidence(candidates));
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
        double score = durationScore + trajectoryScore + areaScore + evidenceScore;
        if (EXCLUDED_NORMAL.equals(candidate.getExclusionFlag())) {
            score = 0.0;
        } else {
            score = score * normalSceneAdjustment * confidenceAdjustment;
        }
        score = Math.max(0.0, Math.min(100.0, score));
        String severity = determineSeverityLevel(score);
        if (CONFIDENCE_LOW.equals(feature.getConfidenceLevel()) && severityRank(severity) > severityRank("L2")) {
            severity = "L2";
        }
        return new LoiteringEventScore(durationScore, trajectoryScore, areaScore, evidenceScore,
                confidenceAdjustment, normalSceneAdjustment, score, severity);
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
        private final int excludedEvents;
        private final int lowConfidenceEvents;

        private DetectionStats(int points, int cleanPoints, int segments, int candidates,
                               int excludedEvents, int lowConfidenceEvents) {
            this.points = points;
            this.cleanPoints = cleanPoints;
            this.segments = segments;
            this.candidates = candidates;
            this.excludedEvents = excludedEvents;
            this.lowConfidenceEvents = lowConfidenceEvents;
        }

        private static DetectionStats empty() {
            return new DetectionStats(0, 0, 0, 0, 0, 0);
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

        public int getExcludedEvents() {
            return excludedEvents;
        }

        public int getLowConfidenceEvents() {
            return lowConfidenceEvents;
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

        private LoiteringCandidate(String mmsi, String subType, SegmentWithQuality segment,
                                   LoiteringEventFeature feature) {
            this.mmsi = mmsi;
            this.subType = subType;
            this.segment = segment;
            this.feature = feature;
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
        private final double eventScore;
        private final String severityLevel;

        private LoiteringEventScore(int durationScore, int trajectoryScore, int areaScore, int evidenceScore,
                                    double confidenceAdjustment, double normalSceneAdjustment,
                                    double eventScore, String severityLevel) {
            this.durationScore = durationScore;
            this.trajectoryScore = trajectoryScore;
            this.areaScore = areaScore;
            this.evidenceScore = evidenceScore;
            this.confidenceAdjustment = confidenceAdjustment;
            this.normalSceneAdjustment = normalSceneAdjustment;
            this.eventScore = eventScore;
            this.severityLevel = severityLevel;
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
                    + ",\"confidenceAdjustment\":" + confidenceAdjustment
                    + ",\"normalSceneAdjustment\":" + normalSceneAdjustment
                    + "}";
        }
    }
}
