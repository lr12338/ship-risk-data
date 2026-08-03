package com.hifleet.maritime;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class MaritimeSceneServiceTest {
    @Test
    public void defaultServiceIsOffAndDoesNotAffectFormalCalculations() {
        MaritimeSceneService service = new MaritimeSceneService();
        service.initialize();

        assertFalse(service.isEnabled());
        assertEquals("OFF", service.getPolicy().getMode());
        assertEquals(MaritimeSceneClass.SCENE_UNKNOWN,
                service.classify(121.0, 31.0).getSceneClass());
    }

    @Test
    public void shadowDecisionUsesOnlyShadowOutput() {
        MaritimeSceneService service = new MaritimeSceneService();
        service.initialize();
        SceneEpisodeAggregator.Summary summary = service.aggregate(Arrays.asList(
                new MaritimeSceneDecision(MaritimeSceneClass.SCENE_UNKNOWN, SceneConfidence.UNKNOWN,
                        MaritimeSceneEvidence.unavailable("test"), null, null, null, null,
                        MaritimeScenePolicy.VERSION, false)));
        assertEquals("SCENE_UNKNOWN", service.shadowDecision(summary));
    }
}
