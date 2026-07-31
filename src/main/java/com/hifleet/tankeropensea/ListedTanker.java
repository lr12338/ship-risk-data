package com.hifleet.tankeropensea;

public class ListedTanker {
    private final int seqNo;
    private final ShipMetadata metadata;
    private final ShipEligibilityDecision eligibility;

    public ListedTanker(int seqNo, ShipMetadata metadata,
                        ShipEligibilityDecision eligibility) {
        this.seqNo = seqNo;
        this.metadata = metadata;
        this.eligibility = eligibility;
    }

    public int getSeqNo() { return seqNo; }
    public ShipMetadata getMetadata() { return metadata; }
    public ShipEligibilityDecision getEligibility() { return eligibility; }
}
