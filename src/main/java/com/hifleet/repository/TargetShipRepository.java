package com.hifleet.repository;

import com.hifleet.domain.TargetShip;
import com.hifleet.behaviorplatform.TargetListSnapshot;
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

    public TargetListSnapshot snapshot(String listBatchNo) {
        tableInitializer.ensureTargetShipTableReady();
        return jdbcTemplate.queryForObject("SELECT COUNT(*) total_count,"
                        + "SUM(CASE WHEN enabled=1 THEN 1 ELSE 0 END) enabled_count,"
                        + "MIN(CASE WHEN enabled=1 THEN seq_no END) min_seq,"
                        + "MAX(CASE WHEN enabled=1 THEN seq_no END) max_seq,"
                        + "COUNT(DISTINCT CASE WHEN enabled=1 THEN seq_no END) distinct_seq,"
                        + "COUNT(DISTINCT CASE WHEN enabled=1 THEN mmsi END) distinct_mmsi "
                        + "FROM dataanalysis.ship_behavior_target_ship WHERE list_batch_no=?",
                new Object[]{listBatchNo}, (rs, rowNum) -> new TargetListSnapshot(
                        rs.getInt("total_count"), rs.getInt("enabled_count"),
                        (Integer) rs.getObject("min_seq"), (Integer) rs.getObject("max_seq"),
                        rs.getInt("distinct_seq"), rs.getInt("distinct_mmsi")));
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
}
