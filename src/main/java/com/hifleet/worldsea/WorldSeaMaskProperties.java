package com.hifleet.worldsea;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WorldSeaMaskProperties {
    @Value("${ship.behavior.world-sea-mask.enabled:true}") private boolean enabled = true;
    @Value("${ship.behavior.world-sea-mask.required:true}") private boolean required = true;
    @Value("${ship.behavior.world-sea-mask.datasource:NEWDATAANALYSIS}") private String datasource = "NEWDATAANALYSIS";
    @Value("${ship.behavior.world-sea-mask.schema:dataanalysis}") private String schema = "dataanalysis";
    @Value("${ship.behavior.world-sea-mask.table:ship_behavior_world_sea}") private String table = "ship_behavior_world_sea";
    @Value("${ship.behavior.world-sea-mask.dataset-version:WORLD_SEA_102_V1}") private String datasetVersion = "WORLD_SEA_102_V1";
    @Value("${ship.behavior.world-sea-mask.expected-feature-count:102}") private int expectedFeatureCount = 102;
    @Value("${ship.behavior.world-sea-mask.mode:STRICT_ALL_EVIDENCE}") private String mode = "STRICT_ALL_EVIDENCE";
    @Value("${ship.behavior.world-sea-mask.min-evidence-coverage:1.0}") private double minEvidenceCoverage = 1.0d;
    @Value("${ship.behavior.world-sea-mask.shadow-min-evidence-coverage:0.95}") private double shadowMinEvidenceCoverage = 0.95d;
    @Value("${ship.behavior.world-sea-mask.require-event-center:true}") private boolean requireEventCenter = true;
    @Value("${ship.behavior.world-sea-mask.fail-closed:true}") private boolean failClosed = true;
    @Value("${ship.behavior.world-sea-mask.loitering.split-on-outside:true}") private boolean splitLoiteringOnOutside = true;
    @Value("${ship.behavior.world-sea-mask.alongside.prefilter-own-windows:true}") private boolean prefilterAlongsideOwnWindows = true;
    @Value("${ship.behavior.world-sea-mask.alongside.require-own-point:true}") private boolean requireOwnPoint = true;
    @Value("${ship.behavior.world-sea-mask.alongside.require-target-point:true}") private boolean requireTargetPoint = true;
    @Value("${ship.behavior.world-sea-mask.alongside.require-pair-midpoint:true}") private boolean requirePairMidpoint = true;

    public void validate() {
        if (!"NEWDATAANALYSIS".equals(datasource)
                || !"dataanalysis".equals(schema)
                || !"ship_behavior_world_sea".equals(table)) {
            throw new IllegalStateException("WORLD_SEA_MASK datasource/schema/table is not allowlisted");
        }
        if (!"STRICT_ALL_EVIDENCE".equals(mode)) {
            throw new IllegalStateException("WORLD_SEA_MASK mode must be STRICT_ALL_EVIDENCE");
        }
        if (!enabled || !required || !failClosed || minEvidenceCoverage != 1.0d
                || !requireEventCenter || !splitLoiteringOnOutside
                || !prefilterAlongsideOwnWindows || !requireOwnPoint
                || !requireTargetPoint || !requirePairMidpoint) {
            throw new IllegalStateException("WORLD_SEA_MASK strict fail-closed configuration is required");
        }
        if (expectedFeatureCount <= 0) {
            throw new IllegalStateException("WORLD_SEA_MASK expected feature count must be positive");
        }
        if (shadowMinEvidenceCoverage < 0.0d || shadowMinEvidenceCoverage > 1.0d) {
            throw new IllegalStateException("WORLD_SEA_MASK shadow coverage must be between 0 and 1");
        }
    }

    public String identity(WorldSeaMaskSnapshot snapshot) {
        return "enabled=" + enabled + "|mode=" + mode + "|coverage=" + minEvidenceCoverage
                + "|shadowCoverage=" + shadowMinEvidenceCoverage
                + "|datasetVersion=" + snapshot.getDatasetVersion()
                + "|sourceSha256=" + snapshot.getSourceFileSha256()
                + "|geometryFingerprint=" + snapshot.getGeometryFingerprint()
                + "|requireMidpoint=" + requirePairMidpoint
                + "|requireEventCenter=" + requireEventCenter;
    }

    public boolean isEnabled() { return enabled; }
    public boolean isRequired() { return required; }
    public String getSchema() { return schema; }
    public String getTable() { return table; }
    public String getDatasetVersion() { return datasetVersion; }
    public int getExpectedFeatureCount() { return expectedFeatureCount; }
    public double getMinEvidenceCoverage() { return minEvidenceCoverage; }
    public double getShadowMinEvidenceCoverage() { return shadowMinEvidenceCoverage; }
    public boolean isRequireEventCenter() { return requireEventCenter; }
    public boolean isRequireOwnPoint() { return requireOwnPoint; }
    public boolean isRequireTargetPoint() { return requireTargetPoint; }
    public boolean isRequirePairMidpoint() { return requirePairMidpoint; }

    void setExpectedFeatureCount(int value) { expectedFeatureCount = value; }
    void setDatasetVersion(String value) { datasetVersion = value; }
}
