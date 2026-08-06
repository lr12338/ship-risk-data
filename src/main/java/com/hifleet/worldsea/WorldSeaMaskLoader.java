package com.hifleet.worldsea;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.io.WKBReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

@Service
public class WorldSeaMaskLoader {
    static final String COORDINATE_ASSUMPTION = "LON_LAT_WGS84_LIKE_UNVERIFIED";
    private final JdbcTemplate jdbcTemplate;
    private final WorldSeaMaskProperties properties;

    @Autowired
    public WorldSeaMaskLoader(@Qualifier("newDataanalysisJdbcTemplate") JdbcTemplate jdbcTemplate,
                              WorldSeaMaskProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public WorldSeaMaskSnapshot load() {
        long started = System.currentTimeMillis();
        properties.validate();
        String metaSql = "SELECT dataset_version, source_file_sha256, geometry_fingerprint, "
                + "coordinate_assumption FROM dataanalysis.ship_behavior_world_sea_meta "
                + "WHERE dataset_version=? AND status='ACTIVE'";
        List<Map<String, Object>> metaRows = jdbcTemplate.queryForList(metaSql, properties.getDatasetVersion());
        if (metaRows.size() != 1) {
            throw new IllegalStateException("WORLD_SEA_MASK active metadata row missing or duplicated");
        }
        String sql = "SELECT ID, NAME, CNNAME, AsBinary(the_geom) AS geom_wkb "
                + "FROM dataanalysis.ship_behavior_world_sea WHERE the_geom IS NOT NULL ORDER BY ID";
        List<Row> rows = jdbcTemplate.query(sql, (rs, rowNum) ->
                new Row(rs.getInt("ID"), rs.getString("NAME"), rs.getString("CNNAME"), rs.getBytes("geom_wkb")));
        return build(metaRows.get(0), rows, started);
    }

    WorldSeaMaskSnapshot build(Map<String, Object> meta, List<Row> rows, long started) {
        if (rows == null || rows.size() != properties.getExpectedFeatureCount()) {
            throw new IllegalStateException("WORLD_SEA_MASK feature count mismatch expected="
                    + properties.getExpectedFeatureCount() + " actual=" + (rows == null ? 0 : rows.size()));
        }
        try {
            STRtree index = new STRtree();
            WKBReader reader = new WKBReader();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Set<Integer> ids = new HashSet<Integer>();
            List<Row> sortedRows = new ArrayList<Row>(rows);
            Collections.sort(sortedRows, new Comparator<Row>() {
                @Override public int compare(Row left, Row right) {
                    return Integer.compare(left.id, right.id);
                }
            });
            int polygons = 0;
            int multiPolygons = 0;
            long vertices = 0L;
            for (Row row : sortedRows) {
                if (!ids.add(row.id)) throw new IllegalStateException("WORLD_SEA_MASK duplicate ID=" + row.id);
                if (row.wkb == null || row.wkb.length == 0) throw new IllegalStateException("WORLD_SEA_MASK empty geometry ID=" + row.id);
                Geometry geometry = reader.read(row.wkb);
                String type = geometry.getGeometryType();
                if ("Polygon".equals(type)) polygons++;
                else if ("MultiPolygon".equals(type)) multiPolygons++;
                else throw new IllegalStateException("WORLD_SEA_MASK unsupported geometry type=" + type + " ID=" + row.id);
                if (geometry.isEmpty() || !geometry.isValid()) throw new IllegalStateException("WORLD_SEA_MASK invalid geometry ID=" + row.id);
                for (Coordinate coordinate : geometry.getCoordinates()) {
                    if (!Double.isFinite(coordinate.x) || !Double.isFinite(coordinate.y)
                            || coordinate.x < -180.0 || coordinate.x > 180.0
                            || coordinate.y < -90.0 || coordinate.y > 90.0) {
                        throw new IllegalStateException("WORLD_SEA_MASK coordinate out of range ID=" + row.id);
                    }
                }
                vertices += geometry.getNumPoints();
                update(digest, ByteBuffer.allocate(4).putInt(row.id).array());
                update(digest, bytes(row.name));
                update(digest, bytes(row.cnName));
                update(digest, row.wkb);
                index.insert(geometry.getEnvelopeInternal(), WorldSeaMaskSnapshot.entry(
                        row.id, row.name, row.cnName, geometry, PreparedGeometryFactory.prepare(geometry)));
            }
            index.build();
            String fingerprint = hex(digest.digest());
            String expectedFingerprint = string(meta.get("geometry_fingerprint"));
            if (!fingerprint.equalsIgnoreCase(expectedFingerprint)) {
                throw new IllegalStateException("WORLD_SEA_MASK database geometry fingerprint mismatch");
            }
            String assumption = string(meta.get("coordinate_assumption"));
            if (!COORDINATE_ASSUMPTION.equals(assumption)) {
                throw new IllegalStateException("WORLD_SEA_MASK coordinate assumption mismatch");
            }
            List<String> samples = new java.util.ArrayList<String>();
            for (int i = 0; i < Math.min(5, sortedRows.size()); i++) samples.add(sortedRows.get(i).name);
            return new WorldSeaMaskSnapshot(index, rows.size(), polygons, multiPolygons, vertices,
                    string(meta.get("dataset_version")), string(meta.get("source_file_sha256")),
                    fingerprint, System.currentTimeMillis() - started, assumption, samples);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("WORLD_SEA_MASK load failed", ex);
        }
    }

    static String fingerprint(List<Row> rows) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<Row> sorted = new ArrayList<Row>(rows);
            Collections.sort(sorted, new Comparator<Row>() {
                @Override public int compare(Row left, Row right) {
                    return Integer.compare(left.id, right.id);
                }
            });
            for (Row row : sorted) {
                update(digest, ByteBuffer.allocate(4).putInt(row.id).array());
                update(digest, bytes(row.name)); update(digest, bytes(row.cnName)); update(digest, row.wkb);
            }
            return hex(digest.digest());
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private static byte[] bytes(String value) { return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8); }
    private static void update(MessageDigest digest, byte[] bytes) {
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
    }
    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder();
        for (byte b : value) result.append(String.format("%02x", b & 0xff));
        return result.toString();
    }

    static final class Row {
        final int id; final String name; final String cnName; final byte[] wkb;
        Row(int id, String name, String cnName, byte[] wkb) {
            this.id = id; this.name = name; this.cnName = cnName; this.wkb = wkb;
        }
        int getId() { return id; }
        String getName() { return name; }
        String getCnName() { return cnName; }
        byte[] getWkb() { return wkb; }
    }
}
