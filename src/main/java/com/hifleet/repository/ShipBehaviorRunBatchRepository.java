package com.hifleet.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Repository
public class ShipBehaviorRunBatchRepository {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ShipBehaviorTableInitializer initializer;

    public void claim(String batchNo, String behaviorType, String listBatchNo,
                      String startTime, String endTime, String ruleVersion,
                      boolean enableInsert, String configHash) {
        initializer.ensureRunBatchTableReady();
        try {
            jdbcTemplate.update("INSERT INTO dataanalysis.ship_behavior_run_batch "
                            + "(batch_no,behavior_type,list_batch_no,start_time,end_time,rule_version,write_mode,config_hash) "
                            + "VALUES (?,?,?,?,?,?,?,?)", batchNo, behaviorType, listBatchNo,
                    timestamp(startTime), timestamp(endTime), ruleVersion,
                    enableInsert ? "FORMAL_EVENT" : "DRY_RUN", configHash);
            return;
        } catch (DuplicateKeyException ignored) {
            // Existing immutable manifests are compared below.
        }
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT behavior_type,list_batch_no,start_time,end_time,rule_version,write_mode,config_hash "
                        + "FROM dataanalysis.ship_behavior_run_batch WHERE batch_no=?", batchNo);
        boolean matches = behaviorType.equals(row.get("behavior_type"))
                && listBatchNo.equals(row.get("list_batch_no"))
                && timestamp(startTime).equals(row.get("start_time"))
                && timestamp(endTime).equals(row.get("end_time"))
                && ruleVersion.equals(row.get("rule_version"))
                && (enableInsert ? "FORMAL_EVENT" : "DRY_RUN").equals(row.get("write_mode"))
                && configHash.equals(row.get("config_hash"));
        if (!matches) {
            throw new IllegalStateException("batchNo already exists with different immutable parameters: " + batchNo);
        }
    }

    private Timestamp timestamp(String value) {
        return Timestamp.valueOf(LocalDateTime.parse(value, FORMATTER));
    }
}
