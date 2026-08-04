package com.hifleet.maritime;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MaritimeSceneClassifier {
    private final MaritimeScenePolicy policy;

    public MaritimeSceneClassifier(MaritimeScenePolicy policy) {
        this.policy = policy == null ? MaritimeScenePolicy.defaults("SHADOW") : policy;
    }

    public MaritimeSceneDecision classify(
            List<MaritimeSceneTileLoader.TileRecord> records,
            MaritimeSceneEvidence evidence,
            Double distanceToCoastNm,
            Double distanceToBoundaryMeters,
            String snapshotVersion,
            String snapshotFingerprint,
            String policyVersion) {
        Set<String> types = new HashSet<String>();
        for (MaritimeSceneTileLoader.TileRecord record : records) types.add(record.getSceneType());
        boolean land = types.contains("land");
        boolean river = types.contains("inland_river_centerline")
                || types.contains("inland_river_corridor");
        boolean lake = types.contains("inland_lake");
        boolean canal = types.contains("major_canal");
        boolean estuary = types.contains("estuary_or_river_mouth");
        boolean marine = hasMarine(types);

        MaritimeSceneClass sceneClass;
        SceneConfidence confidence;
        if (countInlandTypes(river, lake, canal, estuary) > 1) {
            sceneClass = MaritimeSceneClass.SCENE_CONFLICT;
            confidence = SceneConfidence.UNKNOWN;
        } else if ((river || lake || estuary) && marine) {
            sceneClass = MaritimeSceneClass.SCENE_CONFLICT;
            confidence = SceneConfidence.UNKNOWN;
        } else if (river) {
            sceneClass = MaritimeSceneClass.INLAND_RIVER;
            confidence = evidence.isJrcStableWater() || evidence.isRuntimeWaterSupport()
                    ? SceneConfidence.HIGH : SceneConfidence.LOW;
        } else if (lake) {
            sceneClass = MaritimeSceneClass.INLAND_LAKE;
            confidence = sourceConfidence(records, "inland_lake");
        } else if (canal) {
            sceneClass = MaritimeSceneClass.MAJOR_CANAL;
            confidence = sourceConfidence(records, "major_canal");
        } else if (estuary) {
            sceneClass = MaritimeSceneClass.ESTUARY_OR_RIVER_MOUTH;
            confidence = SceneConfidence.MEDIUM;
        } else if (marine && land) {
            sceneClass = MaritimeSceneClass.BOUNDARY_UNCERTAIN;
            confidence = SceneConfidence.LOW;
        } else if (marine) {
            sceneClass = marineClass(types);
            confidence = marineConfidence(sceneClass, distanceToCoastNm);
            if (isNearBoundary(distanceToCoastNm, distanceToBoundaryMeters)) {
                sceneClass = MaritimeSceneClass.BOUNDARY_UNCERTAIN;
                confidence = SceneConfidence.LOW;
            }
        } else if (land) {
            sceneClass = MaritimeSceneClass.LAND;
            confidence = SceneConfidence.HIGH;
        } else {
            sceneClass = MaritimeSceneClass.SCENE_UNKNOWN;
            confidence = SceneConfidence.UNKNOWN;
        }
        if ((sceneClass == MaritimeSceneClass.INLAND_LAKE
                || sceneClass == MaritimeSceneClass.MAJOR_CANAL)
                && distanceToBoundaryMeters != null
                && distanceToBoundaryMeters <= sourceBoundaryTolerance(
                records, sceneClass == MaritimeSceneClass.INLAND_LAKE
                        ? "inland_lake" : "major_canal",
                policy.getBoundaryToleranceMeters())) {
            sceneClass = MaritimeSceneClass.BOUNDARY_UNCERTAIN;
            confidence = SceneConfidence.LOW;
        }
        return new MaritimeSceneDecision(
                sceneClass, confidence, evidence, distanceToCoastNm, distanceToBoundaryMeters,
                snapshotVersion, snapshotFingerprint, policyVersion,
                sceneClass == MaritimeSceneClass.SCENE_CONFLICT);
    }

    private boolean isNearBoundary(Double coastDistanceNm, Double boundaryDistanceMeters) {
        return boundaryDistanceMeters != null
                && boundaryDistanceMeters <= policy.getBoundaryToleranceMeters()
                || coastDistanceNm != null && coastDistanceNm <= 1.0;
    }

    private static int countInlandTypes(boolean river, boolean lake, boolean canal, boolean estuary) {
        int count = 0;
        if (river) count++;
        if (lake) count++;
        if (canal) count++;
        if (estuary) count++;
        return count;
    }

    private static boolean hasMarine(Set<String> types) {
        return types.contains("marine_internal_water")
                || types.contains("marine_archipelagic_water")
                || types.contains("marine_territorial_sea")
                || types.contains("marine_eez")
                || types.contains("marine_high_seas");
    }

    private static MaritimeSceneClass marineClass(Set<String> types) {
        if (types.contains("marine_internal_water")) return MaritimeSceneClass.MARITIME_INTERNAL_WATER;
        if (types.contains("marine_archipelagic_water")) return MaritimeSceneClass.ARCHIPELAGIC_WATER;
        if (types.contains("marine_territorial_sea")) return MaritimeSceneClass.TERRITORIAL_SEA;
        if (types.contains("marine_eez")) return MaritimeSceneClass.OFFSHORE_EEZ;
        return MaritimeSceneClass.HIGH_SEAS;
    }

    private SceneConfidence marineConfidence(
            MaritimeSceneClass sceneClass, Double distanceToCoastNm) {
        if (sceneClass == MaritimeSceneClass.HIGH_SEAS) return SceneConfidence.HIGH;
        if (sceneClass == MaritimeSceneClass.OFFSHORE_EEZ
                && distanceToCoastNm != null
                && distanceToCoastNm >= policy.getOffshoreMinCoastDistanceNm()) {
            return SceneConfidence.HIGH;
        }
        return SceneConfidence.MEDIUM;
    }

    private static SceneConfidence sourceConfidence(
            List<MaritimeSceneTileLoader.TileRecord> records, String sceneType) {
        for (MaritimeSceneTileLoader.TileRecord record : records) {
            if (sceneType.equals(record.getSceneType())
                    && "HIGH".equalsIgnoreCase(record.getConfidence())) {
                return SceneConfidence.HIGH;
            }
        }
        return SceneConfidence.UNKNOWN;
    }

    private static double sourceBoundaryTolerance(
            List<MaritimeSceneTileLoader.TileRecord> records,
            String sceneType,
            double fallback) {
        for (MaritimeSceneTileLoader.TileRecord record : records) {
            if (!sceneType.equals(record.getSceneType())) continue;
            Double value = record.getProperties().getDouble("boundary_tolerance_meters");
            if (value != null && value >= 0.0) return value;
        }
        return fallback;
    }
}
