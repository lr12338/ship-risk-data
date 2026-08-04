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

    @Test
    public void mediumConfidenceInlandNeverProducesWouldReject() {
        MaritimeSceneService service = new MaritimeSceneService();
        service.initialize();
        MaritimeSceneDecision mediumInland = new MaritimeSceneDecision(
                MaritimeSceneClass.INLAND_RIVER, SceneConfidence.MEDIUM,
                MaritimeSceneEvidence.unavailable("test"), 2.0, null, null, null,
                MaritimeScenePolicy.VERSION, false);
        SceneEpisodeAggregator.Summary summary = service.aggregate(
                Arrays.asList(mediumInland, mediumInland), Arrays.asList(0L, 7200000L));

        assertEquals("SCENE_UNKNOWN", service.shadowDecision(summary));
    }

    @Test
    public void highConfidenceInlandProducesShadowReferenceOnly() {
        MaritimeScenePolicy policy = new MaritimeScenePolicy(
                "SHADOW", 1000.0, 60.0, 0.90, 60.0, 0.90, 0.05);
        MaritimeSceneService service = new MaritimeSceneService(null, policy);
        MaritimeSceneDecision highInland = new MaritimeSceneDecision(
                MaritimeSceneClass.INLAND_RIVER, SceneConfidence.HIGH,
                MaritimeSceneEvidence.unavailable("test"), 2.0, null, null, null,
                MaritimeScenePolicy.VERSION, false);
        SceneEpisodeAggregator.Summary summary = service.aggregate(
                Arrays.asList(highInland, highInland), Arrays.asList(0L, 7200000L));

        assertEquals("WOULD_REJECT_INLAND", service.shadowDecision(summary));
    }

    @Test
    public void territorialAndBoundaryScenesAlwaysRequireCoastalReview() {
        MaritimeSceneService service = new MaritimeSceneService();
        service.initialize();
        MaritimeSceneDecision territorial = new MaritimeSceneDecision(
                MaritimeSceneClass.TERRITORIAL_SEA, SceneConfidence.MEDIUM,
                MaritimeSceneEvidence.unavailable("test"), 20.0, null, null, null,
                MaritimeScenePolicy.VERSION, false);

        assertEquals("COASTAL_REVIEW", service.shadowDecision(
                service.aggregate(Arrays.asList(territorial, territorial))));
    }

    @Test
    public void highLakeCanalAndOpenWaterEpisodesFollowTheContract() {
        MaritimeScenePolicy policy = MaritimeScenePolicy.defaults("SHADOW");
        MaritimeSceneService service = new MaritimeSceneService(null, policy);

        assertEquals("WOULD_REJECT_INLAND", service.shadowDecision(service.aggregate(
                Arrays.asList(decision(MaritimeSceneClass.INLAND_LAKE, SceneConfidence.HIGH),
                        decision(MaritimeSceneClass.INLAND_LAKE, SceneConfidence.HIGH)),
                Arrays.asList(0L, 3600000L, 7200000L))));
        assertEquals("WOULD_REJECT_INLAND", service.shadowDecision(service.aggregate(
                Arrays.asList(decision(MaritimeSceneClass.MAJOR_CANAL, SceneConfidence.HIGH),
                        decision(MaritimeSceneClass.MAJOR_CANAL, SceneConfidence.HIGH)),
                Arrays.asList(0L, 3600000L, 7200000L))));
        assertEquals("OPEN_WATER_CONFIRMED", service.shadowDecision(service.aggregate(
                Arrays.asList(decision(MaritimeSceneClass.HIGH_SEAS, SceneConfidence.HIGH),
                        decision(MaritimeSceneClass.HIGH_SEAS, SceneConfidence.HIGH)))));
        assertEquals("OPEN_WATER_CONFIRMED", service.shadowDecision(service.aggregate(
                Arrays.asList(decision(MaritimeSceneClass.OFFSHORE_EEZ, SceneConfidence.HIGH),
                        decision(MaritimeSceneClass.OFFSHORE_EEZ, SceneConfidence.HIGH)))));
    }

    private MaritimeSceneDecision decision(MaritimeSceneClass scene, SceneConfidence confidence) {
        return new MaritimeSceneDecision(scene, confidence,
                MaritimeSceneEvidence.unavailable("test"), 20.0, 10000.0,
                null, null, MaritimeScenePolicy.VERSION, false);
    }
}
