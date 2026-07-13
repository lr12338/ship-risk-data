package com.hifleet.analysis.alongside.core.score;

import com.hifleet.analysis.alongside.core.model.AlongsideContext;
import com.hifleet.analysis.alongside.core.model.AlongsideDetectionResult;
import com.hifleet.analysis.alongside.core.model.AlongsideScoreResult;
import com.hifleet.analysis.alongside.core.model.PairEvidenceWindow;
import com.hifleet.analysis.alongside.core.model.SceneAdjustmentResult;
import com.hifleet.analysis.alongside.core.rule.AlongsideRuleEngineImpl;

public class AlongsideRiskScorerImpl implements AlongsideRiskScorer {

    private final String noSceneDataMaxLevel;
    private final boolean autoL4Enabled;

    public AlongsideRiskScorerImpl(String noSceneDataMaxLevel, boolean autoL4Enabled) {
        this.noSceneDataMaxLevel = noSceneDataMaxLevel == null ? "L2" : noSceneDataMaxLevel;
        this.autoL4Enabled = autoL4Enabled;
    }

    @Override
    public AlongsideScoreResult score(AlongsideDetectionResult detection, AlongsideContext context) {
        PairEvidenceWindow window = detection.getWindow();
        double headingRatio = headingRatio(window);
        String confidence = confidence(window, headingRatio);
        boolean weak = AlongsideRuleEngineImpl.SUB_TYPE_WEAK.equals(detection.getEventSubType());
        double score = 0.0d;
        score += window.getMinDistanceNm() <= 0.1d ? 6.0d : 4.0d;
        score += window.getDurationMinutes() >= 30L ? 5.0d : 3.0d;
        score += window.getPointCount() >= 6 ? 5.0d : 3.0d;
        score += window.getHighTimeGapPointCount() >= Math.max(1, window.getPointCount() * 0.8d) ? 4.0d : 2.0d;
        score += window.getAvgRelativeSpeedKnots() <= 1.0d ? 3.0d : 0.0d;
        score += headingRatio >= 0.8d ? 2.0d : 0.0d;
        if (window.getAisGapCount() > 0) {
            score += 10.0d;
        }
        if (window.getDurationMinutes() >= 360L) {
            score += 10.0d;
        } else if (window.getDurationMinutes() >= 120L) {
            score += 6.0d;
        } else if (window.getDurationMinutes() >= 30L) {
            score += 3.0d;
        }
        SceneAdjustmentResult adjustment = context == null ? null : context.getSceneAdjustment();
        if (adjustment != null) {
            score *= adjustment.getScoreMultiplier();
        }
        if ("LOW".equals(confidence)) {
            score *= 0.6d;
        }
        if (weak) {
            score = Math.min(score, 20.0d);
        }
        score = Math.max(0.0d, Math.min(100.0d, score));
        String severity = weak ? "L1" : severity(score);
        if (context == null || (!context.isSceneDataAvailable() && !context.isCounterpartyRiskAvailable())) {
            severity = cap(severity, noSceneDataMaxLevel);
        }
        if (!autoL4Enabled && "L4".equals(severity)) {
            severity = "L3";
        }
        AlongsideScoreResult result = new AlongsideScoreResult();
        result.setScore(score);
        result.setSeverityLevel(severity);
        result.setConfidenceLevel(confidence);
        result.setScoreDetailJson("{\"track_coverage_ratio\":" + window.getTrackCoverageRatio()
            + ",\"heading_parallel_ratio\":" + headingRatio
            + ",\"ais_gap_count\":" + window.getAisGapCount()
            + ",\"both_stationary_ratio\":" + window.getBothStationaryRatio()
            + ",\"anchored_or_moored_ratio\":" + window.getAnchoredOrMooredRatio()
            + ",\"max_nearby_stationary_vessel_count\":" + window.getMaxNearbyStationaryVesselCount()
            + "}");
        return result;
    }

    private String confidence(PairEvidenceWindow window, double headingRatio) {
        if (window.getTrackCoverageRatio() < 0.6d || window.getPointCount() < 3) {
            return "LOW";
        }
        boolean headingOk = headingRatio < 0.0d || headingRatio >= 0.8d;
        if (window.getPointCount() >= 6 && window.getTrackCoverageRatio() >= 0.8d
            && window.getAisGapCount() == 0 && headingOk) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private double headingRatio(PairEvidenceWindow window) {
        if (window.getHeadingKnownCount() == 0) {
            return -1.0d;
        }
        return window.getHeadingAcceptedCount() / (double) window.getHeadingKnownCount();
    }

    private String severity(double score) {
        if (score <= 0.0d) {
            return "L0";
        }
        if (score <= 25.0d) {
            return "L1";
        }
        if (score <= 50.0d) {
            return "L2";
        }
        if (score <= 75.0d) {
            return "L3";
        }
        return "L4";
    }

    private String cap(String severity, String cap) {
        return rank(severity) > rank(cap) ? cap : severity;
    }

    private int rank(String severity) {
        if ("L4".equals(severity)) {
            return 4;
        }
        if ("L3".equals(severity)) {
            return 3;
        }
        if ("L2".equals(severity)) {
            return 2;
        }
        if ("L1".equals(severity)) {
            return 1;
        }
        return 0;
    }
}
