package com.hifleet.maritime;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ScenePreflightResult {
    private final SceneRuntimeInfo runtime;
    private final SceneSnapshotInfo snapshot;
    public ScenePreflightResult(SceneRuntimeInfo runtime, SceneSnapshotInfo snapshot) {
        if (runtime == null || snapshot == null) throw new IllegalArgumentException("runtime and snapshot are required");
        this.runtime = runtime;
        this.snapshot = snapshot;
    }
    public boolean isCompatible(String requiredSnapshotPolicyVersion) {
        return requiredSnapshotPolicyVersion == null
                || requiredSnapshotPolicyVersion.equals(snapshot.getSnapshotPolicyVersion());
    }
    public Map<String, Object> toLegacyMap() {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("enabled", runtime.isEnabled());
        value.put("mode", runtime.getMode());
        value.put("available", snapshot.isAvailable());
        value.put("releaseVersion", snapshot.getSnapshotVersion());
        value.put("snapshotFingerprint", snapshot.getSnapshotFingerprint());
        value.put("checksumStatus", snapshot.getChecksumStatus());
        value.put("policyVersion", runtime.getRuntimePolicyVersion());
        value.put("snapshotPolicyVersion", snapshot.getSnapshotPolicyVersion());
        return value;
    }
    public String getRuntimePolicyVersion() { return runtime.getRuntimePolicyVersion(); }
    public String getSnapshotPolicyVersion() { return snapshot.getSnapshotPolicyVersion(); }
    public String getSnapshotVersion() { return snapshot.getSnapshotVersion(); }
    public String getSnapshotFingerprint() { return snapshot.getSnapshotFingerprint(); }
    public String getMode() { return runtime.getMode(); }
    public boolean isAvailable() { return snapshot.isAvailable(); }
    public String getChecksumStatus() { return snapshot.getChecksumStatus(); }
    public SceneRuntimeInfo getRuntime() { return runtime; }
    public SceneSnapshotInfo getSnapshot() { return snapshot; }
}
