package com.hifleet.behaviorplatform;

import java.util.List;

public interface BehaviorResultWriter {
    WriteMode mode();
    int write(BehaviorRunManifest manifest, List<BehaviorAuditResult> results);
}
