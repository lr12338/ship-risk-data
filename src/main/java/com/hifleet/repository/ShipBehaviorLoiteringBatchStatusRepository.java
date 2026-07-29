package com.hifleet.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class ShipBehaviorLoiteringBatchStatusRepository {

    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAILED = "FAILED";
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_NO_DATA = "NO_DATA";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate newDataanalysisJdbcTemplate;

    public Set<String> findCompletedWindows(String batchNo) {
        String sql = "SELECT mmsi, window_start_time, window_end_time "
                + "FROM dataanalysis.ship_behavior_loitering_batch_status "
                + "WHERE batch_no = ? AND status = ?";
        List<String> list = newDataanalysisJdbcTemplate.query(sql,
                new Object[]{batchNo, STATUS_DONE},
                (rs, rowNum) -> windowKey(rs.getString("mmsi"),
                        rs.getTimestamp("window_start_time").toLocalDateTime().format(TIME_FORMATTER),
                        rs.getTimestamp("window_end_time").toLocalDateTime().format(TIME_FORMATTER)));
        return new HashSet<String>(list);
    }

    public void markDone(String batchNo, String mmsi, String windowStartTime, String windowEndTime,
                         int pointCount, int eventCount) {
        markDone(batchNo, mmsi, windowStartTime, windowEndTime,
                pointCount, eventCount, 0, 0, eventCount, eventCount,
                pointCount == 0 ? RESULT_NO_DATA : RESULT_SUCCESS, null);
    }

    public void markDone(String batchNo, String mmsi,
                         String windowStartTime, String windowEndTime,
                         int pointCount, int candidateCount, int weakCount,
                         int downgradedCount, int validCount, int storedEventCount,
                         String resultCode, Long elapsedMs) {
        upsert(batchNo, mmsi, windowStartTime, windowEndTime,
                STATUS_DONE, pointCount, storedEventCount,
                candidateCount, weakCount, downgradedCount, validCount,
                storedEventCount, resultCode, elapsedMs, null);
    }

    public void markFailed(String batchNo, String mmsi, String windowStartTime, String windowEndTime,
                           String errorMessage) {
        markFailed(batchNo, mmsi, windowStartTime, windowEndTime,
                errorMessage, null);
    }

    public void markFailed(String batchNo, String mmsi,
                           String windowStartTime, String windowEndTime,
                           String errorMessage, Long elapsedMs) {
        upsert(batchNo, mmsi, windowStartTime, windowEndTime,
                STATUS_FAILED, null, 0, null, null, null, null, 0,
                null, elapsedMs, truncate(errorMessage));
    }

    public int countByStatus(String batchNo, String status) {
        String sql = "SELECT COUNT(*) FROM dataanalysis.ship_behavior_loitering_batch_status "
                + "WHERE batch_no = ? AND status = ?";
        Integer count = newDataanalysisJdbcTemplate.queryForObject(sql,
                new Object[]{batchNo, status}, Integer.class);
        return count == null ? 0 : count;
    }

    public static String windowKey(String mmsi, String windowStartTime, String windowEndTime) {
        return mmsi + "|" + windowStartTime + "|" + windowEndTime;
    }

    private void upsert(String batchNo, String mmsi, String windowStartTime, String windowEndTime,
                        String status, Integer pointCount, int eventCount,
                        Integer candidateCount, Integer weakCount,
                        Integer downgradedCount, Integer validCount,
                        Integer storedEventCount, String resultCode,
                        Long elapsedMs, String errorMessage) {
        String sql = "INSERT INTO dataanalysis.ship_behavior_loitering_batch_status "
                + "(batch_no, mmsi, window_start_time, window_end_time, status, point_count, event_count, "
                + "candidate_count, weak_count, downgraded_count, valid_count, stored_event_count, "
                + "result_code, elapsed_ms, error_message) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE status = VALUES(status), point_count = VALUES(point_count), "
                + "event_count = VALUES(event_count), candidate_count = VALUES(candidate_count), "
                + "weak_count = VALUES(weak_count), downgraded_count = VALUES(downgraded_count), "
                + "valid_count = VALUES(valid_count), stored_event_count = VALUES(stored_event_count), "
                + "result_code = VALUES(result_code), elapsed_ms = VALUES(elapsed_ms), "
                + "error_message = VALUES(error_message), updated_at = CURRENT_TIMESTAMP";
        newDataanalysisJdbcTemplate.update(sql, batchNo, mmsi, windowStartTime, windowEndTime,
                status, pointCount, eventCount, candidateCount, weakCount,
                downgradedCount, validCount, storedEventCount, resultCode,
                elapsedMs, errorMessage);
    }

    private String truncate(String message) {
        if (message == null || message.length() <= 1024) {
            return message;
        }
        return message.substring(0, 1024);
    }
}
