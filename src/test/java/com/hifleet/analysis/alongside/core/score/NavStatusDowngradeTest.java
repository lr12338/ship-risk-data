package com.hifleet.analysis.alongside.core.score;

import com.hifleet.analysis.alongside.core.model.AlongsideContext;
import com.hifleet.analysis.alongside.core.model.AlongsideDetectionResult;
import com.hifleet.analysis.alongside.core.model.PairEvidenceWindow;
import com.hifleet.analysis.alongside.core.model.SceneAdjustmentResult;
import org.junit.Assert;
import org.junit.Test;

public class NavStatusDowngradeTest {

    @Test
    public void shouldDowngradeAnchoredOrMooredWindow() {
        PairEvidenceWindow window = new PairEvidenceWindow();
        window.setAnchoredOrMooredSeen(true);
        AlongsideDetectionResult detection = new AlongsideDetectionResult();
        detection.setWindow(window);

        SceneAdjustmentResult result = new AlongsideSceneAdjustmentServiceImpl().adjust(detection, new AlongsideContext());

        Assert.assertEquals("DOWNGRADED_NORMAL_LIKELY", result.getExclusionFlag());
        Assert.assertEquals("NAV_STATUS_ANCHORED_OR_MOORED", result.getExclusionReason());
        Assert.assertEquals(0.5d, result.getScoreMultiplier(), 0.001d);
    }
}
