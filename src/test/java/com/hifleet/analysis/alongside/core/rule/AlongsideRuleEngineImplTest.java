package com.hifleet.analysis.alongside.core.rule;

import com.hifleet.analysis.alongside.core.model.AlongsideDetectionResult;
import com.hifleet.analysis.alongside.core.model.AlongsideRuleConfig;
import com.hifleet.analysis.alongside.core.model.PairEvidenceWindow;
import com.hifleet.analysis.alongside.core.model.PairPriority;
import org.junit.Assert;
import org.junit.Test;

public class AlongsideRuleEngineImplTest {

    @Test
    public void shouldConfirmWhenSixPointsThirtyMinutesAndMetricsMatch() {
        AlongsideDetectionResult result = engine().evaluate(window(6, 30L, 0.1d, 0.2d, 6, 6), config());

        Assert.assertTrue(result.isDetected());
        Assert.assertEquals("ALONGSIDE_CONFIRMED_CANDIDATE", result.getEventSubType());
    }

    @Test
    public void shouldEmitWeakCandidateWhenThreePointsTwentyMinutes() {
        AlongsideDetectionResult result = engine().evaluate(window(3, 20L, 0.2d, 0.2d, 3, 3), config());

        Assert.assertTrue(result.isDetected());
        Assert.assertEquals("WEAK_ALONGSIDE_CANDIDATE", result.getEventSubType());
    }

    @Test
    public void shouldRejectDistanceOverThreshold() {
        AlongsideDetectionResult result = engine().evaluate(window(6, 30L, 0.6d, 0.2d, 6, 6), config());

        Assert.assertFalse(result.isDetected());
        Assert.assertEquals("DISTANCE_OVER_CANDIDATE_THRESHOLD", result.getRejectReason());
    }

    @Test
    public void shouldRejectTimeGapOverThreshold() {
        PairEvidenceWindow window = window(6, 30L, 0.1d, 0.2d, 6, 6);
        window.setMaxTimeGapSeconds(601L);

        AlongsideDetectionResult result = engine().evaluate(window, config());

        Assert.assertFalse(result.isDetected());
        Assert.assertEquals("TIME_GAP_OVER_CANDIDATE_THRESHOLD", result.getRejectReason());
    }

    @Test
    public void shouldRejectRelativeSpeedOverThreshold() {
        AlongsideDetectionResult result = engine().evaluate(window(6, 30L, 0.1d, 1.2d, 6, 6), config());

        Assert.assertFalse(result.isDetected());
        Assert.assertEquals("RELATIVE_SPEED_OVER_CONFIRM_THRESHOLD", result.getRejectReason());
    }

    @Test
    public void shouldNotConfirmWhenHeadingIsNotParallel() {
        AlongsideDetectionResult result = engine().evaluate(window(6, 30L, 0.1d, 0.2d, 6, 0), config());

        Assert.assertFalse(result.isDetected());
        Assert.assertEquals("HEADING_NOT_PARALLEL", result.getRejectReason());
    }

    @Test
    public void shouldKeepHighSpeedPairAsWeakInsteadOfConfirmed() {
        PairEvidenceWindow window = window(6, 30L, 0.1d, 0.2d, 6, 6);
        window.setMaxSpeedKnots(3.0d);

        AlongsideDetectionResult result = engine().evaluate(window, config());

        Assert.assertTrue(result.isDetected());
        Assert.assertEquals("WEAK_ALONGSIDE_CANDIDATE", result.getEventSubType());
    }

    private AlongsideRuleEngineImpl engine() {
        return new AlongsideRuleEngineImpl();
    }

    private AlongsideRuleConfig config() {
        AlongsideRuleConfig config = new AlongsideRuleConfig();
        config.setCandidateMaxDistanceNm(0.5d);
        config.setCandidateMaxSpeedKnots(4.0d);
        config.setCandidateMaxTimeGapMinutes(10L);
        config.setCandidateMinDurationMinutes(20L);
        config.setCandidateMinPoints(3);
        config.setConfirmMaxDistanceNm(0.15d);
        config.setConfirmMaxSpeedKnots(2.0d);
        config.setConfirmMaxRelativeSpeedKnots(1.0d);
        config.setConfirmMaxTimeGapMinutes(10L);
        config.setConfirmHighConfidenceTimeGapMinutes(5L);
        config.setConfirmMinDurationMinutes(30L);
        config.setConfirmMinPoints(6);
        config.setHeadingEnabled(true);
        config.setHeadingParallelThresholdDegrees(30.0d);
        return config;
    }

    private PairEvidenceWindow window(int points, long duration, double maxDistance, double relativeSpeed,
                                      int headingKnown, int headingAccepted) {
        PairEvidenceWindow window = new PairEvidenceWindow();
        window.setPairId("100-200");
        window.setPointCount(points);
        window.setDurationMinutes(duration);
        window.setMinDistanceNm(Math.min(0.05d, maxDistance));
        window.setMaxDistanceNm(maxDistance);
        window.setAvgRelativeSpeedKnots(relativeSpeed);
        window.setMaxSpeedKnots(1.2d);
        window.setMaxTimeGapSeconds(60L);
        window.setHeadingKnownCount(headingKnown);
        window.setHeadingAcceptedCount(headingAccepted);
        window.setPairPriority(PairPriority.HIGH);
        return window;
    }
}
