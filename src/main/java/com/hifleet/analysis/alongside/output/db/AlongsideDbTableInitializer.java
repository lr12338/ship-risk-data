package com.hifleet.analysis.alongside.output.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class AlongsideDbTableInitializer {

    public void initialize(Connection connection, AlongsideDbSinkConfig config) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(eventTableDdl(config.getEventTable()));
            statement.execute(detailTableDdl(config.getDetailTable()));
        }
    }

    public String eventTableDdl(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " ("
            + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
            + "event_id VARCHAR(64) NOT NULL,"
            + "behavior_type VARCHAR(32) NOT NULL DEFAULT 'ALONGSIDE',"
            + "event_type VARCHAR(64),"
            + "event_sub_type VARCHAR(64),"
            + "source_mode VARCHAR(64),"
            + "rule_version VARCHAR(64),"
            + "pair_id VARCHAR(64) NOT NULL,"
            + "mmsi_a VARCHAR(32) NOT NULL,"
            + "mmsi_b VARCHAR(32) NOT NULL,"
            + "mmsi VARCHAR(32),"
            + "target_mmsi VARCHAR(32),"
            + "start_time DATETIME NOT NULL,"
            + "end_time DATETIME NOT NULL,"
            + "duration_minutes INT,"
            + "point_count INT,"
            + "min_distance_nm DOUBLE,"
            + "max_distance_nm DOUBLE,"
            + "avg_distance_nm DOUBLE,"
            + "avg_speed DOUBLE,"
            + "avg_relative_speed DOUBLE,"
            + "heading_parallel_ratio DOUBLE,"
            + "pair_priority VARCHAR(64),"
            + "ship_class_a VARCHAR(64),"
            + "ship_class_b VARCHAR(64),"
            + "severity_level VARCHAR(16),"
            + "event_score DOUBLE,"
            + "confidence_level VARCHAR(32),"
            + "exclusion_flag VARCHAR(64),"
            + "exclusion_reason VARCHAR(255),"
            + "score_detail_json JSON,"
            + "kafka_topic VARCHAR(128),"
            + "kafka_partition_id INT,"
            + "kafka_offset BIGINT,"
            + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
            + "UNIQUE KEY uk_event_id (event_id),"
            + "KEY idx_pair_time (pair_id, start_time, end_time),"
            + "KEY idx_mmsi_a_time (mmsi_a, start_time),"
            + "KEY idx_mmsi_b_time (mmsi_b, start_time),"
            + "KEY idx_event_sub_type (event_sub_type),"
            + "KEY idx_severity (severity_level)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    public String detailTableDdl(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " ("
            + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
            + "event_id VARCHAR(64) NOT NULL,"
            + "behavior_type VARCHAR(32) NOT NULL DEFAULT 'ALONGSIDE',"
            + "pair_id VARCHAR(64) NOT NULL,"
            + "mmsi VARCHAR(32) NOT NULL,"
            + "target_mmsi VARCHAR(32),"
            + "point_role VARCHAR(16) NOT NULL,"
            + "event_start_time DATETIME NOT NULL,"
            + "event_end_time DATETIME NOT NULL,"
            + "point_time DATETIME NOT NULL,"
            + "point_index INT,"
            + "lon DOUBLE,"
            + "lat DOUBLE,"
            + "speed DOUBLE,"
            + "course DOUBLE,"
            + "heading DOUBLE,"
            + "nav_status VARCHAR(64),"
            + "paired_point_time DATETIME,"
            + "time_gap_seconds DOUBLE,"
            + "distance_nm DOUBLE,"
            + "relative_speed DOUBLE,"
            + "heading_diff DOUBLE,"
            + "match_confidence VARCHAR(32),"
            + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + "KEY idx_event_id (event_id),"
            + "KEY idx_pair_time (pair_id, event_start_time, event_end_time),"
            + "KEY idx_mmsi_time (mmsi, point_time),"
            + "KEY idx_point_role (point_role)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }
}
