package com.hifleet.analysis.alongside.output.db;

import com.hifleet.analysis.alongside.core.model.AlongsideEvent;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.io.Serializable;

public class AlongsideEventDbMapper implements Serializable {

    private static final long serialVersionUID = 1L;

    public String upsertSql(String tableName) {
        return "INSERT INTO " + tableName + " ("
            + "event_id, behavior_type, event_type, event_sub_type, source_mode, rule_version, "
            + "pair_id, mmsi_a, mmsi_b, mmsi, target_mmsi, start_time, end_time, "
            + "start_lon, start_lat, end_lon, end_lat, center_lon, center_lat, duration_minutes, point_count, "
            + "min_distance_nm, max_distance_nm, avg_distance_nm, avg_speed, avg_relative_speed, heading_parallel_ratio, "
            + "pair_priority, ship_class_a, ship_class_b, severity_level, event_score, confidence_level, "
            + "exclusion_flag, exclusion_reason, score_detail_json, ais_gap_count, max_ais_gap_minutes, track_coverage_ratio, "
            + "kafka_topic, kafka_partition_id, kafka_offset"
            + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
            + "ON DUPLICATE KEY UPDATE "
            + "event_type=VALUES(event_type), event_sub_type=VALUES(event_sub_type), source_mode=VALUES(source_mode), "
            + "rule_version=VALUES(rule_version), pair_id=VALUES(pair_id), mmsi_a=VALUES(mmsi_a), mmsi_b=VALUES(mmsi_b), "
            + "mmsi=VALUES(mmsi), target_mmsi=VALUES(target_mmsi), start_time=VALUES(start_time), end_time=VALUES(end_time), "
            + "start_lon=VALUES(start_lon), start_lat=VALUES(start_lat), end_lon=VALUES(end_lon), end_lat=VALUES(end_lat), "
            + "center_lon=VALUES(center_lon), center_lat=VALUES(center_lat), "
            + "duration_minutes=VALUES(duration_minutes), point_count=VALUES(point_count), min_distance_nm=VALUES(min_distance_nm), "
            + "max_distance_nm=VALUES(max_distance_nm), avg_distance_nm=VALUES(avg_distance_nm), avg_speed=VALUES(avg_speed), "
            + "avg_relative_speed=VALUES(avg_relative_speed), heading_parallel_ratio=VALUES(heading_parallel_ratio), "
            + "pair_priority=VALUES(pair_priority), ship_class_a=VALUES(ship_class_a), ship_class_b=VALUES(ship_class_b), "
            + "severity_level=VALUES(severity_level), event_score=VALUES(event_score), confidence_level=VALUES(confidence_level), "
            + "exclusion_flag=VALUES(exclusion_flag), exclusion_reason=VALUES(exclusion_reason), "
            + "score_detail_json=VALUES(score_detail_json), ais_gap_count=VALUES(ais_gap_count), "
            + "max_ais_gap_minutes=VALUES(max_ais_gap_minutes), track_coverage_ratio=VALUES(track_coverage_ratio), kafka_topic=VALUES(kafka_topic), "
            + "kafka_partition_id=VALUES(kafka_partition_id), kafka_offset=VALUES(kafka_offset), updated_at=CURRENT_TIMESTAMP";
    }

    public void bind(PreparedStatement statement, AlongsideEvent event) throws SQLException {
        AlongsideEventIdGenerator.ensureEventId(event);
        int index = 1;
        statement.setString(index++, event.getEventId());
        statement.setString(index++, "ALONGSIDE");
        statement.setString(index++, event.getEventType());
        statement.setString(index++, event.getEventSubType());
        statement.setString(index++, event.getSourceMode());
        statement.setString(index++, event.getRuleVersion());
        statement.setString(index++, event.getPairId());
        statement.setString(index++, text(event.getMmsiA()));
        statement.setString(index++, text(event.getMmsiB()));
        statement.setString(index++, text(event.getMmsiA()));
        statement.setString(index++, text(event.getMmsiB()));
        statement.setTimestamp(index++, timestamp(event.getStartTime()));
        statement.setTimestamp(index++, timestamp(event.getEndTime()));
        setDouble(statement, index++, event.getStartLon());
        setDouble(statement, index++, event.getStartLat());
        setDouble(statement, index++, event.getEndLon());
        setDouble(statement, index++, event.getEndLat());
        setDouble(statement, index++, event.getCenterLon());
        setDouble(statement, index++, event.getCenterLat());
        setInt(statement, index++, event.getDurationMinutes());
        setInt(statement, index++, event.getPointCount());
        setDouble(statement, index++, event.getMinDistanceNm());
        setDouble(statement, index++, event.getMaxDistanceNm());
        setDouble(statement, index++, event.getAvgDistanceNm());
        setDouble(statement, index++, event.getAvgSpeedKnots());
        setDouble(statement, index++, event.getAvgRelativeSpeedKnots());
        setDouble(statement, index++, event.getHeadingParallelRatio());
        statement.setString(index++, event.getPairPriority());
        statement.setString(index++, event.getShipClassA());
        statement.setString(index++, event.getShipClassB());
        statement.setString(index++, event.getSeverityLevel());
        setDouble(statement, index++, event.getEventScore());
        statement.setString(index++, event.getConfidenceLevel());
        statement.setString(index++, event.getExclusionFlag());
        statement.setString(index++, event.getExclusionReason());
        statement.setString(index++, event.getScoreDetailJson());
        setInt(statement, index++, event.getAisGapCount());
        setLong(statement, index++, event.getMaxAisGapMinutes());
        setDouble(statement, index++, event.getTrackCoverageRatio());
        statement.setObject(index++, null);
        statement.setObject(index++, null);
        statement.setObject(index, null);
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static String text(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private static void setInt(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setInt(index, value.intValue());
        }
    }

    private static void setInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setInt(index, value.intValue());
        }
    }

    private static void setDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setDouble(index, value.doubleValue());
        }
    }

    private static void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setLong(index, value.longValue());
        }
    }
}
