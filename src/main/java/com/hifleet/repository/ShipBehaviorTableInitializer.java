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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class ShipBehaviorTableInitializer {

    private static final Logger log = LoggerFactory.getLogger(ShipBehaviorTableInitializer.class);

    private static final String TABLE_SCHEMA = "dataanalysis";
    private static final String EVENT_TABLE_NAME = "ship_behavior_event";
    private static final String DETAIL_TABLE_NAME = "ship_behavior_event_detail";
    private static final String LOITERING_BATCH_STATUS_TABLE_NAME = "ship_behavior_loitering_batch_status";
    private static final String ALONGSIDE_BATCH_STATUS_TABLE_NAME = "ship_behavior_alongside_batch_status";
    private static final String CHECK_SQL = "SELECT COUNT(*) FROM information_schema.tables " +
            "WHERE table_schema = ? AND table_name = ?";
    private static final String CHECK_COLUMN_SQL = "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_schema = ? AND table_name = ? AND column_name = ?";
    private static final String CHECK_COLUMN_LENGTH_SQL = "SELECT character_maximum_length FROM information_schema.columns " +
            "WHERE table_schema = ? AND table_name = ? AND column_name = ?";
    private static final int BATCH_NO_MIN_LENGTH = 64;

    private static final List<ColumnMigration> EVENT_TABLE_MIGRATIONS = buildEventTableMigrations();

    private volatile boolean eventTableReady;
    private volatile boolean detailTableReady;
    private volatile boolean loiteringBatchStatusTableReady;
    private volatile boolean alongsideBatchStatusTableReady;

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate newDataanalysisJdbcTemplate;

    @PostConstruct
    public void init() {
        ensureTableReady();
        ensureDetailTableReady();
        ensureLoiteringBatchStatusTableReady();
        ensureAlongsideBatchStatusTableReady();
    }

    public void ensureTableReady() {
        eventTableReady = ensureReady(eventTableReady, EVENT_TABLE_NAME, "sql/ship_behavior_event.sql", true);
    }

    public void ensureDetailTableReady() {
        detailTableReady = ensureReady(detailTableReady, DETAIL_TABLE_NAME, "sql/ship_behavior_event_detail.sql", false);
    }

    public void ensureLoiteringBatchStatusTableReady() {
        loiteringBatchStatusTableReady = ensureReady(loiteringBatchStatusTableReady,
                LOITERING_BATCH_STATUS_TABLE_NAME, "sql/ship_behavior_loitering_batch_status.sql", false);
    }

    public void ensureAlongsideBatchStatusTableReady() {
        alongsideBatchStatusTableReady = ensureReady(alongsideBatchStatusTableReady,
                ALONGSIDE_BATCH_STATUS_TABLE_NAME, "sql/ship_behavior_alongside_batch_status.sql", false);
    }

    private boolean ensureReady(boolean ready, String tableName, String ddlPath, boolean ensureColumns) {
        if (ready) {
            return true;
        }
        synchronized (this) {
            if (readyFor(tableName)) {
                return true;
            }
            ensureTableExists(tableName, ddlPath);
            ensureBatchNoLength(tableName);
            if (ensureColumns && EVENT_TABLE_NAME.equals(tableName)) {
                ensureEventTableColumnsReady();
                log.info("{} schema ready", tableName);
            }
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
        if (LOITERING_BATCH_STATUS_TABLE_NAME.equals(tableName)) {
            return loiteringBatchStatusTableReady;
        }
        if (ALONGSIDE_BATCH_STATUS_TABLE_NAME.equals(tableName)) {
            return alongsideBatchStatusTableReady;
        }
        return false;
    }

    private void ensureBatchNoLength(String tableName) {
        if (!EVENT_TABLE_NAME.equals(tableName) && !DETAIL_TABLE_NAME.equals(tableName)) {
            return;
        }
        Integer length = newDataanalysisJdbcTemplate.queryForObject(
                CHECK_COLUMN_LENGTH_SQL, Integer.class, TABLE_SCHEMA, tableName, "batch_no");
        if (length != null && length >= BATCH_NO_MIN_LENGTH) {
            log.info("{}.batch_no column length ready: {}", tableName, length);
            return;
        }
        String alterSql = "ALTER TABLE dataanalysis." + tableName
                + " MODIFY COLUMN batch_no VARCHAR(64) DEFAULT NULL COMMENT '计算批次'";
        log.info("{}.batch_no column length insufficient: {}, applying migration", tableName, length);
        try {
            newDataanalysisJdbcTemplate.execute(alterSql);
        } catch (Exception ex) {
            throw new IllegalStateException("ensure batch_no length failed for "
                    + TABLE_SCHEMA + "." + tableName + ", sql=" + alterSql, ex);
        }
    }

    private void ensureTableExists(String tableName, String ddlPath) {
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

    private void ensureEventTableColumnsReady() {
        for (ColumnMigration migration : EVENT_TABLE_MIGRATIONS) {
            if (columnExists(EVENT_TABLE_NAME, migration.getColumnName())) {
                log.info("{} column ready: {}", EVENT_TABLE_NAME, migration.getColumnName());
                continue;
            }
            log.info("{} column missing, applying migration: {}", EVENT_TABLE_NAME, migration.getColumnName());
            try {
                newDataanalysisJdbcTemplate.execute(migration.getAlterSql());
            } catch (Exception ex) {
                throw new IllegalStateException("ensure column failed for "
                        + TABLE_SCHEMA + "." + EVENT_TABLE_NAME + "." + migration.getColumnName()
                        + ", sql=" + migration.getAlterSql(), ex);
            }
            if (!columnExists(EVENT_TABLE_NAME, migration.getColumnName())) {
                throw new IllegalStateException("column still missing after migration: "
                        + TABLE_SCHEMA + "." + EVENT_TABLE_NAME + "." + migration.getColumnName());
            }
            log.info("{} column ready: {}", EVENT_TABLE_NAME, migration.getColumnName());
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = newDataanalysisJdbcTemplate.queryForObject(
                CHECK_COLUMN_SQL, Integer.class, TABLE_SCHEMA, tableName, columnName);
        return count != null && count > 0;
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

    static List<ColumnMigration> buildEventTableMigrations() {
        List<ColumnMigration> migrations = new ArrayList<ColumnMigration>();
        migrations.add(new ColumnMigration("event_sub_type",
                "ALTER TABLE dataanalysis.ship_behavior_event ADD COLUMN event_sub_type VARCHAR(32) DEFAULT NULL COMMENT '徘徊子类型'"));
        migrations.add(new ColumnMigration("severity_level",
                "ALTER TABLE dataanalysis.ship_behavior_event ADD COLUMN severity_level VARCHAR(8) DEFAULT NULL COMMENT 'L0-L4'"));
        migrations.add(new ColumnMigration("event_score",
                "ALTER TABLE dataanalysis.ship_behavior_event ADD COLUMN event_score DOUBLE DEFAULT NULL COMMENT '单事件风险分，0-100'"));
        migrations.add(new ColumnMigration("confidence_level",
                "ALTER TABLE dataanalysis.ship_behavior_event ADD COLUMN confidence_level VARCHAR(16) DEFAULT NULL COMMENT 'HIGH/MEDIUM/LOW'"));
        migrations.add(new ColumnMigration("exclusion_flag",
                "ALTER TABLE dataanalysis.ship_behavior_event ADD COLUMN exclusion_flag VARCHAR(32) DEFAULT NULL COMMENT 'EXCLUDED_NORMAL/DOWNGRADED_NORMAL_LIKELY/VALID_RISK_EVENT'"));
        migrations.add(new ColumnMigration("exclusion_reason",
                "ALTER TABLE dataanalysis.ship_behavior_event ADD COLUMN exclusion_reason VARCHAR(512) DEFAULT NULL COMMENT '正常场景排除原因'"));
        migrations.add(new ColumnMigration("ais_gap_count",
                "ALTER TABLE dataanalysis.ship_behavior_event ADD COLUMN ais_gap_count INT DEFAULT NULL COMMENT 'AIS gap次数'"));
        migrations.add(new ColumnMigration("max_ais_gap_minutes",
                "ALTER TABLE dataanalysis.ship_behavior_event ADD COLUMN max_ais_gap_minutes BIGINT DEFAULT NULL COMMENT '最大AIS gap分钟数'"));
        migrations.add(new ColumnMigration("track_coverage_ratio",
                "ALTER TABLE dataanalysis.ship_behavior_event ADD COLUMN track_coverage_ratio DOUBLE DEFAULT NULL COMMENT '轨迹覆盖率'"));
        migrations.add(new ColumnMigration("turn_count_per_hour",
                "ALTER TABLE dataanalysis.ship_behavior_event ADD COLUMN turn_count_per_hour DOUBLE DEFAULT NULL COMMENT '每小时转向次数'"));
        migrations.add(new ColumnMigration("cog_change_per_hour",
                "ALTER TABLE dataanalysis.ship_behavior_event ADD COLUMN cog_change_per_hour DOUBLE DEFAULT NULL COMMENT '每小时累计航向变化'"));
        migrations.add(new ColumnMigration("tortuosity",
                "ALTER TABLE dataanalysis.ship_behavior_event ADD COLUMN tortuosity DOUBLE DEFAULT NULL COMMENT '轨迹曲折系数'"));
        migrations.add(new ColumnMigration("center_lon",
                "ALTER TABLE dataanalysis.ship_behavior_event ADD COLUMN center_lon DOUBLE DEFAULT NULL COMMENT '事件中心经度'"));
        migrations.add(new ColumnMigration("center_lat",
                "ALTER TABLE dataanalysis.ship_behavior_event ADD COLUMN center_lat DOUBLE DEFAULT NULL COMMENT '事件中心纬度'"));
        migrations.add(new ColumnMigration("radius_nm",
                "ALTER TABLE dataanalysis.ship_behavior_event ADD COLUMN radius_nm DOUBLE DEFAULT NULL COMMENT '事件活动半径，海里'"));
        migrations.add(new ColumnMigration("score_detail_json",
                "ALTER TABLE dataanalysis.ship_behavior_event ADD COLUMN score_detail_json TEXT DEFAULT NULL COMMENT '评分明细JSON'"));
        return Collections.unmodifiableList(migrations);
    }

    static class ColumnMigration {
        private final String columnName;
        private final String alterSql;

        ColumnMigration(String columnName, String alterSql) {
            this.columnName = columnName;
            this.alterSql = alterSql;
        }

        String getColumnName() {
            return columnName;
        }

        String getAlterSql() {
            return alterSql;
        }
    }
}
