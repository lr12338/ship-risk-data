package com.hifleet.analysis.alongside.core.rule;

import com.hifleet.analysis.alongside.core.model.AlongsideDetectionResult;
import com.hifleet.analysis.alongside.core.model.AlongsideRuleConfig;
import com.hifleet.analysis.alongside.core.model.PairEvidenceWindow;
import com.hifleet.analysis.alongside.core.model.PairPriority;

public class AlongsideRuleEngineImpl implements AlongsideRuleEngine {

    public static final String SUB_TYPE_CONFIRMED = "ALONGSIDE_CONFIRMED_CANDIDATE";
    public static final String SUB_TYPE_WEAK = "WEAK_ALONGSIDE_CANDIDATE";
    public static final String SUB_TYPE_UPDATE = "ALONGSIDE_UPDATE";
    public static final String SUB_TYPE_END = "ALONGSIDE_END";

    @Override
    public AlongsideDetectionResult evaluate(PairEvidenceWindow window, AlongsideRuleConfig config) {
        AlongsideDetectionResult result = new AlongsideDetectionResult();
        result.setWindow(window);
        if (window == null) {
            return reject(result, "EMPTY_WINDOW");
        }
        if (window.getPairPriority() == PairPriority.EXCLUDE) {
            return reject(result, "PAIR_PRIORITY_EXCLUDE");
        }
        if (window.getMaxDistanceNm() > config.getCandidateMaxDistanceNm()) {
            return reject(result, "DISTANCE_OVER_CANDIDATE_THRESHOLD");
        }
        if (window.getMaxTimeGapSeconds() > config.getCandidateMaxTimeGapMinutes() * 60L) {
            return reject(result, "TIME_GAP_OVER_CANDIDATE_THRESHOLD");
        }
        if (window.getAvgRelativeSpeedKnots() > config.getConfirmMaxRelativeSpeedKnots()) {
            return reject(result, "RELATIVE_SPEED_OVER_CONFIRM_THRESHOLD");
        }
        if (config.isHeadingEnabled() && window.getHeadingKnownCount() > 0
            && headingParallelRatio(window) <= 0.0d) {
            return reject(result, "HEADING_NOT_PARALLEL");
        }

        boolean weakReady = window.getDurationMinutes() >= config.getCandidateMinDurationMinutes()
            && window.getPointCount() >= config.getCandidateMinPoints();
        if (!weakReady) {
            return reject(result, "INSUFFICIENT_DURATION_OR_POINTS");
        }

        boolean confirmed = window.getDurationMinutes() >= config.getConfirmMinDurationMinutes()
            && window.getPointCount() >= config.getConfirmMinPoints()
            && window.getMaxDistanceNm() <= config.getConfirmMaxDistanceNm()
            && window.getMaxSpeedKnots() <= config.getConfirmMaxSpeedKnots()
            && window.getAvgRelativeSpeedKnots() <= config.getConfirmMaxRelativeSpeedKnots()
            && (!config.isHeadingEnabled() || window.getHeadingKnownCount() == 0
                || headingParallelRatio(window) >= 0.8d);
        result.setDetected(true);
        result.setEventSubType(confirmed ? SUB_TYPE_CONFIRMED : SUB_TYPE_WEAK);
        return result;
    }

    private AlongsideDetectionResult reject(AlongsideDetectionResult result, String reason) {
        result.setDetected(false);
        result.setRejectReason(reason);
        return result;
    }

    static double headingParallelRatio(PairEvidenceWindow window) {
        if (window.getHeadingKnownCount() == 0) {
            return -1.0d;
        }
        return window.getHeadingAcceptedCount() / (double) window.getHeadingKnownCount();
    }
}
