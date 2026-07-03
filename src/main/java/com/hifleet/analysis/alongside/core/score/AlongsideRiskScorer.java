package com.hifleet.analysis.alongside.core.score;

import com.hifleet.analysis.alongside.core.model.AlongsideContext;
import com.hifleet.analysis.alongside.core.model.AlongsideDetectionResult;
import com.hifleet.analysis.alongside.core.model.AlongsideScoreResult;

public interface AlongsideRiskScorer {
    AlongsideScoreResult score(AlongsideDetectionResult detection, AlongsideContext context);
}
