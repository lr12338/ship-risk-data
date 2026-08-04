package com.hifleet.maritime;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TimedSceneEpisodeAggregatorTest {
    @Test
    public void twoHourInlandTrackHasTimeWeightedDuration() {
        MaritimeSceneDecision inland = decision(MaritimeSceneClass.INLAND_RIVER, SceneConfidence.HIGH, false);
        java.util.List<TimedSceneObservation> observations =
                new java.util.ArrayList<TimedSceneObservation>();
        for (int minute = 0; minute <= 120; minute += 10) {
            observations.add(new TimedSceneObservation(minute * 60_000L, inland));
        }
        SceneEpisodeAggregator.Summary summary = new SceneEpisodeAggregator(10)
                .aggregateTimed(observations);

        assertEquals(1.0, summary.getHighConfidenceInlandTimeRatio(), 0.0);
        assertTrue(summary.getInlandDurationMinutes() >= 60.0);
        assertEquals(120.0, summary.getEventSpanMinutes(), 0.0);
        assertEquals(120.0, summary.getObservedDurationMinutes(), 0.0);
        assertEquals(0, summary.getLargeGapCount());
        assertEquals(10.0, summary.getMaxGapMinutes(), 0.0);
        assertEquals(1.0, summary.getTemporalCoverageRatio(), 0.0);
    }

    @Test
    public void largeGapIsCappedAndLowersCoverage() {
        MaritimeSceneDecision inland = decision(MaritimeSceneClass.INLAND_RIVER, SceneConfidence.HIGH, false);
        SceneEpisodeAggregator.Summary summary = new SceneEpisodeAggregator(10)
                .aggregateTimed(Arrays.asList(new TimedSceneObservation(0L, inland),
                        new TimedSceneObservation(60L * 60_000L, inland)));

        assertEquals(10.0, summary.getObservedDurationMinutes(), 0.0);
        assertEquals(60.0, summary.getEventSpanMinutes(), 0.0);
        assertEquals(1, summary.getLargeGapCount());
        assertEquals(1.0 / 6.0, summary.getTemporalCoverageRatio(), 0.0001);
    }

    @Test
    public void longLowFrequencySegmentOutweighsHighFrequencyShortSegment() {
        MaritimeSceneDecision open = decision(MaritimeSceneClass.HIGH_SEAS, SceneConfidence.HIGH, false);
        MaritimeSceneDecision inland = decision(MaritimeSceneClass.INLAND_RIVER, SceneConfidence.HIGH, false);
        SceneEpisodeAggregator.Summary summary = new SceneEpisodeAggregator(10)
                .aggregateTimed(Arrays.asList(
                        new TimedSceneObservation(0L, open),
                        new TimedSceneObservation(60_000L, open),
                        new TimedSceneObservation(2L * 60_000L, open),
                        new TimedSceneObservation(3L * 60_000L, open),
                        new TimedSceneObservation(4L * 60_000L, inland),
                        new TimedSceneObservation(14L * 60_000L, inland),
                        new TimedSceneObservation(24L * 60_000L, inland)));

        assertTrue(summary.getHighConfidenceInlandPointRatio()
                < summary.getHighConfidenceOpenWaterPointRatio());
        assertTrue(summary.getHighConfidenceInlandTimeRatio()
                > summary.getHighConfidenceOpenWaterTimeRatio());
    }

    private MaritimeSceneDecision decision(MaritimeSceneClass scene,
                                            SceneConfidence confidence,
                                            boolean conflict) {
        return new MaritimeSceneDecision(scene, confidence,
                MaritimeSceneEvidence.unavailable("test"), 20.0, null,
                "phase1-global-base-v1", "test-fingerprint",
                MaritimeScenePolicy.VERSION, conflict);
    }
}
