package com.hifleet.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

@Component
public class ShipBehaviorTableInitializer {

    private static final Logger log = LoggerFactory.getLogger(ShipBehaviorTableInitializer.class);

    private static final String TABLE_SCHEMA = "dataanalysis";
    private static final String EVENT_TABLE_NAME = "ship_behavior_event";
    private static final String DETAIL_TABLE_NAME = "ship_behavior_event_detail";
    private static final String CHECK_SQL = "SELECT COUNT(*) FROM information_schema.tables " +
            "WHERE table_schema = ? AND table_name = ?";

    private volatile boolean eventTableReady;
    private volatile boolean detailTableReady;

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate newDataanalysisJdbcTemplate;

    @PostConstruct
    public void init() {
        ensureTableReady();
        ensureDetailTableReady();
    }

    public void ensureTableReady() {
        eventTableReady = ensureReady(eventTableReady, EVENT_TABLE_NAME, "sql/ship_behavior_event.sql");
    }

    public void ensureDetailTableReady() {
        detailTableReady = ensureReady(detailTableReady, DETAIL_TABLE_NAME, "sql/ship_behavior_event_detail.sql");
    }

    private boolean ensureReady(boolean ready, String tableName, String ddlPath) {
        if (ready) {
            return true;
        }
        synchronized (this) {
            if (readyFor(tableName)) {
                return true;
            }
            ensureTableReadyInternal(tableName, ddlPath);
            return true;
        }
    }

    private boolean readyFor(String tableName) {
        if (EVENT_TABLE_NAME.equals(tableName)) {
            return eventTableReady;
        }
        if (DETAIL_TABLE_NAME.equals(tableName)) {
            return detailTableReady;
        }
        return false;
    }

    private void ensureTableReadyInternal(String tableName, String ddlPath) {
        Integer count = newDataanalysisJdbcTemplate.queryForObject(
                CHECK_SQL, Integer.class, TABLE_SCHEMA, tableName);
        if (count != null && count > 0) {
            log.info("{} exists, skip ddl", tableName);
            return;
        }

        String ddl = loadDdl(ddlPath, tableName);
        executeStatements(ddl);
        log.info("{} created", tableName);
    }

    private String loadDdl(String ddlPath, String tableName) {
        ClassPathResource resource = new ClassPathResource(ddlPath);
        try {
            return StreamUtils.copyToString(resource.getInputStream(), Charset.forName("UTF-8"));
        } catch (IOException ex) {
            throw new IllegalStateException("load ddl failed for " + TABLE_SCHEMA + "." + tableName, ex);
        }
    }

    private void executeStatements(String ddl) {
        List<String> statements = splitStatements(ddl);
        for (String statement : statements) {
            newDataanalysisJdbcTemplate.execute(statement);
        }
    }

    private List<String> splitStatements(String ddl) {
        java.util.ArrayList<String> statements = new java.util.ArrayList<String>();
        for (String part : ddl.split(";")) {
            String statement = part.trim();
            if (!statement.isEmpty()) {
                statements.add(statement);
            }
        }
        return statements;
    }
}
