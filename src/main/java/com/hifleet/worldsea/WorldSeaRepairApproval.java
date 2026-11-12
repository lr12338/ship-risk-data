package com.hifleet.worldsea;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.List;
import java.util.LinkedHashSet;

/** Parser and strict manual-review gate shared by repair and release commands. */
final class WorldSeaRepairApproval {
    static Approval readApproved(File file, String expectedFingerprint) throws Exception {
        if (file == null || !file.isFile()) throw new IllegalStateException("repair approval file is missing");
        Approval value = new ObjectMapper().readValue(file, Approval.class);
        validate(value, expectedFingerprint);
        return value;
    }

    static void validate(Approval value, String expectedFingerprint) {
        if (value == null || !"WORLD_SEA_102_V1R1".equals(value.datasetVersion)) {
            throw new IllegalStateException("approval datasetVersion mismatch");
        }
        if (!"APPROVED".equals(value.reviewStatus)) throw new IllegalStateException("repair review is not APPROVED");
        if (blank(value.reviewer) || blank(value.reviewedAt)) throw new IllegalStateException("approval reviewer/reviewedAt is required");
        if (blank(value.repairedGeometryFingerprint)
                || !value.repairedGeometryFingerprint.equalsIgnoreCase(expectedFingerprint)) {
            throw new IllegalStateException("approval fingerprint differs from repaired stage");
        }
        if (value.reviewedFeatureIds == null || !new LinkedHashSet<Integer>(value.reviewedFeatureIds)
                .equals(WorldSeaGeometryRepair.REPAIR_IDS)) {
            throw new IllegalStateException("approval reviewedFeatureIds must exactly match repair allowlist");
        }
        if (!"DROP_NON_POLYGONAL_COLLAPSE".equals(value.baffinCollapsedLineDecision)) {
            throw new IllegalStateException("Baffin Bay collapse decision mismatch");
        }
        if (!"RETAIN".equals(value.boHaiTinyComponentDecision)) {
            throw new IllegalStateException("Bo Hai tiny component must be retained for V1R1");
        }
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    static final class Approval {
        public String datasetVersion;
        public String repairedGeometryFingerprint;
        public String reviewStatus;
        public List<Integer> reviewedFeatureIds;
        public String baffinCollapsedLineDecision;
        public String boHaiTinyComponentDecision;
        public String reviewer;
        public String reviewedAt;
        public String comments;
        public Approval() {}
    }

    private WorldSeaRepairApproval() {}
}
