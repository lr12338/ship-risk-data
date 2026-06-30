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
    private static final String TABLE_NAME = "ship_behavior_event";
    private static final String CHECK_SQL = "SELECT COUNT(*) FROM information_schema.tables " +
            "WHERE table_schema = ? AND table_name = ?";

    private volatile boolean ready;

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate newDataanalysisJdbcTemplate;

    @PostConstruct
    public void init() {
        ensureTableReady();
    }

    public void ensureTableReady() {
        if (ready) {
            return;
        }
        synchronized (this) {
            if (ready) {
                return;
            }
            ensureTableReadyInternal();
            ready = true;
        }
    }

    private void ensureTableReadyInternal() {
        Integer count = newDataanalysisJdbcTemplate.queryForObject(
                CHECK_SQL, Integer.class, TABLE_SCHEMA, TABLE_NAME);
        if (count != null && count > 0) {
            log.info("{} exists, skip ddl", TABLE_NAME);
            return;
        }

        String ddl = loadDdl();
        executeStatements(ddl);
        log.info("{} created", TABLE_NAME);
    }

    private String loadDdl() {
        ClassPathResource resource = new ClassPathResource("sql/ship_behavior_event.sql");
        try {
            return StreamUtils.copyToString(resource.getInputStream(), Charset.forName("UTF-8"));
        } catch (IOException ex) {
            throw new IllegalStateException("load ddl failed for " + TABLE_SCHEMA + "." + TABLE_NAME, ex);
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
