package com.hifleet.behaviorplatform;

import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.service.ShipAlongsideDetector;
import java.util.List;

public final class AlongsideDetectorAdapter implements BehaviorDetectorAdapter {
    public BehaviorType behaviorType() { return BehaviorType.ALONGSIDE; }
    public List<ShipBehaviorTrace> detect(BehaviorDetectorRequest request) {
        return new ShipAlongsideDetector().detectWithTrace(request.getMmsi(), request.getOwnPoints(),
                request.getTargetMmsi(), request.getTargetPoints(),
                ShipAlongsideDetector.AlongsideRule.defaultRule());
    }
}
