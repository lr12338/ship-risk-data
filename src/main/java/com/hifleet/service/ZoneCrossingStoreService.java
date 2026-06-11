package com.hifleet.service;

import com.hifleet.domain.ZoneCrossingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 区域进出事件落库，进入和离开各保存一条记录。
 */
@Service
public class ZoneCrossingStoreService {

    private static final Logger log = LoggerFactory.getLogger(ZoneCrossingStoreService.class);

    @Value("${zone.crossing.store.enabled:true}")
    private boolean storeEnabled;

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate newDataanalysisJdbcTemplate;

    public void saveEvents(List<ZoneCrossingEvent> events) {
        if (!storeEnabled || events == null || events.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO dataanalysis.ship_zone_crossing_event " +
                "(mmsi, zone_id, zone_name, event_category, event_type, event_time, lon, lat, port_id, zone_type, zone_source) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        for (ZoneCrossingEvent event : events) {
            try {
                newDataanalysisJdbcTemplate.update(sql,
                        event.getMmsi(),
                        event.getZoneId(),
                        event.getZoneName(),
                        event.getEventCategory(),
                        event.getEventType(),
                        event.getEventTime(),
                        event.getLon(),
                        event.getLat(),
                        event.getPortId(),
                        event.getZoneType(),
                        event.getZoneSource());
            } catch (Exception ex) {
                log.error("save zone crossing event failed, event={}", event, ex);
            }
        }
    }
}
