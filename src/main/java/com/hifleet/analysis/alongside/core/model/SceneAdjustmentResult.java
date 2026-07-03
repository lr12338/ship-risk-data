package com.hifleet.analysis.alongside.core.model;

import java.io.Serializable;

public class SceneAdjustmentResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private double scoreMultiplier = 1.0d;
    private String exclusionFlag = "VALID_RISK_EVENT";
    private String exclusionReason = "NO_SCENE_DATA";

    public double getScoreMultiplier() {
        return scoreMultiplier;
    }

    public void setScoreMultiplier(double scoreMultiplier) {
        this.scoreMultiplier = scoreMultiplier;
    }

    public String getExclusionFlag() {
        return exclusionFlag;
    }

    public void setExclusionFlag(String exclusionFlag) {
        this.exclusionFlag = exclusionFlag;
    }

    public String getExclusionReason() {
        return exclusionReason;
    }

    public void setExclusionReason(String exclusionReason) {
        this.exclusionReason = exclusionReason;
    }
}
