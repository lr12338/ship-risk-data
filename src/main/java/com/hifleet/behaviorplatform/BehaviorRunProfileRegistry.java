package com.hifleet.behaviorplatform;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BehaviorRunProfileRegistry {
    private final Map<String, BehaviorRunProfile> values = new LinkedHashMap<String, BehaviorRunProfile>();

    public void register(BehaviorRunProfile profile) {
        if (values.containsKey(profile.getProfileId()))
            throw new IllegalArgumentException("duplicate profileId: " + profile.getProfileId());
        values.put(profile.getProfileId(), profile);
    }

    public BehaviorRunProfile require(String profileId) {
        BehaviorRunProfile value = values.get(profileId);
        if (value == null) throw new IllegalArgumentException("unknown behavior profile: " + profileId);
        return value;
    }

    public Collection<BehaviorRunProfile> all() {
        return Collections.unmodifiableCollection(values.values());
    }
}
