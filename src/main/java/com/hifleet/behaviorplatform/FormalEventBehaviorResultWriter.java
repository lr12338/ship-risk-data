package com.hifleet.behaviorplatform;

import java.util.List;

public final class FormalEventBehaviorResultWriter implements BehaviorResultWriter {
    private final FormalBehaviorEventSink sink;
    private final BehaviorWriteGate gate;
    public FormalEventBehaviorResultWriter(FormalBehaviorEventSink sink, BehaviorWriteGate gate) {
        this.sink = sink;
        this.gate = gate;
    }
    public WriteMode mode() { return WriteMode.FORMAL_DB; }
    public int write(BehaviorRunManifest manifest, List<BehaviorAuditResult> results) {
        BehaviorWriteGate.requireAllowed(WriteMode.FORMAL_DB, gate);
        int count = 0;
        for (BehaviorAuditResult result : results) if (sink.store(result)) count++;
        return count;
    }
}
