package com.hifleet.loitering;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoiteringPortSceneProperties {
    @Value("${ship.behavior.loitering.min-speed:1.0}") private double minSpeed = 1.0;
    @Value("${ship.behavior.loitering.split-below-min-speed:true}") private boolean splitBelowMinSpeed = true;
    @Value("${ship.behavior.loitering.port-approach.enabled:true}") private boolean enabled = true;
    @Value("${ship.behavior.loitering.port-approach.required:false}") private boolean required = false;
    @Value("${ship.behavior.scene.port-approach-distance-nm:${ship.behavior.loitering.port-approach.max-distance-nm:10.0}}") private double maxDistanceNm = 10.0;
    @Value("${ship.behavior.loitering.offshore.enabled:true}") private boolean offshoreEnabled = true;
    @Value("${ship.behavior.loitering.port-approach.min-evidence-coverage:1.0}") private double minEvidenceCoverage = 1.0;
    @Value("${ship.behavior.loitering.scene.fail-closed:true}") private boolean failClosed = true;

    public void validate() {
        if (!Double.isFinite(minSpeed) || minSpeed < 0.0 || !Double.isFinite(maxDistanceNm)
                || maxDistanceNm <= 0.0 || minEvidenceCoverage < 0.0 || minEvidenceCoverage > 1.0) {
            throw new IllegalStateException("invalid LOITERING_PORT_SCENE configuration");
        }
    }
    public double getMinSpeed() { return minSpeed; }
    public boolean isSplitBelowMinSpeed() { return splitBelowMinSpeed; }
    public boolean isEnabled() { return enabled; }
    public boolean isRequired() { return required; }
    public boolean isOffshoreEnabled() { return offshoreEnabled; }
    public double getMaxDistanceNm() { return maxDistanceNm; }
    public double getMinEvidenceCoverage() { return minEvidenceCoverage; }
    public boolean isFailClosed() { return failClosed; }
}
