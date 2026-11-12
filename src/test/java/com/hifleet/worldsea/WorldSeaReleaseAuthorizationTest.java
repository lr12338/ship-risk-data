package com.hifleet.worldsea;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class WorldSeaReleaseAuthorizationTest {
    @Test
    public void matchingOperatorAuthorizationPasses() {
        WorldSeaReleaseAuthorization.Authorization value = operator();
        WorldSeaReleaseAuthorization.validateOperator(value, "fingerprint");
        assertEquals("OPERATOR_AUTHORIZED", value.auditStatus());
    }

    @Test(expected = IllegalStateException.class)
    public void operatorDatasetMismatchIsRejected() {
        WorldSeaReleaseAuthorization.Authorization value = operator(); value.datasetVersion = "WORLD_SEA_102_V1";
        WorldSeaReleaseAuthorization.validateOperator(value, "fingerprint");
    }

    @Test(expected = IllegalStateException.class)
    public void operatorFingerprintMismatchIsRejected() {
        WorldSeaReleaseAuthorization.validateOperator(operator(), "other");
    }

    @Test(expected = IllegalStateException.class)
    public void operatorIncompleteIdsAreRejected() {
        WorldSeaReleaseAuthorization.Authorization value = operator(); value.reviewedFeatureIds = Arrays.asList(11, 26);
        WorldSeaReleaseAuthorization.validateOperator(value, "fingerprint");
    }

    @Test(expected = IllegalStateException.class)
    public void operatorExtraIdIsRejected() {
        WorldSeaReleaseAuthorization.Authorization value = operator();
        value.reviewedFeatureIds = Arrays.asList(11, 26, 85, 91, 92, 99, 24556, 100000);
        WorldSeaReleaseAuthorization.validateOperator(value, "fingerprint");
    }

    @Test(expected = IllegalStateException.class)
    public void operatorDuplicateIdIsRejected() {
        WorldSeaReleaseAuthorization.Authorization value = operator();
        value.reviewedFeatureIds = Arrays.asList(11, 26, 85, 91, 92, 99, 24556, 24556);
        WorldSeaReleaseAuthorization.validateOperator(value, "fingerprint");
    }

    @Test(expected = IllegalStateException.class)
    public void operatorDecisionMismatchIsRejected() {
        WorldSeaReleaseAuthorization.Authorization value = operator(); value.boHaiTinyComponentDecision = "DROP";
        WorldSeaReleaseAuthorization.validateOperator(value, "fingerprint");
    }

    @Test(expected = IllegalStateException.class)
    public void operatorBaffinDecisionMismatchIsRejected() {
        WorldSeaReleaseAuthorization.Authorization value = operator();
        value.baffinCollapsedLineDecision = "RETAIN";
        WorldSeaReleaseAuthorization.validateOperator(value, "fingerprint");
    }

    @Test(expected = IllegalStateException.class)
    public void blankAuthorizationReferenceIsRejected() {
        WorldSeaReleaseAuthorization.Authorization value = operator(); value.authorizationRef = " ";
        WorldSeaReleaseAuthorization.validateOperator(value, "fingerprint");
    }

    @Test(expected = IllegalStateException.class)
    public void invalidAuthorizationTimeIsRejected() {
        WorldSeaReleaseAuthorization.Authorization value = operator(); value.authorizedAt = "2026-08-07";
        WorldSeaReleaseAuthorization.validateOperator(value, "fingerprint");
    }

    @Test
    public void humanApprovalRemainsCompatible() {
        WorldSeaRepairApproval.Approval approval = new WorldSeaRepairApproval.Approval();
        approval.datasetVersion = "WORLD_SEA_102_V1R1"; approval.repairedGeometryFingerprint = "fingerprint";
        approval.reviewStatus = "APPROVED"; approval.reviewedFeatureIds = Arrays.asList(11, 26, 85, 91, 92, 99, 24556);
        approval.baffinCollapsedLineDecision = "DROP_NON_POLYGONAL_COLLAPSE";
        approval.boHaiTinyComponentDecision = "RETAIN"; approval.reviewer = "human";
        approval.reviewedAt = "2026-08-07T09:00:00+08:00";
        assertEquals("APPROVED", WorldSeaReleaseAuthorization.fromHuman(approval, "fingerprint").auditStatus());
    }

    private WorldSeaReleaseAuthorization.Authorization operator() {
        WorldSeaReleaseAuthorization.Authorization value = new WorldSeaReleaseAuthorization.Authorization();
        value.datasetVersion = "WORLD_SEA_102_V1R1"; value.repairedGeometryFingerprint = "fingerprint";
        value.authorizationMode = "OPERATOR_OVERRIDE";
        value.authorizationRef = "PROJECT_OWNER_EXPLICIT_AUTHORIZATION_2026-08-07_WORLD_SEA_V1R1";
        value.authorizedAt = "2026-08-07T09:00:00+08:00";
        value.reviewedFeatureIds = Arrays.asList(11, 26, 85, 91, 92, 99, 24556);
        value.baffinCollapsedLineDecision = "DROP_NON_POLYGONAL_COLLAPSE";
        value.boHaiTinyComponentDecision = "RETAIN";
        return value;
    }
}
