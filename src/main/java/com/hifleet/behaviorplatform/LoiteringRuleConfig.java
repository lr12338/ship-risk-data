package com.hifleet.behaviorplatform;

import com.hifleet.service.ShipLoiteringDetector;

public final class LoiteringRuleConfig {
    private final RuleSetDefinition rules;
    private LoiteringRuleConfig(RuleSetDefinition rules) { this.rules = rules; }
    public static LoiteringRuleConfig from(RuleSetDefinition rules) { return new LoiteringRuleConfig(rules); }

    public ShipLoiteringDetector.LoiteringConfig detectorConfig() {
        ShipLoiteringDetector.LoiteringConfig value = ShipLoiteringDetector.LoiteringConfig.defaultConfig();
        value.setSlowMaxSpeed(rules.threshold("slow-max-speed", 2.0));
        value.setSlowMinDurationMinutes((long) rules.threshold("min-duration", 120.0));
        value.setManeuverMinSpeed(rules.threshold("maneuver-min-speed", 0.5));
        value.setManeuverMaxSpeed(rules.threshold("maneuver-max-speed", 5.0));
        value.setManeuverMinDurationMinutes((long) rules.threshold("min-duration", 120.0));
        value.setManeuverMinTurnCountPerHour(rules.threshold("min-turn-count-per-hour", 3.0));
        value.setManeuverMinTortuosity(rules.threshold("min-tortuosity", 3.0));
        value.setRuleVersion("RULESET:" + rules.getRuleSetId() + ":" + rules.getRuleSetVersion());
        return value;
    }
}
