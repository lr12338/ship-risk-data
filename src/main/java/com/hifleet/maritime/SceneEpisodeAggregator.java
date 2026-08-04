package com.hifleet.maritime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class SceneEpisodeAggregator {
    private static final long DEFAULT_GAP_CAP_MILLIS = 10L * 60_000L;
    private final long gapCapMillis;

    public SceneEpisodeAggregator() {
        this(10L);
    }

    public SceneEpisodeAggregator(long gapCapMinutes) {
        if (gapCapMinutes <= 0L) {
            throw new IllegalArgumentException("gapCapMinutes must be positive");
        }
        this.gapCapMillis = gapCapMinutes * 60_000L;
    }

    public Summary aggregate(List<MaritimeSceneDecision> decisions) {
        List<TimedSceneObservation> observations = new ArrayList<TimedSceneObservation>();
        if (decisions != null) {
            for (MaritimeSceneDecision decision : decisions) {
                if (decision != null) observations.add(new TimedSceneObservation(0L, decision));
            }
        }
        return summarize(observations, false, DEFAULT_GAP_CAP_MILLIS);
    }

    /** Compatibility entry point. New callers should pass TimedSceneObservation values. */
    public Summary aggregate(List<MaritimeSceneDecision> decisions, List<Long> epochMillis) {
        List<TimedSceneObservation> observations = new ArrayList<TimedSceneObservation>();
        int count = Math.min(decisions == null ? 0 : decisions.size(),
                epochMillis == null ? 0 : epochMillis.size());
        for (int i = 0; i < count; i++) {
            if (decisions.get(i) != null && epochMillis.get(i) != null) {
                observations.add(new TimedSceneObservation(epochMillis.get(i), decisions.get(i)));
            }
        }
        return summarize(observations, true, Long.MAX_VALUE);
    }

    public Summary aggregateTimed(List<TimedSceneObservation> source) {
        List<TimedSceneObservation> observations = new ArrayList<TimedSceneObservation>();
        if (source != null) {
            for (TimedSceneObservation observation : source) {
                if (observation != null && observation.getDecision() != null) observations.add(observation);
            }
        }
        Collections.sort(observations, new Comparator<TimedSceneObservation>() {
            @Override
            public int compare(TimedSceneObservation left, TimedSceneObservation right) {
                return Long.compare(left.getEpochMillis(), right.getEpochMillis());
            }
        });
        return summarize(observations, true, gapCapMillis);
    }

    private Summary summarize(List<TimedSceneObservation> observations, boolean timed, long weightCapMillis) {
        int total = observations.size();
        int inland = 0;
        int highConfidenceInland = 0;
        int highConfidenceOpenWater = 0;
        int lake = 0;
        int canal = 0;
        int internal = 0;
        int territorial = 0;
        int eez = 0;
        int highSeas = 0;
        int land = 0;
        int unknown = 0;
        int conflict = 0;
        int transitions = 0;
        Map<MaritimeSceneClass, Integer> counts =
                new EnumMap<MaritimeSceneClass, Integer>(MaritimeSceneClass.class);
        List<Double> coastDistances = new ArrayList<Double>();
        double observedMillis = 0.0;
        double inlandMillis = 0.0;
        double openWaterMillis = 0.0;
        double unknownMillis = 0.0;
        double conflictMillis = 0.0;
        double maxGapMillis = 0.0;
        int largeGapCount = 0;
        for (int i = 0; i < total; i++) {
            MaritimeSceneDecision decision = observations.get(i).getDecision();
            MaritimeSceneClass scene = decision.getSceneClass();
            counts.put(scene, counts.containsKey(scene) ? counts.get(scene) + 1 : 1);
            if (isInland(scene)) inland++;
            if (isHighConfidenceInlandReference(decision)) highConfidenceInland++;
            if (isHighConfidenceOpenWaterReference(decision)) highConfidenceOpenWater++;
            if (scene == MaritimeSceneClass.INLAND_LAKE) lake++;
            if (scene == MaritimeSceneClass.MAJOR_CANAL) canal++;
            if (scene == MaritimeSceneClass.MARITIME_INTERNAL_WATER) internal++;
            if (scene == MaritimeSceneClass.TERRITORIAL_SEA) territorial++;
            if (scene == MaritimeSceneClass.OFFSHORE_EEZ) eez++;
            if (scene == MaritimeSceneClass.HIGH_SEAS) highSeas++;
            if (scene == MaritimeSceneClass.LAND) land++;
            if (scene == MaritimeSceneClass.SCENE_UNKNOWN) unknown++;
            if (isConflict(decision)) conflict++;
            if (i > 0 && scene != observations.get(i - 1).getDecision().getSceneClass()) transitions++;
            if (decision.getDistanceToCoastNm() != null
                    && Double.isFinite(decision.getDistanceToCoastNm())) {
                coastDistances.add(decision.getDistanceToCoastNm());
            }
            if (timed && i + 1 < total) {
                long rawGap = Math.max(0L, observations.get(i + 1).getEpochMillis()
                        - observations.get(i).getEpochMillis());
                maxGapMillis = Math.max(maxGapMillis, rawGap);
                if (rawGap > weightCapMillis) largeGapCount++;
                long weight = Math.min(rawGap, weightCapMillis);
                observedMillis += weight;
                if (isHighConfidenceInlandReference(decision)) inlandMillis += weight;
                if (isHighConfidenceOpenWaterReference(decision)) openWaterMillis += weight;
                if (scene == MaritimeSceneClass.SCENE_UNKNOWN) unknownMillis += weight;
                if (isConflict(decision)) conflictMillis += weight;
            }
        }
        Collections.sort(coastDistances);
        double pointDenominator = total == 0 ? 1.0 : total;
        double timeDenominator = observedMillis <= 0.0 ? 1.0 : observedMillis;
        double eventSpanMillis = timed && total >= 2
                ? Math.max(0L, observations.get(total - 1).getEpochMillis()
                - observations.get(0).getEpochMillis()) : 0.0;
        return new Summary(dominant(counts), dominantConfidence(observations, dominant(counts)),
                inland / pointDenominator, highConfidenceInland / pointDenominator,
                highConfidenceOpenWater / pointDenominator, lake / pointDenominator,
                canal / pointDenominator, internal / pointDenominator,
                territorial / pointDenominator, eez / pointDenominator,
                highSeas / pointDenominator, land / pointDenominator,
                unknown / pointDenominator, conflict / pointDenominator, transitions,
                percentile(coastDistances, 0.0), percentile(coastDistances, 0.50),
                percentile(coastDistances, 1.0), eventSpanMillis / 60_000.0,
                observedMillis / 60_000.0,
                eventSpanMillis <= 0.0 ? 0.0 : observedMillis / eventSpanMillis,
                inlandMillis / 60_000.0, maxGapMillis / 60_000.0, largeGapCount,
                inlandMillis / timeDenominator, openWaterMillis / timeDenominator,
                unknownMillis / timeDenominator, conflictMillis / timeDenominator, total);
    }

    static boolean isInland(MaritimeSceneClass scene) {
        return scene == MaritimeSceneClass.INLAND_RIVER
                || scene == MaritimeSceneClass.INLAND_LAKE
                || scene == MaritimeSceneClass.MAJOR_CANAL
                || scene == MaritimeSceneClass.ESTUARY_OR_RIVER_MOUTH;
    }

    static boolean isHighConfidenceInlandReference(MaritimeSceneDecision decision) {
        MaritimeSceneClass scene = decision.getSceneClass();
        return decision.getConfidence() == SceneConfidence.HIGH && !isConflict(decision)
                && (scene == MaritimeSceneClass.INLAND_RIVER
                || scene == MaritimeSceneClass.INLAND_LAKE
                || scene == MaritimeSceneClass.MAJOR_CANAL);
    }

    static boolean isHighConfidenceOpenWaterReference(MaritimeSceneDecision decision) {
        MaritimeSceneClass scene = decision.getSceneClass();
        return decision.getConfidence() == SceneConfidence.HIGH && !isConflict(decision)
                && (scene == MaritimeSceneClass.HIGH_SEAS
                || scene == MaritimeSceneClass.OFFSHORE_EEZ);
    }

    private static boolean isConflict(MaritimeSceneDecision decision) {
        return decision.getSceneClass() == MaritimeSceneClass.SCENE_CONFLICT
                || decision.isConflictFlag();
    }

    private static MaritimeSceneClass dominant(Map<MaritimeSceneClass, Integer> counts) {
        MaritimeSceneClass best = MaritimeSceneClass.SCENE_UNKNOWN;
        int bestCount = -1;
        for (Map.Entry<MaritimeSceneClass, Integer> entry : counts.entrySet()) {
            MaritimeSceneClass scene = entry.getKey();
            if ((scene == MaritimeSceneClass.SCENE_UNKNOWN || scene == MaritimeSceneClass.SCENE_CONFLICT)
                    && counts.size() > 1) continue;
            if (entry.getValue() > bestCount) {
                best = scene;
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    private static SceneConfidence dominantConfidence(
            List<TimedSceneObservation> observations, MaritimeSceneClass dominant) {
        for (TimedSceneObservation observation : observations) {
            if (observation.getDecision().getSceneClass() == dominant) {
                return observation.getDecision().getConfidence();
            }
        }
        return SceneConfidence.UNKNOWN;
    }

    private static Double percentile(List<Double> values, double ratio) {
        if (values.isEmpty()) return null;
        int index = (int) Math.round((values.size() - 1) * ratio);
        return values.get(Math.max(0, Math.min(values.size() - 1, index)));
    }

    public static final class Summary {
        private final MaritimeSceneClass dominantScene;
        private final SceneConfidence dominantSceneConfidence;
        private final double inlandPointRatio;
        private final double highConfidenceInlandPointRatio;
        private final double highConfidenceOpenWaterPointRatio;
        private final double lakePointRatio;
        private final double canalPointRatio;
        private final double internalWaterPointRatio;
        private final double territorialSeaPointRatio;
        private final double offshoreEezPointRatio;
        private final double highSeasPointRatio;
        private final double landPointRatio;
        private final double unknownPointRatio;
        private final double conflictPointRatio;
        private final int sceneTransitionCount;
        private final Double coastDistanceMinNm;
        private final Double coastDistanceP50Nm;
        private final Double coastDistanceMaxNm;
        private final double eventSpanMinutes;
        private final double observedDurationMinutes;
        private final double temporalCoverageRatio;
        private final double inlandDurationMinutes;
        private final double maxGapMinutes;
        private final int largeGapCount;
        private final double highConfidenceInlandTimeRatio;
        private final double highConfidenceOpenWaterTimeRatio;
        private final double unknownTimeRatio;
        private final double conflictTimeRatio;
        private final int pointCount;

        private Summary(MaritimeSceneClass dominantScene, SceneConfidence dominantSceneConfidence,
                        double inlandPointRatio, double highConfidenceInlandPointRatio,
                        double highConfidenceOpenWaterPointRatio, double lakePointRatio,
                        double canalPointRatio, double internalWaterPointRatio,
                        double territorialSeaPointRatio, double offshoreEezPointRatio,
                        double highSeasPointRatio, double landPointRatio,
                        double unknownPointRatio, double conflictPointRatio,
                        int sceneTransitionCount, Double coastDistanceMinNm,
                        Double coastDistanceP50Nm, Double coastDistanceMaxNm,
                        double eventSpanMinutes, double observedDurationMinutes,
                        double temporalCoverageRatio, double inlandDurationMinutes,
                        double maxGapMinutes, int largeGapCount,
                        double highConfidenceInlandTimeRatio,
                        double highConfidenceOpenWaterTimeRatio,
                        double unknownTimeRatio, double conflictTimeRatio, int pointCount) {
            this.dominantScene = dominantScene;
            this.dominantSceneConfidence = dominantSceneConfidence;
            this.inlandPointRatio = inlandPointRatio;
            this.highConfidenceInlandPointRatio = highConfidenceInlandPointRatio;
            this.highConfidenceOpenWaterPointRatio = highConfidenceOpenWaterPointRatio;
            this.lakePointRatio = lakePointRatio;
            this.canalPointRatio = canalPointRatio;
            this.internalWaterPointRatio = internalWaterPointRatio;
            this.territorialSeaPointRatio = territorialSeaPointRatio;
            this.offshoreEezPointRatio = offshoreEezPointRatio;
            this.highSeasPointRatio = highSeasPointRatio;
            this.landPointRatio = landPointRatio;
            this.unknownPointRatio = unknownPointRatio;
            this.conflictPointRatio = conflictPointRatio;
            this.sceneTransitionCount = sceneTransitionCount;
            this.coastDistanceMinNm = coastDistanceMinNm;
            this.coastDistanceP50Nm = coastDistanceP50Nm;
            this.coastDistanceMaxNm = coastDistanceMaxNm;
            this.eventSpanMinutes = eventSpanMinutes;
            this.observedDurationMinutes = observedDurationMinutes;
            this.temporalCoverageRatio = temporalCoverageRatio;
            this.inlandDurationMinutes = inlandDurationMinutes;
            this.maxGapMinutes = maxGapMinutes;
            this.largeGapCount = largeGapCount;
            this.highConfidenceInlandTimeRatio = highConfidenceInlandTimeRatio;
            this.highConfidenceOpenWaterTimeRatio = highConfidenceOpenWaterTimeRatio;
            this.unknownTimeRatio = unknownTimeRatio;
            this.conflictTimeRatio = conflictTimeRatio;
            this.pointCount = pointCount;
        }

        public MaritimeSceneClass getDominantScene() { return dominantScene; }
        public SceneConfidence getDominantSceneConfidence() { return dominantSceneConfidence; }
        public double getInlandPointRatio() { return inlandPointRatio; }
        public double getHighConfidenceInlandPointRatio() { return highConfidenceInlandPointRatio; }
        public double getHighConfidenceOpenWaterPointRatio() { return highConfidenceOpenWaterPointRatio; }
        public double getLakePointRatio() { return lakePointRatio; }
        public double getCanalPointRatio() { return canalPointRatio; }
        public double getInternalWaterPointRatio() { return internalWaterPointRatio; }
        public double getTerritorialSeaPointRatio() { return territorialSeaPointRatio; }
        public double getOffshoreEezPointRatio() { return offshoreEezPointRatio; }
        public double getHighSeasPointRatio() { return highSeasPointRatio; }
        public double getLandPointRatio() { return landPointRatio; }
        public double getUnknownPointRatio() { return unknownPointRatio; }
        public double getConflictPointRatio() { return conflictPointRatio; }
        public int getSceneTransitionCount() { return sceneTransitionCount; }
        public Double getCoastDistanceMinNm() { return coastDistanceMinNm; }
        public Double getCoastDistanceP50Nm() { return coastDistanceP50Nm; }
        public Double getCoastDistanceMaxNm() { return coastDistanceMaxNm; }
        public double getDurationMinutes() { return eventSpanMinutes; }
        public double getEventSpanMinutes() { return eventSpanMinutes; }
        public double getObservedDurationMinutes() { return observedDurationMinutes; }
        public double getTemporalCoverageRatio() { return temporalCoverageRatio; }
        public double getInlandDurationMinutes() { return inlandDurationMinutes; }
        public double getMaxGapMinutes() { return maxGapMinutes; }
        public int getLargeGapCount() { return largeGapCount; }
        public double getHighConfidenceInlandTimeRatio() { return highConfidenceInlandTimeRatio; }
        public double getInlandTimeRatio() { return highConfidenceInlandTimeRatio; }
        public double getHighConfidenceOpenWaterTimeRatio() { return highConfidenceOpenWaterTimeRatio; }
        public double getOpenWaterTimeRatio() { return highConfidenceOpenWaterTimeRatio; }
        public double getUnknownTimeRatio() { return unknownTimeRatio; }
        public double getConflictTimeRatio() { return conflictTimeRatio; }
        public int getPointCount() { return pointCount; }
    }
}
