package com.hifleet.maritime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.nio.file.Paths;
import java.util.Set;

@Service
public class MaritimeSceneService {
    @Value("${ship.behavior.maritime-scene.enabled:false}")
    private boolean enabled;
    @Value("${ship.behavior.maritime-scene.mode:OFF}")
    private String mode = "OFF";
    @Value("${ship.behavior.maritime-scene.snapshot-path:}")
    private String snapshotPath;
    @Value("${ship.behavior.maritime-scene.expected-fingerprint:}")
    private String expectedFingerprint;
    @Value("${ship.behavior.maritime-scene.policy-version:MARITIME_SCENE_SHADOW_V1}")
    private String policyVersion = MaritimeScenePolicy.VERSION;
    @Value("${ship.behavior.maritime-scene.cache.max-tiles:64}")
    private int maxTiles = 64;
    @Value("${ship.behavior.maritime-scene.boundary-tolerance-meters:1000}")
    private double boundaryToleranceMeters = 1000.0;
    @Value("${ship.behavior.maritime-scene.coast-search-radius-nm:60}")
    private double coastSearchRadiusNm = 60.0;
    @Value("${ship.behavior.maritime-scene.high-inland-point-ratio:0.90}")
    private double highInlandPointRatio = 0.90;
    @Value("${ship.behavior.maritime-scene.min-inland-minutes:60}")
    private double minInlandMinutes = 60.0;
    @Value("${ship.behavior.maritime-scene.open-water-point-ratio:0.90}")
    private double openWaterPointRatio = 0.90;
    @Value("${ship.behavior.maritime-scene.max-unknown-ratio:0.05}")
    private double maxUnknownRatio = 0.05;
    @Value("${ship.behavior.maritime-scene.offshore-min-coast-distance-nm:12}")
    private double offshoreMinCoastDistanceNm = 12.0;

    private MaritimeScenePolicy policy = MaritimeScenePolicy.defaults("OFF");
    private MaritimeSceneSnapshotManager snapshotManager =
            new MaritimeSceneSnapshotManager(null, null, MaritimeScenePolicy.VERSION, 64);
    private MaritimeSceneClassifier classifier = new MaritimeSceneClassifier(policy);
    private final CoastDistanceService coastDistanceService = new CoastDistanceService();
    private final SceneEpisodeAggregator episodeAggregator = new SceneEpisodeAggregator();

    public MaritimeSceneService() {
    }

    public MaritimeSceneService(MaritimeSceneSnapshotManager snapshotManager, MaritimeScenePolicy policy) {
        this.snapshotManager = snapshotManager;
        this.policy = policy == null ? MaritimeScenePolicy.defaults("SHADOW") : policy;
        this.classifier = new MaritimeSceneClassifier(this.policy);
        this.enabled = true;
        this.mode = this.policy.getMode();
        this.policyVersion = MaritimeScenePolicy.VERSION;
    }

    @PostConstruct
    public void initialize() {
        policy = new MaritimeScenePolicy(
                mode, boundaryToleranceMeters, coastSearchRadiusNm,
                highInlandPointRatio, minInlandMinutes,
                openWaterPointRatio, maxUnknownRatio, offshoreMinCoastDistanceNm);
        classifier = new MaritimeSceneClassifier(policy);
        if (enabled && policy.isShadow()) {
            snapshotManager = new MaritimeSceneSnapshotManager(
                    blankToNull(snapshotPath) == null ? null : Paths.get(snapshotPath),
                    blankToNull(expectedFingerprint),
                    blankToNull(policyVersion), maxTiles);
            snapshotManager.load();
        } else {
            snapshotManager = new MaritimeSceneSnapshotManager(
                    null, null, policyVersion, maxTiles);
        }
    }

    public MaritimeSceneDecision classify(double longitude, double latitude) {
        return classify(longitude, latitude, false);
    }

    public MaritimeSceneDecision classify(
            double longitude, double latitude, boolean runtimeWaterSupport) {
        if (!enabled || !policy.isShadow() || !snapshotManager.isAvailable()) {
            return unknown("scene service unavailable", runtimeWaterSupport);
        }
        try {
            longitude = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
            latitude = Math.max(-90.0,
                    Math.min(Math.nextAfter(90.0, Double.NEGATIVE_INFINITY), latitude));
            MaritimeSceneTileLoader loader = snapshotManager.getTileLoader();
            String centerTileId = MaritimeSceneTileLoader.tileId(longitude, latitude);
            if (!snapshotManager.getManifest().isTileCovered(centerTileId)) {
                return unknown("tile not covered: " + centerTileId, runtimeWaterSupport);
            }
            org.locationtech.jts.geom.Point point = MaritimeSceneTileLoader.point(longitude, latitude);
            List<MaritimeSceneTileLoader.TileRecord> matches = new ArrayList<MaritimeSceneTileLoader.TileRecord>();
            List<MaritimeSceneTileLoader.TileRecord> coast = new ArrayList<MaritimeSceneTileLoader.TileRecord>();
            Set<String> keys = new LinkedHashSet<String>();
            for (int latOffset = -1; latOffset <= 1; latOffset++) {
                for (int lonOffset = -1; lonOffset <= 1; lonOffset++) {
                    String tileId = MaritimeSceneTileLoader.tileId(
                            longitude + lonOffset, latitude + latOffset);
                    MaritimeSceneTileLoader.TileData tile = loader.load(tileId);
                    for (MaritimeSceneTileLoader.TileRecord record : tile.sceneCandidates(point)) {
                        String key = record.getSceneType() + "|" + record.getSourceFeatureId()
                                + "|" + record.getGeometry().getGeometryType();
                        if (record.covers(point) && keys.add(key)) matches.add(record);
                    }
                    coast.addAll(tile.getCoastRecords());
                }
            }
            MaritimeSceneEvidence evidence = evidence(matches, runtimeWaterSupport);
            CoastDistanceService.Result coastResult = coastDistanceService.nearest(
                    longitude, latitude, coast, policy.getCoastSearchRadiusNm());
            Double boundaryDistance = boundaryDistance(longitude, latitude, matches);
            return classifier.classify(
                    matches, evidence, coastResult.getDistanceNm(), boundaryDistance,
                    snapshotManager.getManifest().getReleaseVersion(),
                    snapshotManager.getManifest().getSnapshotFingerprint(), policyVersion);
        } catch (RuntimeException ex) {
            return unknown("tile load failed: " + ex.getMessage(), runtimeWaterSupport);
        }
    }

    private MaritimeSceneEvidence evidence(
            List<MaritimeSceneTileLoader.TileRecord> matches, boolean runtimeWaterSupport) {
        List<String> types = new ArrayList<String>();
        List<String> ids = new ArrayList<String>();
        boolean jrcStableWater = false;
        for (MaritimeSceneTileLoader.TileRecord record : matches) {
            types.add(record.getSceneType());
            ids.add(record.getSourceFeatureId());
            jrcStableWater |= record.getProperties().getBooleanValue("jrcStableWater");
        }
        return new MaritimeSceneEvidence(types, ids, true, jrcStableWater, runtimeWaterSupport, null);
    }

    private Double boundaryDistance(
            double longitude, double latitude,
            List<MaritimeSceneTileLoader.TileRecord> matches) {
        double best = Double.POSITIVE_INFINITY;
        for (MaritimeSceneTileLoader.TileRecord record : matches) {
            if (record.getBoundaryGeometry() == null || record.getBoundaryGeometry().isEmpty()) {
                continue;
            }
            best = Math.min(best, CoastDistanceService.distanceToGeometryMeters(
                    longitude, latitude, record.getBoundaryGeometry()));
        }
        return Double.isFinite(best) ? best : null;
    }

    private MaritimeSceneDecision unknown(String reason, boolean runtimeWaterSupport) {
        MaritimeSceneEvidence evidence = MaritimeSceneEvidence.unavailable(reason);
        return new MaritimeSceneDecision(
                MaritimeSceneClass.SCENE_UNKNOWN, SceneConfidence.UNKNOWN, evidence,
                null, null, snapshotVersion(), snapshotFingerprint(), policyVersion, false);
    }

    public SceneEpisodeAggregator.Summary aggregate(List<MaritimeSceneDecision> decisions) {
        return episodeAggregator.aggregate(decisions);
    }

    public SceneEpisodeAggregator.Summary aggregate(
            List<MaritimeSceneDecision> decisions, List<Long> epochMillis) {
        return episodeAggregator.aggregate(decisions, epochMillis);
    }

    public String shadowDecision(SceneEpisodeAggregator.Summary summary) {
        if (summary == null) return "SCENE_UNKNOWN";
        if (summary.getConflictPointRatio() > 0.0
                || summary.getDominantScene() == MaritimeSceneClass.SCENE_CONFLICT) {
            return "SCENE_CONFLICT";
        }
        if (summary.getHighConfidenceInlandPointRatio() >= policy.getHighInlandPointRatio()
                && summary.getInlandDurationMinutes() >= policy.getMinInlandMinutes()) {
            return "WOULD_REJECT_INLAND";
        }
        double openWaterRatio = summary.getHighConfidenceOpenWaterPointRatio();
        if (openWaterRatio >= policy.getOpenWaterPointRatio()
                && summary.getUnknownPointRatio() <= policy.getMaxUnknownRatio()
                && summary.getConflictPointRatio() == 0.0
                && summary.getDominantSceneConfidence() == SceneConfidence.HIGH) {
            return "OPEN_WATER_CONFIRMED";
        }
        if (isCoastalReviewScene(summary.getDominantScene())) {
            return "COASTAL_REVIEW";
        }
        if (summary.getCoastDistanceMinNm() != null && summary.getCoastDistanceMinNm() <= 1.0) {
            return "COASTAL_REVIEW";
        }
        return "SCENE_UNKNOWN";
    }

    private static boolean isCoastalReviewScene(MaritimeSceneClass scene) {
        return scene == MaritimeSceneClass.MARITIME_INTERNAL_WATER
                || scene == MaritimeSceneClass.ARCHIPELAGIC_WATER
                || scene == MaritimeSceneClass.TERRITORIAL_SEA
                || scene == MaritimeSceneClass.ESTUARY_OR_RIVER_MOUTH
                || scene == MaritimeSceneClass.BOUNDARY_UNCERTAIN;
    }

    public java.util.Map<String, Object> preflight() {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<String, Object>();
        result.put("enabled", enabled);
        result.put("mode", mode);
        result.put("policyVersion", policyVersion);
        result.putAll(snapshotManager.preflight());
        return result;
    }

    public boolean isEnabled() { return enabled; }
    public String getMode() { return mode; }
    public MaritimeScenePolicy getPolicy() { return policy; }
    public MaritimeSceneSnapshotManager getSnapshotManager() { return snapshotManager; }

    private String snapshotVersion() {
        return snapshotManager.getManifest() == null ? null
                : snapshotManager.getManifest().getReleaseVersion();
    }

    private String snapshotFingerprint() {
        return snapshotManager.getManifest() == null ? null
                : snapshotManager.getManifest().getSnapshotFingerprint();
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
