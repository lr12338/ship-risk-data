package com.hifleet.regionbehavior;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class ShadowRulesConfig {
    public static final String SCENE_POLICY_VERSION = "MARITIME_SCENE_SHADOW_V2";
    public static final String ALONGSIDE_BEHAVIOR_RULE_VERSION = "ALONGSIDE_SIDE_GEOMETRY_SHADOW_V1";
    public static final String ALONGSIDE_COMBINED_RULE_VERSION = "ALONGSIDE_V3_SCENE_GEOMETRY_SHADOW_V1";
    public static final String LOITERING_BEHAVIOR_RULE_VERSION = "LOITERING_SHAPE_SHADOW_V1";
    public static final String LOITERING_COMBINED_RULE_VERSION = "LOITERING_V2_SCENE_SHAPE_SHADOW_V1";

    @Value("${ship.behavior.region.shadow-rules.enabled:false}")
    private boolean enabled;
    @Value("${ship.behavior.region.shadow-rules.enforce-enabled:false}")
    private boolean enforceEnabled;
    @Value("${ship.behavior.region.shadow-rules.database-write-enabled:false}")
    private boolean databaseWriteEnabled;
    @Value("${ship.behavior.region.shadow-rules.ship-type-policy:OIL_TANKER_ONLY}")
    private String shipTypePolicy = ShipTypePolicy.OIL_TANKER_ONLY.name();
    @Value("${ship.behavior.region.shadow-rules.scene.gap-cap-minutes:10}")
    private long sceneGapCapMinutes = 10L;
    @Value("${ship.behavior.region.shadow-rules.scene.min-temporal-coverage:0.75}")
    private double sceneMinTemporalCoverage = 0.75;
    @Value("${ship.behavior.region.shadow-rules.scene.inland-time-ratio:0.90}")
    private double sceneInlandTimeRatio = 0.90;
    @Value("${ship.behavior.region.shadow-rules.scene.inland-min-duration-minutes:60}")
    private double sceneInlandMinDurationMinutes = 60.0;
    @Value("${ship.behavior.region.shadow-rules.scene.open-water-time-ratio:0.90}")
    private double sceneOpenWaterTimeRatio = 0.90;
    @Value("${ship.behavior.region.shadow-rules.scene.max-unknown-time-ratio:0.05}")
    private double sceneMaxUnknownTimeRatio = 0.05;
    @Value("${ship.behavior.region.shadow-rules.alongside.enabled:true}")
    private boolean alongsideEnabled = true;
    @Value("${ship.behavior.region.shadow-rules.alongside.min-duration-minutes:45}")
    private long alongsideMinDurationMinutes = 45L;
    @Value("${ship.behavior.region.shadow-rules.alongside.min-synchronized-points:10}")
    private int alongsideMinSynchronizedPoints = 10;
    @Value("${ship.behavior.region.shadow-rules.alongside.max-sync-gap-minutes:3}")
    private long alongsideMaxSyncGapMinutes = 3L;
    @Value("${ship.behavior.region.shadow-rules.alongside.min-sync-coverage:0.75}")
    private double alongsideMinSyncCoverage = 0.75;
    @Value("${ship.behavior.region.shadow-rules.alongside.max-relative-speed-p90-kn:0.5}")
    private double alongsideMaxRelativeSpeedP90Kn = 0.5;
    @Value("${ship.behavior.region.shadow-rules.alongside.max-heading-difference-p90-deg:15}")
    private double alongsideMaxHeadingDifferenceP90Deg = 15.0;
    @Value("${ship.behavior.region.shadow-rules.alongside.min-side-by-side-ratio:0.75}")
    private double alongsideMinSideBySideRatio = 0.75;
    @Value("${ship.behavior.region.shadow-rules.alongside.min-side-stability-ratio:0.75}")
    private double alongsideMinSideStabilityRatio = 0.75;
    @Value("${ship.behavior.region.shadow-rules.alongside.stationary-enabled:false}")
    private boolean alongsideStationaryEnabled;
    @Value("${ship.behavior.region.shadow-rules.loitering.enabled:true}")
    private boolean loiteringEnabled = true;
    @Value("${ship.behavior.region.shadow-rules.loitering.min-duration-minutes:120}")
    private long loiteringMinDurationMinutes = 120L;
    @Value("${ship.behavior.region.shadow-rules.loitering.min-track-coverage:0.75}")
    private double loiteringMinTrackCoverage = 0.75;
    @Value("${ship.behavior.region.shadow-rules.loitering.min-shape-evidence-count:2}")
    private int loiteringMinShapeEvidenceCount = 2;

    @PostConstruct
    public void validate() {
        if (enforceEnabled) throw new IllegalStateException("shadow-rules enforce-enabled must remain false");
        if (databaseWriteEnabled) throw new IllegalStateException("shadow-rules database-write-enabled must remain false");
        ShipTypePolicy.valueOf(shipTypePolicy);
        positive(sceneGapCapMinutes, "scene.gap-cap-minutes");
        ratio(sceneMinTemporalCoverage, "scene.min-temporal-coverage");
        ratio(sceneInlandTimeRatio, "scene.inland-time-ratio");
        positive(sceneInlandMinDurationMinutes, "scene.inland-min-duration-minutes");
        ratio(sceneOpenWaterTimeRatio, "scene.open-water-time-ratio");
        ratio(sceneMaxUnknownTimeRatio, "scene.max-unknown-time-ratio");
        positive(alongsideMinDurationMinutes, "alongside.min-duration-minutes");
        positive(alongsideMinSynchronizedPoints, "alongside.min-synchronized-points");
        positive(alongsideMaxSyncGapMinutes, "alongside.max-sync-gap-minutes");
        ratio(alongsideMinSyncCoverage, "alongside.min-sync-coverage");
        positive(alongsideMaxRelativeSpeedP90Kn, "alongside.max-relative-speed-p90-kn");
        positive(alongsideMaxHeadingDifferenceP90Deg, "alongside.max-heading-difference-p90-deg");
        ratio(alongsideMinSideBySideRatio, "alongside.min-side-by-side-ratio");
        ratio(alongsideMinSideStabilityRatio, "alongside.min-side-stability-ratio");
        positive(loiteringMinDurationMinutes, "loitering.min-duration-minutes");
        ratio(loiteringMinTrackCoverage, "loitering.min-track-coverage");
        positive(loiteringMinShapeEvidenceCount, "loitering.min-shape-evidence-count");
    }

    private void ratio(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalStateException(name + " must be between 0 and 1");
        }
    }

    private void positive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) throw new IllegalStateException(name + " must be positive");
    }

    public boolean isEnabled() { return enabled; }
    public boolean isEnforceEnabled() { return enforceEnabled; }
    public boolean isDatabaseWriteEnabled() { return databaseWriteEnabled; }
    public ShipTypePolicy getShipTypePolicy() { return ShipTypePolicy.valueOf(shipTypePolicy); }
    public long getSceneGapCapMinutes() { return sceneGapCapMinutes; }
    public double getSceneMinTemporalCoverage() { return sceneMinTemporalCoverage; }
    public double getSceneInlandTimeRatio() { return sceneInlandTimeRatio; }
    public double getSceneInlandMinDurationMinutes() { return sceneInlandMinDurationMinutes; }
    public double getSceneOpenWaterTimeRatio() { return sceneOpenWaterTimeRatio; }
    public double getSceneMaxUnknownTimeRatio() { return sceneMaxUnknownTimeRatio; }
    public boolean isAlongsideEnabled() { return alongsideEnabled; }
    public long getAlongsideMinDurationMinutes() { return alongsideMinDurationMinutes; }
    public int getAlongsideMinSynchronizedPoints() { return alongsideMinSynchronizedPoints; }
    public long getAlongsideMaxSyncGapMinutes() { return alongsideMaxSyncGapMinutes; }
    public double getAlongsideMinSyncCoverage() { return alongsideMinSyncCoverage; }
    public double getAlongsideMaxRelativeSpeedP90Kn() { return alongsideMaxRelativeSpeedP90Kn; }
    public double getAlongsideMaxHeadingDifferenceP90Deg() { return alongsideMaxHeadingDifferenceP90Deg; }
    public double getAlongsideMinSideBySideRatio() { return alongsideMinSideBySideRatio; }
    public double getAlongsideMinSideStabilityRatio() { return alongsideMinSideStabilityRatio; }
    public boolean isAlongsideStationaryEnabled() { return alongsideStationaryEnabled; }
    public boolean isLoiteringEnabled() { return loiteringEnabled; }
    public long getLoiteringMinDurationMinutes() { return loiteringMinDurationMinutes; }
    public double getLoiteringMinTrackCoverage() { return loiteringMinTrackCoverage; }
    public int getLoiteringMinShapeEvidenceCount() { return loiteringMinShapeEvidenceCount; }

    public void setEnabled(boolean value) { enabled = value; }
    public void setShipTypePolicy(String value) { shipTypePolicy = value; }
}
