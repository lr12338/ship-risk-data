package com.hifleet.maritime;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.io.WKBReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MaritimeSceneTileLoader {
    private final Path releaseRoot;
    private final MaritimeSceneTileCache cache;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    public MaritimeSceneTileLoader(Path releaseRoot, int maxTiles) {
        this.releaseRoot = releaseRoot.toAbsolutePath().normalize();
        this.cache = new MaritimeSceneTileCache(maxTiles);
    }

    public TileData load(String tileId) {
        return cache.getOrLoad(tileId, new MaritimeSceneTileCache.Loader<TileData>() {
            @Override
            public TileData load(String id) {
                return readTile(id);
            }
        });
    }

    public MaritimeSceneTileCache getCache() { return cache; }

    private TileData readTile(String tileId) {
        List<TileRecord> scenes = readRecords(tileId, "scene");
        List<TileRecord> coast = readRecords(tileId, "coast");
        STRtree sceneIndex = new STRtree();
        STRtree coastIndex = new STRtree();
        for (TileRecord record : scenes) sceneIndex.insert(record.getGeometry().getEnvelopeInternal(), record);
        for (TileRecord record : coast) coastIndex.insert(record.getGeometry().getEnvelopeInternal(), record);
        sceneIndex.build();
        coastIndex.build();
        return new TileData(tileId, scenes, coast, sceneIndex, coastIndex);
    }

    private List<TileRecord> readRecords(String tileId, String kind) {
        Path path = releaseRoot.resolve("tiles").resolve(tileId + "." + kind + ".ndjson.gz");
        if (!Files.exists(path)) return Collections.emptyList();
        List<TileRecord> result = new ArrayList<TileRecord>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new java.util.zip.GZIPInputStream(Files.newInputStream(path)),
                StandardCharsets.UTF_8))) {
            WKBReader wkbReader = new WKBReader(geometryFactory);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                JSONObject json = JSON.parseObject(line);
                String wkb = json.getString("geometryWkbHex");
                if (wkb == null || wkb.trim().isEmpty()) {
                    throw new IllegalStateException("missing geometryWkbHex in " + path);
                }
                Geometry geometry = wkbReader.read(WKBReader.hexToBytes(wkb));
                if (geometry == null || geometry.isEmpty()) continue;
                result.add(new TileRecord(
                        json.getString("sceneType"),
                        json.getString("sourceFeatureId"),
                        json.getString("tileId") == null ? tileId : json.getString("tileId"),
                        geometry,
                        json.getJSONObject("properties")));
            }
            return result;
        } catch (IOException ex) {
            throw new IllegalStateException("cannot read maritime scene tile " + path, ex);
        } catch (Exception ex) {
            throw new IllegalStateException("invalid maritime scene tile " + path, ex);
        }
    }

    public static String tileId(double longitude, double latitude) {
        int lon = (int) Math.floor(longitude);
        int lat = (int) Math.floor(latitude);
        return (lat >= 0 ? "N" : "S") + String.format("%02d", Math.abs(lat))
                + "_" + (lon >= 0 ? "E" : "W") + String.format("%03d", Math.abs(lon));
    }

    public static Point point(double longitude, double latitude) {
        return new GeometryFactory().createPoint(new Coordinate(longitude, latitude));
    }

    public static final class TileData {
        private final String tileId;
        private final List<TileRecord> sceneRecords;
        private final List<TileRecord> coastRecords;
        private final STRtree sceneIndex;
        private final STRtree coastIndex;

        private TileData(
                String tileId,
                List<TileRecord> sceneRecords,
                List<TileRecord> coastRecords,
                STRtree sceneIndex,
                STRtree coastIndex) {
            this.tileId = tileId;
            this.sceneRecords = Collections.unmodifiableList(new ArrayList<TileRecord>(sceneRecords));
            this.coastRecords = Collections.unmodifiableList(new ArrayList<TileRecord>(coastRecords));
            this.sceneIndex = sceneIndex;
            this.coastIndex = coastIndex;
        }

        public String getTileId() { return tileId; }
        public List<TileRecord> getSceneRecords() { return sceneRecords; }
        public List<TileRecord> getCoastRecords() { return coastRecords; }

        @SuppressWarnings("unchecked")
        public List<TileRecord> sceneCandidates(Point point) {
            return sceneIndex.query(point.getEnvelopeInternal());
        }

        @SuppressWarnings("unchecked")
        public List<TileRecord> coastCandidates(Envelope envelope) {
            return coastIndex.query(envelope);
        }
    }

    public static final class TileRecord {
        private final String sceneType;
        private final String sourceFeatureId;
        private final String tileId;
        private final Geometry geometry;
        private final PreparedGeometry preparedGeometry;
        private final JSONObject properties;

        public TileRecord(
                String sceneType,
                String sourceFeatureId,
                String tileId,
                Geometry geometry,
                JSONObject properties) {
            this.sceneType = sceneType == null ? "UNKNOWN" : sceneType;
            this.sourceFeatureId = sourceFeatureId;
            this.tileId = tileId;
            this.geometry = geometry;
            this.preparedGeometry = PreparedGeometryFactory.prepare(geometry);
            this.properties = properties == null ? new JSONObject() : properties;
        }

        public String getSceneType() { return sceneType; }
        public String getSourceFeatureId() { return sourceFeatureId; }
        public String getTileId() { return tileId; }
        public Geometry getGeometry() { return geometry; }
        public PreparedGeometry getPreparedGeometry() { return preparedGeometry; }
        public JSONObject getProperties() { return properties; }
        public boolean covers(Point point) { return preparedGeometry.covers(point); }
    }
}
