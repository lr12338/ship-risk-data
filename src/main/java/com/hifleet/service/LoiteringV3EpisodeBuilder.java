package com.hifleet.service;

import com.hifleet.trajectory.TrajectoryPoint;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Loitering V3 单船候选 episode 构建器。输入点已经带有独立的港口场景判断。
 */
public class LoiteringV3EpisodeBuilder {

    public static final String END_WINDOW = "WINDOW_END";
    public static final String END_AIS_GAP = "AIS_GAP";
    public static final String END_BEHAVIOR_EXIT = "BEHAVIOR_EXIT";
    public static final String END_NORMAL_BERTHING = "NORMAL_BERTHING";

    private static final long SAMPLE_INTERVAL_MINUTES = 5L;
    private static final long MAX_AIS_GAP_MINUTES = 30L;
    private static final long MAX_EXCURSION_MINUTES = 15L;
    private static final int START_MIN_CORE_POINTS = 4;
    private static final long START_MIN_DURATION_MINUTES = 15L;

    public List<Episode> build(List<Observation> source) {
        List<Observation> observations = cleanAndSort(source);
        if (observations.isEmpty()) {
            return Collections.emptyList();
        }

        List<List<Observation>> segments = splitByGap(observations);
        List<Episode> episodes = new ArrayList<Episode>();
        for (int i = 0; i < segments.size(); i++) {
            boolean endsWithGap = i < segments.size() - 1;
            episodes.addAll(buildSegment(resample(segments.get(i)), endsWithGap));
        }
        return episodes;
    }

    private List<Observation> cleanAndSort(List<Observation> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<Observation> observations = new ArrayList<Observation>();
        for (Observation observation : source) {
            if (observation != null && observation.getPoint() != null
                    && observation.getPoint().getTime() != null) {
                observations.add(observation);
            }
        }
        Collections.sort(observations, new Comparator<Observation>() {
            @Override
            public int compare(Observation a, Observation b) {
                return a.getPoint().getTime().compareTo(b.getPoint().getTime());
            }
        });
        return observations;
    }

    private List<List<Observation>> splitByGap(List<Observation> observations) {
        List<List<Observation>> segments = new ArrayList<List<Observation>>();
        List<Observation> current = new ArrayList<Observation>();
        for (Observation observation : observations) {
            if (!current.isEmpty()) {
                LocalDateTime previousTime = current.get(current.size() - 1).getPoint().getTime();
                long gapMinutes = Duration.between(previousTime,
                        observation.getPoint().getTime()).toMinutes();
                if (gapMinutes > MAX_AIS_GAP_MINUTES) {
                    segments.add(current);
                    current = new ArrayList<Observation>();
                }
            }
            current.add(observation);
        }
        if (!current.isEmpty()) {
            segments.add(current);
        }
        return segments;
    }

    private List<Sample> resample(List<Observation> observations) {
        if (observations.isEmpty()) {
            return Collections.emptyList();
        }
        LocalDateTime anchor = observations.get(0).getPoint().getTime();
        Map<Long, Sample> bySlot = new TreeMap<Long, Sample>();
        for (Observation observation : observations) {
            long offsetSeconds = Duration.between(anchor, observation.getPoint().getTime()).getSeconds();
            long slot = Math.round(offsetSeconds / (SAMPLE_INTERVAL_MINUTES * 60.0));
            LocalDateTime slotTime = anchor.plusMinutes(slot * SAMPLE_INTERVAL_MINUTES);
            long distanceSeconds = Math.abs(Duration.between(slotTime,
                    observation.getPoint().getTime()).getSeconds());
            Sample existing = bySlot.get(slot);
            if (existing == null || distanceSeconds < existing.distanceToSlotSeconds) {
                bySlot.put(slot, new Sample(observation, slotTime, distanceSeconds));
            }
        }
        return new ArrayList<Sample>(bySlot.values());
    }

    private List<Episode> buildSegment(List<Sample> samples, boolean endsWithGap) {
        List<Episode> episodes = new ArrayList<Episode>();
        List<Sample> pendingCore = new ArrayList<Sample>();
        List<Sample> active = null;
        int lastEligibleSize = 0;
        LocalDateTime previousPendingSlot = null;

        for (Sample sample : samples) {
            Observation observation = sample.observation;
            if (active != null) {
                if (observation.isNormalBerthing()) {
                    episodes.add(toEpisode(active, lastEligibleSize,
                            END_NORMAL_BERTHING, false));
                    active = null;
                    pendingCore.clear();
                    previousPendingSlot = null;
                    continue;
                }

                if (active.size() > lastEligibleSize
                        && Duration.between(active.get(lastEligibleSize - 1).slotTime,
                        sample.slotTime).toMinutes() > MAX_EXCURSION_MINUTES) {
                    episodes.add(toEpisode(active, lastEligibleSize,
                            END_BEHAVIOR_EXIT, false));
                    active = null;
                    pendingCore.clear();
                    previousPendingSlot = null;
                }
                if (active != null) {
                    active.add(sample);
                    if (isEligible(observation)) {
                        lastEligibleSize = active.size();
                    }
                    continue;
                }
            }

            if (observation.isNormalBerthing() || !isCore(observation)) {
                pendingCore.clear();
                previousPendingSlot = null;
                continue;
            }

            if (previousPendingSlot != null
                    && Duration.between(previousPendingSlot, sample.slotTime).toMinutes()
                    != SAMPLE_INTERVAL_MINUTES) {
                pendingCore.clear();
            }
            pendingCore.add(sample);
            previousPendingSlot = sample.slotTime;
            long pendingDuration = Duration.between(pendingCore.get(0).slotTime,
                    sample.slotTime).toMinutes();
            if (pendingCore.size() >= START_MIN_CORE_POINTS
                    && pendingDuration >= START_MIN_DURATION_MINUTES) {
                active = new ArrayList<Sample>(pendingCore);
                lastEligibleSize = active.size();
                pendingCore.clear();
                previousPendingSlot = null;
            }
        }

        if (active != null) {
            episodes.add(toEpisode(active, lastEligibleSize,
                    endsWithGap ? END_AIS_GAP : END_WINDOW, !endsWithGap));
        }
        return episodes;
    }

    private boolean isCore(Observation observation) {
        double speed = observation.getPoint().getSpeed();
        return observation.isNearTargetArea() && speed >= 1.0 && speed <= 3.0;
    }

    private boolean isEligible(Observation observation) {
        double speed = observation.getPoint().getSpeed();
        return observation.isNearTargetArea() && speed >= 0.5 && speed <= 4.0;
    }

    private Episode toEpisode(List<Sample> samples, int pointCount,
                              String endReason, boolean open) {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        for (int i = 0; i < pointCount; i++) {
            points.add(samples.get(i).observation.getPoint());
        }
        return new Episode(points, endReason, open);
    }

    public static class Observation {
        private final TrajectoryPoint point;
        private final boolean nearPort;
        private final boolean nearChannel;
        private final boolean normalBerthing;

        public Observation(TrajectoryPoint point, boolean nearPort,
                           boolean nearChannel, boolean normalBerthing) {
            this.point = point;
            this.nearPort = nearPort;
            this.nearChannel = nearChannel;
            this.normalBerthing = normalBerthing;
        }

        public TrajectoryPoint getPoint() {
            return point;
        }

        public boolean isNearTargetArea() {
            return nearPort || nearChannel;
        }

        public boolean isNormalBerthing() {
            return normalBerthing;
        }
    }

    public static class Episode {
        private final List<TrajectoryPoint> points;
        private final String endReason;
        private final boolean open;

        private Episode(List<TrajectoryPoint> points, String endReason, boolean open) {
            this.points = Collections.unmodifiableList(new ArrayList<TrajectoryPoint>(points));
            this.endReason = endReason;
            this.open = open;
        }

        public List<TrajectoryPoint> getPoints() {
            return points;
        }

        public LocalDateTime getStartTime() {
            return points.get(0).getTime();
        }

        public LocalDateTime getEndTime() {
            return points.get(points.size() - 1).getTime();
        }

        public String getEndReason() {
            return endReason;
        }

        public boolean isOpen() {
            return open;
        }
    }

    private static class Sample {
        private final Observation observation;
        private final LocalDateTime slotTime;
        private final long distanceToSlotSeconds;

        private Sample(Observation observation, LocalDateTime slotTime,
                       long distanceToSlotSeconds) {
            this.observation = observation;
            this.slotTime = slotTime;
            this.distanceToSlotSeconds = distanceToSlotSeconds;
        }
    }
}
