package com.hifleet.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AlongsideAsymmetricShadowReportTest {
    @Test
    public void aggregatesDryRunDifferencesWithoutWritingEvents() {
        AlongsideAsymmetricShadowReport report = new AlongsideAsymmetricShadowReport();
        report.record(new AlongsideAsymmetricShadowReport.Record(TargetVesselRole.BUNKER_OR_SUPPLY,
                false, true, false, true, true, false, false, true));
        report.record(new AlongsideAsymmetricShadowReport.Record(TargetVesselRole.TUG_OR_SERVICE,
                true, true, true, true, false, false, true, false));
        report.record(new AlongsideAsymmetricShadowReport.Record(TargetVesselRole.UNKNOWN,
                true, true, false, false, false, true, false, false));

        assertEquals(3, report.getTotal());
        assertEquals(1, report.getNewCandidates());
        assertEquals(1, report.getNewEvents());
        assertEquals(1, report.getLegacyMerchantPairMisses());
        assertEquals(1, report.getTrajectoryQualityRejected());
        assertEquals(1, report.getPortOperationDowngrades());
        assertEquals(1, report.getOffPortReviews());
        assertEquals(1, report.getRoleCount(TargetVesselRole.UNKNOWN));
    }
}
