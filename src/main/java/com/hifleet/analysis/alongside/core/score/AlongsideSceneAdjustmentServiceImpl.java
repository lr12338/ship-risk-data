package com.hifleet.analysis.alongside.core.score;

import com.hifleet.analysis.alongside.core.model.AlongsideContext;
import com.hifleet.analysis.alongside.core.model.AlongsideDetectionResult;
import com.hifleet.analysis.alongside.core.model.SceneAdjustmentResult;

public class AlongsideSceneAdjustmentServiceImpl implements AlongsideSceneAdjustmentService {

    @Override
    public SceneAdjustmentResult adjust(AlongsideDetectionResult detection, AlongsideContext context) {
        SceneAdjustmentResult result = new SceneAdjustmentResult();
        if (detection != null && detection.getWindow() != null && detection.getWindow().isAnchoredOrMooredSeen()) {
            result.setExclusionFlag("DOWNGRADED_NORMAL_LIKELY");
            result.setExclusionReason("NAV_STATUS_ANCHORED_OR_MOORED");
            result.setScoreMultiplier(0.5d);
        }
        return result;
    }
}
