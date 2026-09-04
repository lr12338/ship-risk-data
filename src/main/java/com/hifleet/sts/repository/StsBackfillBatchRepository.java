package com.hifleet.sts.repository;

import com.hifleet.sts.domain.StsBackfillBatchRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

@Repository
public class StsBackfillBatchRepository {

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    public StsBackfillBatchRow findByBatchNo(String batchNo) {
        String sql = "SELECT * FROM dataanalysis.ship_sts_backfill_batch WHERE batch_no=?";
        try {
            return jdbcTemplate.queryForObject(sql, new Object[]{batchNo}, new BatchRowMapper());
        } catch (Exception e) {
            return null;
        }
    }

    public int insert(StsBackfillBatchRow row) {
        String sql = "INSERT INTO dataanalysis.ship_sts_backfill_batch (" +
                "batch_no, runner_version, config_fingerprint, " +
                "target_list_batch_no, target_list_fingerprint, " +
                "expected_target_count, actual_target_count, " +
                "ship_offset, ship_count, selected_ship_count, " +
                "region_set_code, region_set_fingerprint, resolved_region_count, " +
                "from_time, to_time, slice_days, status, " +
                "total_tasks, completed_tasks, failed_tasks, " +
                "total_candidate_ships, total_processed_ships, " +
                "total_baseline_events, total_shadow_inserted, total_duplicate_skipped, " +
                "last_error, created_at, started_at, updated_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        Timestamp now = new Timestamp(System.currentTimeMillis());
        return jdbcTemplate.update(sql,
                row.getBatchNo(), row.getRunnerVersion(), row.getConfigFingerprint(),
                row.getTargetListBatchNo(), row.getTargetListFingerprint(),
                row.getExpectedTargetCount(), row.getActualTargetCount(),
                row.getShipOffset(), row.getShipCount(), row.getSelectedShipCount(),
                row.getRegionSetCode(), row.getRegionSetFingerprint(), row.getResolvedRegionCount(),
                toTs(row.getFromTime()), toTs(row.getToTime()), row.getSliceDays(), row.getStatus(),
                row.getTotalTasks(), row.getCompletedTasks(), row.getFailedTasks(),
                row.getTotalCandidateShips(), row.getTotalProcessedShips(),
                row.getTotalBaselineEvents(), row.getTotalShadowInserted(), row.getTotalDuplicateSkipped(),
                row.getLastError(),
                now, now, now);
    }

    public int updateStatus(String batchNo, String status, String lastError) {
        String sql = "UPDATE dataanalysis.ship_sts_backfill_batch " +
                "SET status=?, last_error=?, updated_at=NOW() " +
                "WHERE batch_no=?";
        return jdbcTemplate.update(sql, status,
                lastError != null ? truncate(lastError, 1000) : null, batchNo);
    }

    public int updateProgress(String batchNo, int completedTasks, int failedTasks,
                               int candidateShips, int processedShips,
                               int baselineEvents, int shadowInserted, int duplicateSkipped) {
        String sql = "UPDATE dataanalysis.ship_sts_backfill_batch SET " +
                "completed_tasks=?, failed_tasks=?, " +
                "total_candidate_ships=total_candidate_ships+?, " +
                "total_processed_ships=total_processed_ships+?, " +
                "total_baseline_events=total_baseline_events+?, " +
                "total_shadow_inserted=total_shadow_inserted+?, " +
                "total_duplicate_skipped=total_duplicate_skipped+?, " +
                "updated_at=NOW() " +
                "WHERE batch_no=?";
        return jdbcTemplate.update(sql,
                completedTasks, failedTasks,
                candidateShips, processedShips,
                baselineEvents, shadowInserted, duplicateSkipped,
                batchNo);
    }

    public int markFinished(String batchNo, String status, String lastError) {
        String sql = "UPDATE dataanalysis.ship_sts_backfill_batch SET " +
                "status=?, last_error=?, finished_at=NOW(), updated_at=NOW() " +
                "WHERE batch_no=?";
        return jdbcTemplate.update(sql, status,
                lastError != null ? truncate(lastError, 1000) : null, batchNo);
    }

    private static Timestamp toTs(java.util.Date d) {
        return d == null ? null : new Timestamp(d.getTime());
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    private static class BatchRowMapper implements RowMapper<StsBackfillBatchRow> {
        @Override
        public StsBackfillBatchRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            StsBackfillBatchRow r = new StsBackfillBatchRow();
            r.setBatchNo(rs.getString("batch_no"));
            r.setRunnerVersion(rs.getString("runner_version"));
            r.setConfigFingerprint(rs.getString("config_fingerprint"));
            r.setTargetListBatchNo(rs.getString("target_list_batch_no"));
            r.setTargetListFingerprint(rs.getString("target_list_fingerprint"));
            r.setExpectedTargetCount(rs.getInt("expected_target_count"));
            r.setActualTargetCount(rs.getInt("actual_target_count"));
            r.setShipOffset(rs.getInt("ship_offset"));
            r.setShipCount(rs.getInt("ship_count"));
            r.setSelectedShipCount(rs.getInt("selected_ship_count"));
            r.setRegionSetCode(rs.getString("region_set_code"));
            r.setRegionSetFingerprint(rs.getString("region_set_fingerprint"));
            r.setResolvedRegionCount(rs.getInt("resolved_region_count"));
            r.setFromTime(rs.getTimestamp("from_time"));
            r.setToTime(rs.getTimestamp("to_time"));
            r.setSliceDays(rs.getInt("slice_days"));
            r.setStatus(rs.getString("status"));
            r.setTotalTasks(rs.getInt("total_tasks"));
            r.setCompletedTasks(rs.getInt("completed_tasks"));
            r.setFailedTasks(rs.getInt("failed_tasks"));
            r.setTotalCandidateShips(rs.getInt("total_candidate_ships"));
            r.setTotalProcessedShips(rs.getInt("total_processed_ships"));
            r.setTotalBaselineEvents(rs.getInt("total_baseline_events"));
            r.setTotalShadowInserted(rs.getInt("total_shadow_inserted"));
            r.setTotalDuplicateSkipped(rs.getInt("total_duplicate_skipped"));
            r.setLastError(rs.getString("last_error"));
            r.setCreatedAt(rs.getTimestamp("created_at"));
            r.setStartedAt(rs.getTimestamp("started_at"));
            r.setUpdatedAt(rs.getTimestamp("updated_at"));
            r.setFinishedAt(rs.getTimestamp("finished_at"));
            return r;
        }
    }
}
