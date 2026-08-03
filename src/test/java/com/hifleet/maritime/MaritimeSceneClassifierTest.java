package com.hifleet.maritime;

import com.alibaba.fastjson.JSONObject;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class MaritimeSceneClassifierTest {
    private final GeometryFactory factory = new GeometryFactory();
    private final MaritimeSceneClassifier classifier =
            new MaritimeSceneClassifier(MaritimeScenePolicy.defaults("SHADOW"));

    @Test
    public void riverWithStableWaterIsHighAndRiverWithoutWaterIsLow() {
        MaritimeSceneTileLoader.TileRecord land = record("land");
        MaritimeSceneTileLoader.TileRecord river = record("inland_river_corridor");
        MaritimeSceneEvidence stable = evidence(true, false);
        MaritimeSceneEvidence missing = evidence(false, false);

        assertEquals(SceneConfidence.HIGH, classify(Arrays.asList(land, river), stable).getConfidence());
        assertEquals(SceneConfidence.LOW, classify(Arrays.asList(land, river), missing).getConfidence());
        assertEquals(MaritimeSceneClass.INLAND_RIVER,
                classify(Arrays.asList(land, river), stable).getSceneClass());
    }

    @Test
    public void marineAndLandAtBoundaryIsUncertain() {
        MaritimeSceneDecision decision = classify(
                Arrays.asList(record("land"), record("marine_territorial_sea")), evidence(false, false));
        assertEquals(MaritimeSceneClass.BOUNDARY_UNCERTAIN, decision.getSceneClass());
        assertEquals(SceneConfidence.LOW, decision.getConfidence());
    }

    @Test
    public void InlandAndMarineConflictIsolated() {
        MaritimeSceneDecision decision = classify(
                Arrays.asList(record("inland_lake"), record("marine_eez")), evidence(false, false));
        assertEquals(MaritimeSceneClass.SCENE_CONFLICT, decision.getSceneClass());
        assertEquals(SceneConfidence.UNKNOWN, decision.getConfidence());
        assertEquals(true, decision.isConflictFlag());
    }

    @Test
    public void marineClassesAreDistinguished() {
        assertEquals(MaritimeSceneClass.MARITIME_INTERNAL_WATER,
                classify(Collections.singletonList(record("marine_internal_water")), evidence(false, false)).getSceneClass());
        assertEquals(MaritimeSceneClass.OFFSHORE_EEZ,
                classify(Collections.singletonList(record("marine_eez")), evidence(false, false), 10.0).getSceneClass());
        assertEquals(MaritimeSceneClass.HIGH_SEAS,
                classify(Collections.singletonList(record("marine_high_seas")), evidence(false, false), 10.0).getSceneClass());
    }

    private MaritimeSceneDecision classify(
            java.util.List<MaritimeSceneTileLoader.TileRecord> records,
            MaritimeSceneEvidence evidence) {
        return classify(records, evidence, 10.0);
    }

    private MaritimeSceneDecision classify(
            java.util.List<MaritimeSceneTileLoader.TileRecord> records,
            MaritimeSceneEvidence evidence, double coastDistance) {
        return classifier.classify(records, evidence, coastDistance, 10000.0,
                "release", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                MaritimeScenePolicy.VERSION);
    }

    private MaritimeSceneTileLoader.TileRecord record(String type) {
        return new MaritimeSceneTileLoader.TileRecord(
                type, type + "-1", "N31_E121",
                factory.createPoint(new Coordinate(121.0, 31.0)), new JSONObject());
    }

    private MaritimeSceneEvidence evidence(boolean jrcStable, boolean runtimeWaterSupport) {
        return new MaritimeSceneEvidence(
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                true, jrcStable, runtimeWaterSupport, null);
    }
}
