package com.hifleet.maritime;

public class MaritimeSceneDecision {
    private final MaritimeSceneClass sceneClass;
    private final SceneConfidence confidence;
    private final MaritimeSceneEvidence evidence;
    private final Double distanceToCoastNm;
    private final Double distanceToBoundaryMeters;
    private final String snapshotVersion;
    private final String snapshotFingerprint;
    private final String policyVersion;
    private final boolean conflictFlag;

    public MaritimeSceneDecision(
            MaritimeSceneClass sceneClass,
            SceneConfidence confidence,
            MaritimeSceneEvidence evidence,
            Double distanceToCoastNm,
            Double distanceToBoundaryMeters,
            String snapshotVersion,
            String snapshotFingerprint,
            String policyVersion,
            boolean conflictFlag) {
        this.sceneClass = sceneClass == null ? MaritimeSceneClass.SCENE_UNKNOWN : sceneClass;
        this.confidence = confidence == null ? SceneConfidence.UNKNOWN : confidence;
        this.evidence = evidence == null ? MaritimeSceneEvidence.unavailable("missing") : evidence;
        this.distanceToCoastNm = distanceToCoastNm;
        this.distanceToBoundaryMeters = distanceToBoundaryMeters;
        this.snapshotVersion = snapshotVersion;
        this.snapshotFingerprint = snapshotFingerprint;
        this.policyVersion = policyVersion;
        this.conflictFlag = conflictFlag || this.sceneClass == MaritimeSceneClass.SCENE_CONFLICT;
    }

    public static MaritimeSceneDecision unknown(
            String reason, String snapshotVersion, String snapshotFingerprint, String policyVersion) {
        return new MaritimeSceneDecision(
                MaritimeSceneClass.SCENE_UNKNOWN, SceneConfidence.UNKNOWN,
                MaritimeSceneEvidence.unavailable(reason), null, null,
                snapshotVersion, snapshotFingerprint, policyVersion, false);
    }

    public MaritimeSceneClass getSceneClass() { return sceneClass; }
    public SceneConfidence getConfidence() { return confidence; }
    public MaritimeSceneEvidence getEvidence() { return evidence; }
    public Double getDistanceToCoastNm() { return distanceToCoastNm; }
    public Double getDistanceToBoundaryMeters() { return distanceToBoundaryMeters; }
    public String getSnapshotVersion() { return snapshotVersion; }
    public String getSnapshotFingerprint() { return snapshotFingerprint; }
    public String getPolicyVersion() { return policyVersion; }
    public boolean isConflictFlag() { return conflictFlag; }
}
