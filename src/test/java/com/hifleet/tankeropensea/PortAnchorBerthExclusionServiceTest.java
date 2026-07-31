package com.hifleet.tankeropensea;

import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class PortAnchorBerthExclusionServiceTest {
    private final PortAnchorBerthExclusionService service =
            new PortAnchorBerthExclusionService();

    @Test
    public void polygonPointAndSegmentHitsAreRejected() {
        assertRejected("PORT", row("PORT", "ports", "1",
                polygon(120.0, 20.0, 120.1, 20.1), false),
                track(120.05, 20.05, 120.06, 20.06));
        assertRejected("ANCHORAGE", row("ANCHORAGE", "anchors", "2",
                polygon(120.0, 20.0, 120.1, 20.1), false),
                track(119.95, 20.05, 120.15, 20.05));
        assertRejected("BERTH", row("BERTH", "berths", "3",
                polygon(120.0, 20.0, 120.1, 20.1), false),
                track(120.05, 20.05, 120.06, 20.06));
    }

    @Test
    public void berthPointUsesHalfNauticalMileBuffer() {
        PortAnchorBerthExclusionService.Snapshot snapshot =
                PortAnchorBerthExclusionService.snapshotForTest(
                        Collections.singletonList(row(
                                "BERTH", "berth_points", "4",
                                "POINT(120 20)", true)));

        assertFalse(service.classify(
                track(120.0, 20.007, 120.0, 20.008), snapshot).isAccepted());
        assertTrue(service.classify(
                track(120.0, 20.010, 120.0, 20.011), snapshot).isAccepted());
    }

    @Test
    public void fingerprintIsDeterministicAcrossInputOrder() {
        PortAnchorBerthExclusionService.SourceRow first =
                row("PORT", "ports", "1",
                        polygon(120.0, 20.0, 120.1, 20.1), false);
        PortAnchorBerthExclusionService.SourceRow second =
                row("ANCHORAGE", "anchors", "2",
                        polygon(121.0, 21.0, 121.1, 21.1), false);

        String ordered = PortAnchorBerthExclusionService.snapshotForTest(
                Arrays.asList(first, second)).getFingerprint();
        String reversed = PortAnchorBerthExclusionService.snapshotForTest(
                Arrays.asList(second, first)).getFingerprint();

        assertEquals(ordered, reversed);
        assertTrue(ordered.matches("^[0-9a-f]{64}$"));
    }

    @Test
    public void antimeridianSegmentDoesNotCrossUnrelatedLongitudes() {
        PortAnchorBerthExclusionService.Snapshot snapshot =
                PortAnchorBerthExclusionService.snapshotForTest(
                        Collections.singletonList(row("PORT", "ports", "5",
                                polygon(-1.0, -1.0, 1.0, 1.0), false)));

        assertTrue(service.classify(
                track(179.9, 0.0, -179.9, 0.0), snapshot).isAccepted());
    }

    @Test(expected = IllegalStateException.class)
    public void expectedFingerprintMismatchFailsClosed() {
        PortAnchorBerthExclusionService.Snapshot snapshot =
                PortAnchorBerthExclusionService.snapshotForTest(
                        Collections.singletonList(row("PORT", "ports", "6",
                                polygon(120.0, 20.0, 120.1, 20.1), false)));
        ReflectionTestUtils.setField(service, "cachedSnapshot", snapshot);

        service.requireReady(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    }

    @Test
    public void selfIntersectingPolygonIsRepairedWithoutDegradingSnapshot() {
        PortAnchorBerthExclusionService.Snapshot snapshot =
                PortAnchorBerthExclusionService.snapshotForTest(
                        Collections.singletonList(row(
                                "PORT", "ports", "7",
                                "POLYGON((120 20,120.1 20.1,120.1 20,"
                                        + "120 20.1,120 20))", false)));

        assertEquals(ExclusionHealth.AVAILABLE, snapshot.getHealth());
        assertEquals(1, snapshot.getRepairedGeometries());
    }

    @Test
    public void namedAlertNonAreaRowsAreFiltered() {
        PortAnchorBerthExclusionService.Snapshot snapshot =
                PortAnchorBerthExclusionService.snapshotForTest(Arrays.asList(
                        row("ANCHORAGE", "alertzones_anchor", "8",
                                polygon(120.0, 20.0, 120.1, 20.1), false, true),
                        row("ANCHORAGE", "alertzones_anchor", "9",
                                "POINT(120 20)", false, true)));

        assertEquals(ExclusionHealth.AVAILABLE, snapshot.getHealth());
        assertEquals(1, snapshot.getFilteredGeometries());
    }

    @Test
    public void unparseableRowDegradesSnapshotWhenSourceStillHasUsableGeometry() {
        PortAnchorBerthExclusionService.Snapshot snapshot =
                PortAnchorBerthExclusionService.snapshotForTest(Arrays.asList(
                        row("PORT", "ports", "10",
                                polygon(120.0, 20.0, 120.1, 20.1), false),
                        row("PORT", "ports", "11", "POLYGON((bad))", false)));

        assertEquals(ExclusionHealth.DEGRADED, snapshot.getHealth());
        assertEquals(1, snapshot.getUnparseableGeometries());
    }

    @Test
    public void unrepairableAreaUsesConservativeQuarantineEnvelope() {
        PortAnchorBerthExclusionService.Snapshot snapshot =
                PortAnchorBerthExclusionService.snapshotForTest(
                        Collections.singletonList(row(
                                "PORT", "ports", "12",
                                "POLYGON((120 20,120 20,120 20,120 20))",
                                false)));

        ExclusionDecision decision = service.classify(
                track(120.0, 20.0, 120.01, 20.01), snapshot);
        assertTrue(decision.isQuarantined());
        assertEquals("GEOMETRY_QUARANTINE_HIT", decision.getReason());
        assertEquals(1, snapshot.getQuarantinedGeometries());
    }

    @Test(expected = IllegalStateException.class)
    public void sourceQueryFailureStopsBeforeSnapshotIsPublished() {
        PortAnchorBerthExclusionService loader =
                new PortAnchorBerthExclusionService();
        ReflectionTestUtils.setField(loader, "eventJdbcTemplate",
                new SourceJdbcTemplate("portsboundary", false));
        ReflectionTestUtils.setField(loader, "aisJdbcTemplate",
                new SourceJdbcTemplate(null, false));

        loader.requireReady(null);
    }

    @Test(expected = IllegalStateException.class)
    public void sourceWithZeroRowsStopsBeforeSnapshotIsPublished() {
        PortAnchorBerthExclusionService loader =
                new PortAnchorBerthExclusionService();
        ReflectionTestUtils.setField(loader, "eventJdbcTemplate",
                new SourceJdbcTemplate(null, true));
        ReflectionTestUtils.setField(loader, "aisJdbcTemplate",
                new SourceJdbcTemplate(null, false));

        loader.requireReady(null);
    }

    private void assertRejected(String type,
                                PortAnchorBerthExclusionService.SourceRow row,
                                java.util.List<TrajectoryPoint> track) {
        ExclusionDecision decision = service.classify(track,
                PortAnchorBerthExclusionService.snapshotForTest(
                        Collections.singletonList(row)));
        assertFalse(decision.isAccepted());
        assertEquals("EXCLUDED_BY_" + type, decision.getReason());
    }

    private PortAnchorBerthExclusionService.SourceRow row(
            String type, String source, String id, String wkt, boolean point) {
        return row(type, source, id, wkt, point, false);
    }

    private PortAnchorBerthExclusionService.SourceRow row(
            String type, String source, String id, String wkt,
            boolean point, boolean filterNonArea) {
        return new PortAnchorBerthExclusionService.SourceRow(
                type, source, id, wkt, point, filterNonArea);
    }

    private java.util.List<TrajectoryPoint> track(double lon1, double lat1,
                                                   double lon2, double lat2) {
        LocalDateTime time = LocalDateTime.of(2026, 1, 1, 0, 0);
        return Arrays.asList(
                new TrajectoryPoint("123456789", time,
                        lon1, lat1, 2.0, 90.0, "under way"),
                new TrajectoryPoint("123456789", time.plusMinutes(5),
                        lon2, lat2, 2.0, 90.0, "under way"));
    }

    private String polygon(double minLon, double minLat,
                           double maxLon, double maxLat) {
        return "POLYGON((" + minLon + " " + minLat + ","
                + maxLon + " " + minLat + ","
                + maxLon + " " + maxLat + ","
                + minLon + " " + maxLat + ","
                + minLon + " " + minLat + "))";
    }

    private static class SourceJdbcTemplate extends JdbcTemplate {
        private final String failSqlPart;
        private final boolean emptyPortsBoundary;

        private SourceJdbcTemplate(String failSqlPart,
                                   boolean emptyPortsBoundary) {
            this.failSqlPart = failSqlPart;
            this.emptyPortsBoundary = emptyPortsBoundary;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
            if (failSqlPart != null && sql.contains(failSqlPart)) {
                throw new IllegalStateException("simulated query failure");
            }
            if (emptyPortsBoundary && sql.contains("portsboundary")) {
                return Collections.emptyList();
            }
            String wkt = sql.contains("berth_with_port_id")
                    && !sql.contains("berth_sts_with_port_id")
                    ? "POINT(120 20)"
                    : "POLYGON((120 20,120.1 20,120.1 20.1,"
                    + "120 20.1,120 20))";
            ResultSet resultSet = (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class[]{ResultSet.class},
                    (proxy, method, args) -> {
                        if ("getString".equals(method.getName())) {
                            return "source_id".equals(args[0]) ? "1" : wkt;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
            try {
                return Collections.singletonList(rowMapper.mapRow(resultSet, 0));
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
