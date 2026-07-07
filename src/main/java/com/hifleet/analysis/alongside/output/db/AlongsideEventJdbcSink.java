package com.hifleet.analysis.alongside.output.db;

import com.hifleet.analysis.alongside.core.model.AlongsideEvent;
import com.hifleet.analysis.alongside.core.model.PairProximityEvidence;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AlongsideEventJdbcSink extends RichSinkFunction<AlongsideEvent> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(AlongsideEventJdbcSink.class);

    private final AlongsideDbSinkConfig config;
    private final AlongsideEventDbMapper eventMapper = new AlongsideEventDbMapper();
    private final AlongsideEventDetailDbMapper detailMapper = new AlongsideEventDetailDbMapper();
    private transient Connection connection;
    private transient List<AlongsideEvent> buffer;
    private transient long lastFlushMs;

    public AlongsideEventJdbcSink(AlongsideDbSinkConfig config) {
        this.config = config;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        if (!config.isEnabled()) {
            return;
        }
        if (blank(config.getUrl()) || blank(config.getUsername())) {
            throw new IllegalArgumentException("alongside db sink is enabled but url/username is empty");
        }
        loadJdbcDriver(config.getUrl());
        connection = DriverManager.getConnection(config.getUrl(), config.getUsername(), config.getPassword());
        connection.setAutoCommit(false);
        if (config.isAutoCreateTable()) {
            new AlongsideDbTableInitializer().initialize(connection, config);
            connection.commit();
        }
        buffer = new ArrayList<AlongsideEvent>();
        lastFlushMs = System.currentTimeMillis();
    }

    @Override
    public void invoke(AlongsideEvent value, Context context) throws Exception {
        if (!config.isEnabled() || value == null) {
            return;
        }
        if (!isAllowedSeverity(value)) {
            LOG.debug("ALONGSIDE_REALTIME db_sink_skip event_id={} severity_level={} allowed_severity_levels={}",
                value.getEventId(), value.getSeverityLevel(), config.getAllowedSeverityLevels());
            return;
        }
        buffer.add(value);
        long now = System.currentTimeMillis();
        if (buffer.size() >= Math.max(1, config.getBatchSize())
            || now - lastFlushMs >= Math.max(1L, config.getFlushIntervalMs())) {
            flushWithRetry();
        }
    }

    @Override
    public void close() throws Exception {
        if (config.isEnabled() && buffer != null && !buffer.isEmpty()) {
            flushWithRetry();
        }
        if (connection != null) {
            connection.close();
        }
    }

    void flushWithRetry() throws SQLException {
        int attempts = Math.max(1, config.getMaxRetries() + 1);
        SQLException last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                flushOnce();
                return;
            } catch (SQLException ex) {
                last = ex;
                rollbackQuietly();
                LOG.warn("ALONGSIDE_REALTIME db_sink_flush_failed attempt={} message={}", i + 1, ex.getMessage());
            }
        }
        throw last;
    }

    private void flushOnce() throws SQLException {
        if (buffer.isEmpty()) {
            return;
        }
        try (PreparedStatement eventStatement = connection.prepareStatement(eventMapper.upsertSql(config.getEventTable()));
             PreparedStatement deleteDetailStatement = connection.prepareStatement(
                 "DELETE FROM " + config.getDetailTable() + " WHERE event_id = ?");
             PreparedStatement detailStatement = connection.prepareStatement(detailMapper.insertSql(config.getDetailTable()))) {
            for (AlongsideEvent event : buffer) {
                eventMapper.bind(eventStatement, event);
                eventStatement.addBatch();
            }
            eventStatement.executeBatch();
            if (config.isDetailEnabled()) {
                for (AlongsideEvent event : buffer) {
                    deleteDetailStatement.setString(1, AlongsideEventIdGenerator.ensureEventId(event));
                    deleteDetailStatement.executeUpdate();
                    addDetailBatch(detailStatement, event);
                }
                detailStatement.executeBatch();
            }
            connection.commit();
            LOG.info("ALONGSIDE_REALTIME db_sink_flush events={} detail_enabled={}", buffer.size(), config.isDetailEnabled());
            buffer.clear();
            lastFlushMs = System.currentTimeMillis();
        }
    }

    private void addDetailBatch(PreparedStatement detailStatement, AlongsideEvent event) throws SQLException {
        if (event.getEvidencePoints() == null || event.getEvidencePoints().isEmpty()) {
            return;
        }
        int pointIndex = 0;
        int maxPoints = Math.max(0, config.getMaxDetailPointsPerEvent());
        for (PairProximityEvidence evidence : event.getEvidencePoints()) {
            if (pointIndex >= maxPoints) {
                break;
            }
            detailMapper.bindOwn(detailStatement, event, evidence, pointIndex);
            detailStatement.addBatch();
            detailMapper.bindTarget(detailStatement, event, evidence, pointIndex);
            detailStatement.addBatch();
            pointIndex++;
        }
    }

    private void rollbackQuietly() {
        try {
            if (connection != null) {
                connection.rollback();
            }
        } catch (SQLException ignored) {
            // keep original flush error
        }
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    boolean isAllowedSeverity(AlongsideEvent event) {
        if (config.getAllowedSeverityLevels() == null || config.getAllowedSeverityLevels().isEmpty()) {
            return true;
        }
        String severity = event.getSeverityLevel();
        return severity != null && config.getAllowedSeverityLevels().contains(severity.trim().toUpperCase());
    }

    private static void loadJdbcDriver(String url) throws ClassNotFoundException {
        if (url != null && url.startsWith("jdbc:mysql:")) {
            Class.forName("com.mysql.cj.jdbc.Driver");
        }
    }
}
