package com.hifleet.maritime;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class SceneEpisodeAggregatorTest {
    @Test
    public void aggregatesRatiosTransitionsAndCoastPercentiles() {
        MaritimeSceneEvidence evidence = MaritimeSceneEvidence.unavailable("test");
        MaritimeSceneDecision inland = decision(MaritimeSceneClass.INLAND_RIVER, SceneConfidence.HIGH, 5.0);
        MaritimeSceneDecision openWater = decision(MaritimeSceneClass.HIGH_SEAS, SceneConfidence.HIGH, 20.0);
        SceneEpisodeAggregator.Summary summary = new SceneEpisodeAggregator().aggregate(
                Arrays.asList(inland, inland, openWater), Arrays.asList(0L, 3600000L, 7200000L));

        assertEquals(2.0 / 3.0, summary.getInlandPointRatio(), 0.000001);
        assertEquals(1, summary.getSceneTransitionCount());
        assertEquals(5.0, summary.getCoastDistanceMinNm(), 0.000001);
        assertEquals(20.0, summary.getCoastDistanceMaxNm(), 0.000001);
        assertEquals(120.0, summary.getInlandDurationMinutes(), 0.000001);
        assertEquals(MaritimeSceneClass.INLAND_RIVER, summary.getDominantScene());
        if (evidence == null) throw new AssertionError("unreachable");
    }

    @Test
    public void emptyEpisodeIsUnknown() {
        SceneEpisodeAggregator.Summary summary = new SceneEpisodeAggregator().aggregate(
                Collections.<MaritimeSceneDecision>emptyList());
        assertEquals(MaritimeSceneClass.SCENE_UNKNOWN, summary.getDominantScene());
        assertEquals(0, summary.getPointCount());
    }

    private MaritimeSceneDecision decision(
            MaritimeSceneClass scene, SceneConfidence confidence, double coastDistance) {
        return new MaritimeSceneDecision(scene, confidence,
                new MaritimeSceneEvidence(Collections.<String>emptyList(),
                        Collections.<String>emptyList(), true, false, false, null),
                coastDistance, null, "release", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                MaritimeScenePolicy.VERSION, scene == MaritimeSceneClass.SCENE_CONFLICT);
    }
}
