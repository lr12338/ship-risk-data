package com.hifleet.sts.repository;

import com.hifleet.sts.domain.StsFleetShipIdentity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Read-only repository for ship_behavior_target_ship used as STS fleet source.
 *
 * READ-ONLY CONTRACT: This class must never issue INSERT/UPDATE/DELETE/DDL.
 * All methods perform SELECT only.
 */
@Repository
public class StsTargetFleetRepository {

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private static final String IMO_VALID_REGEX = "^[0-9]{7}$";

    public int countPhysicalShips(String listBatchNo) {
        String sql = "SELECT COUNT(DISTINCT " +
                "CASE WHEN imo REGEXP ? THEN CONCAT('IMO:', imo) " +
                "WHEN mmsi IS NOT NULL AND mmsi != '' THEN CONCAT('MMSI:', mmsi) END) " +
                "FROM dataanalysis.ship_behavior_target_ship " +
                "WHERE list_batch_no=? AND enabled=1";
        Integer count = jdbcTemplate.queryForObject(sql,
                new Object[]{IMO_VALID_REGEX, listBatchNo}, Integer.class);
        return count == null ? 0 : count;
    }

    public int countRows(String listBatchNo) {
        String sql = "SELECT COUNT(*) FROM dataanalysis.ship_behavior_target_ship " +
                "WHERE list_batch_no=? AND enabled=1";
        Integer count = jdbcTemplate.queryForObject(sql,
                new Object[]{listBatchNo}, Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * Select a deterministic page of physical ships.
     *
     * Ordering: MIN(seq_no) ASC, physicalShipKey ASC
     *
     * @return list of physical ship identities in deterministic order
     */
    public List<StsFleetShipIdentity> selectPhysicalShips(
            String listBatchNo, int offset, int limit) {
        String sql =
            "SELECT physical_key, imo, mmsi_list, ship_name, ship_type, min_seq_no, identity_source " +
            "FROM ( " +
            "  SELECT " +
            "    CASE WHEN imo REGEXP ? THEN CONCAT('IMO:', imo) " +
            "         ELSE CONCAT('MMSI:', mmsi) END AS physical_key, " +
            "    CASE WHEN imo REGEXP ? THEN imo ELSE NULL END AS imo, " +
            "    GROUP_CONCAT(mmsi ORDER BY mmsi SEPARATOR ',') AS mmsi_list, " +
            "    MIN(ship_name) AS ship_name, " +
            "    MIN(ship_type) AS ship_type, " +
            "    MIN(seq_no) AS min_seq_no, " +
            "    CASE WHEN imo REGEXP ? THEN 'IMO' ELSE 'MMSI_ONLY' END AS identity_source, " +
            "    @rn := @rn + 1 AS rn " +
            "  FROM dataanalysis.ship_behavior_target_ship, (SELECT @rn := 0) vars " +
            "  WHERE list_batch_no=? AND enabled=1 AND mmsi IS NOT NULL AND mmsi != '' " +
            "  GROUP BY physical_key, imo, identity_source " +
            "  ORDER BY min_seq_no ASC, physical_key ASC " +
            ") ranked " +
            "WHERE rn > ? AND rn <= ? " +
            "ORDER BY min_seq_no ASC, physical_key ASC";

        return jdbcTemplate.query(sql,
                new Object[]{IMO_VALID_REGEX, IMO_VALID_REGEX, IMO_VALID_REGEX,
                        listBatchNo, offset, offset + limit},
                new FleetIdentityRowMapper());
    }

    public List<StsFleetShipIdentity> selectAllPhysicalShips(String listBatchNo) {
        String sql =
            "SELECT " +
            "  CASE WHEN imo REGEXP ? THEN CONCAT('IMO:', imo) " +
            "       ELSE CONCAT('MMSI:', mmsi) END AS physical_key, " +
            "  CASE WHEN imo REGEXP ? THEN imo ELSE NULL END AS imo, " +
            "  GROUP_CONCAT(mmsi ORDER BY mmsi SEPARATOR ',') AS mmsi_list, " +
            "  MIN(ship_name) AS ship_name, " +
            "  MIN(ship_type) AS ship_type, " +
            "  MIN(seq_no) AS min_seq_no, " +
            "  CASE WHEN imo REGEXP ? THEN 'IMO' ELSE 'MMSI_ONLY' END AS identity_source " +
            "FROM dataanalysis.ship_behavior_target_ship " +
            "WHERE list_batch_no=? AND enabled=1 AND mmsi IS NOT NULL AND mmsi != '' " +
            "GROUP BY physical_key, imo, identity_source " +
            "ORDER BY min_seq_no ASC, physical_key ASC";

        return jdbcTemplate.query(sql,
                new Object[]{IMO_VALID_REGEX, IMO_VALID_REGEX, IMO_VALID_REGEX, listBatchNo},
                new FleetIdentityRowMapper());
    }

    public int countMmsiIdentityConflicts(String listBatchNo) {
        String sql =
            "SELECT COUNT(*) FROM ( " +
            "  SELECT mmsi, COUNT(DISTINCT imo) AS imo_cnt " +
            "  FROM dataanalysis.ship_behavior_target_ship " +
            "  WHERE list_batch_no=? AND enabled=1 " +
            "    AND imo IS NOT NULL AND imo != '' AND imo REGEXP ? " +
            "  GROUP BY mmsi " +
            "  HAVING COUNT(DISTINCT imo) > 1 " +
            ") t";
        Integer count = jdbcTemplate.queryForObject(sql,
                new Object[]{listBatchNo, IMO_VALID_REGEX}, Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * Check for MMSI identity conflicts within a batch.
     * A conflict = same MMSI mapping to multiple different valid IMOs.
     *
     * With UNIQUE(list_batch_no, mmsi) on the table, this should always be 0.
     * But we verify at fleet load time as a safety check.
     */
    public Map<String, List<String>> findMmsiIdentityConflicts(String listBatchNo) {
        String sql =
            "SELECT mmsi, GROUP_CONCAT(DISTINCT imo ORDER BY imo SEPARATOR ',') AS imos " +
            "FROM dataanalysis.ship_behavior_target_ship " +
            "WHERE list_batch_no=? AND enabled=1 " +
            "  AND imo IS NOT NULL AND imo != '' AND imo REGEXP ? " +
            "GROUP BY mmsi " +
            "HAVING COUNT(DISTINCT imo) > 1 " +
            "LIMIT 100";

        final Map<String, List<String>> result = new TreeMap<String, List<String>>();
        jdbcTemplate.query(sql, new Object[]{listBatchNo, IMO_VALID_REGEX}, rs -> {
            String mmsi = rs.getString("mmsi");
            String imosStr = rs.getString("imos");
            List<String> imos = new ArrayList<String>();
            if (imosStr != null) {
                for (String s : imosStr.split(",")) {
                    if (!s.isEmpty()) imos.add(s);
                }
            }
            result.put(mmsi, imos);
        });
        return result;
    }

    public Set<String> getAllMmsis(String listBatchNo) {
        String sql = "SELECT DISTINCT mmsi FROM dataanalysis.ship_behavior_target_ship " +
                "WHERE list_batch_no=? AND enabled=1 AND mmsi IS NOT NULL AND mmsi != ''";
        final Set<String> mmsis = new TreeSet<String>();
        jdbcTemplate.query(sql, new Object[]{listBatchNo}, rs -> {
            String mmsi = rs.getString(1);
            if (mmsi != null && !mmsi.isEmpty()) mmsis.add(mmsi);
        });
        return mmsis;
    }

    private static class FleetIdentityRowMapper implements RowMapper<StsFleetShipIdentity> {
        @Override
        public StsFleetShipIdentity mapRow(ResultSet rs, int rowNum) throws SQLException {
            StsFleetShipIdentity identity = new StsFleetShipIdentity();
            identity.setPhysicalShipKey(rs.getString("physical_key"));
            String imo = rs.getString("imo");
            identity.setImo(imo == null || imo.isEmpty() ? null : imo);
            identity.setShipName(rs.getString("ship_name"));
            identity.setShipType(rs.getString("ship_type"));
            identity.setMinSeqNo(rs.getInt("min_seq_no"));
            identity.setIdentitySource(rs.getString("identity_source"));

            String mmsiList = rs.getString("mmsi_list");
            String primaryMmsi = null;
            if (mmsiList != null && !mmsiList.isEmpty()) {
                for (String m : mmsiList.split(",")) {
                    if (!m.isEmpty()) {
                        identity.addMmsi(m);
                        if (primaryMmsi == null) primaryMmsi = m;
                    }
                }
            }
            identity.setPrimaryMmsi(primaryMmsi);
            return identity;
        }
    }
}
