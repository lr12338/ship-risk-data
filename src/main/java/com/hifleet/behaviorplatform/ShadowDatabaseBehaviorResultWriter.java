package com.hifleet.behaviorplatform;

import java.util.List;

public final class ShadowDatabaseBehaviorResultWriter implements BehaviorResultWriter {
    private final ShadowBehaviorResultSink shadowSink;
    @SuppressWarnings("unused") private final FormalBehaviorEventSink formalSinkBoundary;
    public ShadowDatabaseBehaviorResultWriter(ShadowBehaviorResultSink shadowSink,
                                              FormalBehaviorEventSink formalSinkBoundary) {
        this.shadowSink = shadowSink;
        this.formalSinkBoundary = formalSinkBoundary;
    }
    public WriteMode mode() { return WriteMode.SHADOW_DB; }
    public int write(BehaviorRunManifest manifest, List<BehaviorAuditResult> results) {
        int count = 0;
        for (BehaviorAuditResult result : results) if (shadowSink.upsert(manifest, result)) count++;
        return count;
    }
}
