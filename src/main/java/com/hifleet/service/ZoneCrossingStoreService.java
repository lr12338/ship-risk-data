package com.hifleet.service;

import com.hifleet.domain.ZoneCrossingEvent;
import org.apache.commons.lang3.StringUtils;
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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 区域进出事件落库，进入和离开各保存一条记录。
 */
@Service
public class ZoneCrossingStoreService {

    private static final Logger log = LoggerFactory.getLogger(ZoneCrossingStoreService.class);
    private static final DateTimeFormatter EVENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_ZONE_NAME_LENGTH = 255;
    private static final int MAX_ZONE_SOURCE_LENGTH = 64;

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
        List<ZoneCrossingEvent> validEvents = sanitizeEvents(events);
        if (validEvents.isEmpty()) {
            return;
        }
        int effectiveBatchSize = Math.max(1, batchSize);
        for (int offset = 0; offset < validEvents.size(); offset += effectiveBatchSize) {
            int end = Math.min(offset + effectiveBatchSize, validEvents.size());
            saveBatch(validEvents.subList(offset, end));
        }
    }

    private List<ZoneCrossingEvent> sanitizeEvents(List<ZoneCrossingEvent> events) {
        List<ZoneCrossingEvent> validEvents = new ArrayList<ZoneCrossingEvent>(events.size());
        for (ZoneCrossingEvent event : events) {
            String reason = validateEvent(event);
            if (reason != null) {
                log.warn("skip invalid zone crossing event, mmsi={}, zoneId={}, reason={}, event={}",
                        event == null ? null : event.getMmsi(),
                        event == null ? null : event.getZoneId(),
                        reason,
                        event);
                continue;
            }
            normalizeEvent(event);
            validEvents.add(event);
        }
        return validEvents;
    }

    private String validateEvent(ZoneCrossingEvent event) {
        if (event == null) {
            return "event is null";
        }
        if (StringUtils.isBlank(event.getMmsi())) {
            return "mmsi is blank";
        }
        if (StringUtils.isBlank(event.getEventCategory())) {
            return "event_category is blank";
        }
        if (StringUtils.isBlank(event.getEventType())) {
            return "event_type is blank";
        }
        if (resolveEventTime(event.getEventTime()) == null) {
            return "event_time invalid: " + event.getEventTime();
        }
        return null;
    }

    private void normalizeEvent(ZoneCrossingEvent event) {
        String resolved = resolveEventTime(event.getEventTime());
        if (resolved != null) {
            event.setEventTime(resolved);
        }
        if (event.getZoneName() != null && event.getZoneName().length() > MAX_ZONE_NAME_LENGTH) {
            event.setZoneName(event.getZoneName().substring(0, MAX_ZONE_NAME_LENGTH));
        }
        if (event.getZoneSource() != null && event.getZoneSource().length() > MAX_ZONE_SOURCE_LENGTH) {
            event.setZoneSource(event.getZoneSource().substring(0, MAX_ZONE_SOURCE_LENGTH));
        }
    }

    /**
     * 将轨迹中的 event_time 解析为 MySQL 可接受的 canonical 字符串。
     * HBase 轨迹偶发 2025-02-29 / 02-31 等非法日历日；Java SMART 解析会归一化，
     * 但必须写入归一化后的值，否则 MySQL strict 模式会拒绝原始字符串。
     */
    private String resolveEventTime(String eventTime) {
        if (StringUtils.isBlank(eventTime)) {
            return null;
        }
        String normalized = normalizeEventTime(eventTime);
        try {
            LocalDateTime parsed = LocalDateTime.parse(normalized, EVENT_TIME_FORMATTER);
            String resolved = parsed.format(EVENT_TIME_FORMATTER);
            if (!normalized.equals(resolved)) {
                log.warn("correct invalid event_time {} -> {}", normalized, resolved);
            }
            return resolved;
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String normalizeEventTime(String eventTime) {
        String trimmed = eventTime.trim();
        return trimmed.length() == 16 ? trimmed + ":00" : trimmed;
    }

    private void saveBatch(final List<ZoneCrossingEvent> batch) {
        try {
            batchUpdate(batch);
        } catch (Exception ex) {
            log.error("batch save zone crossing events failed, size={}, rootCause={}",
                    batch.size(), rootCauseMessage(ex), ex);
            saveOneByOne(batch);
        }
    }

    private void saveOneByOne(List<ZoneCrossingEvent> batch) {
        int saved = 0;
        RuntimeException lastFailure = null;
        for (ZoneCrossingEvent event : batch) {
            try {
                batchUpdate(Collections.singletonList(event));
                saved++;
            } catch (Exception ex) {
                lastFailure = new RuntimeException(buildFailureMessage(event, ex), ex);
                log.error("save single zone crossing event failed, mmsi={}, zoneId={}, category={}, type={}, time={}, zoneName={}, rootCause={}",
                        event.getMmsi(),
                        event.getZoneId(),
                        event.getEventCategory(),
                        event.getEventType(),
                        event.getEventTime(),
                        event.getZoneName(),
                        rootCauseMessage(ex),
                        ex);
            }
        }
        if (lastFailure != null) {
            throw new RuntimeException("save zone crossing events failed, saved=" + saved + "/" + batch.size()
                    + ", lastError=" + lastFailure.getMessage(), lastFailure);
        }
    }

    private void batchUpdate(final List<ZoneCrossingEvent> batch) {
        newDataanalysisJdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement stmt, int i) throws SQLException {
                ZoneCrossingEvent event = batch.get(i);
                stmt.setString(1, event.getMmsi());
                stmt.setObject(2, event.getZoneId());
                stmt.setString(3, event.getZoneName());
                stmt.setString(4, event.getEventCategory());
                stmt.setString(5, event.getEventType());
                stmt.setTimestamp(6, Timestamp.valueOf(
                        LocalDateTime.parse(event.getEventTime(), EVENT_TIME_FORMATTER)));
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
    }

    private String buildFailureMessage(ZoneCrossingEvent event, Exception ex) {
        return "save zone crossing event failed, mmsi=" + event.getMmsi()
                + ", zoneId=" + event.getZoneId()
                + ", category=" + event.getEventCategory()
                + ", type=" + event.getEventType()
                + ", time=" + event.getEventTime()
                + ", rootCause=" + rootCauseMessage(ex);
    }

    private String rootCauseMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
