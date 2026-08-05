package com.hifleet.behaviorplatform;

import java.util.List;

public final class NoopBehaviorResultWriter implements BehaviorResultWriter {
    public WriteMode mode() { return WriteMode.NONE; }
    public int write(BehaviorRunManifest manifest, List<BehaviorAuditResult> results) { return 0; }
}
