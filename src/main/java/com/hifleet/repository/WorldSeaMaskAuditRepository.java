package com.hifleet.repository;

import com.hifleet.worldsea.WorldSeaEventGate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorldSeaMaskAuditRepository {
    @Autowired @Qualifier("newDataanalysisJdbcTemplate") private JdbcTemplate jdbcTemplate;

    public void record(String batchNo, String mmsi, String behaviorType, String startTime, String endTime,
                       WorldSeaEventGate.GateStats stats, int rejectedWindows, int savedReplayQueries) {
        recordReplay(batchNo, mmsi, behaviorType, startTime, endTime, stats, rejectedWindows,
                0, 0, savedReplayQueries, 0, 0, 0, 0);
    }

    public void recordReplay(String batchNo, String mmsi, String behaviorType, String startTime, String endTime,
                             WorldSeaEventGate.GateStats stats, int rejectedWindows,
                             int baselinePlanned, int filteredPlanned, int saved,
                             int attempted, int successful, int failed, int truncated) {
        String sql = "INSERT INTO dataanalysis.ship_behavior_world_sea_audit "
                + "(batch_no,mmsi,behavior_type,window_start_time,window_end_time,raw_event_count,"
                + "world_sea_pass_event_count,world_sea_rejected_event_count,world_sea_checked_point_count,"
                + "world_sea_rejected_window_count,baseline_planned_replay_query_count,"
                + "filtered_planned_replay_query_count,saved_replay_query_count,attempted_replay_query_count,"
                + "successful_replay_query_count,failed_replay_query_count,truncated_replay_query_count) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE raw_event_count=VALUES(raw_event_count),"
                + "world_sea_pass_event_count=VALUES(world_sea_pass_event_count),"
                + "world_sea_rejected_event_count=VALUES(world_sea_rejected_event_count),"
                + "world_sea_checked_point_count=VALUES(world_sea_checked_point_count),"
                + "world_sea_rejected_window_count=VALUES(world_sea_rejected_window_count),"
                + "baseline_planned_replay_query_count=VALUES(baseline_planned_replay_query_count),"
                + "filtered_planned_replay_query_count=VALUES(filtered_planned_replay_query_count),"
                + "saved_replay_query_count=VALUES(saved_replay_query_count),"
                + "attempted_replay_query_count=VALUES(attempted_replay_query_count),"
                + "successful_replay_query_count=VALUES(successful_replay_query_count),"
                + "failed_replay_query_count=VALUES(failed_replay_query_count),"
                + "truncated_replay_query_count=VALUES(truncated_replay_query_count),updated_at=CURRENT_TIMESTAMP";
        jdbcTemplate.update(sql, batchNo, mmsi, behaviorType, startTime, endTime,
                stats.getRawEventCount(), stats.getPassEventCount(), stats.getRejectedEventCount(),
                stats.getCheckedPointCount(), rejectedWindows, baselinePlanned, filteredPlanned, saved,
                attempted, successful, failed, truncated);
    }
}
