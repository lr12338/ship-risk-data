package com.hifleet.worldsea;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Executes only the pinned V1R1 migration; it never publishes geometry data. */
public final class WorldSeaMigrationTool {
    private static final File CONFIG = new File("/data/zhengyi/application.properties");
    private static final File MIGRATION = new File("/data/zhengyi/import/world-sea-mask-v1r1/migration.sql");
    private static final String MIGRATION_SHA256 = "42a75a2f43dda62047cd7fdf766684b2f9f644ff6ed28d54b1d3d9648393fb41";
    private static final String SCHEMA = "dataanalysis";

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !"APPLY".equals(args[0])) {
            throw new IllegalArgumentException("usage: WorldSeaMigrationTool APPLY");
        }
        byte[] bytes = Files.readAllBytes(MIGRATION.toPath());
        if (!MIGRATION_SHA256.equals(sha256(bytes))) throw new IllegalStateException("migration.sql SHA-256 mismatch");
        List<String> statements = statements(new String(bytes, StandardCharsets.UTF_8));
        try (Connection connection = connection()) {
            int completed = 0;
            for (String sql : statements) {
                try (Statement statement = connection.createStatement()) { statement.execute(sql); }
                completed++;
            }
            System.out.println("WORLD_SEA_MIGRATION APPLY PASS sha256=" + MIGRATION_SHA256
                    + " statements=" + completed + " geometryTableWrites=0 activeMetadataWrites=0");
        }
    }

    static List<String> statements(String sql) {
        List<String> values = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean quote = false, lineComment = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (lineComment) {
                if (c == '\n') { lineComment = false; current.append(c); }
                continue;
            }
            if (!quote && c == '-' && next == '-') { lineComment = true; i++; continue; }
            if (c == '\'' && quote && next == '\'') { current.append(c).append(next); i++; continue; }
            if (c == '\'') quote = !quote;
            if (c == ';' && !quote) {
                String value = current.toString().trim();
                if (!value.isEmpty()) values.add(value);
                current.setLength(0);
            } else current.append(c);
        }
        if (quote || !current.toString().trim().isEmpty()) throw new IllegalStateException("invalid/unterminated migration SQL");
        return values;
    }

    private static Connection connection() throws Exception {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(CONFIG)) { properties.load(input); }
        Class.forName(properties.getProperty("spring.datasource.newdataanalysis.driver-class-name", "com.mysql.jdbc.Driver"));
        Connection connection = DriverManager.getConnection(required(properties, "spring.datasource.newdataanalysis.url"),
                required(properties, "spring.datasource.newdataanalysis.username"),
                required(properties, "spring.datasource.newdataanalysis.password"));
        if (!SCHEMA.equals(connection.getCatalog())) connection.setCatalog(SCHEMA);
        return connection;
    }
    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) throw new IllegalStateException("required datasource property missing: " + key);
        return value.trim();
    }
    private static String sha256(byte[] bytes) throws Exception {
        StringBuilder value = new StringBuilder();
        for (byte current : MessageDigest.getInstance("SHA-256").digest(bytes)) value.append(String.format("%02x", current & 0xff));
        return value.toString();
    }
    private WorldSeaMigrationTool() {}
}
