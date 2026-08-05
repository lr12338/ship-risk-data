package com.hifleet.behaviorplatform;

import com.hifleet.service.ShipAlongsideDetector;
import com.hifleet.service.ShipLoiteringDetector;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class RuleSetRuntimeConfigTest {
    @Test
    public void alongsideRuleSetChangesDetectorRule() {
        RuleSetDefinition rules = rules("alongside-runtime-v2",
                new RuleDefinition("min-speed", "2", 0.8, "kn"),
                new RuleDefinition("max-speed", "2", 1.7, "kn"),
                new RuleDefinition("min-duration", "2", 55, "min"),
                new RuleDefinition("min-synchronized-points", "2", 12, "count"));
        ShipAlongsideDetector.AlongsideRule value = AlongsideRuleConfig.from(rules).detectorRule();
        assertEquals(0.8, value.getConfirmMinSpeed(), 0.0);
        assertEquals(1.7, value.getConfirmMaxSpeed(), 0.0);
        assertEquals(55, value.getMinDurationMinutes());
        assertEquals(12, value.getFormalMinPoints());
    }

    @Test
    public void loiteringRuleSetChangesDetectorConfig() {
        RuleSetDefinition rules = rules("loitering-runtime-v2",
                new RuleDefinition("slow-max-speed", "2", 1.6, "kn"),
                new RuleDefinition("min-duration", "2", 150, "min"),
                new RuleDefinition("maneuver-min-speed", "2", 0.7, "kn"));
        ShipLoiteringDetector.LoiteringConfig value = LoiteringRuleConfig.from(rules).detectorConfig();
        assertEquals(1.6, value.getSlowMaxSpeed(), 0.0);
        assertEquals(150, value.getSlowMinDurationMinutes());
        assertEquals(0.7, value.getManeuverMinSpeed(), 0.0);
    }

    private RuleSetDefinition rules(String id, RuleDefinition... values) {
        RuleSetDefinition result = new RuleSetDefinition(id, 2, false, Arrays.asList(values));
        result.validateAndHash();
        return result;
    }
}
