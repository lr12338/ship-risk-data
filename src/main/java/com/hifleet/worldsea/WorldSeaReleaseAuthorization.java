package com.hifleet.worldsea;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;

/** Strict release gate supporting human review and an explicit project-owner override. */
final class WorldSeaReleaseAuthorization {
    static Authorization read(File humanApproval, File operatorAuthorization,
                              String expectedFingerprint) throws Exception {
        if (operatorAuthorization != null && operatorAuthorization.isFile()) {
            Authorization value = new ObjectMapper().readValue(operatorAuthorization, Authorization.class);
            validateOperator(value, expectedFingerprint);
            return value;
        }
        return fromHuman(WorldSeaRepairApproval.readApproved(humanApproval, expectedFingerprint),
                expectedFingerprint);
    }

    static void validateOperator(Authorization value, String expectedFingerprint) {
        if (value == null || !"WORLD_SEA_102_V1R1".equals(value.datasetVersion)) {
            throw new IllegalStateException("authorization datasetVersion mismatch");
        }
        if (!"OPERATOR_OVERRIDE".equals(value.authorizationMode)) {
            throw new IllegalStateException("authorizationMode must be OPERATOR_OVERRIDE");
        }
        if (blank(value.authorizationRef)) throw new IllegalStateException("authorizationRef is required");
        try { OffsetDateTime.parse(value.authorizedAt); }
        catch (Exception ex) { throw new IllegalStateException("authorizedAt must be ISO-8601 with an offset", ex); }
        validateIdentity(value.repairedGeometryFingerprint, expectedFingerprint,
                value.reviewedFeatureIds, value.baffinCollapsedLineDecision, value.boHaiTinyComponentDecision);
    }

    static Authorization fromHuman(WorldSeaRepairApproval.Approval approval,
                                   String expectedFingerprint) {
        WorldSeaRepairApproval.validate(approval, expectedFingerprint);
        Authorization value = new Authorization();
        value.datasetVersion = approval.datasetVersion;
        value.repairedGeometryFingerprint = approval.repairedGeometryFingerprint;
        value.reviewedFeatureIds = approval.reviewedFeatureIds;
        value.baffinCollapsedLineDecision = approval.baffinCollapsedLineDecision;
        value.boHaiTinyComponentDecision = approval.boHaiTinyComponentDecision;
        value.authorizationMode = "HUMAN_REVIEW";
        value.authorizationRef = approval.reviewer;
        value.authorizedAt = approval.reviewedAt;
        value.reviewStatus = "APPROVED";
        value.reviewer = approval.reviewer;
        value.comments = approval.comments;
        return value;
    }

    private static void validateIdentity(String fingerprint, String expectedFingerprint,
                                         List<Integer> ids, String baffinDecision, String boHaiDecision) {
        if (blank(fingerprint) || !fingerprint.equalsIgnoreCase(expectedFingerprint)) {
            throw new IllegalStateException("authorization fingerprint differs from repaired stage");
        }
        if (ids == null || ids.size() != WorldSeaGeometryRepair.REPAIR_IDS.size()
                || !new LinkedHashSet<Integer>(ids).equals(WorldSeaGeometryRepair.REPAIR_IDS)) {
            throw new IllegalStateException("authorization feature IDs must exactly match repair allowlist");
        }
        if (!"DROP_NON_POLYGONAL_COLLAPSE".equals(baffinDecision)) {
            throw new IllegalStateException("Baffin Bay collapse decision mismatch");
        }
        if (!"RETAIN".equals(boHaiDecision)) {
            throw new IllegalStateException("Bo Hai tiny component must be retained for V1R1");
        }
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    static final class Authorization {
        public String datasetVersion;
        public String repairedGeometryFingerprint;
        public String authorizationMode;
        public String authorizationRef;
        public String authorizedAt;
        public List<Integer> reviewedFeatureIds;
        public String baffinCollapsedLineDecision;
        public String boHaiTinyComponentDecision;
        public String reviewStatus;
        public String reviewer;
        public String comments;
        public Authorization() {}
        String auditStatus() { return "OPERATOR_OVERRIDE".equals(authorizationMode)
                ? "OPERATOR_AUTHORIZED" : "APPROVED"; }
    }

    private WorldSeaReleaseAuthorization() {}
}
