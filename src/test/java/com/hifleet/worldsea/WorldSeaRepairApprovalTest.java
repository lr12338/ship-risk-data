package com.hifleet.worldsea;

import org.junit.Test;

import java.util.Arrays;

public class WorldSeaRepairApprovalTest {
    @Test(expected = IllegalStateException.class)
    public void pendingApprovalIsRejected() {
        WorldSeaRepairApproval.validate(approval("PENDING_REVIEW", "fingerprint", "reviewer", "2026-08-06T10:00:00Z"),
                "fingerprint");
    }

    @Test(expected = IllegalStateException.class)
    public void differentFingerprintIsRejected() {
        WorldSeaRepairApproval.validate(approval("APPROVED", "wrong", "reviewer", "2026-08-06T10:00:00Z"),
                "fingerprint");
    }

    @Test(expected = IllegalStateException.class)
    public void incompleteIdsAreRejected() {
        WorldSeaRepairApproval.Approval value = approval("APPROVED", "fingerprint", "reviewer", "2026-08-06T10:00:00Z");
        value.reviewedFeatureIds = Arrays.asList(11, 26);
        WorldSeaRepairApproval.validate(value, "fingerprint");
    }

    @Test(expected = IllegalStateException.class)
    public void missingReviewerIsRejected() {
        WorldSeaRepairApproval.validate(approval("APPROVED", "fingerprint", null, "2026-08-06T10:00:00Z"),
                "fingerprint");
    }

    @Test
    public void approvedMatchingCompleteReviewPasses() {
        WorldSeaRepairApproval.validate(approval("APPROVED", "fingerprint", "reviewer", "2026-08-06T10:00:00Z"),
                "fingerprint");
    }

    private WorldSeaRepairApproval.Approval approval(String status, String fingerprint,
                                                       String reviewer, String reviewedAt) {
        WorldSeaRepairApproval.Approval value = new WorldSeaRepairApproval.Approval();
        value.datasetVersion = "WORLD_SEA_102_V1R1";
        value.repairedGeometryFingerprint = fingerprint;
        value.reviewStatus = status;
        value.reviewedFeatureIds = Arrays.asList(11, 26, 85, 91, 92, 99, 24556);
        value.baffinCollapsedLineDecision = "DROP_NON_POLYGONAL_COLLAPSE";
        value.boHaiTinyComponentDecision = "RETAIN";
        value.reviewer = reviewer;
        value.reviewedAt = reviewedAt;
        return value;
    }
}
