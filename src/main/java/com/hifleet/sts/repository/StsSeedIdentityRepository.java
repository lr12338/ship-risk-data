package com.hifleet.sts.repository;

import com.hifleet.sts.domain.StsSeedIdentityRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class StsSeedIdentityRepository {

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("aid41JdbcTemplate")
    private JdbcTemplate aisJdbcTemplate;

    public List<StsSeedIdentityRow> selectHistoricalCandidateRows(String seedBatchNo) {
        return jdbcTemplate.query("SELECT * FROM dataanalysis.ship_sts_seed_identity "
                + "WHERE seed_batch_no=? ORDER BY ship_ordinal,imo,mmsi",
                new Object[]{seedBatchNo}, new SeedIdentityRowMapper());
    }

    public String findHistoricalImoByMmsi(String seedBatchNo, String mmsi) {
        List<String> values = jdbcTemplate.queryForList(
                "SELECT DISTINCT imo FROM dataanalysis.ship_sts_seed_identity WHERE seed_batch_no=? AND mmsi=? LIMIT 2",
                String.class, seedBatchNo, mmsi);
        if (values.size() > 1) throw new IllegalStateException("ambiguous seed MMSI: " + mmsi);
        return values.isEmpty() ? null : values.get(0);
    }

    public java.util.Map<String, Object> historicalTargetMetadata(String mmsi) {
        List<java.util.Map<String, Object>> rows = aisJdbcTemplate.queryForList(
                "SELECT imonumber,type,minotype FROM ais41.recentships WHERE mmsi=?", mmsi);
        return rows.isEmpty() ? Collections.<String, Object>emptyMap() : rows.get(0);
    }

    public int countDistinctImos(String seedBatchNo) {
        String sql = "SELECT COUNT(DISTINCT imo) FROM dataanalysis.ship_sts_seed_identity " +
                "WHERE seed_batch_no = ? AND enabled = 1";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, seedBatchNo);
        return count != null ? count : 0;
    }

    public int countIdentityRows(String seedBatchNo) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dataanalysis.ship_sts_seed_identity "
                        + "WHERE seed_batch_no = ? AND enabled = 1",
                Integer.class, seedBatchNo);
        return count != null ? count : 0;
    }

    public int countDistinctMmsis(String seedBatchNo) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT mmsi) FROM dataanalysis.ship_sts_seed_identity "
                        + "WHERE seed_batch_no = ? AND enabled = 1",
                Integer.class, seedBatchNo);
        return count != null ? count : 0;
    }

    public int countNonCrudeRows(String seedBatchNo) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dataanalysis.ship_sts_seed_identity "
                        + "WHERE seed_batch_no = ? AND enabled = 1 "
                        + "AND (minotype IS NULL OR minotype <> '原油船')",
                Integer.class, seedBatchNo);
        return count != null ? count : 0;
    }

    public List<String> selectImos(String seedBatchNo, int offset, int limit) {
        if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
        if (limit <= 0) throw new IllegalArgumentException("limit must be > 0");
        String sql = "SELECT imo FROM dataanalysis.ship_sts_seed_identity " +
                "WHERE seed_batch_no = ? AND enabled = 1 " +
                "GROUP BY imo, ship_ordinal " +
                "ORDER BY ship_ordinal ASC, imo ASC " +
                "LIMIT ? OFFSET ?";
        return jdbcTemplate.queryForList(sql, String.class, seedBatchNo, limit, offset);
    }

    public String findImoByMmsi(String seedBatchNo, String mmsi) {
        List<String> values = jdbcTemplate.queryForList(
                "SELECT DISTINCT imo FROM dataanalysis.ship_sts_seed_identity "
                        + "WHERE seed_batch_no = ? AND mmsi = ? AND enabled = 1 LIMIT 2",
                String.class, seedBatchNo, mmsi);
        if (values.size() > 1) {
            throw new IllegalStateException("MMSI belongs to multiple IMOs in seed: " + mmsi);
        }
        return values.isEmpty() ? null : values.get(0);
    }

    public List<StsSeedIdentityRow> selectIdentityRows(String seedBatchNo, List<String> selectedImos) {
        if (selectedImos == null || selectedImos.isEmpty()) {
            return Collections.emptyList();
        }
        StringBuilder sql = new StringBuilder(
                "SELECT id, seed_batch_no, ship_ordinal, imo, mmsi, " +
                "first_seen, last_seen, identity_mode, name, callsign, " +
                "length, width, type, minotype, enabled " +
                "FROM dataanalysis.ship_sts_seed_identity " +
                "WHERE seed_batch_no = ? AND enabled = 1 AND imo IN (");
        List<Object> params = new ArrayList<Object>();
        params.add(seedBatchNo);
        for (int i = 0; i < selectedImos.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("?");
            params.add(selectedImos.get(i));
        }
        sql.append(") ORDER BY ship_ordinal ASC, imo ASC, mmsi ASC");

        return jdbcTemplate.query(sql.toString(), params.toArray(), new SeedIdentityRowMapper());
    }

    private static String formatTs(java.sql.Timestamp ts) {
        if (ts == null) return null;
        String s = ts.toString();
        int dot = s.indexOf('.');
        if (dot >= 0) s = s.substring(0, dot);
        return s;
    }

    private static class SeedIdentityRowMapper implements RowMapper<StsSeedIdentityRow> {
        @Override
        public StsSeedIdentityRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            StsSeedIdentityRow r = new StsSeedIdentityRow();
            r.setId(rs.getLong("id"));
            r.setSeedBatchNo(rs.getString("seed_batch_no"));
            r.setShipOrdinal(rs.getInt("ship_ordinal"));
            r.setImo(rs.getString("imo"));
            r.setMmsi(rs.getString("mmsi"));
            java.sql.Timestamp fs = rs.getTimestamp("first_seen");
            if (fs != null) r.setFirstSeen(formatTs(fs));
            java.sql.Timestamp ls = rs.getTimestamp("last_seen");
            if (ls != null) r.setLastSeen(formatTs(ls));
            r.setIdentityMode(rs.getString("identity_mode"));
            r.setName(rs.getString("name"));
            r.setCallsign(rs.getString("callsign"));
            r.setLength(rs.getInt("length"));
            r.setWidth(rs.getInt("width"));
            r.setType(rs.getString("type"));
            r.setMinotype(rs.getString("minotype"));
            r.setEnabled(rs.getInt("enabled"));
            return r;
        }
    }
}
