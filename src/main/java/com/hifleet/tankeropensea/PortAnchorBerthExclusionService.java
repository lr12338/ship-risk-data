package com.hifleet.tankeropensea;

import com.alibaba.fastjson.JSON;
import com.hifleet.trajectory.TrajectoryPoint;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.io.WKTReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PortAnchorBerthExclusionService {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final double BERTH_POINT_BUFFER_NM = 0.5;

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate eventJdbcTemplate;

    @Autowired
    @Qualifier("aid41ReadOnlyJdbcTemplate")
    private JdbcTemplate aisJdbcTemplate;

    private volatile Snapshot cachedSnapshot;

    public Snapshot requireReady(String expectedFingerprint) {
        Snapshot snapshot = snapshot();
        if (expectedFingerprint != null
                && !snapshot.fingerprint.equalsIgnoreCase(expectedFingerprint)) {
            throw new IllegalStateException("exclusion fingerprint mismatch expected="
                    + expectedFingerprint + " actual=" + snapshot.fingerprint);
        }
        return snapshot;
    }

    public ExclusionDecision classify(List<TrajectoryPoint> points, Snapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("exclusion snapshot is required");
        }
        ZoneEntry quarantineHit = null;
        for (Geometry segment : segments(points)) {
            @SuppressWarnings("unchecked")
            List<ZoneEntry> candidates =
                    snapshot.index.query(segment.getEnvelopeInternal());
            for (ZoneEntry candidate : candidates) {
                if (!candidate.prepared.intersects(segment)) continue;
                if (!candidate.quarantined) {
                    return ExclusionDecision.rejected(candidate.type, candidate.source,
                            candidate.id, snapshot.fingerprint, snapshot.sourceCounts);
                }
                quarantineHit = candidate;
            }
        }
        if (quarantineHit != null) {
            return ExclusionDecision.quarantined(quarantineHit.type,
                    quarantineHit.source, quarantineHit.id,
                    snapshot.fingerprint, snapshot.sourceCounts);
        }
        return ExclusionDecision.accepted(snapshot.fingerprint, snapshot.sourceCounts);
    }

    private Snapshot snapshot() {
        Snapshot current = cachedSnapshot;
        if (current != null) return current;
        synchronized (this) {
            if (cachedSnapshot == null) cachedSnapshot = load();
            return cachedSnapshot;
        }
    }

    private Snapshot load() {
        List<SourceSpec> sources = Arrays.asList(
                new SourceSpec("PORT", "zones_with_port_id", eventJdbcTemplate,
                        "SELECT CAST(id AS CHAR) source_id,AsText(bound) geom_wkt "
                                + "FROM dataanalysis.zones_with_port_id "
                                + "WHERE is_delete=0 AND bound IS NOT NULL", false, false),
                new SourceSpec("PORT", "portsboundary", eventJdbcTemplate,
                        "SELECT CAST(id AS CHAR) source_id,AsText(bound) geom_wkt "
                                + "FROM dataanalysis.portsboundary WHERE bound IS NOT NULL",
                        false, false),
                new SourceSpec("ANCHORAGE", "port_anchorages", eventJdbcTemplate,
                        "SELECT CAST(oid AS CHAR) source_id,AsText(bound) geom_wkt "
                                + "FROM dataanalysis.port_anchorages WHERE bound IS NOT NULL",
                        false, false),
                new SourceSpec("ANCHORAGE", "anchor_boundary", aisJdbcTemplate,
                        "SELECT CAST(OID AS CHAR) source_id,AsText(bound) geom_wkt "
                                + "FROM ais41.anchor_boundary WHERE bound IS NOT NULL",
                        false, false),
                new SourceSpec("ANCHORAGE", "alertzones_anchor", aisJdbcTemplate,
                        "SELECT CAST(oid AS CHAR) source_id,AsText(bound) geom_wkt "
                                + "FROM ais41.alertzones WHERE bound IS NOT NULL AND "
                                + "(LOWER(name) LIKE '%anchor%' OR name LIKE '%锚地%' "
                                + "OR name LIKE '%锚泊%')", false, true),
                new SourceSpec("BERTH", "berth_sts_with_port_id", eventJdbcTemplate,
                        "SELECT CAST(id AS CHAR) source_id,AsText(bound) geom_wkt "
                                + "FROM dataanalysis.berth_sts_with_port_id "
                                + "WHERE bound IS NOT NULL", false, false),
                new SourceSpec("BERTH", "berth_with_port_id", eventJdbcTemplate,
                        "SELECT CAST(id AS CHAR) source_id,AsText(bound) geom_wkt "
                                + "FROM dataanalysis.berth_with_port_id "
                                + "WHERE bound IS NOT NULL", true, false));
        List<SourceRow> rows = new ArrayList<SourceRow>();
        for (SourceSpec source : sources) {
            List<SourceRow> sourceRows;
            try {
                sourceRows = source.jdbcTemplate.query(source.sql,
                        (rs, rowNum) -> new SourceRow(source.type, source.name,
                                rs.getString("source_id"), rs.getString("geom_wkt"),
                                source.bufferPoint, source.filterNonArea));
            } catch (RuntimeException ex) {
                throw new IllegalStateException(
                        "required exclusion source query failed: " + source.name, ex);
            }
            if (sourceRows.isEmpty()) {
                throw new IllegalStateException(
                        "required exclusion source is empty: " + source.name);
            }
            rows.addAll(sourceRows);
        }
        return build(rows, true);
    }

    static Snapshot snapshotForTest(List<SourceRow> rows) {
        return build(rows, false);
    }

    private static Snapshot build(List<SourceRow> rows, boolean requireAllSources) {
        PortAnchorBerthExclusionService service =
                new PortAnchorBerthExclusionService();
        List<ZoneEntry> entries = new ArrayList<ZoneEntry>();
        List<FingerprintRecord> fingerprints = new ArrayList<FingerprintRecord>();
        Map<String, SourceHealthStats> stats =
                new LinkedHashMap<String, SourceHealthStats>();
        for (SourceRow row : rows) {
            SourceHealthStats sourceStats = stats.get(row.source);
            if (sourceStats == null) {
                sourceStats = new SourceHealthStats(row.source);
                stats.put(row.source, sourceStats);
            }
            sourceStats.rawCount++;
            service.parse(row, entries, fingerprints, sourceStats);
        }
        for (SourceHealthStats sourceStats : stats.values()) {
            if (sourceStats.usableCount == 0) {
                throw new IllegalStateException(
                        "required exclusion source has zero usable geometry: "
                                + sourceStats.source);
            }
        }
        if (requireAllSources && stats.size() != 7) {
            throw new IllegalStateException("required exclusion source count mismatch expected=7 actual="
                    + stats.size());
        }
        return buildSnapshot(entries, fingerprints, stats);
    }

    private void parse(SourceRow row, List<ZoneEntry> entries,
                       List<FingerprintRecord> fingerprints,
                       SourceHealthStats stats) {
        Geometry geometry;
        try {
            geometry = new WKTReader(GEOMETRY_FACTORY).read(row.wkt);
        } catch (Exception ex) {
            stats.unparseableCount++;
            fingerprints.add(FingerprintRecord.raw(row, "UNPARSEABLE"));
            return;
        }
        if (geometry == null || geometry.isEmpty()) {
            stats.unparseableCount++;
            fingerprints.add(FingerprintRecord.raw(row, "EMPTY"));
            return;
        }
        if (row.filterNonArea && geometry.getDimension() != 2) {
            stats.filteredCount++;
            fingerprints.add(FingerprintRecord.raw(row, "FILTERED_NON_AREA"));
            return;
        }
        if (row.bufferPoint) {
            if (geometry instanceof Point) {
                addEntry(row, berthBuffer((Point) geometry), false,
                        entries, fingerprints, stats);
            } else {
                quarantine(row, geometry, entries, fingerprints, stats);
            }
            return;
        }
        Geometry polygonal = polygonal(geometry);
        if (geometry.isValid() && polygonal != null && !polygonal.isEmpty()) {
            addEntry(row, polygonal, false, entries, fingerprints, stats);
            return;
        }
        try {
            Geometry fixed = GeometryFixer.fix(geometry);
            Geometry fixedPolygonal = polygonal(fixed);
            if (fixedPolygonal != null && !fixedPolygonal.isEmpty()
                    && fixedPolygonal.isValid()) {
                stats.repairedCount++;
                addEntry(row, fixedPolygonal, false,
                        entries, fingerprints, stats);
                return;
            }
        } catch (RuntimeException ignored) {
            // Fall through to conservative envelope quarantine.
        }
        quarantine(row, geometry, entries, fingerprints, stats);
    }

    private void quarantine(SourceRow row, Geometry geometry,
                            List<ZoneEntry> entries,
                            List<FingerprintRecord> fingerprints,
                            SourceHealthStats stats) {
        Geometry envelope = geometry.getEnvelope();
        if (envelope == null || envelope.isEmpty()) {
            stats.unparseableCount++;
            fingerprints.add(FingerprintRecord.raw(row, "NO_ENVELOPE"));
            return;
        }
        stats.quarantinedCount++;
        addEntry(row, envelope, true, entries, fingerprints, stats);
    }

    private void addEntry(SourceRow row, Geometry geometry, boolean quarantined,
                          List<ZoneEntry> entries,
                          List<FingerprintRecord> fingerprints,
                          SourceHealthStats stats) {
        ZoneEntry entry = new ZoneEntry(row.type, row.source, row.id,
                geometry, quarantined);
        entries.add(entry);
        fingerprints.add(FingerprintRecord.geometry(entry));
        stats.usableCount++;
    }

    private static Geometry polygonal(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) return null;
        List<Polygon> polygons = new ArrayList<Polygon>();
        collectPolygons(geometry, polygons);
        if (polygons.isEmpty()) return null;
        return GEOMETRY_FACTORY.buildGeometry(polygons);
    }

    private static void collectPolygons(Geometry geometry, List<Polygon> target) {
        if (geometry instanceof Polygon) {
            target.add((Polygon) geometry);
            return;
        }
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            Geometry child = geometry.getGeometryN(i);
            if (child != geometry) collectPolygons(child, target);
        }
    }

    private Geometry berthBuffer(Point point) {
        double lat = point.getY();
        double latRadius = BERTH_POINT_BUFFER_NM / 60.0;
        double cos = Math.max(0.01, Math.cos(Math.toRadians(lat)));
        double lonRadius = latRadius / cos;
        Coordinate[] coordinates = new Coordinate[73];
        for (int i = 0; i < 72; i++) {
            double radians = Math.toRadians(i * 5.0);
            coordinates[i] = new Coordinate(
                    point.getX() + lonRadius * Math.cos(radians),
                    lat + latRadius * Math.sin(radians));
        }
        coordinates[72] = new Coordinate(coordinates[0]);
        return GEOMETRY_FACTORY.createPolygon(coordinates);
    }

    private List<Geometry> segments(List<TrajectoryPoint> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("trajectory is required");
        }
        Coordinate[] coordinates = new Coordinate[points.size()];
        for (int i = 0; i < points.size(); i++) {
            TrajectoryPoint point = points.get(i);
            if (point == null || point.getLon() < -180.0 || point.getLon() > 180.0
                    || point.getLat() < -90.0 || point.getLat() > 90.0) {
                throw new IllegalArgumentException(
                        "trajectory contains invalid coordinate");
            }
            coordinates[i] = new Coordinate(point.getLon(), point.getLat());
        }
        if (coordinates.length == 1) {
            return Collections.<Geometry>singletonList(
                    GEOMETRY_FACTORY.createPoint(coordinates[0]));
        }
        List<Geometry> result = new ArrayList<Geometry>();
        for (int i = 1; i < coordinates.length; i++) {
            Coordinate previous = coordinates[i - 1];
            Coordinate current = coordinates[i];
            if (Math.abs(previous.x - current.x) > 180.0) {
                result.add(GEOMETRY_FACTORY.createPoint(previous));
                result.add(GEOMETRY_FACTORY.createPoint(current));
            } else {
                result.add(GEOMETRY_FACTORY.createLineString(
                        new Coordinate[]{previous, current}));
            }
        }
        return result;
    }

    private static Snapshot buildSnapshot(
            List<ZoneEntry> entries, List<FingerprintRecord> fingerprints,
            Map<String, SourceHealthStats> stats) {
        Collections.sort(entries, Comparator
                .comparing((ZoneEntry value) -> value.source)
                .thenComparing(value -> value.id)
                .thenComparing(value -> value.geometry.toText()));
        STRtree index = new STRtree();
        for (ZoneEntry entry : entries) {
            index.insert(entry.geometry.getEnvelopeInternal(), entry);
        }
        index.build();
        Map<String, Integer> sourceCounts = new LinkedHashMap<String, Integer>();
        int repaired = 0;
        int filtered = 0;
        int quarantined = 0;
        int unparseable = 0;
        ExclusionHealth health = ExclusionHealth.AVAILABLE;
        for (SourceHealthStats value : stats.values()) {
            sourceCounts.put(value.source, value.usableCount);
            repaired += value.repairedCount;
            filtered += value.filteredCount;
            quarantined += value.quarantinedCount;
            unparseable += value.unparseableCount;
        }
        if (unparseable > 0) health = ExclusionHealth.DEGRADED;
        return new Snapshot(index, fingerprint(fingerprints), sourceCounts,
                stats, health, repaired, filtered, quarantined, unparseable);
    }

    private static String fingerprint(List<FingerprintRecord> records) {
        try {
            Collections.sort(records, Comparator
                    .comparing((FingerprintRecord value) -> value.source)
                    .thenComparing(value -> value.id)
                    .thenComparing(value -> value.disposition));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            WKBWriter writer = new WKBWriter(2, false);
            for (FingerprintRecord record : records) {
                update(digest, record.type);
                update(digest, record.source);
                update(digest, record.id);
                update(digest, record.disposition);
                if (record.geometry == null) {
                    update(digest, record.rawWkt == null ? "" : record.rawWkt);
                } else {
                    digest.update(writer.write(record.geometry));
                }
            }
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "cannot calculate exclusion fingerprint", ex);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    public static class Snapshot {
        private final STRtree index;
        private final String fingerprint;
        private final Map<String, Integer> sourceCounts;
        private final Map<String, SourceHealthStats> sourceHealth;
        private final ExclusionHealth health;
        private final int repairedGeometries;
        private final int filteredGeometries;
        private final int quarantinedGeometries;
        private final int unparseableGeometries;

        private Snapshot(STRtree index, String fingerprint,
                         Map<String, Integer> sourceCounts,
                         Map<String, SourceHealthStats> sourceHealth,
                         ExclusionHealth health, int repairedGeometries,
                         int filteredGeometries, int quarantinedGeometries,
                         int unparseableGeometries) {
            this.index = index;
            this.fingerprint = fingerprint;
            this.sourceCounts = new LinkedHashMap<String, Integer>(sourceCounts);
            this.sourceHealth =
                    new LinkedHashMap<String, SourceHealthStats>(sourceHealth);
            this.health = health;
            this.repairedGeometries = repairedGeometries;
            this.filteredGeometries = filteredGeometries;
            this.quarantinedGeometries = quarantinedGeometries;
            this.unparseableGeometries = unparseableGeometries;
        }

        public String getFingerprint() { return fingerprint; }
        public ExclusionHealth getHealth() { return health; }
        public Map<String, Integer> getSourceCounts() {
            return new LinkedHashMap<String, Integer>(sourceCounts);
        }
        public Map<String, SourceHealthStats> getSourceHealth() {
            return new LinkedHashMap<String, SourceHealthStats>(sourceHealth);
        }
        public String getHealthReportJson() {
            return JSON.toJSONString(sourceHealth);
        }
        public int getRepairedGeometries() { return repairedGeometries; }
        public int getFilteredGeometries() { return filteredGeometries; }
        public int getQuarantinedGeometries() { return quarantinedGeometries; }
        public int getUnparseableGeometries() { return unparseableGeometries; }
    }

    public static class SourceHealthStats {
        private final String source;
        private int rawCount;
        private int usableCount;
        private int filteredCount;
        private int repairedCount;
        private int quarantinedCount;
        private int unparseableCount;

        private SourceHealthStats(String source) {
            this.source = source;
        }

        public String getSource() { return source; }
        public int getRawCount() { return rawCount; }
        public int getUsableCount() { return usableCount; }
        public int getFilteredCount() { return filteredCount; }
        public int getRepairedCount() { return repairedCount; }
        public int getQuarantinedCount() { return quarantinedCount; }
        public int getUnparseableCount() { return unparseableCount; }
    }

    static class SourceRow {
        private final String type;
        private final String source;
        private final String id;
        private final String wkt;
        private final boolean bufferPoint;
        private final boolean filterNonArea;

        SourceRow(String type, String source, String id,
                  String wkt, boolean bufferPoint) {
            this(type, source, id, wkt, bufferPoint, false);
        }

        SourceRow(String type, String source, String id,
                  String wkt, boolean bufferPoint, boolean filterNonArea) {
            this.type = type;
            this.source = source;
            this.id = id;
            this.wkt = wkt;
            this.bufferPoint = bufferPoint;
            this.filterNonArea = filterNonArea;
        }
    }

    private static class SourceSpec {
        private final String type;
        private final String name;
        private final JdbcTemplate jdbcTemplate;
        private final String sql;
        private final boolean bufferPoint;
        private final boolean filterNonArea;

        private SourceSpec(String type, String name, JdbcTemplate jdbcTemplate,
                           String sql, boolean bufferPoint,
                           boolean filterNonArea) {
            this.type = type;
            this.name = name;
            this.jdbcTemplate = jdbcTemplate;
            this.sql = sql;
            this.bufferPoint = bufferPoint;
            this.filterNonArea = filterNonArea;
        }
    }

    private static class ZoneEntry {
        private final String type;
        private final String source;
        private final String id;
        private final Geometry geometry;
        private final boolean quarantined;
        private final PreparedGeometry prepared;

        private ZoneEntry(String type, String source, String id,
                          Geometry geometry, boolean quarantined) {
            this.type = type;
            this.source = source;
            this.id = id;
            this.geometry = geometry;
            this.quarantined = quarantined;
            this.prepared = PreparedGeometryFactory.prepare(geometry);
        }
    }

    private static class FingerprintRecord {
        private final String type;
        private final String source;
        private final String id;
        private final String disposition;
        private final Geometry geometry;
        private final String rawWkt;

        private FingerprintRecord(String type, String source, String id,
                                  String disposition, Geometry geometry,
                                  String rawWkt) {
            this.type = type;
            this.source = source;
            this.id = id;
            this.disposition = disposition;
            this.geometry = geometry;
            this.rawWkt = rawWkt;
        }

        private static FingerprintRecord geometry(ZoneEntry entry) {
            return new FingerprintRecord(entry.type, entry.source, entry.id,
                    entry.quarantined ? "QUARANTINED" : "USABLE",
                    entry.geometry, null);
        }

        private static FingerprintRecord raw(SourceRow row, String disposition) {
            return new FingerprintRecord(row.type, row.source, row.id,
                    disposition, null, row.wkt);
        }
    }
}
