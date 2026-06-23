package com.hifleet.service;

import com.hifleet.domain.ZoneCrossingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * 区域进出事件落库，进入和离开各保存一条记录。
 */
@Service
public class ZoneCrossingStoreService {

    private static final Logger log = LoggerFactory.getLogger(ZoneCrossingStoreService.class);

    private static final String INSERT_SQL = "INSERT INTO dataanalysis.ship_zone_crossing_event " +
            "(mmsi, zone_id, zone_name, event_category, event_type, event_time, lon, lat, port_id, zone_type, zone_source) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Value("${zone.crossing.store.enabled:true}")
    private boolean storeEnabled;

    @Value("${zone.crossing.store.batch-size:200}")
    private int batchSize;

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate newDataanalysisJdbcTemplate;

    public void saveEvents(List<ZoneCrossingEvent> events) {
        if (!storeEnabled || events == null || events.isEmpty()) {
            return;
        }
        int effectiveBatchSize = Math.max(1, batchSize);
        for (int offset = 0; offset < events.size(); offset += effectiveBatchSize) {
            int end = Math.min(offset + effectiveBatchSize, events.size());
            saveBatch(events.subList(offset, end));
        }
    }

    private void saveBatch(final List<ZoneCrossingEvent> batch) {
        try {
            newDataanalysisJdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement stmt, int i) throws SQLException {
                    ZoneCrossingEvent event = batch.get(i);
                    stmt.setString(1, event.getMmsi());
                    stmt.setObject(2, event.getZoneId());
                    stmt.setString(3, event.getZoneName());
                    stmt.setString(4, event.getEventCategory());
                    stmt.setString(5, event.getEventType());
                    stmt.setString(6, event.getEventTime());
                    stmt.setObject(7, event.getLon());
                    stmt.setObject(8, event.getLat());
                    stmt.setObject(9, event.getPortId());
                    stmt.setObject(10, event.getZoneType());
                    stmt.setString(11, event.getZoneSource());
                }

                @Override
                public int getBatchSize() {
                    return batch.size();
                }
            });
        } catch (Exception ex) {
            log.error("batch save zone crossing events failed, size={}", batch.size(), ex);
            throw new RuntimeException("save zone crossing events failed", ex);
        }
    }
}
