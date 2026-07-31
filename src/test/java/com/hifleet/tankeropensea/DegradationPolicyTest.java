package com.hifleet.tankeropensea;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DegradationPolicyTest {
    @Test
    public void availableSnapshotOutsideQuarantineStaysFormal() {
        PortAnchorBerthExclusionService.Snapshot snapshot = available();

        assertNull(DegradationPolicy.reason(snapshot,
                ExclusionDecision.accepted(snapshot.getFingerprint(),
                        snapshot.getSourceCounts())));
    }

    @Test
    public void quarantineHitRoutesCandidateToReview() {
        PortAnchorBerthExclusionService.Snapshot snapshot = available();

        assertEquals("GEOMETRY_QUARANTINE_HIT",
                DegradationPolicy.reason(snapshot,
                        ExclusionDecision.quarantined("PORT", "ports", "1",
                                snapshot.getFingerprint(),
                                snapshot.getSourceCounts())));
    }

    @Test
    public void degradedSnapshotRoutesEveryOtherwiseAcceptedCandidateToReview() {
        PortAnchorBerthExclusionService.Snapshot snapshot =
                PortAnchorBerthExclusionService.snapshotForTest(Arrays.asList(
                        new PortAnchorBerthExclusionService.SourceRow(
                                "PORT", "ports", "1",
                                "POLYGON((130 30,131 30,131 31,130 31,130 30))",
                                false),
                        new PortAnchorBerthExclusionService.SourceRow(
                                "PORT", "ports", "2", "POLYGON((bad))", false)));

        assertEquals("EXCLUSION_SNAPSHOT_DEGRADED",
                DegradationPolicy.reason(snapshot,
                        ExclusionDecision.accepted(snapshot.getFingerprint(),
                                snapshot.getSourceCounts())));
    }

    private PortAnchorBerthExclusionService.Snapshot available() {
        return PortAnchorBerthExclusionService.snapshotForTest(
                Collections.singletonList(
                        new PortAnchorBerthExclusionService.SourceRow(
                                "PORT", "ports", "1",
                                "POLYGON((130 30,131 30,131 31,130 31,130 30))",
                                false)));
    }
}
