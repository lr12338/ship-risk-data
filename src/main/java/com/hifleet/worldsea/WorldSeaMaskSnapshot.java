package com.hifleet.worldsea;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.index.strtree.STRtree;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

public final class WorldSeaMaskSnapshot {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private final STRtree index;
    private final int featureCount;
    private final int polygonCount;
    private final int multiPolygonCount;
    private final long vertexCount;
    private final String datasetVersion;
    private final String sourceFileSha256;
    private final String geometryFingerprint;
    private final String loadedAt;
    private final long loadElapsedMs;
    private final String coordinateAssumption;
    private final List<String> sampleAreaNames;

    WorldSeaMaskSnapshot(STRtree index, int featureCount, int polygonCount,
                         int multiPolygonCount, long vertexCount, String datasetVersion,
                         String sourceFileSha256, String geometryFingerprint,
                         long loadElapsedMs, String coordinateAssumption) {
        this(index, featureCount, polygonCount, multiPolygonCount, vertexCount, datasetVersion,
                sourceFileSha256, geometryFingerprint, loadElapsedMs, coordinateAssumption,
                Collections.<String>emptyList());
    }

    WorldSeaMaskSnapshot(STRtree index, int featureCount, int polygonCount,
                         int multiPolygonCount, long vertexCount, String datasetVersion,
                         String sourceFileSha256, String geometryFingerprint,
                         long loadElapsedMs, String coordinateAssumption, List<String> sampleAreaNames) {
        this.index = index;
        this.featureCount = featureCount;
        this.polygonCount = polygonCount;
        this.multiPolygonCount = multiPolygonCount;
        this.vertexCount = vertexCount;
        this.datasetVersion = datasetVersion;
        this.sourceFileSha256 = sourceFileSha256;
        this.geometryFingerprint = geometryFingerprint;
        this.loadedAt = LocalDateTime.now().toString();
        this.loadElapsedMs = loadElapsedMs;
        this.coordinateAssumption = coordinateAssumption;
        this.sampleAreaNames = Collections.unmodifiableList(new ArrayList<String>(sampleAreaNames));
    }

    public WorldSeaMatch match(double lon, double lat) {
        if (!Double.isFinite(lon) || !Double.isFinite(lat)
                || lon < -180.0 || lon > 180.0 || lat < -90.0 || lat > 90.0) {
            return null;
        }
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
        List<?> candidates = index.query(point.getEnvelopeInternal());
        Entry best = null;
        for (Object value : candidates) {
            Entry entry = (Entry) value;
            if (entry.prepared.covers(point) && (best == null || entry.area < best.area)) {
                best = entry;
            }
        }
        return best == null ? null : new WorldSeaMatch(best.id, best.name, best.cnName);
    }

    public boolean intersects(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) return false;
        for (Object value : index.query(geometry.getEnvelopeInternal())) {
            if (((Entry) value).prepared.intersects(geometry)) return true;
        }
        return false;
    }

    public boolean coversAllCoordinates(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) return false;
        for (Coordinate coordinate : geometry.getCoordinates()) {
            if (match(coordinate.x, coordinate.y) == null) return false;
        }
        return true;
    }

    static Entry entry(int id, String name, String cnName, Geometry geometry, PreparedGeometry prepared) {
        return new Entry(id, name, cnName, prepared, Math.abs(geometry.getArea()));
    }

    static final class Entry {
        final int id; final String name; final String cnName;
        final PreparedGeometry prepared; final double area;
        Entry(int id, String name, String cnName, PreparedGeometry prepared, double area) {
            this.id = id; this.name = name; this.cnName = cnName;
            this.prepared = prepared; this.area = area;
        }
    }

    public int getFeatureCount() { return featureCount; }
    public int getPolygonCount() { return polygonCount; }
    public int getMultiPolygonCount() { return multiPolygonCount; }
    public long getVertexCount() { return vertexCount; }
    public String getDatasetVersion() { return datasetVersion; }
    public String getSourceFileSha256() { return sourceFileSha256; }
    public String getGeometryFingerprint() { return geometryFingerprint; }
    public String getLoadedAt() { return loadedAt; }
    public long getLoadElapsedMs() { return loadElapsedMs; }
    public String getCoordinateAssumption() { return coordinateAssumption; }
    public List<String> getSampleAreaNames() { return sampleAreaNames; }
}
