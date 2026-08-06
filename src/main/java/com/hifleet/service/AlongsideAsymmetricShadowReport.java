package com.hifleet.service;

import java.util.EnumMap;
import java.util.Map;

/** In-memory dry-run comparison only; it has no repository or event writer dependency. */
public class AlongsideAsymmetricShadowReport {
    private int total;
    private int newCandidates;
    private int newEvents;
    private int legacyMerchantPairMisses;
    private int trajectoryQualityRejected;
    private int portOperationDowngrades;
    private int offPortReviews;
    private final Map<TargetVesselRole, Integer> roles = new EnumMap<TargetVesselRole, Integer>(TargetVesselRole.class);

    public void record(Record record) {
        if (record == null) { return; }
        total++;
        increment(record.role == null ? TargetVesselRole.UNKNOWN : record.role);
        if (!record.oldRuleRecalled && record.newRuleRecalled) { newCandidates++; }
        if (!record.oldRuleEvent && record.newRuleEvent) { newEvents++; }
        if (record.legacyMerchantPairMiss) { legacyMerchantPairMisses++; }
        if (record.trajectoryQualityRejected) { trajectoryQualityRejected++; }
        if (record.portOperationDowngrade) { portOperationDowngrades++; }
        if (record.offPortReview) { offPortReviews++; }
    }

    private void increment(TargetVesselRole role) {
        Integer count = roles.get(role);
        roles.put(role, count == null ? 1 : count + 1);
    }

    public int getTotal() { return total; }
    public int getNewCandidates() { return newCandidates; }
    public int getNewEvents() { return newEvents; }
    public int getLegacyMerchantPairMisses() { return legacyMerchantPairMisses; }
    public int getTrajectoryQualityRejected() { return trajectoryQualityRejected; }
    public int getPortOperationDowngrades() { return portOperationDowngrades; }
    public int getOffPortReviews() { return offPortReviews; }
    public int getRoleCount(TargetVesselRole role) { Integer count = roles.get(role); return count == null ? 0 : count; }

    public static class Record {
        private final TargetVesselRole role;
        private final boolean oldRuleRecalled;
        private final boolean newRuleRecalled;
        private final boolean oldRuleEvent;
        private final boolean newRuleEvent;
        private final boolean legacyMerchantPairMiss;
        private final boolean trajectoryQualityRejected;
        private final boolean portOperationDowngrade;
        private final boolean offPortReview;

        public Record(TargetVesselRole role, boolean oldRuleRecalled, boolean newRuleRecalled,
                      boolean oldRuleEvent, boolean newRuleEvent, boolean legacyMerchantPairMiss,
                      boolean trajectoryQualityRejected, boolean portOperationDowngrade, boolean offPortReview) {
            this.role = role;
            this.oldRuleRecalled = oldRuleRecalled;
            this.newRuleRecalled = newRuleRecalled;
            this.oldRuleEvent = oldRuleEvent;
            this.newRuleEvent = newRuleEvent;
            this.legacyMerchantPairMiss = legacyMerchantPairMiss;
            this.trajectoryQualityRejected = trajectoryQualityRejected;
            this.portOperationDowngrade = portOperationDowngrade;
            this.offPortReview = offPortReview;
        }
    }
}
