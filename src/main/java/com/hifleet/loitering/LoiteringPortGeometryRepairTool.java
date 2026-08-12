package com.hifleet.loitering;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.io.WKBReader;

import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Fixed-scope preview/apply tool for the 2026-08-10 port-anchor geometry repair. */
public final class LoiteringPortGeometryRepairTool {
    private static final String DEFAULT_CONFIG = "/data/zhengyi/application.properties";
    private static final String SCHEMA = "dataanalysis";
    private static final String SOURCE = "zones_with_port_id";
    private static final String BACKUP = "loitering_scene_geometry_backup_20260810_port_anchor_v1";

    public static void main(String[] args) throws Exception {
        Map<String, String> options = options(args);
        String mode = options.getOrDefault("mode", "preview");
        String config = options.getOrDefault("config", DEFAULT_CONFIG);
        try (Connection connection = connection(config)) {
            if ("preview".equals(mode)) {
                connection.setReadOnly(true);
                print(LoiteringPortGeometryRepair.repair(loadSource(connection, false)), "PREVIEW", 0);
            } else if ("apply".equals(mode)) {
                apply(connection, required(options, "expected-source-fingerprint"), required(options, "confirm"));
            } else {
                throw new IllegalArgumentException("--mode must be preview or apply");
            }
        }
    }

    private static void apply(Connection connection, String expectedFingerprint, String confirm) throws Exception {
        if (!expectedFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("--expected-source-fingerprint must be 64 lowercase hex characters");
        }
        if (!LoiteringPortGeometryRepair.RUN_ID.equals(confirm)) {
            throw new IllegalArgumentException("--confirm must equal " + LoiteringPortGeometryRepair.RUN_ID);
        }
        LoiteringPortGeometryRepair.RepairResult preflight = LoiteringPortGeometryRepair.repair(loadSource(connection, false));
        if (!expectedFingerprint.equals(preflight.sourceFingerprint)) {
            throw new IllegalStateException("source fingerprint changed expected=" + expectedFingerprint
                    + " actual=" + preflight.sourceFingerprint);
        }
        createBackupTable(connection);
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        connection.setAutoCommit(false);
        try {
            LoiteringPortGeometryRepair.RepairResult repair = LoiteringPortGeometryRepair.repair(loadSource(connection, true));
            if (!expectedFingerprint.equals(repair.sourceFingerprint)) {
                throw new IllegalStateException("source fingerprint changed expected=" + expectedFingerprint
                        + " actual=" + repair.sourceFingerprint);
            }
            ensureBackup(connection, repair);
            try (PreparedStatement statement = connection.prepareStatement("UPDATE `" + SCHEMA + "`.`" + SOURCE
                    + "` SET bound=GeomFromWKB(?,101) WHERE id=? AND SHA2(AsBinary(bound),256)=?")) {
                int updated = 0;
                for (LoiteringPortGeometryRepair.FeatureRepair feature : repair.features) {
                    statement.setBytes(1, feature.repairedWkb);
                    statement.setInt(2, feature.source.id);
                    statement.setString(3, LoiteringPortGeometryRepair.sha256(feature.source.wkb));
                    int count = statement.executeUpdate();
                    if (count != 1) throw new IllegalStateException("repair update count differs ID="
                            + feature.source.id + " count=" + count);
                    updated += count;
                }
                if (updated != LoiteringPortGeometryRepair.REPAIR_IDS.size()) {
                    throw new IllegalStateException("repair update count is not 26 actual=" + updated);
                }
            }
            verifyApplied(connection, repair);
            connection.commit();
            print(repair, "APPLY", repair.features.size());
        } catch (Exception exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private static void createBackupTable(Connection connection) throws Exception {
        String sql = "CREATE TABLE IF NOT EXISTS `" + SCHEMA + "`.`" + BACKUP + "` ("
                + "id int(11) NOT NULL,name varchar(100) DEFAULT NULL,port_id int(11) DEFAULT NULL,"
                + "zones_type int(11) DEFAULT NULL,bound geometry DEFAULT NULL,oid int(11) DEFAULT NULL,"
                + "portcode varchar(50) DEFAULT NULL,create_time datetime DEFAULT NULL,is_delete int(11) DEFAULT NULL,"
                + "original_wkb_sha256 char(64) NOT NULL,backup_at datetime NOT NULL,repair_method varchar(64) NOT NULL,"
                + "repair_run_id varchar(64) NOT NULL,PRIMARY KEY(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8";
        try (Statement statement = connection.createStatement()) { statement.execute(sql); }
    }

    private static void ensureBackup(Connection connection, LoiteringPortGeometryRepair.RepairResult repair) throws Exception {
        int count;
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(
                "SELECT COUNT(*) FROM `" + SCHEMA + "`.`" + BACKUP + "`")) { rs.next(); count = rs.getInt(1); }
        if (count == 0) {
            String sql = "INSERT INTO `" + SCHEMA + "`.`" + BACKUP + "` "
                    + "(id,name,port_id,zones_type,bound,oid,portcode,create_time,is_delete,original_wkb_sha256,backup_at,repair_method,repair_run_id) "
                    + "SELECT id,name,port_id,zones_type,bound,oid,portcode,create_time,is_delete,SHA2(AsBinary(bound),256),NOW(),?,? "
                    + "FROM `" + SCHEMA + "`.`" + SOURCE + "` WHERE id IN (" + ids() + ")";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, LoiteringPortGeometryRepair.METHOD);
                statement.setString(2, LoiteringPortGeometryRepair.RUN_ID);
                if (statement.executeUpdate() != LoiteringPortGeometryRepair.REPAIR_IDS.size()) {
                    throw new IllegalStateException("backup insert count is not 26");
                }
            }
        } else if (count != LoiteringPortGeometryRepair.REPAIR_IDS.size()) {
            throw new IllegalStateException("existing backup count is not 26 actual=" + count);
        }
        List<LoiteringPortGeometryRepair.SourceRow> backupRows = new ArrayList<LoiteringPortGeometryRepair.SourceRow>();
        String sql = "SELECT id,port_id,zones_type,ST_SRID(bound),name,AsBinary(bound),original_wkb_sha256,repair_method,repair_run_id "
                + "FROM `" + SCHEMA + "`.`" + BACKUP + "` ORDER BY id";
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                byte[] wkb = rs.getBytes(6);
                if (!LoiteringPortGeometryRepair.sha256(wkb).equals(rs.getString(7))) {
                    throw new IllegalStateException("backup WKB hash mismatch ID=" + rs.getInt(1));
                }
                if (!LoiteringPortGeometryRepair.METHOD.equals(rs.getString(8))
                        || !LoiteringPortGeometryRepair.RUN_ID.equals(rs.getString(9))) {
                    throw new IllegalStateException("backup metadata mismatch ID=" + rs.getInt(1));
                }
                backupRows.add(new LoiteringPortGeometryRepair.SourceRow(rs.getInt(1), rs.getInt(2), rs.getInt(3),
                        rs.getInt(4), rs.getString(5), wkb));
            }
        }
        String backupFingerprint = LoiteringPortGeometryRepair.fingerprintSource(backupRows);
        if (!repair.sourceFingerprint.equals(backupFingerprint)) {
            throw new IllegalStateException("backup fingerprint mismatch expected=" + repair.sourceFingerprint
                    + " actual=" + backupFingerprint);
        }
    }

    private static void verifyApplied(Connection connection, LoiteringPortGeometryRepair.RepairResult repair) throws Exception {
        Map<Integer, LoiteringPortGeometryRepair.FeatureRepair> expected = new LinkedHashMap<Integer, LoiteringPortGeometryRepair.FeatureRepair>();
        for (LoiteringPortGeometryRepair.FeatureRepair feature : repair.features) expected.put(feature.source.id, feature);
        List<LoiteringPortGeometryRepair.FeatureRepair> applied = new ArrayList<LoiteringPortGeometryRepair.FeatureRepair>();
        String sql = "SELECT id,ST_SRID(bound),AsBinary(bound) FROM `" + SCHEMA + "`.`" + SOURCE
                + "` WHERE id IN (" + ids() + ") ORDER BY id";
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                LoiteringPortGeometryRepair.FeatureRepair feature = expected.get(rs.getInt(1));
                if (feature == null) throw new IllegalStateException("unexpected applied ID=" + rs.getInt(1));
                byte[] wkb = rs.getBytes(3);
                Geometry geometry = new WKBReader().read(wkb); geometry.setSRID(rs.getInt(2));
                if (geometry.isEmpty() || !(geometry instanceof Polygonal) || !geometry.isValid()
                        || geometry.getSRID() != LoiteringPortGeometryRepair.SRID
                        || !Arrays.equals(feature.repairedWkb, wkb)) {
                    throw new IllegalStateException("database repair verification failed ID=" + rs.getInt(1));
                }
                applied.add(new LoiteringPortGeometryRepair.FeatureRepair(feature.source, geometry, wkb,
                        feature.originalError, feature.relativeAreaDelta, feature.envelopeDelta,
                        feature.largeAreaDeltaAccepted));
            }
        }
        if (applied.size() != repair.features.size()
                || !repair.repairedFingerprint.equals(LoiteringPortGeometryRepair.fingerprintRepaired(applied))) {
            throw new IllegalStateException("database repaired fingerprint differs from preview");
        }
    }

    private static List<LoiteringPortGeometryRepair.SourceRow> loadSource(Connection connection, boolean forUpdate) throws Exception {
        List<LoiteringPortGeometryRepair.SourceRow> rows = new ArrayList<LoiteringPortGeometryRepair.SourceRow>();
        String sql = "SELECT id,port_id,zones_type,ST_SRID(bound),name,AsBinary(bound) FROM `" + SCHEMA + "`.`" + SOURCE
                + "` WHERE id IN (" + ids() + ") ORDER BY id" + (forUpdate ? " FOR UPDATE" : "");
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) rows.add(new LoiteringPortGeometryRepair.SourceRow(rs.getInt(1), rs.getInt(2), rs.getInt(3),
                    rs.getInt(4), rs.getString(5), rs.getBytes(6)));
        }
        return rows;
    }

    private static void print(LoiteringPortGeometryRepair.RepairResult repair, String mode, int databaseWrites) throws Exception {
        for (LoiteringPortGeometryRepair.FeatureRepair feature : repair.features) {
            System.out.println("PORT_ANCHOR_REPAIR id=" + feature.source.id + " originalError=" + feature.originalError
                    + " repairedType=" + feature.repaired.getGeometryType() + " components="
                    + feature.repaired.getNumGeometries() + " relativeAreaDelta=" + feature.relativeAreaDelta
                    + " envelopeDelta=" + feature.envelopeDelta + " status="
                    + (feature.largeAreaDeltaAccepted ? "LARGE_AREA_DELTA_ACCEPTED" : "ACCEPTED")
                    + " originalWkbSha256=" + LoiteringPortGeometryRepair.sha256(feature.source.wkb)
                    + " repairedWkbSha256=" + LoiteringPortGeometryRepair.sha256(feature.repairedWkb));
        }
        System.out.println("PORT_ANCHOR_REPAIR " + mode + " PASS rows=" + repair.features.size()
                + " sourceFingerprint=" + repair.sourceFingerprint + " repairedFingerprint="
                + repair.repairedFingerprint + " databaseWrites=" + databaseWrites);
    }

    private static Connection connection(String configFile) throws Exception {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(configFile)) { properties.load(input); }
        Class.forName(properties.getProperty("spring.datasource.newdataanalysis.driver-class-name", "com.mysql.jdbc.Driver"));
        Connection connection = DriverManager.getConnection(required(properties, "spring.datasource.newdataanalysis.url"),
                required(properties, "spring.datasource.newdataanalysis.username"),
                required(properties, "spring.datasource.newdataanalysis.password"));
        if (!SCHEMA.equals(connection.getCatalog())) connection.setCatalog(SCHEMA);
        return connection;
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (String arg : args) {
            if (!arg.startsWith("--") || arg.indexOf('=') < 3) throw new IllegalArgumentException("invalid option=" + arg);
            int split = arg.indexOf('='); String key = arg.substring(2, split); String value = arg.substring(split + 1);
            if (!Arrays.asList("mode", "config", "expected-source-fingerprint", "confirm").contains(key)
                    || values.put(key, value) != null) throw new IllegalArgumentException("unknown/duplicate option=" + key);
        }
        return values;
    }
    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("required option missing --" + key);
        return value.trim();
    }
    private static String required(Properties values, String key) {
        String value = values.getProperty(key);
        if (value == null || value.trim().isEmpty()) throw new IllegalStateException("required datasource property missing: " + key);
        return value.trim();
    }
    private static String ids() {
        StringBuilder value = new StringBuilder();
        for (Integer id : LoiteringPortGeometryRepair.REPAIR_IDS) {
            if (value.length() > 0) value.append(','); value.append(id);
        }
        return value.toString();
    }
    private LoiteringPortGeometryRepairTool() {}
}
