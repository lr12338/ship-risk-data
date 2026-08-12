package com.hifleet.regionbehavior;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateFilter;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.io.WKTReader;

import java.security.MessageDigest;

public final class RegionGeometry {
    private static final double EARTH_RADIUS_METERS = 6371008.8;
    private static final double METERS_PER_NM = 1852.0;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private final Geometry coreRegion;
    private final Geometry analysisRegion;
    private final double areaSqKm;
    private final String fingerprint;

    private RegionGeometry(Geometry coreRegion, Geometry analysisRegion,
                           double areaSqKm, String fingerprint) {
        this.coreRegion = coreRegion;
        this.analysisRegion = analysisRegion;
        this.areaSqKm = areaSqKm;
        this.fingerprint = fingerprint;
    }

    public static RegionGeometry parse(String wkt, double bufferNm, RegionBehaviorConfig config) {
        Geometry parsed;
        try {
            parsed = new WKTReader(GEOMETRY_FACTORY).read(wkt);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid polygonWkt", ex);
        }
        if (parsed == null || parsed.isEmpty()) {
            throw new IllegalArgumentException("polygon geometry must not be empty");
        }
        validateCoordinates(parsed);
        if (parsed.getNumPoints() > config.getMaxPolygonVertices()) {
            throw new IllegalArgumentException("polygon exceeds maxPolygonVertices="
                    + config.getMaxPolygonVertices());
        }
        if (parsed.getEnvelopeInternal().getWidth() > 180.0) {
            throw new IllegalArgumentException("date-line crossing polygons are not supported in V1");
        }
        Geometry fixed = parsed.isValid() ? parsed.copy() : GeometryFixer.fix(parsed);
        fixed = polygonal(fixed);
        if (fixed == null || fixed.isEmpty() || !fixed.isValid()) {
            throw new IllegalArgumentException("MakeValid did not produce valid Polygon/MultiPolygon");
        }
        double area = metricAreaSqKm(fixed);
        if (!(area > 0.0)) {
            throw new IllegalArgumentException("polygon area must be positive");
        }
        Geometry analysis = bufferNm <= 0.0 ? fixed.copy() : metricBuffer(fixed, bufferNm * METERS_PER_NM);
        analysis = polygonal(GeometryFixer.fix(analysis));
        if (analysis == null || analysis.isEmpty() || !analysis.isValid()) {
            throw new IllegalArgumentException("analysis region buffer is invalid");
        }
        validateCoordinates(analysis);
        if (analysis.getEnvelopeInternal().getWidth() > 180.0) {
            throw new IllegalArgumentException("analysis region crosses the date line");
        }
        return new RegionGeometry(fixed, analysis, area, fingerprint(fixed));
    }

    public void assertWithinAreaLimit(RegionBehaviorConfig config) {
        if (areaSqKm > config.getMaxRegionAreaSqKm()) {
            throw new IllegalArgumentException("region area exceeds maxRegionAreaSqKm="
                    + config.getMaxRegionAreaSqKm());
        }
    }

    public boolean containsCore(Coordinate coordinate) {
        return coreRegion.covers(GEOMETRY_FACTORY.createPoint(coordinate));
    }

    public boolean containsAnalysis(Coordinate coordinate) {
        return analysisRegion.covers(GEOMETRY_FACTORY.createPoint(coordinate));
    }

    public boolean intersectsCore(Geometry geometry) {
        return geometry != null && coreRegion.intersects(geometry);
    }

    private static void validateCoordinates(Geometry geometry) {
        for (Coordinate coordinate : geometry.getCoordinates()) {
            if (!Double.isFinite(coordinate.x) || !Double.isFinite(coordinate.y)
                    || coordinate.x < -180.0 || coordinate.x > 180.0
                    || coordinate.y < -90.0 || coordinate.y > 90.0) {
                throw new IllegalArgumentException("polygon coordinates must be valid longitude/latitude");
            }
        }
    }

    private static Geometry polygonal(Geometry geometry) {
        if (geometry instanceof Polygon || geometry instanceof MultiPolygon) {
            return geometry;
        }
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        java.util.List<Polygon> polygons = new java.util.ArrayList<Polygon>();
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            Geometry part = geometry.getGeometryN(i);
            if (part instanceof Polygon) {
                polygons.add((Polygon) part);
            }
        }
        return polygons.isEmpty() ? null
                : GEOMETRY_FACTORY.createMultiPolygon(polygons.toArray(new Polygon[polygons.size()]));
    }

    private static double metricAreaSqKm(Geometry geometry) {
        Geometry projected = project(geometry, false);
        return projected.getArea() / 1_000_000.0;
    }

    private static Geometry metricBuffer(Geometry geometry, double meters) {
        Projection projection = Projection.forGeometry(geometry);
        Geometry projected = geometry.copy();
        projection.forward(projected);
        Geometry buffered = projected.buffer(meters);
        projection.reverse(buffered);
        return buffered;
    }

    private static Geometry project(Geometry geometry, boolean unused) {
        Geometry projected = geometry.copy();
        Projection.forGeometry(geometry).forward(projected);
        return projected;
    }

    private static String fingerprint(Geometry geometry) {
        try {
            Geometry normalized = geometry.copy();
            normalized.normalize();
            byte[] canonical = new WKBWriter(2, true).write(normalized);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical);
            StringBuilder value = new StringBuilder();
            for (byte b : hash) {
                value.append(String.format("%02x", b & 0xff));
            }
            return value.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("cannot fingerprint geometry", ex);
        }
    }

    public Geometry getCoreRegion() { return coreRegion.copy(); }
    public Geometry getAnalysisRegion() { return analysisRegion.copy(); }
    public Envelope getAnalysisEnvelope() { return new Envelope(analysisRegion.getEnvelopeInternal()); }
    public double getAreaSqKm() { return areaSqKm; }
    public String getFingerprint() { return fingerprint; }

    private static final class Projection {
        private final double lon0Radians;
        private final double lat0Radians;
        private final double cosLat0;

        private Projection(double lon0, double lat0) {
            this.lon0Radians = Math.toRadians(lon0);
            this.lat0Radians = Math.toRadians(lat0);
            this.cosLat0 = Math.max(0.01, Math.cos(lat0Radians));
        }

        static Projection forGeometry(Geometry geometry) {
            Coordinate center = geometry.getCentroid().getCoordinate();
            return new Projection(center.x, center.y);
        }

        void forward(Geometry geometry) {
            geometry.apply(new CoordinateFilter() {
                @Override
                public void filter(Coordinate coordinate) {
                    coordinate.x = EARTH_RADIUS_METERS
                            * (Math.toRadians(coordinate.x) - lon0Radians) * cosLat0;
                    coordinate.y = EARTH_RADIUS_METERS
                            * (Math.toRadians(coordinate.y) - lat0Radians);
                }
            });
            geometry.geometryChanged();
        }

        void reverse(Geometry geometry) {
            geometry.apply(new CoordinateFilter() {
                @Override
                public void filter(Coordinate coordinate) {
                    coordinate.x = Math.toDegrees(coordinate.x
                            / (EARTH_RADIUS_METERS * cosLat0) + lon0Radians);
                    coordinate.y = Math.toDegrees(coordinate.y / EARTH_RADIUS_METERS + lat0Radians);
                }
            });
            geometry.geometryChanged();
        }
    }
}
