package com.hifleet.regionbehavior;

import com.hifleet.trajectory.TrajectoryPoint;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TrajectoryPairSynchronizer {
    private final long maxGapMillis;

    public TrajectoryPairSynchronizer(long maxGapMinutes) {
        if (maxGapMinutes < 0L) throw new IllegalArgumentException("maxGapMinutes must not be negative");
        this.maxGapMillis = maxGapMinutes * 60_000L;
    }

    public Result synchronize(List<TrajectoryPoint> ownSource, List<TrajectoryPoint> targetSource) {
        List<TrajectoryPoint> own = validSorted(ownSource);
        List<TrajectoryPoint> target = validSorted(targetSource);
        List<SynchronizedShipPairPoint> pairs = new ArrayList<SynchronizedShipPairPoint>();
        Set<Integer> usedTargets = new HashSet<Integer>();
        for (TrajectoryPoint ownPoint : own) {
            int bestIndex = -1;
            long bestGap = Long.MAX_VALUE;
            for (int i = 0; i < target.size(); i++) {
                if (usedTargets.contains(i)) continue;
                long gap = Math.abs(epochMillis(ownPoint) - epochMillis(target.get(i)));
                if (gap < bestGap) {
                    bestGap = gap;
                    bestIndex = i;
                }
            }
            if (bestIndex < 0 || bestGap > maxGapMillis) continue;
            usedTargets.add(bestIndex);
            TrajectoryPoint targetPoint = target.get(bestIndex);
            long ownMillis = epochMillis(ownPoint);
            long targetMillis = epochMillis(targetPoint);
            pairs.add(new SynchronizedShipPairPoint(ownPoint, targetPoint,
                    ownMillis + (targetMillis - ownMillis) / 2L, bestGap,
                    midpointLongitude(ownPoint.getLon(), targetPoint.getLon()),
                    (ownPoint.getLat() + targetPoint.getLat()) / 2.0));
        }
        Collections.sort(pairs, new Comparator<SynchronizedShipPairPoint>() {
            @Override
            public int compare(SynchronizedShipPairPoint left, SynchronizedShipPairPoint right) {
                return Long.compare(left.getSynchronizedEpochMillis(), right.getSynchronizedEpochMillis());
            }
        });
        return new Result(pairs, own.isEmpty() ? 0.0 : pairs.size() / (double) own.size(),
                target.isEmpty() ? 0.0 : pairs.size() / (double) target.size());
    }

    static double midpointLongitude(double left, double right) {
        double a = normalizeLongitude(left);
        double b = normalizeLongitude(right);
        if (Math.abs(a - b) > 180.0) {
            if (a < b) a += 360.0; else b += 360.0;
        }
        return normalizeLongitude((a + b) / 2.0);
    }

    private static double normalizeLongitude(double value) {
        double normalized = ((value + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        return normalized == -180.0 ? 180.0 : normalized;
    }

    private static long epochMillis(TrajectoryPoint point) {
        return point.getTime().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static List<TrajectoryPoint> validSorted(List<TrajectoryPoint> source) {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        if (source != null) {
            for (TrajectoryPoint point : source) {
                if (point != null && point.getTime() != null
                        && Double.isFinite(point.getLon()) && Double.isFinite(point.getLat())
                        && point.getLon() >= -180.0 && point.getLon() <= 180.0
                        && point.getLat() >= -90.0 && point.getLat() <= 90.0) result.add(point);
            }
        }
        Collections.sort(result, new Comparator<TrajectoryPoint>() {
            @Override
            public int compare(TrajectoryPoint left, TrajectoryPoint right) {
                return left.getTime().compareTo(right.getTime());
            }
        });
        return result;
    }

    public static final class Result {
        private final List<SynchronizedShipPairPoint> pairs;
        private final double ownCoverageRatio;
        private final double targetCoverageRatio;

        private Result(List<SynchronizedShipPairPoint> pairs, double ownCoverageRatio,
                       double targetCoverageRatio) {
            this.pairs = Collections.unmodifiableList(pairs);
            this.ownCoverageRatio = ownCoverageRatio;
            this.targetCoverageRatio = targetCoverageRatio;
        }

        public List<SynchronizedShipPairPoint> getPairs() { return pairs; }
        public double getOwnCoverageRatio() { return ownCoverageRatio; }
        public double getTargetCoverageRatio() { return targetCoverageRatio; }
        public double getCoverageRatio() { return Math.min(ownCoverageRatio, targetCoverageRatio); }
    }
}
