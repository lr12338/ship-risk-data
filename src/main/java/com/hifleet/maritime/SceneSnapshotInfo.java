package com.hifleet.maritime;

public final class SceneSnapshotInfo {
    private final boolean available;
    private final String snapshotVersion;
    private final String snapshotFingerprint;
    private final String snapshotPolicyVersion;
    private final String checksumStatus;
    public SceneSnapshotInfo(boolean available, String snapshotVersion, String snapshotFingerprint,
                             String snapshotPolicyVersion, String checksumStatus) {
        this.available = available;
        this.snapshotVersion = snapshotVersion;
        this.snapshotFingerprint = snapshotFingerprint;
        this.snapshotPolicyVersion = snapshotPolicyVersion;
        this.checksumStatus = checksumStatus;
    }
    public boolean isAvailable() { return available; }
    public String getSnapshotVersion() { return snapshotVersion; }
    public String getSnapshotFingerprint() { return snapshotFingerprint; }
    public String getSnapshotPolicyVersion() { return snapshotPolicyVersion; }
    public String getChecksumStatus() { return checksumStatus; }
}
