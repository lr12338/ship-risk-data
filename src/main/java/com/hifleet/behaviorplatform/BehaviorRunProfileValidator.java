package com.hifleet.behaviorplatform;

public final class BehaviorRunProfileValidator {
    public void validate(BehaviorRunProfile value) {
        if (value.getProfileId() == null || !value.getProfileId().matches("[a-z0-9][a-z0-9-]*-v[0-9]+"))
            throw new IllegalArgumentException("profileId must end with an explicit -vN version");
        if (value.getVersion() <= 0 || value.getBehaviorType() == null
                || value.getBehaviorRuleSetId() == null || value.getBehaviorRuleSetVersion() <= 0
                || value.getSceneRuleSetId() == null || value.getSceneRuleSetVersion() <= 0
                || value.getSceneMode() == null || value.getWriteMode() == null
                || value.getPrimaryShipTypePolicy() == null
                || value.getCounterpartyShipTypePolicy() == null)
            throw new IllegalArgumentException("profile is incomplete: " + value.getProfileId());
        if (value.getProfileHash() == null || value.getProfileHash().length() != 64)
            throw new IllegalArgumentException("profile hash is invalid");
    }
}
