package com.hifleet.loitering;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class LoiteringPortSceneLoader {
    private final JdbcTemplate dataanalysis;
    private final JdbcTemplate ais41;
    private final LoiteringPortSceneProperties properties;

    @Autowired public LoiteringPortSceneLoader(@Qualifier("newDataanalysisJdbcTemplate") JdbcTemplate dataanalysis,
                                                @Qualifier("aid41JdbcTemplate") JdbcTemplate ais41,
                                                LoiteringPortSceneProperties properties) {
        this.dataanalysis = dataanalysis; this.ais41 = ais41; this.properties = properties;
    }

    public LoiteringPortSceneSnapshot load() {
        properties.validate();
        try {
            List<Row> operational = dataanalysis.query("SELECT id,name,AsBinary(bound) wkb FROM dataanalysis.zones_with_port_id "
                    + "WHERE is_delete=0 AND bound IS NOT NULL AND zones_type IN (2,3) "
                    + "AND LOWER(name) NOT LIKE '%anchor%' AND name NOT LIKE '%锚地%' AND name NOT LIKE '%锚泊%'", (rs, n) ->
                    new Row("PORT_OPERATIONAL_ZONE", rs.getInt(1), rs.getString(2), rs.getBytes(3)));
            List<Row> anchors = ais41.query("SELECT oid,name,AsBinary(bound) wkb FROM ais41.alertzones "
                    + "WHERE bound IS NOT NULL AND ST_GeometryType(bound) IN ('POLYGON','MULTIPOLYGON') "
                    + "AND (LOWER(name) LIKE '%anchor%' OR name LIKE '%锚地%' OR name LIKE '%锚泊%')", (rs, n) ->
                    new Row("ANCHORAGE_ZONE", rs.getInt(1), rs.getString(2), rs.getBytes(3)));
            List<LoiteringPortSceneSnapshot.Port> ports = dataanalysis.query("SELECT m.port_id,p.portname,p.lon,p.lat "
                    + "FROM dataanalysis.portcode_portid_mapping m JOIN dataanalysis.newports p ON p.portcode=m.portcode "
                    + "WHERE p.lon IS NOT NULL AND p.lat IS NOT NULL", (rs, n) -> new LoiteringPortSceneSnapshot.Port(
                    rs.getInt(1), rs.getString(2), rs.getDouble(3), rs.getDouble(4)));
            return build(operational, anchors, ports);
        } catch (Exception ex) {
            throw new IllegalStateException("LOITERING_PORT_SCENE_UNAVAILABLE", ex);
        }
    }

    LoiteringPortSceneSnapshot build(List<Row> operational, List<Row> anchors,
                                     List<LoiteringPortSceneSnapshot.Port> ports) {
        if (operational == null || operational.isEmpty() || anchors == null || anchors.isEmpty()
                || ports == null || ports.isEmpty()) throw new IllegalStateException("LOITERING_PORT_SCENE_UNAVAILABLE");
        try {
            STRtree opIndex = new STRtree(), anchorIndex = new STRtree();
            WKBReader reader = new WKBReader(); MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<Row> rows = new ArrayList<Row>(); rows.addAll(operational); rows.addAll(anchors);
            Collections.sort(rows, Comparator.comparing(Row::sortKey));
            for (Row row : rows) {
                Geometry geometry = reader.read(row.wkb);
                if (geometry.isEmpty() || !geometry.isValid() || !("Polygon".equals(geometry.getGeometryType())
                        || "MultiPolygon".equals(geometry.getGeometryType()))) throw new IllegalStateException("LOITERING_PORT_SCENE_UNAVAILABLE");
                update(digest, row.type); update(digest, String.valueOf(row.id)); update(digest, row.name); digest.update(row.wkb);
                STRtree index = "PORT_OPERATIONAL_ZONE".equals(row.type) ? opIndex : anchorIndex;
                index.insert(geometry.getEnvelopeInternal(), LoiteringPortSceneSnapshot.entry(geometry, PreparedGeometryFactory.prepare(geometry)));
            }
            Collections.sort(ports, Comparator.comparingInt(port -> port.id));
            for (LoiteringPortSceneSnapshot.Port port : ports) {
                if (!Double.isFinite(port.lon) || !Double.isFinite(port.lat) || port.lon < -180 || port.lon > 180 || port.lat < -90 || port.lat > 90) throw new IllegalStateException("LOITERING_PORT_SCENE_UNAVAILABLE");
                update(digest, "PORT"); update(digest, String.valueOf(port.id)); update(digest, port.name);
                update(digest, port.lon + "," + port.lat);
            }
            opIndex.build(); anchorIndex.build();
            return new LoiteringPortSceneSnapshot(opIndex, anchorIndex, ports, hex(digest.digest()));
        } catch (Exception ex) { throw new IllegalStateException("LOITERING_PORT_SCENE_UNAVAILABLE", ex); }
    }
    static final class Row { final String type; final int id; final String name; final byte[] wkb;
        Row(String type, int id, String name, byte[] wkb) { this.type=type; this.id=id; this.name=name; this.wkb=wkb; }
        String sortKey() { return type + "\u0000" + id + "\u0000" + (name == null ? "" : name); } }
    private static void update(MessageDigest d, String value) { byte[] b=(value==null?"":value).getBytes(StandardCharsets.UTF_8); d.update(ByteBuffer.allocate(4).putInt(b.length).array()); d.update(b); }
    private static String hex(byte[] value) { StringBuilder r=new StringBuilder(); for(byte b:value) r.append(String.format("%02x", b & 255)); return r.toString(); }
}
