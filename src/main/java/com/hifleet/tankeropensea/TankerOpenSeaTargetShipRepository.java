package com.hifleet.tankeropensea;

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
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class TankerOpenSeaTargetShipRepository {
    private static final String TABLE = "ship_behavior_tanker_open_sea_target_ship";

    @Autowired
    @Qualifier("aid41ReadOnlyJdbcTemplate")
    private JdbcTemplate aisReadOnlyJdbcTemplate;

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate eventJdbcTemplate;

    @Autowired
    @Qualifier("newDataanalysisDataSource")
    private DataSource eventDataSource;

    public List<ShipMetadata> selectSourceCandidatesPage(
            String updatedAfter, int afterMmsi, int limit) {
        if (limit <= 0 || limit > 5000) {
            throw new IllegalArgumentException(
                    "source candidate page limit must be between 1 and 5000");
        }
        String sql = "SELECT CAST(mmsi AS CHAR) mmsi, CAST(imonumber AS CHAR) imo, "
                + "name, type, minotype, NULL ais_ship_type_code, length, dwt, updatetime "
                + "FROM ais41.recentships WHERE CHAR_LENGTH(CAST(mmsi AS CHAR))=9 "
                + "AND mmsi>? AND updatetime>=? ORDER BY mmsi LIMIT ?";
        return aisReadOnlyJdbcTemplate.query(sql,
                new Object[]{afterMmsi, updatedAfter, limit},
                (rs, rowNum) -> metadata(rs));
    }

    public Map<String, ShipMetadata> findSourceByMmsis(Set<String> mmsis) {
        if (mmsis == null || mmsis.isEmpty()) return Collections.emptyMap();
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < mmsis.size(); i++) {
            if (i > 0) placeholders.append(',');
            placeholders.append('?');
        }
        String sql = "SELECT CAST(mmsi AS CHAR) mmsi, CAST(imonumber AS CHAR) imo, "
                + "name, type, minotype, NULL ais_ship_type_code, length, dwt, updatetime "
                + "FROM ais41.recentships WHERE mmsi IN (" + placeholders + ")";
        List<ShipMetadata> rows = aisReadOnlyJdbcTemplate.query(
                sql, mmsis.toArray(), (rs, rowNum) -> metadata(rs));
        Map<String, ShipMetadata> result = new LinkedHashMap<String, ShipMetadata>();
        for (ShipMetadata row : rows) result.put(row.getMmsi(), row);
        return result;
    }

    public int count(String listBatchNo) {
        assertTableReady();
        Integer count = eventJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dataanalysis." + TABLE
                        + " WHERE list_batch_no=?",
                new Object[]{listBatchNo}, Integer.class);
        return count == null ? 0 : count;
    }

    public void validate(String listBatchNo, int minimumCount) {
        int count = count(listBatchNo);
        if (count < minimumCount) {
            throw new IllegalStateException("tanker list is missing or too small listBatchNo="
                    + listBatchNo + " required=" + minimumCount + " actual=" + count);
        }
        Integer invalid = eventJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dataanalysis." + TABLE
                        + " WHERE list_batch_no=? AND (standard_ship_type NOT IN "
                        + "('TANKER_OIL','TANKER_CHEMICAL','TANKER_GAS',"
                        + "'TANKER_UNSPECIFIED','CONTAINER') "
                        + "OR mmsi NOT REGEXP '^[0-9]{9}$')",
                new Object[]{listBatchNo},
                Integer.class);
        if (invalid != null && invalid > 0) {
            throw new IllegalStateException(
                    "liquid/container list contains ineligible rows: " + invalid);
        }
    }

    public void validateDistribution(String listBatchNo, int liquidCargoCount,
                                     int containerCount) {
        Integer containers = eventJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dataanalysis." + TABLE
                        + " WHERE list_batch_no=? AND standard_ship_type='CONTAINER'",
                new Object[]{listBatchNo}, Integer.class);
        Integer liquidCargo = eventJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dataanalysis." + TABLE
                        + " WHERE list_batch_no=? AND standard_ship_type IN "
                        + "('TANKER_OIL','TANKER_CHEMICAL','TANKER_GAS',"
                        + "'TANKER_UNSPECIFIED')",
                new Object[]{listBatchNo}, Integer.class);
        if (containers == null || containers != containerCount
                || liquidCargo == null || liquidCargo != liquidCargoCount) {
            throw new IllegalStateException("immutable list distribution mismatch "
                    + "liquidExpected=" + liquidCargoCount
                    + " liquidActual=" + liquidCargo
                    + " containerExpected=" + containerCount
                    + " containerActual=" + containers);
        }
    }

    public List<ListedTanker> selectPage(String listBatchNo, int afterSeqNo, int limit) {
        assertTableReady();
        String sql = "SELECT seq_no,mmsi,imo,ship_name,raw_type,raw_minotype,"
                + "ais_ship_type_code,length_meters,dwt,metadata_updated_at,"
                + "standard_ship_type,selection_reason,metadata_quality "
                + "FROM dataanalysis." + TABLE + " WHERE list_batch_no=? AND seq_no>? "
                + "ORDER BY seq_no LIMIT ?";
        return eventJdbcTemplate.query(sql,
                new Object[]{listBatchNo, afterSeqNo, limit}, (rs, rowNum) -> {
                    ShipMetadata metadata = new ShipMetadata(
                            rs.getString("mmsi"), rs.getString("imo"),
                            rs.getString("ship_name"), rs.getString("raw_type"),
                            rs.getString("raw_minotype"),
                            rs.getObject("ais_ship_type_code") == null
                                    ? null : rs.getInt("ais_ship_type_code"),
                            rs.getObject("length_meters") == null
                                    ? null : rs.getDouble("length_meters"),
                            rs.getObject("dwt") == null ? null : rs.getLong("dwt"),
                            toLocalDateTime(rs.getTimestamp("metadata_updated_at")));
                    StandardShipType type;
                    try {
                        type = StandardShipType.valueOf(
                                rs.getString("standard_ship_type"));
                    } catch (Exception ex) {
                        type = StandardShipType.CONFLICT;
                    }
                    ShipEligibilityDecision eligibility =
                            ShipEligibilityPolicy.isEligibleType(type)
                                    ? ShipEligibilityDecision.eligible(type,
                                    rs.getString("selection_reason"),
                                    rs.getString("metadata_quality"))
                                    : ShipEligibilityDecision.rejected(type,
                                    "INVALID_LIST_SNAPSHOT",
                                    rs.getString("metadata_quality"));
                    return new ListedTanker(rs.getInt("seq_no"),
                            metadata, eligibility);
                });
    }

    public void insertImmutable(final String listBatchNo,
                                final List<ListedTanker> ships) {
        assertTableReady();
        if (count(listBatchNo) > 0) {
            throw new IllegalStateException("tanker list already exists: " + listBatchNo);
        }
        new TransactionTemplate(new DataSourceTransactionManager(eventDataSource))
                .execute(status -> {
                    eventJdbcTemplate.batchUpdate(
                            "INSERT INTO dataanalysis." + TABLE
                                    + " (list_batch_no,seq_no,mmsi,imo,ship_name,raw_type,"
                                    + "raw_minotype,ais_ship_type_code,standard_ship_type,"
                                    + "length_meters,dwt,metadata_updated_at,selection_reason,"
                                    + "metadata_quality) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                            new BatchPreparedStatementSetter() {
                                @Override
                                public void setValues(PreparedStatement statement, int index)
                                        throws SQLException {
                                    ListedTanker listed = ships.get(index);
                                    ShipMetadata ship = listed.getMetadata();
                                    statement.setString(1, listBatchNo);
                                    statement.setInt(2, listed.getSeqNo());
                                    statement.setString(3, ship.getMmsi());
                                    statement.setString(4, ship.getImo());
                                    statement.setString(5, ship.getShipName());
                                    statement.setString(6, ship.getRawType());
                                    statement.setString(7, ship.getRawMinorType());
                                    statement.setObject(8, ship.getAisShipTypeCode());
                                    statement.setString(9, listed.getEligibility()
                                            .getStandardShipType().name());
                                    statement.setObject(10, ship.getLengthMeters());
                                    statement.setObject(11, ship.getDwt());
                                    statement.setTimestamp(12, ship.getMetadataUpdatedAt() == null
                                            ? null : Timestamp.valueOf(ship.getMetadataUpdatedAt()));
                                    statement.setString(13, listed.getEligibility()
                                            .getDecisionReason());
                                    statement.setString(14, listed.getEligibility()
                                            .getMetadataQuality());
                                }

                                @Override
                                public int getBatchSize() { return ships.size(); }
                            });
                    return null;
                });
    }

    private void assertTableReady() {
        Integer count = eventJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema='dataanalysis' AND table_name=?",
                new Object[]{TABLE}, Integer.class);
        if (count == null || count == 0) {
            throw new IllegalStateException("required migration not applied: "
                    + "sql/ship_behavior_tanker_open_sea.sql");
        }
    }

    private ShipMetadata metadata(java.sql.ResultSet rs) throws SQLException {
        return new ShipMetadata(rs.getString("mmsi"), rs.getString("imo"),
                rs.getString("name"), rs.getString("type"),
                rs.getString("minotype"),
                (Integer) rs.getObject("ais_ship_type_code"),
                rs.getObject("length") == null ? null : rs.getDouble("length"),
                rs.getObject("dwt") == null ? null : rs.getLong("dwt"),
                toLocalDateTime(rs.getTimestamp("updatetime")));
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
