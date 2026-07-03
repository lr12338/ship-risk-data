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
        upsert(batchNo, mmsi, windowStartTime, windowEndTime, STATUS_DONE, pointCount, eventCount, null);
    }

    public void markFailed(String batchNo, String mmsi, String windowStartTime, String windowEndTime,
                           String errorMessage) {
        upsert(batchNo, mmsi, windowStartTime, windowEndTime, STATUS_FAILED, null, 0, truncate(errorMessage));
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
                        String status, Integer pointCount, int eventCount, String errorMessage) {
        String sql = "INSERT INTO dataanalysis.ship_behavior_loitering_batch_status "
                + "(batch_no, mmsi, window_start_time, window_end_time, status, point_count, event_count, error_message) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE status = VALUES(status), point_count = VALUES(point_count), "
                + "event_count = VALUES(event_count), error_message = VALUES(error_message), updated_at = CURRENT_TIMESTAMP";
        newDataanalysisJdbcTemplate.update(sql, batchNo, mmsi, windowStartTime, windowEndTime,
                status, pointCount, eventCount, errorMessage);
    }

    private String truncate(String message) {
        if (message == null || message.length() <= 1024) {
            return message;
        }
        return message.substring(0, 1024);
    }
}
