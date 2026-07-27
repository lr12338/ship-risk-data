package com.hifleet.repository;

import com.hifleet.domain.ShipBehaviorEventDetail;
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
public class ShipBehaviorEventDetailRepository {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String INSERT_SQL = "INSERT INTO dataanalysis.ship_behavior_event_detail " +
            "(behavior_type, mmsi, target_mmsi, point_role, event_start_time, event_end_time, point_time, point_index, " +
            "lon, lat, speed, course, nav_status, batch_no, rule_version) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Value("${ship.behavior.trace.store.enabled:true}")
    private boolean traceStoreEnabled;

    @Value("${ship.behavior.store.batch-size:200}")
    private int batchSize;

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate newDataanalysisJdbcTemplate;

    @Autowired
    private ShipBehaviorTableInitializer shipBehaviorTableInitializer;

    public void saveDetails(List<ShipBehaviorEventDetail> details) {
        if (!traceStoreEnabled || details == null || details.isEmpty()) {
            return;
        }
        shipBehaviorTableInitializer.ensureDetailTableReady();
        List<ShipBehaviorEventDetail> validDetails = filterValid(details);
        int effectiveBatchSize = Math.max(1, batchSize);
        for (int offset = 0; offset < validDetails.size(); offset += effectiveBatchSize) {
            int end = Math.min(offset + effectiveBatchSize, validDetails.size());
            saveBatch(validDetails.subList(offset, end));
        }
    }

    public void deleteAlongsideWindow(String batchNo, String mmsi, String startTime, String endTime) {
        String sql = "DELETE FROM dataanalysis.ship_behavior_event_detail "
                + "WHERE behavior_type = ? AND batch_no = ? AND mmsi = ? "
                + "AND event_start_time >= ? AND event_end_time <= ?";
        newDataanalysisJdbcTemplate.update(sql, ShipBehaviorEvent.TYPE_ALONGSIDE,
                batchNo, mmsi, parseTime(startTime), parseTime(endTime));
    }

    private List<ShipBehaviorEventDetail> filterValid(List<ShipBehaviorEventDetail> details) {
        List<ShipBehaviorEventDetail> result = new ArrayList<ShipBehaviorEventDetail>();
        for (ShipBehaviorEventDetail detail : details) {
            if (detail == null
                    || StringUtils.isBlank(detail.getBehaviorType())
                    || StringUtils.isBlank(detail.getMmsi())
                    || StringUtils.isBlank(detail.getEventStartTime())
                    || StringUtils.isBlank(detail.getEventEndTime())
                    || StringUtils.isBlank(detail.getPointTime())
                    || detail.getPointIndex() == null) {
                continue;
            }
            result.add(detail);
        }
        return result;
    }

    private void saveBatch(final List<ShipBehaviorEventDetail> batch) {
        newDataanalysisJdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement stmt, int i) throws SQLException {
                ShipBehaviorEventDetail detail = batch.get(i);
                stmt.setString(1, detail.getBehaviorType());
                stmt.setString(2, detail.getMmsi());
                stmt.setString(3, detail.getTargetMmsi());
                stmt.setString(4, detail.getPointRole());
                stmt.setTimestamp(5, parseTime(detail.getEventStartTime()));
                stmt.setTimestamp(6, parseTime(detail.getEventEndTime()));
                stmt.setTimestamp(7, parseTime(detail.getPointTime()));
                stmt.setObject(8, detail.getPointIndex());
                stmt.setObject(9, detail.getLon());
                stmt.setObject(10, detail.getLat());
                stmt.setObject(11, detail.getSpeed());
                stmt.setObject(12, detail.getCourse());
                stmt.setString(13, detail.getNavStatus());
                stmt.setString(14, detail.getBatchNo());
                stmt.setString(15, detail.getRuleVersion());
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
