package com.hifleet.repository;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ShipBehaviorTableInitializerTest {

    @Test
    public void createsTableWhenMissing() throws Exception {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        jdbcTemplate.eventTableExists = false;
        jdbcTemplate.detailTableExists = true;

        ShipBehaviorTableInitializer initializer = newInitializer(jdbcTemplate);

        initializer.ensureTableReady();

        assertTrue(jdbcTemplate.executedSqlContains("CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_behavior_event`"));
    }

    @Test
    public void noAlterWhenColumnsAlreadyExist() throws Exception {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        jdbcTemplate.eventTableExists = true;
        jdbcTemplate.detailTableExists = true;
        jdbcTemplate.existingColumns.addAll(allEventColumns());

        ShipBehaviorTableInitializer initializer = newInitializer(jdbcTemplate);

        initializer.ensureTableReady();

        assertEquals(0, jdbcTemplate.alterExecutions.size());
    }

    @Test
    public void shortBatchNoColumnTriggersModify() throws Exception {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        jdbcTemplate.eventTableExists = true;
        jdbcTemplate.detailTableExists = true;
        jdbcTemplate.eventBatchNoLength = 32;
        jdbcTemplate.existingColumns.addAll(allEventColumns());

        ShipBehaviorTableInitializer initializer = newInitializer(jdbcTemplate);

        initializer.ensureTableReady();

        assertTrue(jdbcTemplate.executedSqlContains("MODIFY COLUMN batch_no VARCHAR(64)"));
        assertEquals(64, jdbcTemplate.eventBatchNoLength);
    }

    @Test
    public void createsLoiteringBatchStatusTableWhenMissing() throws Exception {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        jdbcTemplate.eventTableExists = true;
        jdbcTemplate.detailTableExists = true;
        jdbcTemplate.loiteringBatchStatusTableExists = false;
        jdbcTemplate.existingColumns.addAll(allEventColumns());

        ShipBehaviorTableInitializer initializer = newInitializer(jdbcTemplate);

        initializer.ensureLoiteringBatchStatusTableReady();

        assertTrue(jdbcTemplate.executedSqlContains("CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_behavior_loitering_batch_status`"));
    }

    @Test
    public void createsAlongsideBatchStatusTableWhenMissing() throws Exception {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        jdbcTemplate.eventTableExists = true;
        jdbcTemplate.detailTableExists = true;
        jdbcTemplate.alongsideBatchStatusTableExists = false;
        jdbcTemplate.existingColumns.addAll(allEventColumns());

        ShipBehaviorTableInitializer initializer = newInitializer(jdbcTemplate);

        initializer.ensureAlongsideBatchStatusTableReady();

        assertTrue(jdbcTemplate.executedSqlContains("CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_behavior_alongside_batch_status`"));
    }

    @Test
    public void singleMissingColumnTriggersSingleAlter() throws Exception {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        jdbcTemplate.eventTableExists = true;
        jdbcTemplate.detailTableExists = true;
        jdbcTemplate.existingColumns.addAll(allEventColumns());
        jdbcTemplate.existingColumns.remove("event_score");

        ShipBehaviorTableInitializer initializer = newInitializer(jdbcTemplate);

        initializer.ensureTableReady();

        assertEquals(1, jdbcTemplate.alterExecutions.size());
        assertTrue(jdbcTemplate.alterExecutions.get(0).contains("ADD COLUMN event_score"));
    }

    @Test
    public void multipleMissingColumnsTriggerOrderedAlters() throws Exception {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        jdbcTemplate.eventTableExists = true;
        jdbcTemplate.detailTableExists = true;
        jdbcTemplate.existingColumns.addAll(allEventColumns());
        jdbcTemplate.existingColumns.remove("event_sub_type");
        jdbcTemplate.existingColumns.remove("severity_level");
        jdbcTemplate.existingColumns.remove("score_detail_json");

        ShipBehaviorTableInitializer initializer = newInitializer(jdbcTemplate);

        initializer.ensureTableReady();

        assertEquals(3, jdbcTemplate.alterExecutions.size());
        assertTrue(jdbcTemplate.alterExecutions.get(0).contains("ADD COLUMN event_sub_type"));
        assertTrue(jdbcTemplate.alterExecutions.get(1).contains("ADD COLUMN severity_level"));
        assertTrue(jdbcTemplate.alterExecutions.get(2).contains("ADD COLUMN score_detail_json"));
    }

    @Test(expected = IllegalStateException.class)
    public void alterFailureStopsInitialization() throws Exception {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        jdbcTemplate.eventTableExists = true;
        jdbcTemplate.detailTableExists = true;
        jdbcTemplate.existingColumns.addAll(allEventColumns());
        jdbcTemplate.existingColumns.remove("event_score");
        jdbcTemplate.failOnColumn = "event_score";

        ShipBehaviorTableInitializer initializer = newInitializer(jdbcTemplate);

        initializer.ensureTableReady();
    }

    private ShipBehaviorTableInitializer newInitializer(StubJdbcTemplate jdbcTemplate) throws Exception {
        ShipBehaviorTableInitializer initializer = new ShipBehaviorTableInitializer();
        setField(initializer, "newDataanalysisJdbcTemplate", jdbcTemplate);
        return initializer;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Set<String> allEventColumns() {
        Set<String> columns = new HashSet<String>();
        for (ShipBehaviorTableInitializer.ColumnMigration migration : ShipBehaviorTableInitializer.buildEventTableMigrations()) {
            columns.add(migration.getColumnName());
        }
        return columns;
    }

    private static class StubJdbcTemplate extends JdbcTemplate {
        private boolean eventTableExists;
        private boolean detailTableExists;
        private boolean loiteringBatchStatusTableExists;
        private boolean alongsideBatchStatusTableExists;
        private int eventBatchNoLength = 64;
        private int detailBatchNoLength = 64;
        private final Set<String> existingColumns = new HashSet<String>();
        private final List<String> alterExecutions = new ArrayList<String>();
        private final List<String> executedSql = new ArrayList<String>();
        private String failOnColumn;

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("information_schema.tables")) {
                String tableName = String.valueOf(args[1]);
                Integer count = "ship_behavior_event".equals(tableName) && eventTableExists
                        || "ship_behavior_event_detail".equals(tableName) && detailTableExists
                        || "ship_behavior_loitering_batch_status".equals(tableName) && loiteringBatchStatusTableExists
                        || "ship_behavior_alongside_batch_status".equals(tableName) && alongsideBatchStatusTableExists
                        ? 1 : 0;
                return requiredType.cast(count);
            }
            if (sql.contains("character_maximum_length")) {
                String tableName = String.valueOf(args[1]);
                Integer length = "ship_behavior_event_detail".equals(tableName)
                        ? detailBatchNoLength : eventBatchNoLength;
                return requiredType.cast(length);
            }
            if (sql.contains("information_schema.columns")) {
                String columnName = String.valueOf(args[2]);
                Integer count = existingColumns.contains(columnName) ? 1 : 0;
                return requiredType.cast(count);
            }
            throw new UnsupportedOperationException(sql);
        }

        @Override
        public void execute(String sql) {
            executedSql.add(sql);
            if (sql.contains("CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_behavior_event`")) {
                eventTableExists = true;
                eventBatchNoLength = 64;
                existingColumns.addAll(allMigrationColumns());
                return;
            }
            if (sql.contains("CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_behavior_event_detail`")) {
                detailTableExists = true;
                detailBatchNoLength = 64;
                return;
            }
            if (sql.contains("CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_behavior_loitering_batch_status`")) {
                loiteringBatchStatusTableExists = true;
                return;
            }
            if (sql.contains("CREATE TABLE IF NOT EXISTS `dataanalysis`.`ship_behavior_alongside_batch_status`")) {
                alongsideBatchStatusTableExists = true;
                return;
            }
            if (sql.contains("ALTER TABLE dataanalysis.ship_behavior_event MODIFY COLUMN batch_no VARCHAR(64)")) {
                eventBatchNoLength = 64;
                return;
            }
            if (sql.contains("ALTER TABLE dataanalysis.ship_behavior_event_detail MODIFY COLUMN batch_no VARCHAR(64)")) {
                detailBatchNoLength = 64;
                return;
            }
            if (sql.startsWith("ALTER TABLE dataanalysis.ship_behavior_event ADD COLUMN")) {
                alterExecutions.add(sql);
                String columnName = extractColumnName(sql);
                if (columnName.equals(failOnColumn)) {
                    throw new RuntimeException("mock alter failure for " + columnName);
                }
                existingColumns.add(columnName);
                return;
            }
            throw new UnsupportedOperationException(sql);
        }

        private boolean executedSqlContains(String token) {
            for (String sql : executedSql) {
                if (sql.contains(token)) {
                    return true;
                }
            }
            return false;
        }

        private Set<String> allMigrationColumns() {
            Set<String> result = new HashSet<String>();
            for (ShipBehaviorTableInitializer.ColumnMigration migration : ShipBehaviorTableInitializer.buildEventTableMigrations()) {
                result.add(migration.getColumnName());
            }
            return result;
        }

        private String extractColumnName(String sql) {
            String prefix = "ALTER TABLE dataanalysis.ship_behavior_event ADD COLUMN ";
            String remain = sql.substring(prefix.length()).trim();
            int end = remain.indexOf(' ');
            return end > 0 ? remain.substring(0, end) : remain;
        }
    }
}
