package com.hifleet.analysis.alongside.core.model;

import java.io.Serializable;

public class AlongsideDetectionResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean detected;
    private String eventSubType;
    private String rejectReason;
    private PairEvidenceWindow window;

    public boolean isDetected() {
        return detected;
    }

    public void setDetected(boolean detected) {
        this.detected = detected;
    }

    public String getEventSubType() {
        return eventSubType;
    }

    public void setEventSubType(String eventSubType) {
        this.eventSubType = eventSubType;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public PairEvidenceWindow getWindow() {
        return window;
    }

    public void setWindow(PairEvidenceWindow window) {
        this.window = window;
    }
}
