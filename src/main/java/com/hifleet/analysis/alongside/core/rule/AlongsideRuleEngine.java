package com.hifleet.analysis.alongside.core.rule;

import com.hifleet.analysis.alongside.core.model.AlongsideDetectionResult;
import com.hifleet.analysis.alongside.core.model.PairEvidenceWindow;
import com.hifleet.analysis.alongside.core.model.AlongsideRuleConfig;

public interface AlongsideRuleEngine {
    AlongsideDetectionResult evaluate(PairEvidenceWindow window, AlongsideRuleConfig config);
}
