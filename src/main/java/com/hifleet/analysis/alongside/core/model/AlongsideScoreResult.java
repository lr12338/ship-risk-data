package com.hifleet.analysis.alongside.core.model;

import java.io.Serializable;

public class AlongsideScoreResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private double score;
    private String severityLevel;
    private String confidenceLevel;
    private String scoreDetailJson;

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getSeverityLevel() {
        return severityLevel;
    }

    public void setSeverityLevel(String severityLevel) {
        this.severityLevel = severityLevel;
    }

    public String getConfidenceLevel() {
        return confidenceLevel;
    }

    public void setConfidenceLevel(String confidenceLevel) {
        this.confidenceLevel = confidenceLevel;
    }

    public String getScoreDetailJson() {
        return scoreDetailJson;
    }

    public void setScoreDetailJson(String scoreDetailJson) {
        this.scoreDetailJson = scoreDetailJson;
    }
}
