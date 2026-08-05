package com.hifleet.behaviorplatform;

public interface ShadowBehaviorResultSink {
    boolean upsert(BehaviorRunManifest manifest, BehaviorAuditResult result);
}
