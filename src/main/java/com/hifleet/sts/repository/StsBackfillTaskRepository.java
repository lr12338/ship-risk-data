package com.hifleet.sts.repository;

import com.hifleet.sts.domain.StsBackfillTaskRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class StsBackfillTaskRepository {

    public static final int STALE_TIMEOUT_MINUTES = 180;
    public static final int MAX_RETRY = 3;
    static final String RECLAIM_STALE_SQL =
            "UPDATE dataanalysis.ship_sts_backfill_task " +
            "SET status='PENDING', retry_count = retry_count + 1, last_error = " +
            "  CASE WHEN last_error IS NULL THEN 'STALE_RECLAIM' " +
            "       ELSE CONCAT(LEFT(last_error, 900), ' | STALE_RECLAIM') END " +
            "WHERE batch_no=? AND status='RUNNING' " +
            "  AND updated_at < DATE_SUB(NOW(), INTERVAL ? MINUTE) " +
            "  AND retry_count < ?";
    static final String FAIL_EXHAUSTED_STALE_SQL =
            "UPDATE dataanalysis.ship_sts_backfill_task " +
            "SET status='FAILED', finished_at=NOW(), updated_at=NOW(), last_error = " +
            "  CASE WHEN last_error IS NULL THEN 'STALE_RETRY_EXHAUSTED' " +
            "       ELSE CONCAT(LEFT(last_error, 900), ' | STALE_RETRY_EXHAUSTED') END " +
            "WHERE batch_no=? AND status='RUNNING' " +
            "  AND updated_at < DATE_SUB(NOW(), INTERVAL ? MINUTE) " +
            "  AND retry_count >= ?";
    static final String SELECT_CLAIMABLE_SQL =
            "SELECT * FROM dataanalysis.ship_sts_backfill_task " +
            "WHERE batch_no=? AND ((status='PENDING' AND retry_count <= ?) " +
            "OR (status='FAILED' AND retry_count < ?)) " +
            "ORDER BY slice_seq ASC LIMIT 1 FOR UPDATE";
    static final String CLAIM_SQL =
            "UPDATE dataanalysis.ship_sts_backfill_task SET " +
            "retry_count=CASE WHEN status='FAILED' THEN retry_count + 1 ELSE retry_count END, " +
            "status='RUNNING', started_at=NOW(), finished_at=NULL, last_error=NULL, " +
            "candidate_ship_count=0, processed_ship_count=0, trajectory_loaded_ship_count=0, " +
            "activity_windows=0, region_activity_windows=0, replay_queries=0, " +
            "replay_failures=0, replay_truncated=0, baseline_events=0, " +
            "region_accepted_events=0, shadow_inserted=0, shadow_duplicate_skipped=0, " +
            "self_pair_events=0, updated_at=NOW() " +
            "WHERE id=? AND ((status='PENDING' AND retry_count <= ?) " +
            "OR (status='FAILED' AND retry_count < ?))";

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("newDataanalysisDataSource")
    private DataSource dataSource;

    public int countByBatchNo(String batchNo) {
        String sql = "SELECT COUNT(*) FROM dataanalysis.ship_sts_backfill_task WHERE batch_no=?";
        Integer count = jdbcTemplate.queryForObject(sql, new Object[]{batchNo}, Integer.class);
        return count == null ? 0 : count;
    }

    public int countByStatus(String batchNo, String status) {
        String sql = "SELECT COUNT(*) FROM dataanalysis.ship_sts_backfill_task " +
                "WHERE batch_no=? AND status=?";
        Integer count = jdbcTemplate.queryForObject(sql, new Object[]{batchNo, status}, Integer.class);
        return count == null ? 0 : count;
    }

    public int insertTasks(List<StsBackfillTaskRow> tasks) {
        if (tasks == null || tasks.isEmpty()) return 0;
        String sql = "INSERT IGNORE INTO dataanalysis.ship_sts_backfill_task " +
                "(batch_no, slice_seq, slice_from, slice_to, status, retry_count) " +
                "VALUES (?,?,?,?,?,?)";
        int total = 0;
        for (StsBackfillTaskRow task : tasks) {
            total += jdbcTemplate.update(sql,
                    task.getBatchNo(), task.getSliceSeq(),
                    toTs(task.getSliceFrom()), toTs(task.getSliceTo()),
                    "PENDING", 0);
        }
        return total;
    }

    /**
     * Atomically claim one PENDING task, or one eligible FAILED task with retry_count < MAX_RETRY.
     * Also reclaims stale RUNNING tasks older than STALE_TIMEOUT_MINUTES.
     *
     * @return claimed task, or null if no task available
     */
    public StsBackfillTaskRow claimNextTask(String batchNo) {
        TransactionTemplate tx = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return tx.execute(status -> {
            // First, recover stale RUNNING attempts using the same retry budget.
            reclaimStaleRunningTasks(batchNo);
            failExhaustedStaleRunningTasks(batchNo);

            StsBackfillTaskRow task = null;
            try {
                task = jdbcTemplate.queryForObject(SELECT_CLAIMABLE_SQL,
                        new Object[]{batchNo, MAX_RETRY, MAX_RETRY}, new TaskRowMapper());
            } catch (Exception e) {
                return null;
            }

            if (task == null) return null;

            int updated = jdbcTemplate.update(CLAIM_SQL,
                    task.getId(), MAX_RETRY, MAX_RETRY);
            if (updated == 0) return null;

            // Re-read to get updated timestamps
            return findById(task.getId());
        });
    }

    public int reclaimStaleRunningTasks(String batchNo) {
        return jdbcTemplate.update(RECLAIM_STALE_SQL, batchNo,
                STALE_TIMEOUT_MINUTES, MAX_RETRY);
    }

    public int failExhaustedStaleRunningTasks(String batchNo) {
        return jdbcTemplate.update(FAIL_EXHAUSTED_STALE_SQL, batchNo,
                STALE_TIMEOUT_MINUTES, MAX_RETRY);
    }

    public int touchRunning(Long taskId) {
        return jdbcTemplate.update("UPDATE dataanalysis.ship_sts_backfill_task "
                + "SET updated_at=NOW() WHERE id=? AND status='RUNNING'", taskId);
    }

    public StsBackfillTaskRow findById(Long id) {
        String sql = "SELECT * FROM dataanalysis.ship_sts_backfill_task WHERE id=?";
        try {
            return jdbcTemplate.queryForObject(sql, new Object[]{id}, new TaskRowMapper());
        } catch (Exception e) {
            return null;
        }
    }

    public int markComplete(Long taskId,
                             int candidateShipCount, int processedShipCount,
                             int trajectoryLoadedShipCount,
                             int activityWindows, int regionActivityWindows,
                             int replayQueries, int replayFailures, int replayTruncated,
                             int baselineEvents, int regionAcceptedEvents,
                             int shadowInserted, int shadowDuplicateSkipped,
                             int selfPairEvents) {
        String sql = "UPDATE dataanalysis.ship_sts_backfill_task SET " +
                "status='COMPLETE', " +
                "candidate_ship_count=?, processed_ship_count=?, " +
                "trajectory_loaded_ship_count=?, " +
                "activity_windows=?, region_activity_windows=?, " +
                "replay_queries=?, replay_failures=?, replay_truncated=?, " +
                "baseline_events=?, region_accepted_events=?, " +
                "shadow_inserted=?, shadow_duplicate_skipped=?, " +
                "self_pair_events=?, " +
                "last_error=NULL, finished_at=NOW(), updated_at=NOW() " +
                "WHERE id=?";
        return jdbcTemplate.update(sql,
                candidateShipCount, processedShipCount,
                trajectoryLoadedShipCount,
                activityWindows, regionActivityWindows,
                replayQueries, replayFailures, replayTruncated,
                baselineEvents, regionAcceptedEvents,
                shadowInserted, shadowDuplicateSkipped,
                selfPairEvents, taskId);
    }

    public int markFailed(Long taskId, String errorMessage,
                          int candidateShipCount, int processedShipCount,
                          int trajectoryLoadedShipCount,
                          int activityWindows, int regionActivityWindows,
                          int replayQueries, int replayFailures, int replayTruncated,
                          int baselineEvents, int regionAcceptedEvents,
                          int shadowInserted, int shadowDuplicateSkipped,
                          int selfPairEvents) {
        String sql = "UPDATE dataanalysis.ship_sts_backfill_task SET " +
                "status='FAILED', last_error=?, " +
                "candidate_ship_count=?, processed_ship_count=?, " +
                "trajectory_loaded_ship_count=?, activity_windows=?, region_activity_windows=?, " +
                "replay_queries=?, replay_failures=?, replay_truncated=?, " +
                "baseline_events=?, region_accepted_events=?, " +
                "shadow_inserted=?, shadow_duplicate_skipped=?, self_pair_events=?, " +
                "finished_at=NOW(), updated_at=NOW() WHERE id=?";
        return jdbcTemplate.update(sql, truncate(errorMessage, 1000),
                candidateShipCount, processedShipCount, trajectoryLoadedShipCount,
                activityWindows, regionActivityWindows,
                replayQueries, replayFailures, replayTruncated,
                baselineEvents, regionAcceptedEvents,
                shadowInserted, shadowDuplicateSkipped, selfPairEvents, taskId);
    }

    private static Timestamp toTs(java.util.Date d) {
        return d == null ? null : new Timestamp(d.getTime());
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    private static class TaskRowMapper implements RowMapper<StsBackfillTaskRow> {
        @Override
        public StsBackfillTaskRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            StsBackfillTaskRow r = new StsBackfillTaskRow();
            r.setId(rs.getLong("id"));
            r.setBatchNo(rs.getString("batch_no"));
            r.setSliceSeq(rs.getInt("slice_seq"));
            r.setSliceFrom(rs.getTimestamp("slice_from"));
            r.setSliceTo(rs.getTimestamp("slice_to"));
            r.setStatus(rs.getString("status"));
            r.setRetryCount(rs.getInt("retry_count"));
            r.setCandidateShipCount(rs.getInt("candidate_ship_count"));
            r.setProcessedShipCount(rs.getInt("processed_ship_count"));
            r.setTrajectoryLoadedShipCount(rs.getInt("trajectory_loaded_ship_count"));
            r.setActivityWindows(rs.getInt("activity_windows"));
            r.setRegionActivityWindows(rs.getInt("region_activity_windows"));
            r.setReplayQueries(rs.getInt("replay_queries"));
            r.setReplayFailures(rs.getInt("replay_failures"));
            r.setReplayTruncated(rs.getInt("replay_truncated"));
            r.setBaselineEvents(rs.getInt("baseline_events"));
            r.setRegionAcceptedEvents(rs.getInt("region_accepted_events"));
            r.setShadowInserted(rs.getInt("shadow_inserted"));
            r.setShadowDuplicateSkipped(rs.getInt("shadow_duplicate_skipped"));
            r.setSelfPairEvents(rs.getInt("self_pair_events"));
            r.setLastError(rs.getString("last_error"));
            r.setStartedAt(rs.getTimestamp("started_at"));
            r.setUpdatedAt(rs.getTimestamp("updated_at"));
            r.setFinishedAt(rs.getTimestamp("finished_at"));
            return r;
        }
    }
}
