package com.hifleet.behaviorplatform;

import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.service.ShipLoiteringDetector;
import java.util.List;

public final class LoiteringDetectorAdapter implements BehaviorDetectorAdapter {
    public BehaviorType behaviorType() { return BehaviorType.LOITERING; }
    public List<ShipBehaviorTrace> detect(BehaviorDetectorRequest request) {
        return new ShipLoiteringDetector().detectWithTrace(request.getMmsi(), request.getOwnPoints(),
                ShipLoiteringDetector.LoiteringConfig.defaultConfig());
    }
}
