package com.hifleet.tankeropensea;

public class ShipEligibilityDecision {
    private final StandardShipType standardShipType;
    private final boolean eligible;
    private final String decisionReason;
    private final String metadataQuality;

    private ShipEligibilityDecision(StandardShipType standardShipType, boolean eligible,
                                    String decisionReason, String metadataQuality) {
        this.standardShipType = standardShipType;
        this.eligible = eligible;
        this.decisionReason = decisionReason;
        this.metadataQuality = metadataQuality;
    }

    public static ShipEligibilityDecision eligible(StandardShipType type,
                                                   String reason, String quality) {
        return new ShipEligibilityDecision(type, true, reason, quality);
    }

    public static ShipEligibilityDecision rejected(StandardShipType type,
                                                   String reason, String quality) {
        return new ShipEligibilityDecision(type, false, reason, quality);
    }

    public StandardShipType getStandardShipType() { return standardShipType; }
    public boolean isEligible() { return eligible; }
    public String getDecisionReason() { return decisionReason; }
    public String getMetadataQuality() { return metadataQuality; }
}
