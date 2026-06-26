package com.hifleet.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 区域通行分析任务进度记录，支持断点续跑。
 * <p>
 * 每月执行一次全船任务时，已完成的船舶写入本表；
 * 任务中断后重新启动，先跳过 status=DONE 的船舶，只算未完成的。
 */
@Repository
public class ZonePassageStatusRepository {

    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_FAILED = "FAILED";

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate newDataanalysisJdbcTemplate;

    /**
     * 查询本批次已完成的船舶 MMSI 集合。
     */
    public Set<String> findCompletedMmsi(String batchMonth, String startTime, String endTime) {
        String sql = "SELECT mmsi FROM dataanalysis.ship_zone_passage_status " +
                "WHERE batch_month = ? AND start_time = ? AND end_time = ? AND status = ?";
        List<String> list = newDataanalysisJdbcTemplate.query(sql,
                new Object[]{batchMonth, startTime, endTime, STATUS_DONE},
                (rs, rowNum) -> rs.getString("mmsi"));
        return new HashSet<String>(list);
    }

    /**
     * 标记船舶本批次已完成。
     */
    public void markCompleted(String batchMonth, String mmsi, String startTime, String endTime, int eventCount) {
        upsertStatus(batchMonth, mmsi, startTime, endTime, STATUS_DONE, eventCount);
    }

    public void markCompletedBatch(final List<CompletedRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO dataanalysis.ship_zone_passage_status " +
                "(batch_month, mmsi, start_time, end_time, status, event_count) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE status = VALUES(status), event_count = VALUES(event_count), " +
                "processed_at = CURRENT_TIMESTAMP";
        newDataanalysisJdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement stmt, int i) throws SQLException {
                CompletedRecord record = records.get(i);
                stmt.setString(1, record.getBatchMonth());
                stmt.setString(2, record.getMmsi());
                stmt.setString(3, record.getStartTime());
                stmt.setString(4, record.getEndTime());
                stmt.setString(5, STATUS_DONE);
                stmt.setInt(6, record.getEventCount());
            }

            @Override
            public int getBatchSize() {
                return records.size();
            }
        });
    }

    /**
     * 标记船舶本批次处理失败（下次重启会重试）。
     */
    public void markFailed(String batchMonth, String mmsi, String startTime, String endTime) {
        upsertStatus(batchMonth, mmsi, startTime, endTime, STATUS_FAILED, 0);
    }

    /**
     * 统计本批次指定状态的船舶数量。
     */
    public int countByStatus(String batchMonth, String startTime, String endTime, String status) {
        String sql = "SELECT COUNT(*) FROM dataanalysis.ship_zone_passage_status " +
                "WHERE batch_month = ? AND start_time = ? AND end_time = ? AND status = ?";
        Integer count = newDataanalysisJdbcTemplate.queryForObject(sql,
                new Object[]{batchMonth, startTime, endTime, status}, Integer.class);
        return count == null ? 0 : count;
    }

    private void upsertStatus(String batchMonth, String mmsi, String startTime, String endTime,
                            String status, int eventCount) {
        String sql = "INSERT INTO dataanalysis.ship_zone_passage_status " +
                "(batch_month, mmsi, start_time, end_time, status, event_count) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE status = VALUES(status), event_count = VALUES(event_count), " +
                "processed_at = CURRENT_TIMESTAMP";
        newDataanalysisJdbcTemplate.update(sql, batchMonth, mmsi, startTime, endTime, status, eventCount);
    }

    public static final class CompletedRecord {
        private final String batchMonth;
        private final String mmsi;
        private final String startTime;
        private final String endTime;
        private final int eventCount;

        public CompletedRecord(String batchMonth, String mmsi, String startTime, String endTime, int eventCount) {
            this.batchMonth = batchMonth;
            this.mmsi = mmsi;
            this.startTime = startTime;
            this.endTime = endTime;
            this.eventCount = eventCount;
        }

        public String getBatchMonth() {
            return batchMonth;
        }

        public String getMmsi() {
            return mmsi;
        }

        public String getStartTime() {
            return startTime;
        }

        public String getEndTime() {
            return endTime;
        }

        public int getEventCount() {
            return eventCount;
        }
    }
}
