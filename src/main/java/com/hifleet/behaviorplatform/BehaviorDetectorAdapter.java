package com.hifleet.behaviorplatform;

import com.hifleet.domain.ShipBehaviorTrace;
import java.util.List;

public interface BehaviorDetectorAdapter {
    BehaviorType behaviorType();
    List<ShipBehaviorTrace> detect(BehaviorDetectorRequest request);
}
