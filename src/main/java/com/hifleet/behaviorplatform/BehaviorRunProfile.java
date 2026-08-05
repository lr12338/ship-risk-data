package com.hifleet.behaviorplatform;

import com.alibaba.fastjson.JSONObject;

public final class BehaviorRunProfile {
    private final String profileId;
    private final int version;
    private final BehaviorType behaviorType;
    private final String ruleSetId;
    private final int ruleSetVersion;
    private final String behaviorRuleSetId;
    private final int behaviorRuleSetVersion;
    private final String sceneRuleSetId;
    private final int sceneRuleSetVersion;
    private final SceneMode sceneMode;
    private final WriteMode writeMode;
    private final PrimaryShipTypePolicy primaryShipTypePolicy;
    private final CounterpartyShipTypePolicy counterpartyShipTypePolicy;
    private final UnknownShipTypeAction unknownShipTypeAction;
    private final boolean abComparison;
    private final String profileHash;
    private final String resolvedSnapshot;

    private BehaviorRunProfile(JSONObject value, String resolvedSnapshot, String hash) {
        profileId = value.getString("profileId");
        version = value.getIntValue("version");
        behaviorType = enumValue(BehaviorType.class, value.getString("behaviorType"));
        ruleSetId = value.getString("ruleSetId");
        ruleSetVersion = value.getIntValue("ruleSetVersion");
        behaviorRuleSetId = value.getString("behaviorRuleSetId") == null
                ? ruleSetId : value.getString("behaviorRuleSetId");
        behaviorRuleSetVersion = value.getInteger("behaviorRuleSetVersion") == null
                ? ruleSetVersion : value.getIntValue("behaviorRuleSetVersion");
        sceneRuleSetId = value.getString("sceneRuleSetId") == null
                ? behaviorRuleSetId : value.getString("sceneRuleSetId");
        sceneRuleSetVersion = value.getInteger("sceneRuleSetVersion") == null
                ? behaviorRuleSetVersion : value.getIntValue("sceneRuleSetVersion");
        sceneMode = enumValue(SceneMode.class, value.getString("sceneMode"));
        writeMode = enumValue(WriteMode.class, value.getString("writeMode"));
        primaryShipTypePolicy = enumValue(PrimaryShipTypePolicy.class,
                value.getString("primaryShipTypePolicy"));
        counterpartyShipTypePolicy = enumValue(CounterpartyShipTypePolicy.class,
                value.getString("counterpartyShipTypePolicy"));
        unknownShipTypeAction = value.getString("unknownShipTypeAction") == null
                ? UnknownShipTypeAction.REVIEW_REQUIRED
                : enumValue(UnknownShipTypeAction.class, value.getString("unknownShipTypeAction"));
        abComparison = value.getBooleanValue("abComparison");
        profileHash = hash;
        this.resolvedSnapshot = resolvedSnapshot;
        new BehaviorRunProfileValidator().validate(this);
    }

    static BehaviorRunProfile from(JSONObject value) {
        JSONObject clean = new JSONObject(true);
        clean.putAll(value);
        clean.remove("extendsProfileId");
        String snapshot = BehaviorHashes.canonicalJson(clean);
        return new BehaviorRunProfile(clean, snapshot, BehaviorHashes.sha256(snapshot));
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        if (value == null) return null;
        try { return Enum.valueOf(type, value.trim().toUpperCase()); }
        catch (Exception ex) { throw new IllegalArgumentException("invalid " + type.getSimpleName() + ": " + value); }
    }

    public String getProfileId() { return profileId; }
    public int getVersion() { return version; }
    public BehaviorType getBehaviorType() { return behaviorType; }
    public String getRuleSetId() { return ruleSetId; }
    public int getRuleSetVersion() { return ruleSetVersion; }
    public String getBehaviorRuleSetId() { return behaviorRuleSetId; }
    public int getBehaviorRuleSetVersion() { return behaviorRuleSetVersion; }
    public String getSceneRuleSetId() { return sceneRuleSetId; }
    public int getSceneRuleSetVersion() { return sceneRuleSetVersion; }
    public SceneMode getSceneMode() { return sceneMode; }
    public WriteMode getWriteMode() { return writeMode; }
    public PrimaryShipTypePolicy getPrimaryShipTypePolicy() { return primaryShipTypePolicy; }
    public CounterpartyShipTypePolicy getCounterpartyShipTypePolicy() { return counterpartyShipTypePolicy; }
    public UnknownShipTypeAction getUnknownShipTypeAction() { return unknownShipTypeAction; }
    public boolean isAbComparison() { return abComparison; }
    public String getProfileHash() { return profileHash; }
    public String getResolvedSnapshot() { return resolvedSnapshot; }
}
