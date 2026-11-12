package com.hifleet.repository;

import com.hifleet.domain.TargetShip;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class TargetShipRepository {

    public static final String EXISTING_5000_SOURCE_BATCH =
            "liquid-container-5000-2025h2-2026h1-v1";

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ShipBehaviorTableInitializer tableInitializer;

    @Autowired
    @Qualifier("newDataanalysisDataSource")
    private DataSource dataSource;

    public int countEnabled(String listBatchNo) {
        tableInitializer.ensureTargetShipTableReady();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dataanalysis.ship_behavior_target_ship "
                        + "WHERE list_batch_no=? AND enabled=1",
                new Object[]{listBatchNo}, Integer.class);
        return count == null ? 0 : count;
    }

    public int countAll(String listBatchNo) {
        tableInitializer.ensureTargetShipTableReady();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dataanalysis.ship_behavior_target_ship WHERE list_batch_no=?",
                new Object[]{listBatchNo}, Integer.class);
        return count == null ? 0 : count;
    }

    public void validate(String listBatchNo, int expectedCount) {
        int enabled = countEnabled(listBatchNo);
        if (enabled != expectedCount) {
            throw new IllegalStateException("target list " + listBatchNo
                    + " enabled count expected=" + expectedCount + " actual=" + enabled);
        }
        Integer invalid = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dataanalysis.ship_behavior_target_ship "
                        + "WHERE list_batch_no=? AND enabled=1 AND mmsi NOT REGEXP '^[0-9]{9}$'",
                new Object[]{listBatchNo}, Integer.class);
        if (invalid != null && invalid > 0) {
            throw new IllegalStateException("target list contains invalid MMSI: " + invalid);
        }
    }

    public List<TargetShip> selectPage(String listBatchNo, int afterSeqNo, int limit) {
        tableInitializer.ensureTargetShipTableReady();
        return jdbcTemplate.query(
                "SELECT seq_no,mmsi,imo,ship_name,ship_type,source_updatetime "
                        + "FROM dataanalysis.ship_behavior_target_ship "
                        + "WHERE list_batch_no=? AND enabled=1 AND seq_no>? "
                        + "ORDER BY seq_no LIMIT ?",
                new Object[]{listBatchNo, afterSeqNo, limit}, (rs, rowNum) -> {
                    Timestamp updateTime = rs.getTimestamp("source_updatetime");
                    TargetShip ship = new TargetShip(rs.getString("mmsi"),
                            rs.getString("imo"), rs.getString("ship_name"),
                            rs.getString("ship_type"),
                            updateTime == null ? null : updateTime.toLocalDateTime());
                    ship.setSeqNo(rs.getInt("seq_no"));
                    return ship;
                });
    }

    public void insertImmutable(final String listBatchNo, final List<TargetShip> ships) {
        tableInitializer.ensureTargetShipTableReady();
        if (countAll(listBatchNo) > 0) {
            throw new IllegalStateException("target list already exists: " + listBatchNo);
        }
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(status -> {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO dataanalysis.ship_behavior_target_ship "
                            + "(list_batch_no,seq_no,mmsi,imo,ship_name,ship_type,enabled,source_updatetime) "
                            + "VALUES (?,?,?,?,?,?,1,?)",
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement statement, int index)
                                throws SQLException {
                            TargetShip ship = ships.get(index);
                            statement.setString(1, listBatchNo);
                            statement.setInt(2, ship.getSeqNo());
                            statement.setString(3, ship.getMmsi());
                            statement.setString(4, ship.getImo());
                            statement.setString(5, ship.getShipName());
                            statement.setString(6, ship.getShipType());
                            statement.setTimestamp(7, ship.getSourceUpdateTime() == null
                                    ? null : Timestamp.valueOf(ship.getSourceUpdateTime()));
                        }

                        @Override
                        public int getBatchSize() {
                            return ships.size();
                        }
                    });
            return null;
        });
    }

    public void copyExisting5000Immutable(String sourceListBatchNo, String listBatchNo,
                                          int expectedCount) {
        if (!EXISTING_5000_SOURCE_BATCH.equals(sourceListBatchNo)
                || !EXISTING_5000_SOURCE_BATCH.equals(listBatchNo)
                || expectedCount != 5000) {
            throw new IllegalArgumentException("EXISTING_5000_TABLE only supports the approved 5000 ship batch");
        }
        Integer sourceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dataanalysis.ship_behavior_tanker_open_sea_target_ship "
                        + "WHERE list_batch_no=?", new Object[]{sourceListBatchNo}, Integer.class);
        Integer invalid = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dataanalysis.ship_behavior_tanker_open_sea_target_ship "
                        + "WHERE list_batch_no=? AND mmsi NOT REGEXP '^[0-9]{9}$'",
                new Object[]{sourceListBatchNo}, Integer.class);
        Integer distinctMmsi = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT mmsi) FROM dataanalysis.ship_behavior_tanker_open_sea_target_ship "
                        + "WHERE list_batch_no=?", new Object[]{sourceListBatchNo}, Integer.class);
        Integer minSeq = jdbcTemplate.queryForObject(
                "SELECT MIN(seq_no) FROM dataanalysis.ship_behavior_tanker_open_sea_target_ship "
                        + "WHERE list_batch_no=?", new Object[]{sourceListBatchNo}, Integer.class);
        Integer maxSeq = jdbcTemplate.queryForObject(
                "SELECT MAX(seq_no) FROM dataanalysis.ship_behavior_tanker_open_sea_target_ship "
                        + "WHERE list_batch_no=?", new Object[]{sourceListBatchNo}, Integer.class);
        if (sourceCount == null || sourceCount != expectedCount || distinctMmsi == null
                || distinctMmsi != expectedCount || invalid == null || invalid != 0
                || minSeq == null || minSeq != 1 || maxSeq == null || maxSeq != expectedCount) {
            throw new IllegalStateException("approved source batch failed count/MMSI/sequence validation");
        }
        int existing = countAll(listBatchNo);
        if (existing > 0) {
            validate(listBatchNo, expectedCount);
            Integer mismatch = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM dataanalysis.ship_behavior_tanker_open_sea_target_ship s "
                            + "LEFT JOIN dataanalysis.ship_behavior_target_ship t ON t.list_batch_no=? "
                            + "AND t.seq_no=s.seq_no AND t.mmsi=s.mmsi AND t.imo <=> s.imo "
                            + "AND t.ship_name <=> s.ship_name AND t.ship_type <=> s.standard_ship_type "
                            + "AND t.source_updatetime <=> s.metadata_updated_at "
                            + "WHERE s.list_batch_no=? AND t.id IS NULL",
                    new Object[]{listBatchNo, sourceListBatchNo}, Integer.class);
            if (mismatch != null && mismatch == 0) {
                return;
            }
            throw new IllegalStateException("target list exists but differs from approved source batch");
        }
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(status -> {
            jdbcTemplate.update("INSERT INTO dataanalysis.ship_behavior_target_ship "
                            + "(list_batch_no,seq_no,mmsi,imo,ship_name,ship_type,enabled,source_updatetime) "
                            + "SELECT ?,seq_no,mmsi,imo,ship_name,standard_ship_type,1,metadata_updated_at "
                            + "FROM dataanalysis.ship_behavior_tanker_open_sea_target_ship "
                            + "WHERE list_batch_no=? ORDER BY seq_no",
                    listBatchNo, sourceListBatchNo);
            return null;
        });
        validate(listBatchNo, expectedCount);
    }
}
