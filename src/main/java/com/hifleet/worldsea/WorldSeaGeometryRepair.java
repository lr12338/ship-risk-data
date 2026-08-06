package com.hifleet.worldsea;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.Lineal;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.io.ByteOrderValues;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure, deterministic repair rules for the seven audited WORLD_SEA geometries. */
final class WorldSeaGeometryRepair {
    static final Set<Integer> REPAIR_IDS = Collections.unmodifiableSet(new LinkedHashSet<Integer>(Arrays.asList(
            11, 26, 85, 91, 92, 99, 24556)));
    static final String METHOD = "JTS_GEOMETRY_FIXER_1_18_2";
    static final String DATASET_BEFORE = "WORLD_SEA_102_V1";
    static final String DATASET_AFTER = "WORLD_SEA_102_V1R1";
    static final int SRID = 101;

    static DatasetRepair repair(List<SourceRow> sourceRows) throws Exception {
        if (sourceRows == null || sourceRows.isEmpty()) throw new IllegalStateException("WORLD_SEA source rows are empty");
        List<RepairedRow> rows = new ArrayList<RepairedRow>();
        List<FeatureRepair> features = new ArrayList<FeatureRepair>();
        Set<Integer> seen = new LinkedHashSet<Integer>();
        Set<Integer> changed = new LinkedHashSet<Integer>();
        int unchanged = 0;
        for (SourceRow source : sourceRows) {
            if (!seen.add(source.id)) throw new IllegalStateException("duplicate WORLD_SEA ID=" + source.id);
            if (source.wkb == null || source.wkb.length == 0) throw new IllegalStateException("empty Geometry ID=" + source.id);
            if (source.srid != SRID) throw new IllegalStateException("unexpected SRID ID=" + source.id + " srid=" + source.srid);
            Geometry original = new WKBReader().read(source.wkb);
            original.setSRID(SRID);
            requirePolygonal(original, "Original", source.id);
            requireCoordinates(original, source.id);
            IsValidOp before = new IsValidOp(original);
            if (!REPAIR_IDS.contains(source.id)) {
                if (!before.isValid()) throw new IllegalStateException("invalid Geometry outside repair allowlist ID=" + source.id);
                rows.add(new RepairedRow(source, source.wkb, original, false));
                unchanged++;
                continue;
            }
            if (before.isValid()) {
                throw new IllegalStateException("Repair allowlisted geometry is unexpectedly valid, id=" + source.id);
            }
            FeatureRepair feature = repairFeature(source, original, before.getValidationError());
            rows.add(new RepairedRow(source, feature.repairedWkb, feature.repairedGeometry, true));
            features.add(feature);
            changed.add(source.id);
        }
        if (!changed.equals(intersection(REPAIR_IDS, seen))) {
            throw new IllegalStateException("changed IDs differ from present repair allowlist IDs=" + changed);
        }
        if (sourceRows.size() == 102 && !changed.equals(REPAIR_IDS)) {
            throw new IllegalStateException("102-row dataset must contain exactly seven repair IDs");
        }
        return new DatasetRepair(rows, features, unchanged, changed.size(), changed, fingerprint(rows));
    }

    private static FeatureRepair repairFeature(SourceRow source, Geometry original,
                                               TopologyValidationError validationError) throws Exception {
        GeometryFixer fixer = new GeometryFixer(original);
        fixer.setKeepCollapsed(false);
        Geometry repaired = keepMulti(original, fixer.getResult());
        if (repaired != null) repaired.setSRID(SRID);
        if (repaired == null || repaired.isEmpty()) throw new IllegalStateException("Repaired geometry is empty ID=" + source.id);
        requirePolygonal(repaired, "Repaired", source.id);
        requireCoordinates(repaired, source.id);
        IsValidOp after = new IsValidOp(repaired);
        if (!after.isValid()) throw new IllegalStateException("Repaired geometry remains invalid ID=" + source.id
                + " error=" + after.getValidationError());

        GeometryFixer collapsedPreviewFixer = new GeometryFixer(original);
        collapsedPreviewFixer.setKeepCollapsed(true);
        Geometry collapsedPreview = collapsedPreviewFixer.getResult();
        List<String> collapsedTypes = new ArrayList<String>();
        double collapsedLength = collectCollapsed(collapsedPreview, collapsedTypes);

        byte[] repairedWkb = fixedWkb(repaired);
        Geometry roundTrip = new WKBReader().read(repairedWkb);
        roundTrip.setSRID(SRID);
        GeometryFixer repeatFixer = new GeometryFixer(roundTrip);
        repeatFixer.setKeepCollapsed(false);
        Geometry repeated = keepMulti(roundTrip, repeatFixer.getResult());
        repeated.setSRID(SRID);
        byte[] repeatedWkb = fixedWkb(repeated);
        boolean idempotent = repeated.isValid() && repaired.equalsTopo(repeated)
                && repaired.getGeometryType().equals(repeated.getGeometryType())
                && repaired.getNumGeometries() == repeated.getNumGeometries()
                && nearlyEqual(repaired.getArea(), repeated.getArea(), 1e-12)
                && envelopeDelta(repaired.getEnvelopeInternal(), repeated.getEnvelopeInternal()) <= 1e-15
                && Arrays.equals(repairedWkb, repeatedWkb);
        if (!idempotent) throw new IllegalStateException("repair output is not deterministic/idempotent ID=" + source.id
                + " valid=" + repeated.isValid() + " topo=" + repaired.equalsTopo(repeated)
                + " type=" + repaired.getGeometryType() + "/" + repeated.getGeometryType()
                + " components=" + repaired.getNumGeometries() + "/" + repeated.getNumGeometries()
                + " areaDelta=" + Math.abs(repaired.getArea() - repeated.getArea())
                + " envelopeDelta=" + envelopeDelta(repaired.getEnvelopeInternal(), repeated.getEnvelopeInternal())
                + " wkbEqual=" + Arrays.equals(repairedWkb, repeatedWkb));

        List<Double> componentAreas = polygonAreas(repaired);
        double minimumArea = componentAreas.isEmpty() ? 0d : Collections.min(componentAreas);
        double mainArea = componentAreas.isEmpty() ? 0d : Collections.max(componentAreas);
        double minimumRatio = mainArea == 0d ? 0d : minimumArea / mainArea;
        double originalArea = original.getArea();
        double repairedArea = repaired.getArea();
        double relativeAreaDelta = originalArea == 0d
                ? Math.abs(repairedArea - originalArea) : Math.abs(repairedArea - originalArea) / Math.abs(originalArea);
        double envDelta = envelopeDelta(original.getEnvelopeInternal(), repaired.getEnvelopeInternal());
        String reviewStatus = relativeAreaDelta > 1e-8 || envDelta > 1e-9
                ? "MANUAL_REVIEW_REQUIRED" : "PENDING_REVIEW";
        Geometry difference = difference(original, repaired);
        return new FeatureRepair(source.id, source.name, source.cnName, original, repaired,
                validationError == null ? null : validationError.toString(), validationError == null ? null : validationError.getCoordinate(),
                sha256(source.wkb), sha256(repairedWkb), repairedWkb,
                collapsedTypes, collapsedLength, componentAreas, minimumArea, mainArea, minimumRatio,
                originalArea, repairedArea, relativeAreaDelta, envDelta, reviewStatus, idempotent, difference);
    }

    static byte[] fixedWkb(Geometry geometry) {
        if (geometry == null || geometry.isEmpty() || !(geometry instanceof Polygonal)) {
            throw new IllegalStateException("fixed WKB requires non-empty Polygonal Geometry");
        }
        Geometry ordered = geometry.copy();
        ordered.setSRID(SRID);
        ordered.normalize();
        return new WKBWriter(2, ByteOrderValues.LITTLE_ENDIAN, false).write(ordered);
    }

    // GeometryFixer 1.18.2 predates setKeepMulti; preserve MultiPolygon semantics explicitly.
    private static Geometry keepMulti(Geometry original, Geometry repaired) {
        if (original instanceof MultiPolygon && repaired instanceof Polygon) {
            return original.getFactory().createMultiPolygon(new Polygon[]{(Polygon) repaired});
        }
        return repaired;
    }

    private static Geometry difference(Geometry original, Geometry repaired) {
        try {
            Geometry value = original.symDifference(repaired);
            value.setSRID(SRID);
            return value;
        } catch (RuntimeException ex) {
            return repaired.getFactory().createGeometryCollection(new Geometry[0]);
        }
    }

    private static double collectCollapsed(Geometry value, List<String> types) {
        if (value == null || value.isEmpty()) return 0d;
        if (value instanceof GeometryCollection) {
            double length = 0d;
            for (int i = 0; i < value.getNumGeometries(); i++) length += collectCollapsed(value.getGeometryN(i), types);
            return length;
        }
        if (!(value instanceof Polygonal)) {
            types.add(value.getGeometryType());
            return value instanceof Lineal ? value.getLength() : 0d;
        }
        return 0d;
    }

    private static List<Double> polygonAreas(Geometry geometry) {
        List<Double> result = new ArrayList<Double>();
        if (geometry instanceof Polygon) result.add(geometry.getArea());
        else if (geometry instanceof MultiPolygon) {
            for (int i = 0; i < geometry.getNumGeometries(); i++) result.add(geometry.getGeometryN(i).getArea());
        }
        return result;
    }

    private static void requirePolygonal(Geometry geometry, String label, int id) {
        if (geometry == null || geometry.isEmpty()) throw new IllegalStateException(label + " Geometry is empty ID=" + id);
        if (!(geometry instanceof Polygonal)) throw new IllegalStateException(label + " Geometry is not polygonal: "
                + geometry.getGeometryType() + " ID=" + id);
    }

    private static void requireCoordinates(Geometry geometry, int id) {
        for (Coordinate coordinate : geometry.getCoordinates()) {
            if (!Double.isFinite(coordinate.x) || !Double.isFinite(coordinate.y)
                    || coordinate.x < -180d || coordinate.x > 180d
                    || coordinate.y < -90d || coordinate.y > 90d) {
                throw new IllegalStateException("coordinate out of range ID=" + id);
            }
        }
    }

    private static Set<Integer> intersection(Set<Integer> expected, Set<Integer> actual) {
        Set<Integer> value = new LinkedHashSet<Integer>(expected);
        value.retainAll(actual);
        return value;
    }

    private static double envelopeDelta(Envelope left, Envelope right) {
        return Math.max(Math.max(Math.abs(left.getMinX() - right.getMinX()), Math.abs(left.getMaxX() - right.getMaxX())),
                Math.max(Math.abs(left.getMinY() - right.getMinY()), Math.abs(left.getMaxY() - right.getMaxY())));
    }

    private static boolean nearlyEqual(double left, double right, double tolerance) {
        return Math.abs(left - right) <= tolerance * Math.max(1d, Math.max(Math.abs(left), Math.abs(right)));
    }

    private static String fingerprint(List<RepairedRow> rows) {
        List<WorldSeaMaskLoader.Row> values = new ArrayList<WorldSeaMaskLoader.Row>();
        for (RepairedRow row : rows) values.add(new WorldSeaMaskLoader.Row(
                row.source.id, row.source.name, row.source.cnName, row.wkb));
        return WorldSeaMaskLoader.fingerprint(values);
    }

    static String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder result = new StringBuilder();
        for (byte current : digest) result.append(String.format("%02x", current & 0xff));
        return result.toString();
    }

    static final class SourceRow {
        final int id;
        final String name;
        final String cnName;
        final byte[] wkb;
        final int srid;
        SourceRow(int id, String name, String cnName, byte[] wkb, int srid) {
            this.id = id;
            this.name = name;
            this.cnName = cnName;
            this.wkb = wkb;
            this.srid = srid;
        }
    }

    static final class RepairedRow {
        final SourceRow source;
        final byte[] wkb;
        final Geometry geometry;
        final boolean repaired;
        RepairedRow(SourceRow source, byte[] wkb, Geometry geometry, boolean repaired) {
            this.source = source;
            this.wkb = wkb;
            this.geometry = geometry;
            this.repaired = repaired;
        }
    }

    static final class DatasetRepair {
        final List<RepairedRow> rows;
        final List<FeatureRepair> features;
        final int unchangedCount;
        final int changedCount;
        final Set<Integer> changedIds;
        final String repairedFingerprint;
        DatasetRepair(List<RepairedRow> rows, List<FeatureRepair> features, int unchangedCount,
                      int changedCount, Set<Integer> changedIds, String repairedFingerprint) {
            this.rows = rows;
            this.features = features;
            this.unchangedCount = unchangedCount;
            this.changedCount = changedCount;
            this.changedIds = Collections.unmodifiableSet(new LinkedHashSet<Integer>(changedIds));
            this.repairedFingerprint = repairedFingerprint;
        }
    }

    static final class FeatureRepair {
        final int id;
        final String name;
        final String cnName;
        final Geometry originalGeometry;
        final Geometry repairedGeometry;
        final String originalValidityError;
        final Coordinate errorCoordinate;
        final String originalWkbSha256;
        final String repairedWkbSha256;
        final byte[] repairedWkb;
        final List<String> collapsedComponentTypes;
        final double collapsedComponentLength;
        final List<Double> polygonComponentAreas;
        final double minimumPolygonArea;
        final double mainPolygonArea;
        final double minimumPolygonRatio;
        final String tinyComponentDecision;
        final double originalArea;
        final double repairedArea;
        final double relativeAreaDelta;
        final double envelopeDelta;
        final String reviewStatus;
        final boolean idempotent;
        final Geometry differenceGeometry;
        FeatureRepair(int id, String name, String cnName, Geometry originalGeometry, Geometry repairedGeometry,
                      String originalValidityError, Coordinate errorCoordinate, String originalWkbSha256,
                      String repairedWkbSha256, byte[] repairedWkb, List<String> collapsedComponentTypes,
                      double collapsedComponentLength, List<Double> polygonComponentAreas,
                      double minimumPolygonArea, double mainPolygonArea, double minimumPolygonRatio,
                      double originalArea, double repairedArea, double relativeAreaDelta, double envelopeDelta,
                      String reviewStatus, boolean idempotent, Geometry differenceGeometry) {
            this.id = id; this.name = name; this.cnName = cnName;
            this.originalGeometry = originalGeometry; this.repairedGeometry = repairedGeometry;
            this.originalValidityError = originalValidityError; this.errorCoordinate = errorCoordinate;
            this.originalWkbSha256 = originalWkbSha256; this.repairedWkbSha256 = repairedWkbSha256;
            this.repairedWkb = repairedWkb; this.collapsedComponentTypes = collapsedComponentTypes;
            this.collapsedComponentLength = collapsedComponentLength; this.polygonComponentAreas = polygonComponentAreas;
            this.minimumPolygonArea = minimumPolygonArea; this.mainPolygonArea = mainPolygonArea;
            this.minimumPolygonRatio = minimumPolygonRatio; this.tinyComponentDecision = "RETAIN";
            this.originalArea = originalArea;
            this.repairedArea = repairedArea; this.relativeAreaDelta = relativeAreaDelta;
            this.envelopeDelta = envelopeDelta; this.reviewStatus = reviewStatus;
            this.idempotent = idempotent; this.differenceGeometry = differenceGeometry;
        }
    }

    private WorldSeaGeometryRepair() {}
}
