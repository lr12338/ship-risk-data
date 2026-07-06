package com.hifleet.analysis.alongside.output.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

public final class AlongsideDbLocalVerifier {

    private AlongsideDbLocalVerifier() {
    }

    public static void main(String[] args) throws Exception {
        Env env = Env.load();
        String mode = args.length == 0 ? "init" : args[0];
        if ("query".equals(mode)) {
            query(env, args[1]);
            return;
        }
        if ("execute".equals(mode)) {
            execute(env, args[1]);
            return;
        }
        init(env);
    }

    private static void init(Env env) throws SQLException {
        try (Connection server = DriverManager.getConnection(env.serverUrl, env.username, env.password);
             Statement statement = server.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS `" + env.database + "` "
                + "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        AlongsideDbSinkConfig config = new AlongsideDbSinkConfig();
        config.setEventTable(env.eventTable);
        config.setDetailTable(env.detailTable);
        try (Connection connection = DriverManager.getConnection(env.url, env.username, env.password)) {
            new AlongsideDbTableInitializer().initialize(connection, config);
            printQuery(connection, "SHOW TABLES LIKE 'ship_realtime_alongside%'");
            printQuery(connection, "DESCRIBE " + env.eventTable);
            printQuery(connection, "DESCRIBE " + env.detailTable);
        }
    }

    private static void query(Env env, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(env.url, env.username, env.password)) {
            printQuery(connection, sql);
        }
    }

    private static void execute(Env env, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(env.url, env.username, env.password);
             Statement statement = connection.createStatement()) {
            int count = statement.executeUpdate(sql);
            System.out.println("updated_rows\t" + count);
        }
    }

    private static void printQuery(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columns = metaData.getColumnCount();
            for (int i = 1; i <= columns; i++) {
                if (i > 1) {
                    System.out.print('\t');
                }
                System.out.print(metaData.getColumnLabel(i));
            }
            System.out.println();
            while (resultSet.next()) {
                for (int i = 1; i <= columns; i++) {
                    if (i > 1) {
                        System.out.print('\t');
                    }
                    Object value = resultSet.getObject(i);
                    System.out.print(value == null ? "NULL" : String.valueOf(value));
                }
                System.out.println();
            }
        }
    }

    private static final class Env {
        private final String url;
        private final String serverUrl;
        private final String username;
        private final String password;
        private final String database;
        private final String eventTable;
        private final String detailTable;

        private Env(String url, String serverUrl, String username, String password, String database,
                    String eventTable, String detailTable) {
            this.url = url;
            this.serverUrl = serverUrl;
            this.username = username;
            this.password = password;
            this.database = database;
            this.eventTable = eventTable;
            this.detailTable = detailTable;
        }

        private static Env load() {
            String url = require("ALONGSIDE_DB_URL");
            String username = require("ALONGSIDE_DB_USERNAME");
            String password = require("ALONGSIDE_DB_PASSWORD");
            String eventTable = value("ALONGSIDE_DB_TABLE_EVENT", "ship_realtime_alongside_event");
            String detailTable = value("ALONGSIDE_DB_TABLE_DETAIL", "ship_realtime_alongside_event_detail");
            String withoutPrefix = url.substring("jdbc:mysql://".length());
            int queryIndex = withoutPrefix.indexOf('?');
            String query = queryIndex >= 0 ? withoutPrefix.substring(queryIndex) : "";
            String addressAndDb = queryIndex >= 0 ? withoutPrefix.substring(0, queryIndex) : withoutPrefix;
            int slashIndex = addressAndDb.indexOf('/');
            String address = slashIndex >= 0 ? addressAndDb.substring(0, slashIndex) : addressAndDb;
            String database = slashIndex >= 0 ? addressAndDb.substring(slashIndex + 1) : "";
            String serverUrl = "jdbc:mysql://" + address + "/" + query;
            return new Env(url, serverUrl, username, password, database, eventTable, detailTable);
        }

        private static String require(String name) {
            String value = System.getenv(name);
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing env: " + name);
            }
            return value;
        }

        private static String value(String name, String fallback) {
            String value = System.getenv(name);
            return value == null || value.trim().isEmpty() ? fallback : value;
        }
    }
}
