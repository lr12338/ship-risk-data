package com.hifleet.repository;

import com.hifleet.domain.ShipBehaviorEvent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ShipBehaviorEventRepository {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String INSERT_SQL = "INSERT INTO dataanalysis.ship_behavior_event " +
            "(behavior_type, mmsi, target_mmsi, start_time, end_time, duration_minutes, " +
            "start_lon, start_lat, end_lon, end_lat, min_distance_nm, max_distance_nm, " +
            "avg_speed, point_count, rule_version, batch_no, event_sub_type, severity_level, " +
            "event_score, confidence_level, exclusion_flag, exclusion_reason, ais_gap_count, " +
            "max_ais_gap_minutes, track_coverage_ratio, turn_count_per_hour, cog_change_per_hour, " +
            "tortuosity, center_lon, center_lat, radius_nm, score_detail_json) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Value("${ship.behavior.store.enabled:true}")
    private boolean storeEnabled;

    @Value("${ship.behavior.store.batch-size:200}")
    private int batchSize;

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate newDataanalysisJdbcTemplate;

    @Autowired
    private ShipBehaviorTableInitializer shipBehaviorTableInitializer;

    public void saveEvents(List<ShipBehaviorEvent> events) {
        if (!storeEnabled || events == null || events.isEmpty()) {
            return;
        }
        shipBehaviorTableInitializer.ensureTableReady();
        List<ShipBehaviorEvent> validEvents = filterValid(events);
        int effectiveBatchSize = Math.max(1, batchSize);
        for (int offset = 0; offset < validEvents.size(); offset += effectiveBatchSize) {
            int end = Math.min(offset + effectiveBatchSize, validEvents.size());
            saveBatch(validEvents.subList(offset, end));
        }
    }

    private List<ShipBehaviorEvent> filterValid(List<ShipBehaviorEvent> events) {
        List<ShipBehaviorEvent> result = new ArrayList<ShipBehaviorEvent>();
        for (ShipBehaviorEvent event : events) {
            if (event == null
                    || StringUtils.isBlank(event.getBehaviorType())
                    || StringUtils.isBlank(event.getMmsi())
                    || StringUtils.isBlank(event.getStartTime())
                    || StringUtils.isBlank(event.getEndTime())) {
                continue;
            }
            result.add(event);
        }
        return result;
    }

    private void saveBatch(final List<ShipBehaviorEvent> batch) {
        newDataanalysisJdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement stmt, int i) throws SQLException {
                ShipBehaviorEvent event = batch.get(i);
                stmt.setString(1, event.getBehaviorType());
                stmt.setString(2, event.getMmsi());
                stmt.setString(3, event.getTargetMmsi());
                stmt.setTimestamp(4, parseTime(event.getStartTime()));
                stmt.setTimestamp(5, parseTime(event.getEndTime()));
                stmt.setObject(6, event.getDurationMinutes());
                stmt.setObject(7, event.getStartLon());
                stmt.setObject(8, event.getStartLat());
                stmt.setObject(9, event.getEndLon());
                stmt.setObject(10, event.getEndLat());
                stmt.setObject(11, event.getMinDistanceNm());
                stmt.setObject(12, event.getMaxDistanceNm());
                stmt.setObject(13, event.getAvgSpeed());
                stmt.setObject(14, event.getPointCount());
                stmt.setString(15, event.getRuleVersion());
                stmt.setString(16, event.getBatchNo());
                stmt.setString(17, event.getEventSubType());
                stmt.setString(18, event.getSeverityLevel());
                stmt.setObject(19, event.getEventScore());
                stmt.setString(20, event.getConfidenceLevel());
                stmt.setString(21, event.getExclusionFlag());
                stmt.setString(22, event.getExclusionReason());
                stmt.setObject(23, event.getAisGapCount());
                stmt.setObject(24, event.getMaxAisGapMinutes());
                stmt.setObject(25, event.getTrackCoverageRatio());
                stmt.setObject(26, event.getTurnCountPerHour());
                stmt.setObject(27, event.getCogChangePerHour());
                stmt.setObject(28, event.getTortuosity());
                stmt.setObject(29, event.getCenterLon());
                stmt.setObject(30, event.getCenterLat());
                stmt.setObject(31, event.getRadiusNm());
                stmt.setString(32, event.getScoreDetailJson());
            }

            @Override
            public int getBatchSize() {
                return batch.size();
            }
        });
    }

    private Timestamp parseTime(String time) {
        return Timestamp.valueOf(LocalDateTime.parse(normalizeTime(time), TIME_FORMATTER));
    }

    private String normalizeTime(String time) {
        String trimmed = time.trim();
        return trimmed.length() == 16 ? trimmed + ":00" : trimmed;
    }
}
