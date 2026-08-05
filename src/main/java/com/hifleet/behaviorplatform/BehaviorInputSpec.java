package com.hifleet.behaviorplatform;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BehaviorInputSpec {
    private final BehaviorTargetSourceType sourceType;
    private final String snapshot;
    private final String fingerprint;
    private BehaviorInputSpec(BehaviorTargetSourceType sourceType, String snapshot) {
        this.sourceType = sourceType; this.snapshot = snapshot;
        this.fingerprint = BehaviorHashes.sha256(snapshot);
    }
    public static BehaviorInputSpec from(BehaviorJobEnvelope envelope, RegionDefinition region) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("sourceType", envelope.getSourceType().name());
        if (envelope.getSourceType() == BehaviorTargetSourceType.SHIPS) value.put("ships", envelope.getShips());
        if (envelope.getSourceType() == BehaviorTargetSourceType.LIST) {
            value.put("listBatchNo", envelope.getListBatchNo()); value.put("startSeq", envelope.getStartSeq());
        }
        if (region != null) {
            value.put("regionId", region.getRegionId()); value.put("regionVersion", region.getVersion());
            value.put("regionFingerprint", region.getFingerprint());
        }
        return new BehaviorInputSpec(envelope.getSourceType(), BehaviorHashes.canonicalJson(value));
    }
    public BehaviorTargetSourceType getSourceType() { return sourceType; }
    public String getSnapshot() { return snapshot; }
    public String getFingerprint() { return fingerprint; }
}
