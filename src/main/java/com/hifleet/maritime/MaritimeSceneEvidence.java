package com.hifleet.maritime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MaritimeSceneEvidence {
    private final List<String> matchedSceneTypes;
    private final List<String> sourceFeatureIds;
    private final boolean snapshotAvailable;
    private final boolean jrcStableWater;
    private final boolean runtimeWaterSupport;
    private final Map<String, Object> attributes;

    public MaritimeSceneEvidence(
            List<String> matchedSceneTypes,
            List<String> sourceFeatureIds,
            boolean snapshotAvailable,
            boolean jrcStableWater,
            boolean runtimeWaterSupport,
            Map<String, Object> attributes) {
        this.matchedSceneTypes = immutable(matchedSceneTypes);
        this.sourceFeatureIds = immutable(sourceFeatureIds);
        this.snapshotAvailable = snapshotAvailable;
        this.jrcStableWater = jrcStableWater;
        this.runtimeWaterSupport = runtimeWaterSupport;
        this.attributes = Collections.unmodifiableMap(
                new LinkedHashMap<String, Object>(attributes == null
                        ? Collections.<String, Object>emptyMap() : attributes));
    }

    public static MaritimeSceneEvidence unavailable(String reason) {
        Map<String, Object> attributes = new LinkedHashMap<String, Object>();
        attributes.put("reason", reason);
        return new MaritimeSceneEvidence(
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                false, false, false, attributes);
    }

    private static List<String> immutable(List<String> values) {
        return Collections.unmodifiableList(new ArrayList<String>(values == null
                ? Collections.<String>emptyList() : values));
    }

    public List<String> getMatchedSceneTypes() { return matchedSceneTypes; }
    public List<String> getSourceFeatureIds() { return sourceFeatureIds; }
    public boolean isSnapshotAvailable() { return snapshotAvailable; }
    public boolean isJrcStableWater() { return jrcStableWater; }
    public boolean isRuntimeWaterSupport() { return runtimeWaterSupport; }
    public Map<String, Object> getAttributes() { return attributes; }
}
