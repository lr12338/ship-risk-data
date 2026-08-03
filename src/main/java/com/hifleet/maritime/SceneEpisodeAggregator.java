package com.hifleet.maritime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class SceneEpisodeAggregator {
    public Summary aggregate(List<MaritimeSceneDecision> decisions) {
        return aggregate(decisions, Collections.<Long>emptyList());
    }

    public Summary aggregate(List<MaritimeSceneDecision> decisions, List<Long> epochMillis) {
        List<MaritimeSceneDecision> values = decisions == null
                ? Collections.<MaritimeSceneDecision>emptyList() : decisions;
        int total = values.size();
        int inland = 0;
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
        Map<MaritimeSceneClass, Integer> counts = new EnumMap<MaritimeSceneClass, Integer>(MaritimeSceneClass.class);
        List<Double> coastDistances = new ArrayList<Double>();
        for (int i = 0; i < total; i++) {
            MaritimeSceneDecision decision = values.get(i);
            MaritimeSceneClass scene = decision.getSceneClass();
            counts.put(scene, counts.containsKey(scene) ? counts.get(scene) + 1 : 1);
            if (isInland(scene)) inland++;
            if (scene == MaritimeSceneClass.INLAND_LAKE) lake++;
            if (scene == MaritimeSceneClass.MAJOR_CANAL) canal++;
            if (scene == MaritimeSceneClass.MARITIME_INTERNAL_WATER) internal++;
            if (scene == MaritimeSceneClass.TERRITORIAL_SEA) territorial++;
            if (scene == MaritimeSceneClass.OFFSHORE_EEZ) eez++;
            if (scene == MaritimeSceneClass.HIGH_SEAS) highSeas++;
            if (scene == MaritimeSceneClass.LAND) land++;
            if (scene == MaritimeSceneClass.SCENE_UNKNOWN) unknown++;
            if (scene == MaritimeSceneClass.SCENE_CONFLICT || decision.isConflictFlag()) conflict++;
            if (i > 0 && scene != values.get(i - 1).getSceneClass()) transitions++;
            if (decision.getDistanceToCoastNm() != null
                    && Double.isFinite(decision.getDistanceToCoastNm())) {
                coastDistances.add(decision.getDistanceToCoastNm());
            }
        }
        Collections.sort(coastDistances);
        MaritimeSceneClass dominant = dominant(counts);
        SceneConfidence dominantConfidence = dominantConfidence(values, dominant);
        double denominator = total == 0 ? 1.0 : total;
        double durationMinutes = durationMinutes(epochMillis);
        double inlandDurationMinutes = inlandDurationMinutes(values, epochMillis);
        return new Summary(
                dominant, dominantConfidence,
                inland / denominator, lake / denominator, canal / denominator,
                internal / denominator, territorial / denominator, eez / denominator,
                highSeas / denominator, land / denominator, unknown / denominator,
                conflict / denominator, transitions,
                percentile(coastDistances, 0.0), percentile(coastDistances, 0.50),
                percentile(coastDistances, 1.0), durationMinutes, inlandDurationMinutes,
                total);
    }

    private static boolean isInland(MaritimeSceneClass scene) {
        return scene == MaritimeSceneClass.INLAND_RIVER
                || scene == MaritimeSceneClass.INLAND_LAKE
                || scene == MaritimeSceneClass.MAJOR_CANAL
                || scene == MaritimeSceneClass.ESTUARY_OR_RIVER_MOUTH;
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
            List<MaritimeSceneDecision> decisions, MaritimeSceneClass dominant) {
        for (MaritimeSceneDecision decision : decisions) {
            if (decision.getSceneClass() == dominant) return decision.getConfidence();
        }
        return SceneConfidence.UNKNOWN;
    }

    private static Double percentile(List<Double> values, double ratio) {
        if (values.isEmpty()) return null;
        int index = (int) Math.round((values.size() - 1) * ratio);
        return values.get(Math.max(0, Math.min(values.size() - 1, index)));
    }

    private static double durationMinutes(List<Long> timestamps) {
        if (timestamps == null || timestamps.size() < 2) return 0.0;
        return Math.max(0L, timestamps.get(timestamps.size() - 1) - timestamps.get(0)) / 60000.0;
    }

    private static double inlandDurationMinutes(
            List<MaritimeSceneDecision> decisions, List<Long> timestamps) {
        if (timestamps == null || timestamps.size() < 2) return 0.0;
        double total = 0.0;
        int count = Math.min(decisions.size(), timestamps.size() - 1);
        for (int i = 0; i < count; i++) {
            if (!isInland(decisions.get(i).getSceneClass())) continue;
            total += Math.max(0L, timestamps.get(i + 1) - timestamps.get(i)) / 60000.0;
        }
        return total;
    }

    public static final class Summary {
        private final MaritimeSceneClass dominantScene;
        private final SceneConfidence dominantSceneConfidence;
        private final double inlandPointRatio;
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
        private final double durationMinutes;
        private final double inlandDurationMinutes;
        private final int pointCount;

        private Summary(
                MaritimeSceneClass dominantScene,
                SceneConfidence dominantSceneConfidence,
                double inlandPointRatio,
                double lakePointRatio,
                double canalPointRatio,
                double internalWaterPointRatio,
                double territorialSeaPointRatio,
                double offshoreEezPointRatio,
                double highSeasPointRatio,
                double landPointRatio,
                double unknownPointRatio,
                double conflictPointRatio,
                int sceneTransitionCount,
                Double coastDistanceMinNm,
                Double coastDistanceP50Nm,
                Double coastDistanceMaxNm,
                double durationMinutes,
                double inlandDurationMinutes,
                int pointCount) {
            this.dominantScene = dominantScene;
            this.dominantSceneConfidence = dominantSceneConfidence;
            this.inlandPointRatio = inlandPointRatio;
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
            this.durationMinutes = durationMinutes;
            this.inlandDurationMinutes = inlandDurationMinutes;
            this.pointCount = pointCount;
        }

        public MaritimeSceneClass getDominantScene() { return dominantScene; }
        public SceneConfidence getDominantSceneConfidence() { return dominantSceneConfidence; }
        public double getInlandPointRatio() { return inlandPointRatio; }
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
        public double getDurationMinutes() { return durationMinutes; }
        public double getInlandDurationMinutes() { return inlandDurationMinutes; }
        public int getPointCount() { return pointCount; }
    }
}
