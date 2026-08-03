package com.hifleet.maritime;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class MaritimeSceneSnapshotManager {
    private final Path releaseRoot;
    private final String expectedFingerprint;
    private final String expectedPolicyVersion;
    private final int maxTiles;
    private MaritimeSceneManifest manifest;
    private MaritimeSceneTileLoader tileLoader;
    private String failureReason = "not loaded";

    public MaritimeSceneSnapshotManager(
            Path releaseRoot, String expectedFingerprint, String expectedPolicyVersion, int maxTiles) {
        this.releaseRoot = releaseRoot;
        this.expectedFingerprint = expectedFingerprint;
        this.expectedPolicyVersion = expectedPolicyVersion;
        this.maxTiles = maxTiles;
    }

    public synchronized boolean load() {
        try {
            if (releaseRoot == null) throw new IllegalStateException("snapshot path is empty");
            manifest = MaritimeSceneManifest.read(releaseRoot, expectedFingerprint, expectedPolicyVersion);
            tileLoader = new MaritimeSceneTileLoader(manifest.getReleaseRoot(), maxTiles);
            failureReason = null;
            return true;
        } catch (RuntimeException ex) {
            manifest = null;
            tileLoader = null;
            failureReason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return false;
        }
    }

    public synchronized boolean isAvailable() { return manifest != null && tileLoader != null; }
    public synchronized MaritimeSceneManifest getManifest() { return manifest; }
    public synchronized MaritimeSceneTileLoader getTileLoader() { return tileLoader; }
    public synchronized String getFailureReason() { return failureReason; }

    public synchronized Map<String, Object> preflight() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("available", isAvailable());
        result.put("snapshotPath", releaseRoot == null ? null : releaseRoot.toString());
        if (manifest != null) result.putAll(manifest.preflight());
        if (failureReason != null) result.put("failureReason", failureReason);
        return result;
    }
}
