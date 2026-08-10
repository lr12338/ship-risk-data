package com.hifleet.loitering;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.io.ByteOrderValues;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure, deterministic repair rules for the audited port-anchor source rows. */
final class LoiteringPortGeometryRepair {
    static final int SRID = 101;
    static final String METHOD = "JTS_GEOMETRY_FIXER_1_18_2";
    static final String RUN_ID = "PORT_ANCHOR_20260810_V1";
    static final Set<Integer> REPAIR_IDS = immutableSet(312, 393, 513, 709, 844, 857, 1650, 1872, 1885,
            1941, 2373, 2392, 2397, 2868, 3011, 3108, 3298, 3300, 3303, 3537, 8861, 10892,
            12711, 12718, 12719, 13316);
    static final Set<Integer> LARGE_AREA_DELTA_IDS = immutableSet(3298, 3300, 3303);
    private static final double MAX_ENVELOPE_DELTA = 1e-12;

    static RepairResult repair(List<SourceRow> sourceRows) throws Exception {
        if (sourceRows == null) throw new IllegalStateException("source rows are null");
        List<SourceRow> sorted = new ArrayList<SourceRow>(sourceRows);
        Collections.sort(sorted, Comparator.comparingInt(row -> row.id));
        Set<Integer> ids = new LinkedHashSet<Integer>();
        for (SourceRow row : sorted) {
            if (!ids.add(row.id)) throw new IllegalStateException("duplicate source ID=" + row.id);
            if (!REPAIR_IDS.contains(row.id)) throw new IllegalStateException("unexpected source ID=" + row.id);
        }
        if (!ids.equals(REPAIR_IDS)) {
            Set<Integer> missing = new LinkedHashSet<Integer>(REPAIR_IDS); missing.removeAll(ids);
            throw new IllegalStateException("repair source scope mismatch missing=" + missing + " actual=" + ids);
        }
        List<FeatureRepair> features = new ArrayList<FeatureRepair>();
        for (SourceRow row : sorted) features.add(repairOne(row));
        return new RepairResult(features, fingerprintSource(sorted), fingerprintRepaired(features));
    }

    static FeatureRepair repairOne(SourceRow source) throws Exception {
        if (source.zonesType != 1) throw new IllegalStateException("source is not anchorage ID=" + source.id);
        if (source.srid != SRID) throw new IllegalStateException("unexpected SRID ID=" + source.id + " srid=" + source.srid);
        if (source.wkb == null || source.wkb.length == 0) throw new IllegalStateException("empty source WKB ID=" + source.id);
        Geometry original = new WKBReader().read(source.wkb); original.setSRID(source.srid);
        if (!(original instanceof Polygon)) throw new IllegalStateException("source is not Polygon ID=" + source.id
                + " type=" + original.getGeometryType());
        IsValidOp validity = new IsValidOp(original);
        if (validity.isValid()) throw new IllegalStateException("source is unexpectedly valid ID=" + source.id);
        TopologyValidationError error = validity.getValidationError();
        if (error == null || (error.getErrorType() != TopologyValidationError.SELF_INTERSECTION
                && error.getErrorType() != TopologyValidationError.RING_SELF_INTERSECTION)) {
            throw new IllegalStateException("source failure is not self-intersection ID=" + source.id + " error=" + error);
        }
        Geometry repaired = GeometryFixer.fix(original); repaired.setSRID(source.srid);
        requireRepaired(source.id, repaired);
        double envelopeDelta = envelopeDelta(original.getEnvelopeInternal(), repaired.getEnvelopeInternal());
        if (envelopeDelta > MAX_ENVELOPE_DELTA) throw new IllegalStateException("repair envelope changed ID=" + source.id
                + " delta=" + envelopeDelta);
        byte[] repairedWkb = fixedWkb(repaired);
        Geometry deterministic = GeometryFixer.fix(original); deterministic.setSRID(source.srid);
        requireRepaired(source.id, deterministic);
        if (!Arrays.equals(repairedWkb, fixedWkb(deterministic))) {
            throw new IllegalStateException("repair is not deterministic ID=" + source.id);
        }
        Geometry roundTrip = new WKBReader().read(repairedWkb); roundTrip.setSRID(source.srid);
        Geometry repeated = GeometryFixer.fix(roundTrip); repeated.setSRID(source.srid);
        requireRepaired(source.id, repeated);
        if (!repaired.equalsTopo(repeated) || !repaired.getGeometryType().equals(repeated.getGeometryType())
                || repaired.getNumGeometries() != repeated.getNumGeometries()
                || Math.abs(repaired.getArea() - repeated.getArea()) > 1e-12
                || envelopeDelta(repaired.getEnvelopeInternal(), repeated.getEnvelopeInternal()) > MAX_ENVELOPE_DELTA) {
            throw new IllegalStateException("repair is not idempotent ID=" + source.id);
        }
        double areaDelta = original.getArea() == 0d ? Math.abs(repaired.getArea() - original.getArea())
                : Math.abs(repaired.getArea() - original.getArea()) / Math.abs(original.getArea());
        return new FeatureRepair(source, repaired, repairedWkb, error.toString(), areaDelta, envelopeDelta,
                LARGE_AREA_DELTA_IDS.contains(source.id));
    }

    static String fingerprintSource(List<SourceRow> rows) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (SourceRow row : rows) {
            update(digest, String.valueOf(row.id)); update(digest, String.valueOf(row.portId));
            update(digest, String.valueOf(row.zonesType)); update(digest, String.valueOf(row.srid));
            update(digest, row.name); update(digest, row.wkb);
        }
        return hex(digest.digest());
    }

    static String fingerprintRepaired(List<FeatureRepair> features) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (FeatureRepair feature : features) {
            update(digest, String.valueOf(feature.source.id)); update(digest, feature.repairedWkb);
        }
        return hex(digest.digest());
    }

    static byte[] fixedWkb(Geometry geometry) {
        return new WKBWriter(2, ByteOrderValues.LITTLE_ENDIAN, false).write(geometry);
    }

    static String sha256(byte[] bytes) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void requireRepaired(int id, Geometry geometry) {
        if (geometry == null || geometry.isEmpty() || !(geometry instanceof Polygonal) || !geometry.isValid()) {
            throw new IllegalStateException("repaired geometry is not valid polygonal ID=" + id);
        }
        if (geometry.getSRID() != SRID) throw new IllegalStateException("repaired SRID changed ID=" + id);
    }

    private static double envelopeDelta(Envelope left, Envelope right) {
        return Math.max(Math.max(Math.abs(left.getMinX() - right.getMinX()), Math.abs(left.getMaxX() - right.getMaxX())),
                Math.max(Math.abs(left.getMinY() - right.getMinY()), Math.abs(left.getMaxY() - right.getMaxY())));
    }

    private static Set<Integer> immutableSet(Integer... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<Integer>(Arrays.asList(values)));
    }
    private static void update(MessageDigest digest, String value) {
        update(digest, (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }
    private static void update(MessageDigest digest, byte[] value) {
        byte[] bytes = value == null ? new byte[0] : value;
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
    }
    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder();
        for (byte current : value) result.append(String.format("%02x", current & 0xff));
        return result.toString();
    }

    static final class SourceRow {
        final int id, portId, zonesType, srid; final String name; final byte[] wkb;
        SourceRow(int id, int portId, int zonesType, int srid, String name, byte[] wkb) {
            this.id=id; this.portId=portId; this.zonesType=zonesType; this.srid=srid; this.name=name; this.wkb=wkb;
        }
    }
    static final class FeatureRepair {
        final SourceRow source; final Geometry repaired; final byte[] repairedWkb; final String originalError;
        final double relativeAreaDelta, envelopeDelta; final boolean largeAreaDeltaAccepted;
        FeatureRepair(SourceRow source, Geometry repaired, byte[] repairedWkb, String originalError,
                      double relativeAreaDelta, double envelopeDelta, boolean largeAreaDeltaAccepted) {
            this.source=source; this.repaired=repaired; this.repairedWkb=repairedWkb; this.originalError=originalError;
            this.relativeAreaDelta=relativeAreaDelta; this.envelopeDelta=envelopeDelta;
            this.largeAreaDeltaAccepted=largeAreaDeltaAccepted;
        }
    }
    static final class RepairResult {
        final List<FeatureRepair> features; final String sourceFingerprint, repairedFingerprint;
        RepairResult(List<FeatureRepair> features, String sourceFingerprint, String repairedFingerprint) {
            this.features=Collections.unmodifiableList(features); this.sourceFingerprint=sourceFingerprint;
            this.repairedFingerprint=repairedFingerprint;
        }
    }
    private LoiteringPortGeometryRepair() {}
}
