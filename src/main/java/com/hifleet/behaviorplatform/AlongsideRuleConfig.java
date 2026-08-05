package com.hifleet.behaviorplatform;

import com.hifleet.service.ShipAlongsideDetector;

public final class AlongsideRuleConfig {
    private final double minSpeed;
    private final double maxSpeed;
    private final long minDurationMinutes;
    private final int minSynchronizedPoints;
    private final long maxSyncGapMinutes;
    private final double maxRelativeSpeed;
    private final double maxHeadingDifference;
    private final double minSideBySideRatio;
    private final double maxDistanceVariance;
    private final boolean stationaryEnabled;

    private AlongsideRuleConfig(RuleSetDefinition rules) {
        minSpeed = rules.threshold("min-speed", 1.0);
        maxSpeed = rules.threshold("max-speed", 2.0);
        minDurationMinutes = (long) rules.threshold("min-duration", 45.0);
        minSynchronizedPoints = (int) rules.threshold("min-synchronized-points", 10.0);
        maxSyncGapMinutes = (long) rules.threshold("max-sync-gap", 3.0);
        maxRelativeSpeed = rules.threshold("max-relative-speed-p90", 0.5);
        maxHeadingDifference = rules.threshold("max-heading-difference-p90", 15.0);
        minSideBySideRatio = rules.threshold("min-side-by-side-ratio", 0.75);
        maxDistanceVariance = rules.threshold("max-distance-variance", 0.0025);
        stationaryEnabled = rules.threshold("stationary-enabled", 0.0) == 1.0;
        if (minSpeed < 0 || minSpeed > maxSpeed || minDurationMinutes <= 0
                || minSynchronizedPoints <= 0 || maxSyncGapMinutes <= 0)
            throw new IllegalArgumentException("invalid alongside RuleSet thresholds");
    }

    public static AlongsideRuleConfig from(RuleSetDefinition rules) {
        return new AlongsideRuleConfig(rules);
    }

    public ShipAlongsideDetector.AlongsideRule detectorRule() {
        ShipAlongsideDetector.AlongsideRule value = ShipAlongsideDetector.AlongsideRule.defaultRule();
        value.setConfirmMinSpeed(stationaryEnabled ? 0.0 : minSpeed);
        value.setConfirmMaxSpeed(maxSpeed);
        value.setMinDurationMinutes(minDurationMinutes);
        value.setWeakMinDurationMinutes(minDurationMinutes);
        value.setMinPoints(minSynchronizedPoints);
        value.setFormalMinPoints(minSynchronizedPoints);
        value.setMaxTimeGapMinutes(maxSyncGapMinutes);
        value.setHighConfidenceTimeGapMinutes(maxSyncGapMinutes);
        value.setMaxRelativeSpeed(maxRelativeSpeed);
        value.setHeadingParallelThresholdDegree(maxHeadingDifference);
        value.setMinParallelRatio(minSideBySideRatio);
        value.setMaxDistanceVarianceNm2(maxDistanceVariance);
        value.setRuleVersion("RULESET:" + minDurationMinutes + ":" + maxSpeed);
        return value;
    }
}
