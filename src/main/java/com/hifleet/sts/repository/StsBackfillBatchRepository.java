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

    static final String REFRESH_PROGRESS_SQL =
            "UPDATE dataanalysis.ship_sts_backfill_batch b " +
            "JOIN (SELECT batch_no, COUNT(*) AS total_tasks, " +
            "SUM(CASE WHEN status='COMPLETE' THEN 1 ELSE 0 END) AS completed_tasks, " +
            "SUM(CASE WHEN status='FAILED' THEN 1 ELSE 0 END) AS failed_tasks, " +
            "SUM(CASE WHEN status='RUNNING' THEN 1 ELSE 0 END) AS running_tasks, " +
            "SUM(CASE WHEN status='PENDING' THEN 1 ELSE 0 END) AS pending_tasks, " +
            "SUM(CASE WHEN status='FAILED' AND retry_count < ? THEN 1 ELSE 0 END) AS retryable_failed_tasks, " +
            "COALESCE(SUM(candidate_ship_count),0) AS candidate_ships, " +
            "COALESCE(SUM(processed_ship_count),0) AS processed_ships, " +
            "COALESCE(SUM(baseline_events),0) AS baseline_events, " +
            "COALESCE(SUM(shadow_inserted),0) AS shadow_inserted, " +
            "COALESCE(SUM(shadow_duplicate_skipped),0) AS duplicate_skipped " +
            "FROM dataanalysis.ship_sts_backfill_task WHERE batch_no=? GROUP BY batch_no) t " +
            "ON b.batch_no=t.batch_no SET " +
            "b.total_tasks=t.total_tasks, b.completed_tasks=t.completed_tasks, " +
            "b.failed_tasks=t.failed_tasks, " +
            "b.total_candidate_ships=t.candidate_ships, " +
            "b.total_processed_ships=t.processed_ships, " +
            "b.total_baseline_events=t.baseline_events, " +
            "b.total_shadow_inserted=t.shadow_inserted, " +
            "b.total_duplicate_skipped=t.duplicate_skipped, " +
            "b.status=CASE " +
            "WHEN t.running_tasks > 0 OR t.pending_tasks > 0 OR t.retryable_failed_tasks > 0 THEN 'RUNNING' " +
            "WHEN t.failed_tasks > 0 THEN 'PARTIAL_FAILED' " +
            "WHEN t.total_tasks > 0 AND t.completed_tasks=t.total_tasks THEN 'COMPLETE' " +
            "ELSE 'RUNNING' END, " +
            "b.last_error=CASE WHEN t.running_tasks=0 AND t.pending_tasks=0 " +
            "AND t.retryable_failed_tasks=0 AND t.failed_tasks > 0 " +
            "THEN CONCAT(t.failed_tasks, ' tasks failed') ELSE NULL END, " +
            "b.finished_at=CASE " +
            "WHEN t.running_tasks=0 AND t.pending_tasks=0 AND t.retryable_failed_tasks=0 " +
            "AND (t.failed_tasks > 0 OR (t.total_tasks > 0 AND t.completed_tasks=t.total_tasks)) " +
            "THEN COALESCE(b.finished_at,NOW()) ELSE NULL END, b.updated_at=NOW() " +
            "WHERE b.batch_no=?";

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

    public int refreshProgressFromTasks(String batchNo) {
        return jdbcTemplate.update(REFRESH_PROGRESS_SQL,
                StsBackfillTaskRepository.MAX_RETRY, batchNo, batchNo);
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
