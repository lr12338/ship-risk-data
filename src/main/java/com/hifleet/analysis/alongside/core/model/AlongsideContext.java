package com.hifleet.analysis.alongside.core.model;

import java.io.Serializable;

public class AlongsideContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean sceneDataAvailable;
    private boolean counterpartyRiskAvailable;
    private SceneAdjustmentResult sceneAdjustment;

    public boolean isSceneDataAvailable() {
        return sceneDataAvailable;
    }

    public void setSceneDataAvailable(boolean sceneDataAvailable) {
        this.sceneDataAvailable = sceneDataAvailable;
    }

    public boolean isCounterpartyRiskAvailable() {
        return counterpartyRiskAvailable;
    }

    public void setCounterpartyRiskAvailable(boolean counterpartyRiskAvailable) {
        this.counterpartyRiskAvailable = counterpartyRiskAvailable;
    }

    public SceneAdjustmentResult getSceneAdjustment() {
        return sceneAdjustment;
    }

    public void setSceneAdjustment(SceneAdjustmentResult sceneAdjustment) {
        this.sceneAdjustment = sceneAdjustment;
    }
}
