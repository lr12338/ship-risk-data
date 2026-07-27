package com.hifleet.service;

import com.hifleet.trajectory.TrajectoryPoint;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Loitering V3 日报统计和人工案例抽样纯逻辑，不负责查询或生成 HTML。
 */
public class LoiteringV3QualityService {

    private static final String UNKNOWN_REGION = "UNKNOWN";

    public DailyStats summarize(Set<String> analyzedShips, Set<String> eligibleShips,
                                List<EventSample> sourceEvents) {
        List<EventSample> events = uniqueEvents(sourceEvents);
        List<Long> finalDurations = new ArrayList<Long>();
        Map<String, Integer> regionDistribution = new TreeMap<String, Integer>();
        int finalCount = 0;
        int reviewCount = 0;
        int normalBerthingCount = 0;
        int straightTransitCount = 0;

        for (EventSample event : events) {
            if (LoiteringV3RuleEngine.STATUS_FINAL.equals(event.getStatus())) {
                finalCount++;
                finalDurations.add(event.getDurationMinutes());
                String region = isBlank(event.getRegion()) ? UNKNOWN_REGION : event.getRegion();
                Integer count = regionDistribution.get(region);
                regionDistribution.put(region, count == null ? 1 : count + 1);
            } else if (LoiteringV3RuleEngine.STATUS_REVIEW.equals(event.getStatus())) {
                reviewCount++;
            }
            if (containsReason(event, LoiteringV3RuleEngine.REASON_NORMAL_BERTHING)) {
                normalBerthingCount++;
            }
            if (containsReason(event,
                    LoiteringV3RuleEngine.REASON_STRAIGHT_LOW_SPEED_TRANSIT)) {
                straightTransitCount++;
            }
        }

        Collections.sort(finalDurations);
        return new DailyStats(size(analyzedShips), size(eligibleShips), events.size(),
                finalCount, reviewCount, normalBerthingCount, straightTransitCount,
                average(finalDurations), median(finalDurations), percentile(finalDurations, 0.90),
                regionDistribution);
    }

    public List<EventSample> selectTopRiskCases(List<EventSample> sourceEvents, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        List<EventSample> finals = new ArrayList<EventSample>();
        for (EventSample event : uniqueEvents(sourceEvents)) {
            if (LoiteringV3RuleEngine.STATUS_FINAL.equals(event.getStatus())) {
                finals.add(event);
            }
        }
        Collections.sort(finals, new Comparator<EventSample>() {
            @Override
            public int compare(EventSample a, EventSample b) {
                int riskCompare = Double.compare(b.getRiskScore(), a.getRiskScore());
                if (riskCompare != 0) {
                    return riskCompare;
                }
                int timeCompare = a.getStartTime().compareTo(b.getStartTime());
                if (timeCompare != 0) {
                    return timeCompare;
                }
                return a.getEventKey().compareTo(b.getEventKey());
            }
        });
        int resultSize = Math.min(limit, finals.size());
        return Collections.unmodifiableList(new ArrayList<EventSample>(
                finals.subList(0, resultSize)));
    }

    private List<EventSample> uniqueEvents(List<EventSample> sourceEvents) {
        if (sourceEvents == null || sourceEvents.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, EventSample> unique = new LinkedHashMap<String, EventSample>();
        for (EventSample event : sourceEvents) {
            if (event == null || isBlank(event.getEventKey())
                    || event.getStartTime() == null || event.getEndTime() == null
                    || event.getEndTime().isBefore(event.getStartTime())) {
                continue;
            }
            EventSample existing = unique.get(event.getEventKey());
            if (existing == null || prefer(event, existing)) {
                unique.put(event.getEventKey(), event);
            }
        }
        return new ArrayList<EventSample>(unique.values());
    }

    private boolean prefer(EventSample candidate, EventSample existing) {
        int candidateStatus = statusPriority(candidate.getStatus());
        int existingStatus = statusPriority(existing.getStatus());
        if (candidateStatus != existingStatus) {
            return candidateStatus > existingStatus;
        }
        int endCompare = candidate.getEndTime().compareTo(existing.getEndTime());
        if (endCompare != 0) {
            return endCompare > 0;
        }
        return candidate.getRiskScore() > existing.getRiskScore();
    }

    private int statusPriority(String status) {
        if (LoiteringV3RuleEngine.STATUS_FINAL.equals(status)) {
            return 3;
        }
        if (LoiteringV3RuleEngine.STATUS_REVIEW.equals(status)) {
            return 2;
        }
        return 1;
    }

    private int size(Set<String> values) {
        return values == null ? 0 : values.size();
    }

    private boolean containsReason(EventSample event, String reason) {
        return event.getReasonCodes() != null && event.getReasonCodes().contains(reason);
    }

    private double average(List<Long> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        long total = 0L;
        for (Long value : values) {
            total += value;
        }
        return total / (double) values.size();
    }

    private double median(List<Long> sorted) {
        if (sorted.isEmpty()) {
            return 0.0;
        }
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    private double percentile(List<Long> sorted, double ratio) {
        if (sorted.isEmpty()) {
            return 0.0;
        }
        int index = (int) Math.ceil(sorted.size() * ratio) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class EventSample {
        private final String eventKey;
        private final String mmsi;
        private final String imo;
        private final String shipName;
        private final String shipType;
        private final String status;
        private final LocalDateTime startTime;
        private final LocalDateTime endTime;
        private final double riskScore;
        private final String region;
        private final String trajectoryPattern;
        private final String reasonCodes;
        private final List<TrajectoryPoint> trajectory;

        public EventSample(String eventKey, String mmsi, String imo, String shipName,
                           String shipType, String status, LocalDateTime startTime,
                           LocalDateTime endTime, double riskScore, String region,
                           String trajectoryPattern, String reasonCodes,
                           List<TrajectoryPoint> trajectory) {
            this.eventKey = eventKey;
            this.mmsi = mmsi;
            this.imo = imo;
            this.shipName = shipName;
            this.shipType = shipType;
            this.status = status;
            this.startTime = startTime;
            this.endTime = endTime;
            this.riskScore = riskScore;
            this.region = region;
            this.trajectoryPattern = trajectoryPattern;
            this.reasonCodes = reasonCodes;
            this.trajectory = trajectory == null
                    ? Collections.<TrajectoryPoint>emptyList()
                    : Collections.unmodifiableList(new ArrayList<TrajectoryPoint>(trajectory));
        }

        public String getEventKey() {
            return eventKey;
        }

        public String getMmsi() {
            return mmsi;
        }

        public String getImo() {
            return imo;
        }

        public String getShipName() {
            return shipName;
        }

        public String getShipType() {
            return shipType;
        }

        public String getStatus() {
            return status;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public LocalDateTime getEndTime() {
            return endTime;
        }

        public long getDurationMinutes() {
            return Math.max(0L, Duration.between(startTime, endTime).toMinutes());
        }

        public double getRiskScore() {
            return riskScore;
        }

        public String getRegion() {
            return region;
        }

        public String getTrajectoryPattern() {
            return trajectoryPattern;
        }

        public String getReasonCodes() {
            return reasonCodes;
        }

        public List<TrajectoryPoint> getTrajectory() {
            return trajectory;
        }
    }

    public static class DailyStats {
        private final int analyzedShipCount;
        private final int eligibleShipCount;
        private final int candidateEventCount;
        private final int finalEventCount;
        private final int reviewEventCount;
        private final int excludedNormalBerthingCount;
        private final int excludedStraightTransitCount;
        private final double averageDurationMinutes;
        private final double medianDurationMinutes;
        private final double p90DurationMinutes;
        private final Map<String, Integer> regionDistribution;

        private DailyStats(int analyzedShipCount, int eligibleShipCount,
                           int candidateEventCount, int finalEventCount, int reviewEventCount,
                           int excludedNormalBerthingCount, int excludedStraightTransitCount,
                           double averageDurationMinutes, double medianDurationMinutes,
                           double p90DurationMinutes, Map<String, Integer> regionDistribution) {
            this.analyzedShipCount = analyzedShipCount;
            this.eligibleShipCount = eligibleShipCount;
            this.candidateEventCount = candidateEventCount;
            this.finalEventCount = finalEventCount;
            this.reviewEventCount = reviewEventCount;
            this.excludedNormalBerthingCount = excludedNormalBerthingCount;
            this.excludedStraightTransitCount = excludedStraightTransitCount;
            this.averageDurationMinutes = averageDurationMinutes;
            this.medianDurationMinutes = medianDurationMinutes;
            this.p90DurationMinutes = p90DurationMinutes;
            this.regionDistribution = Collections.unmodifiableMap(
                    new LinkedHashMap<String, Integer>(regionDistribution));
        }

        public int getAnalyzedShipCount() {
            return analyzedShipCount;
        }

        public int getEligibleShipCount() {
            return eligibleShipCount;
        }

        public int getCandidateEventCount() {
            return candidateEventCount;
        }

        public int getFinalEventCount() {
            return finalEventCount;
        }

        public int getReviewEventCount() {
            return reviewEventCount;
        }

        public int getExcludedNormalBerthingCount() {
            return excludedNormalBerthingCount;
        }

        public int getExcludedStraightTransitCount() {
            return excludedStraightTransitCount;
        }

        public double getAverageDurationMinutes() {
            return averageDurationMinutes;
        }

        public double getMedianDurationMinutes() {
            return medianDurationMinutes;
        }

        public double getP90DurationMinutes() {
            return p90DurationMinutes;
        }

        public Map<String, Integer> getRegionDistribution() {
            return regionDistribution;
        }
    }
}
