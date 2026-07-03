package com.hifleet.analysis.alongside.core.score;

import com.hifleet.analysis.alongside.core.model.AlongsideContext;
import com.hifleet.analysis.alongside.core.model.AlongsideDetectionResult;
import com.hifleet.analysis.alongside.core.model.SceneAdjustmentResult;

public interface AlongsideSceneAdjustmentService {
    SceneAdjustmentResult adjust(AlongsideDetectionResult detection, AlongsideContext context);
}
