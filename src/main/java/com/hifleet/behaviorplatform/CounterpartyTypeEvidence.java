package com.hifleet.behaviorplatform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CounterpartyTypeEvidence {
    private final String normalizedShipType;
    private final String shipTypeSource;
    private final double shipTypeConfidence;
    private final String counterpartyRole;
    private final List<String> riskContextTags;
    private final double confidenceAdjustment;
    private final double normalOperationLikelihood;
    private final boolean allowed;
    private final boolean reviewRequired;
    private final boolean shadowOnly;

    CounterpartyTypeEvidence(String normalizedShipType, double confidence, List<String> tags,
                             double adjustment, double normalLikelihood, boolean allowed,
                             boolean reviewRequired, boolean shadowOnly) {
        this.normalizedShipType = normalizedShipType;
        this.shipTypeSource = "AIS_STATIC_NORMALIZED";
        this.shipTypeConfidence = confidence;
        this.counterpartyRole = "ALONGSIDE_COUNTERPARTY";
        this.riskContextTags = Collections.unmodifiableList(new ArrayList<String>(tags));
        this.confidenceAdjustment = adjustment;
        this.normalOperationLikelihood = normalLikelihood;
        this.allowed = allowed;
        this.reviewRequired = reviewRequired;
        this.shadowOnly = shadowOnly;
    }
    public String getNormalizedShipType() { return normalizedShipType; }
    public String getShipTypeSource() { return shipTypeSource; }
    public double getShipTypeConfidence() { return shipTypeConfidence; }
    public String getCounterpartyRole() { return counterpartyRole; }
    public List<String> getRiskContextTags() { return riskContextTags; }
    public double getConfidenceAdjustment() { return confidenceAdjustment; }
    public double getNormalOperationLikelihood() { return normalOperationLikelihood; }
    public boolean isAllowed() { return allowed; }
    public boolean isReviewRequired() { return reviewRequired; }
    public boolean isShadowOnly() { return shadowOnly; }
    public boolean isFormalEligible() { return allowed && !reviewRequired && !shadowOnly && !"UNKNOWN".equals(normalizedShipType); }
}
