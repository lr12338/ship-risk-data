package com.hifleet.behaviorplatform;

import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.service.ShipAlongsideDetector;
import java.util.List;

public final class AlongsideDetectorAdapter implements BehaviorDetectorAdapter {
    private final ShipAlongsideDetector.AlongsideRule rule;
    public AlongsideDetectorAdapter() { this.rule = ShipAlongsideDetector.AlongsideRule.defaultRule(); }
    public AlongsideDetectorAdapter(RuleSetDefinition rules) { this.rule = AlongsideRuleConfig.from(rules).detectorRule(); }
    public BehaviorType behaviorType() { return BehaviorType.ALONGSIDE; }
    public List<ShipBehaviorTrace> detect(BehaviorDetectorRequest request) {
        return new ShipAlongsideDetector().detectWithTrace(request.getMmsi(), request.getOwnPoints(),
                request.getTargetMmsi(), request.getTargetPoints(),
                rule);
    }
}
