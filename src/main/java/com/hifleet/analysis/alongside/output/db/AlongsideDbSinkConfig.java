package com.hifleet.analysis.alongside.output.db;

import java.io.Serializable;
import java.util.Properties;

public class AlongsideDbSinkConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled;
    private boolean detailEnabled;
    private String url;
    private String username;
    private String password;
    private int batchSize;
    private long flushIntervalMs;
    private int maxRetries;
    private String eventTable;
    private String detailTable;
    private int maxDetailPointsPerEvent;
    private boolean autoCreateTable;

    public static AlongsideDbSinkConfig fromProperties(Properties props) {
        AlongsideDbSinkConfig config = new AlongsideDbSinkConfig();
        config.setEnabled(toBoolean(value(props, "alongside.db-sink.enabled"), false));
        config.setDetailEnabled(toBoolean(value(props, "alongside.db-sink.detail-enabled"), true));
        config.setUrl(value(props, "alongside.db-sink.url"));
        config.setUsername(value(props, "alongside.db-sink.username"));
        config.setPassword(value(props, "alongside.db-sink.password"));
        config.setBatchSize(toInt(value(props, "alongside.db-sink.batch-size"), 100));
        config.setFlushIntervalMs(toLong(value(props, "alongside.db-sink.flush-interval-ms"), 3000L));
        config.setMaxRetries(toInt(value(props, "alongside.db-sink.max-retries"), 3));
        config.setEventTable(value(props, "alongside.db-sink.table-event"));
        config.setDetailTable(value(props, "alongside.db-sink.table-detail"));
        config.setMaxDetailPointsPerEvent(toInt(value(props, "alongside.db-sink.max-detail-points-per-event"), 200));
        config.setAutoCreateTable(toBoolean(value(props, "alongside.db-sink.auto-create-table"), true));
        return config;
    }

    public static void addDefaults(Properties defaults) {
        defaults.setProperty("alongside.db-sink.enabled", "false");
        defaults.setProperty("alongside.db-sink.detail-enabled", "true");
        defaults.setProperty("alongside.db-sink.url", "");
        defaults.setProperty("alongside.db-sink.username", "");
        defaults.setProperty("alongside.db-sink.password", "");
        defaults.setProperty("alongside.db-sink.batch-size", "100");
        defaults.setProperty("alongside.db-sink.flush-interval-ms", "3000");
        defaults.setProperty("alongside.db-sink.max-retries", "3");
        defaults.setProperty("alongside.db-sink.table-event", "ship_realtime_alongside_event");
        defaults.setProperty("alongside.db-sink.table-detail", "ship_realtime_alongside_event_detail");
        defaults.setProperty("alongside.db-sink.max-detail-points-per-event", "200");
        defaults.setProperty("alongside.db-sink.auto-create-table", "true");
    }

    private static String value(Properties props, String key) {
        return props.getProperty(key);
    }

    private static int toInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static long toLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static boolean toBoolean(String value, boolean fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDetailEnabled() {
        return detailEnabled;
    }

    public void setDetailEnabled(boolean detailEnabled) {
        this.detailEnabled = detailEnabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getFlushIntervalMs() {
        return flushIntervalMs;
    }

    public void setFlushIntervalMs(long flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public String getEventTable() {
        return eventTable;
    }

    public void setEventTable(String eventTable) {
        this.eventTable = eventTable;
    }

    public String getDetailTable() {
        return detailTable;
    }

    public void setDetailTable(String detailTable) {
        this.detailTable = detailTable;
    }

    public int getMaxDetailPointsPerEvent() {
        return maxDetailPointsPerEvent;
    }

    public void setMaxDetailPointsPerEvent(int maxDetailPointsPerEvent) {
        this.maxDetailPointsPerEvent = maxDetailPointsPerEvent;
    }

    public boolean isAutoCreateTable() {
        return autoCreateTable;
    }

    public void setAutoCreateTable(boolean autoCreateTable) {
        this.autoCreateTable = autoCreateTable;
    }
}
