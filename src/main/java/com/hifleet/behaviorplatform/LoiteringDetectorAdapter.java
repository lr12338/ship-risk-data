package com.hifleet.behaviorplatform;

import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.service.ShipLoiteringDetector;
import java.util.List;

public final class LoiteringDetectorAdapter implements BehaviorDetectorAdapter {
    private final ShipLoiteringDetector.LoiteringConfig config;
    public LoiteringDetectorAdapter() { this.config = ShipLoiteringDetector.LoiteringConfig.defaultConfig(); }
    public LoiteringDetectorAdapter(RuleSetDefinition rules) { this.config = LoiteringRuleConfig.from(rules).detectorConfig(); }
    public BehaviorType behaviorType() { return BehaviorType.LOITERING; }
    public List<ShipBehaviorTrace> detect(BehaviorDetectorRequest request) {
        return new ShipLoiteringDetector().detectWithTrace(request.getMmsi(), request.getOwnPoints(),
                config);
    }
}
