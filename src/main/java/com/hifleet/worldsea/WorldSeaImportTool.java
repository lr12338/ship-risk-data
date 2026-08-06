package com.hifleet.worldsea;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/** One-time, fixed-scope importer for WORLD_SEA_MASK. It never logs credentials. */
public final class WorldSeaImportTool {
    private static final String IMPORT_FILE = "/data/zhengyi/import/world-sea-mask-v1/world-sea-dataanalysis-import.sql";
    private static final String CONFIG_FILE = "/data/zhengyi/application.properties";
    private static final String SCHEMA = "dataanalysis";
    private static final String FORMAL = "ship_behavior_world_sea";
    private static final Pattern STAGE = Pattern.compile("ship_behavior_world_sea_stage_\\d{8}_\\d{6}");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !("DRY_RUN".equals(args[0]) || "APPLY".equals(args[0])
                || "VALIDATE_STAGE".equals(args[0]) || "STATE".equals(args[0]))
                || !STAGE.matcher(args[1]).matches()) {
            throw new IllegalArgumentException("usage: WorldSeaImportTool DRY_RUN|STATE|VALIDATE_STAGE|APPLY ship_behavior_world_sea_stage_YYYYMMDD_HHMMSS");
        }
        File file = new File(IMPORT_FILE);
        if (!file.isFile()) throw new IllegalStateException("fixed import file is missing");
        List<String> statements = statements(file);
        validate(statements, args[1]);
        String sourceSha = sourceSha(file);
        long maxStatementBytes = maxBytes(statements);
        if ("DRY_RUN".equals(args[0])) {
            System.out.println("WORLD_SEA_IMPORT DRY_RUN PASS stage=" + args[1]
                    + " statements=" + statements.size() + " sourceSha256=" + sourceSha
                    + " maxStatementBytes=" + maxStatementBytes + " databaseWrites=0");
            return;
        }
        if ("STATE".equals(args[0])) state(args[1]);
        else if ("VALIDATE_STAGE".equals(args[0])) validateExistingStage(args[1]);
        else apply(args[1], statements, sourceSha, maxStatementBytes);
    }

    private static void state(String stage) throws Exception {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(CONFIG_FILE)) { properties.load(input); }
        Class.forName(properties.getProperty("spring.datasource.newdataanalysis.driver-class-name", "com.mysql.jdbc.Driver"));
        try (Connection connection = DriverManager.getConnection(
                required(properties, "spring.datasource.newdataanalysis.url"),
                required(properties, "spring.datasource.newdataanalysis.username"),
                required(properties, "spring.datasource.newdataanalysis.password"))) {
            System.out.println("WORLD_SEA_IMPORT STATE stageExists=" + tableExists(connection, stage)
                    + " stageRows=" + count(connection, stage)
                    + " formalExists=" + tableExists(connection, FORMAL)
                    + " metaExists=" + tableExists(connection, "ship_behavior_world_sea_meta")
                    + " auditExists=" + tableExists(connection, "ship_behavior_world_sea_audit")
                    + " maxAllowedPacket=" + maxAllowedPacket(connection)
                    + " srids=" + grouped(connection, stage, "SRID(the_geom)")
                    + " geometryTypes=" + grouped(connection, stage, "GeometryType(the_geom)")
                    + " databaseWrites=0");
        }
    }

    private static void validateExistingStage(String stage) throws Exception {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(CONFIG_FILE)) { properties.load(input); }
        Class.forName(properties.getProperty("spring.datasource.newdataanalysis.driver-class-name", "com.mysql.jdbc.Driver"));
        try (Connection connection = DriverManager.getConnection(
                required(properties, "spring.datasource.newdataanalysis.url"),
                required(properties, "spring.datasource.newdataanalysis.username"),
                required(properties, "spring.datasource.newdataanalysis.password"))) {
            Validation value = validateTable(connection, stage);
            System.out.println("WORLD_SEA_STAGE_VALIDATION PASS stage=" + stage + " rows=" + value.rows.size()
                    + " polygons=" + value.polygons + " multiPolygons=" + value.multiPolygons
                    + " vertices=" + value.vertices + " geometryFingerprint=" + value.fingerprint
                    + " databaseWrites=0");
        }
    }

    private static void apply(String stage, List<String> statements, String sourceSha,
                              long maxStatementBytes) throws Exception {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(CONFIG_FILE)) { properties.load(input); }
        String url = required(properties, "spring.datasource.newdataanalysis.url");
        String user = required(properties, "spring.datasource.newdataanalysis.username");
        String password = required(properties, "spring.datasource.newdataanalysis.password");
        Class.forName(properties.getProperty("spring.datasource.newdataanalysis.driver-class-name", "com.mysql.jdbc.Driver"));
        long started = System.currentTimeMillis();
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            if (!SCHEMA.equals(connection.getCatalog())) connection.setCatalog(SCHEMA);
            long packet = maxAllowedPacket(connection);
            if (maxStatementBytes >= packet) {
                throw new IllegalStateException("controlled INSERT exceeds max_allowed_packet; no global setting changed");
            }
            if (tableExists(connection, stage)) throw new IllegalStateException("stage already exists; refusing to drop it");
            int inserted = 0;
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements) {
                    statement.execute(sql);
                    if (sql.startsWith("INSERT INTO")) inserted++;
                }
            }
            Validation validation = validateTable(connection, stage);
            if (inserted != 102 || validation.rows.size() != 102) {
                throw new IllegalStateException("stage row count mismatch");
            }
            String backup = null;
            if (!tableExists(connection, FORMAL)) {
                rename(connection, "RENAME TABLE `" + SCHEMA + "`.`" + stage + "` TO `" + SCHEMA + "`.`" + FORMAL + "`");
            } else {
                Validation current = validateTable(connection, FORMAL);
                if (current.fingerprint.equals(validation.fingerprint)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("DROP TABLE `" + SCHEMA + "`.`" + stage + "`");
                    }
                } else {
                    backup = FORMAL + "_backup_" + LocalDateTime.now().format(STAMP);
                    rename(connection, "RENAME TABLE `" + SCHEMA + "`.`" + FORMAL + "` TO `" + SCHEMA + "`.`" + backup
                            + "`, `" + SCHEMA + "`.`" + stage + "` TO `" + SCHEMA + "`.`" + FORMAL + "`");
                }
            }
            createMeta(connection);
            createAudit(connection);
            upsertMeta(connection, sourceSha, validation);
            System.out.println("WORLD_SEA_IMPORT APPLY PASS stage=" + stage + " formal=" + FORMAL
                    + " backup=" + (backup == null ? "NONE" : backup) + " rows=" + validation.rows.size()
                    + " polygons=" + validation.polygons + " multiPolygons=" + validation.multiPolygons
                    + " vertices=" + validation.vertices + " geometryFingerprint=" + validation.fingerprint
                    + " elapsedMs=" + (System.currentTimeMillis() - started));
        }
    }

    private static Validation validateTable(Connection connection, String table) throws Exception {
        List<WorldSeaMaskLoader.Row> rows = new ArrayList<WorldSeaMaskLoader.Row>();
        List<String> invalid = new ArrayList<String>();
        int polygons = 0, multiPolygons = 0; long vertices = 0;
        double minLon = Double.POSITIVE_INFINITY, maxLon = Double.NEGATIVE_INFINITY;
        double minLat = Double.POSITIVE_INFINITY, maxLat = Double.NEGATIVE_INFINITY;
        String sql = "SELECT ID, NAME, CNNAME, AsBinary(the_geom) geom_wkb FROM `" + SCHEMA + "`.`" + table + "` ORDER BY ID";
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            WKBReader reader = new WKBReader();
            while (rs.next()) {
                byte[] wkb = rs.getBytes("geom_wkb");
                Geometry geometry = reader.read(wkb);
                org.locationtech.jts.operation.valid.IsValidOp validity =
                        new org.locationtech.jts.operation.valid.IsValidOp(geometry);
                if (geometry.isEmpty() || !validity.isValid()) invalid.add("ID="
                        + rs.getInt("ID") + " reason=" + validity.getValidationError()
                        + " wkbSha256=" + sha256(wkb) + " vertices=" + geometry.getNumPoints());
                if ("Polygon".equals(geometry.getGeometryType())) polygons++;
                else if ("MultiPolygon".equals(geometry.getGeometryType())) multiPolygons++;
                else throw new IllegalStateException("unsupported geometry type=" + geometry.getGeometryType());
                for (org.locationtech.jts.geom.Coordinate coordinate : geometry.getCoordinates()) {
                    if (!Double.isFinite(coordinate.x) || !Double.isFinite(coordinate.y)
                            || coordinate.x < -180 || coordinate.x > 180 || coordinate.y < -90 || coordinate.y > 90) {
                        throw new IllegalStateException("coordinate out of range ID=" + rs.getInt("ID"));
                    }
                }
                vertices += geometry.getNumPoints();
                minLon = Math.min(minLon, geometry.getEnvelopeInternal().getMinX());
                maxLon = Math.max(maxLon, geometry.getEnvelopeInternal().getMaxX());
                minLat = Math.min(minLat, geometry.getEnvelopeInternal().getMinY());
                maxLat = Math.max(maxLat, geometry.getEnvelopeInternal().getMaxY());
                rows.add(new WorldSeaMaskLoader.Row(rs.getInt("ID"), rs.getString("NAME"), rs.getString("CNNAME"), wkb));
            }
        }
        if (polygons + multiPolygons != rows.size()) throw new IllegalStateException("geometry type count mismatch");
        String fingerprint = WorldSeaMaskLoader.fingerprint(rows);
        if (!invalid.isEmpty()) throw new IllegalStateException("stage validation failed rows=" + rows.size()
                + " polygons=" + polygons + " multiPolygons=" + multiPolygons + " vertices=" + vertices
                + " coordinateRange=[" + minLon + "," + maxLon + "]x[" + minLat + "," + maxLat + "]"
                + " geometryFingerprint=" + fingerprint + " invalidGeometries=" + invalid);
        return new Validation(rows, polygons, multiPolygons, vertices, fingerprint);
    }

    private static void createMeta(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_behavior_world_sea_meta` ("
                    + "dataset_version varchar(64) NOT NULL, source_file_name varchar(255) DEFAULT NULL, "
                    + "source_file_sha256 varchar(64) NOT NULL, geometry_fingerprint varchar(64) NOT NULL, "
                    + "feature_count int NOT NULL, polygon_count int NOT NULL, multipolygon_count int NOT NULL, "
                    + "vertex_count bigint NOT NULL, coordinate_assumption varchar(128) NOT NULL, "
                    + "imported_at datetime NOT NULL, imported_by varchar(128) DEFAULT NULL, status varchar(20) NOT NULL, "
                    + "PRIMARY KEY (dataset_version)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private static void createAudit(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_behavior_world_sea_audit` ("
                    + "id bigint NOT NULL AUTO_INCREMENT,batch_no varchar(64) NOT NULL,mmsi varchar(20) NOT NULL,"
                    + "behavior_type varchar(32) NOT NULL,window_start_time datetime NOT NULL,window_end_time datetime NOT NULL,"
                    + "raw_event_count int NOT NULL DEFAULT 0,world_sea_pass_event_count int NOT NULL DEFAULT 0,"
                    + "world_sea_rejected_event_count int NOT NULL DEFAULT 0,world_sea_checked_point_count int NOT NULL DEFAULT 0,"
                    + "world_sea_rejected_window_count int NOT NULL DEFAULT 0,saved_replay_query_count int NOT NULL DEFAULT 0,"
                    + "created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "PRIMARY KEY(id),UNIQUE KEY uk_world_sea_batch_ship_window(batch_no,mmsi,behavior_type,window_start_time,window_end_time),"
                    + "KEY idx_world_sea_audit_batch(batch_no,behavior_type),KEY idx_world_sea_audit_ship_time(mmsi,window_start_time,window_end_time)) "
                    + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private static void upsertMeta(Connection connection, String sourceSha, Validation value) throws Exception {
        String sql = "INSERT INTO dataanalysis.ship_behavior_world_sea_meta "
                + "(dataset_version,source_file_name,source_file_sha256,geometry_fingerprint,feature_count,polygon_count,"
                + "multipolygon_count,vertex_count,coordinate_assumption,imported_at,imported_by,status) "
                + "VALUES (?,?,?,?,?,?,?,?,?,NOW(),?,'ACTIVE') ON DUPLICATE KEY UPDATE source_file_name=VALUES(source_file_name),"
                + "source_file_sha256=VALUES(source_file_sha256),geometry_fingerprint=VALUES(geometry_fingerprint),"
                + "feature_count=VALUES(feature_count),polygon_count=VALUES(polygon_count),multipolygon_count=VALUES(multipolygon_count),"
                + "vertex_count=VALUES(vertex_count),coordinate_assumption=VALUES(coordinate_assumption),"
                + "imported_at=VALUES(imported_at),imported_by=VALUES(imported_by),status='ACTIVE'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "WORLD_SEA_102_V1"); statement.setString(2, "world_sea.sql");
            statement.setString(3, sourceSha); statement.setString(4, value.fingerprint);
            statement.setInt(5, value.rows.size()); statement.setInt(6, value.polygons);
            statement.setInt(7, value.multiPolygons); statement.setLong(8, value.vertices);
            statement.setString(9, WorldSeaMaskLoader.COORDINATE_ASSUMPTION);
            statement.setString(10, System.getProperty("user.name", "world-sea-import-tool"));
            statement.executeUpdate();
        }
    }

    private static List<String> statements(File file) throws Exception {
        List<String> result = new ArrayList<String>(); StringBuilder current = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line; while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("--")) continue;
                current.append(line).append('\n');
                if (line.trim().endsWith(";")) {
                    result.add(current.toString().trim()); current.setLength(0);
                }
            }
        }
        if (current.length() != 0) throw new IllegalStateException("unterminated controlled SQL statement");
        return result;
    }

    private static void validate(List<String> statements, String stage) {
        if (statements.size() != 103) throw new IllegalStateException("controlled SQL must contain one CREATE and 102 INSERT statements");
        String target = "`dataanalysis`.`" + stage + "`";
        for (int i = 0; i < statements.size(); i++) {
            String upper = statements.get(i).toUpperCase();
            if (upper.contains("CREATE DATABASE") || upper.contains("DROP TABLE") || upper.matches("(?s).*\\bUSE\\s+.*"))
                throw new IllegalStateException("forbidden SQL in controlled import");
            if (!statements.get(i).contains(target)) throw new IllegalStateException("non-allowlisted import target");
            if (i == 0 && !upper.startsWith("CREATE TABLE") || i > 0 && !upper.startsWith("INSERT INTO"))
                throw new IllegalStateException("unexpected controlled SQL statement");
        }
    }

    private static String sourceSha(File file) throws Exception {
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            if (line.startsWith("-- source_sha256=")) return line.substring("-- source_sha256=".length()).trim();
            if (!line.startsWith("--")) break;
        }
        throw new IllegalStateException("source SHA header missing");
    }
    private static long maxBytes(List<String> values) { long max = 0; for (String v : values) max = Math.max(max, v.getBytes(StandardCharsets.UTF_8).length); return max; }
    private static String sha256(byte[] value) throws Exception { byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(value); StringBuilder result = new StringBuilder(); for (byte b : digest) result.append(String.format("%02x", b & 0xff)); return result.toString(); }
    private static String required(Properties properties, String key) { String value = properties.getProperty(key); if (value == null || value.trim().isEmpty()) throw new IllegalStateException("required datasource property missing: " + key); return value.trim(); }
    private static long maxAllowedPacket(Connection connection) throws Exception { try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SHOW VARIABLES LIKE 'max_allowed_packet'")) { if (!rs.next()) throw new IllegalStateException("max_allowed_packet unavailable"); return rs.getLong(2); } }
    private static boolean tableExists(Connection connection, String table) throws Exception { try (PreparedStatement s = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name=?")) { s.setString(1, SCHEMA); s.setString(2, table); try (ResultSet rs = s.executeQuery()) { rs.next(); return rs.getInt(1) == 1; } } }
    private static int count(Connection connection, String table) throws Exception { if (!tableExists(connection, table)) return 0; try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM `" + SCHEMA + "`.`" + table + "`")) { rs.next(); return rs.getInt(1); } }
    private static String grouped(Connection connection, String table, String expression) throws Exception { if (!tableExists(connection, table)) return "[]"; List<String> values = new ArrayList<String>(); try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT " + expression + " value,COUNT(*) count_value FROM `" + SCHEMA + "`.`" + table + "` GROUP BY " + expression + " ORDER BY value")) { while (rs.next()) values.add(rs.getString(1) + ":" + rs.getInt(2)); } return values.toString(); }
    private static void rename(Connection connection, String sql) throws Exception { try (Statement s = connection.createStatement()) { s.execute(sql); } }
    private static final class Validation {
        final List<WorldSeaMaskLoader.Row> rows; final int polygons; final int multiPolygons; final long vertices; final String fingerprint;
        Validation(List<WorldSeaMaskLoader.Row> rows, int polygons, int multiPolygons, long vertices, String fingerprint) { this.rows = rows; this.polygons = polygons; this.multiPolygons = multiPolygons; this.vertices = vertices; this.fingerprint = fingerprint; }
    }
    private WorldSeaImportTool() {}
}
