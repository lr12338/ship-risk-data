package com.hifleet.regionbehavior;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.hifleet.repository.ShipBehaviorTableInitializer;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class RegionBehaviorStateRepository {
    @Autowired @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ShipBehaviorTableInitializer initializer;

    public void claim(RegionBehaviorJobParams params, String fingerprint, String paramsHash) {
        initializer.ensureRegionTablesReady();
        String behavior = params.getBehaviorTypes().get(0);
        try {
            jdbcTemplate.update("INSERT INTO dataanalysis.ship_behavior_region_batch_status "
                            + "(batch_no,params_hash,region_id,region_name,region_fingerprint,polygon_wkt,"
                            + "behavior_type,start_time,end_time,status) VALUES (?,?,?,?,?,?,?,?,?,'RUNNING')",
                    params.getBatchNo(), paramsHash, params.getRegionId(), params.getRegionName(),
                    fingerprint, params.getPolygonWkt(), behavior,
                    Timestamp.valueOf(RegionBehaviorTimes.parse(params.getStartTime())),
                    Timestamp.valueOf(RegionBehaviorTimes.parse(params.getEndTime())));
            return;
        } catch (DuplicateKeyException ignored) {
            // Immutable batch identity is checked below.
        }
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT params_hash,region_fingerprint,behavior_type FROM "
                        + "dataanalysis.ship_behavior_region_batch_status WHERE batch_no=?",
                params.getBatchNo());
        if (!paramsHash.equals(row.get("params_hash"))
                || !fingerprint.equals(row.get("region_fingerprint"))
                || !behavior.equals(row.get("behavior_type"))) {
            throw new IllegalStateException("region batchNo already exists with different immutable parameters");
        }
        jdbcTemplate.update("UPDATE dataanalysis.ship_behavior_region_batch_status SET status='RUNNING' WHERE batch_no=?",
                params.getBatchNo());
    }

    public Set<String> completed(String batchNo, String phase) {
        initializer.ensureRegionTablesReady();
        List<String> values = jdbcTemplate.queryForList(
                "SELECT slice_key FROM dataanalysis.ship_behavior_region_slice_status "
                        + "WHERE batch_no=? AND phase=? AND status='DONE'", String.class, batchNo, phase);
        return new HashSet<String>(values);
    }

    public void mark(String batchNo, String phase, String key, boolean done, String error) {
        initializer.ensureRegionTablesReady();
        jdbcTemplate.update("INSERT INTO dataanalysis.ship_behavior_region_slice_status "
                        + "(batch_no,phase,slice_key,status,error_message) VALUES (?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE status=VALUES(status),error_message=VALUES(error_message)",
                batchNo, phase, key, done ? "DONE" : "FAILED", error);
    }

    public void saveCandidate(String batchNo, String mmsi) {
        jdbcTemplate.update("INSERT IGNORE INTO dataanalysis.ship_behavior_region_candidate (batch_no,mmsi) VALUES (?,?)",
                batchNo, mmsi);
    }

    public List<String> candidateMmsis(String batchNo) {
        return jdbcTemplate.queryForList("SELECT mmsi FROM dataanalysis.ship_behavior_region_candidate "
                + "WHERE batch_no=? ORDER BY mmsi", String.class, batchNo);
    }

    public void finish(String batchNo, boolean complete, String summaryJson) {
        jdbcTemplate.update("UPDATE dataanalysis.ship_behavior_region_batch_status "
                        + "SET status=?,summary_json=? WHERE batch_no=?",
                complete ? "COMPLETED" : "PARTIAL", summaryJson, batchNo);
    }
}
