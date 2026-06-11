package com.hifleet.service;

import com.hifleet.domain.ZoneCrossingEvent;
import com.hifleet.domain.ZoneDefinition;
import com.hifleet.route.beans.NewShipForm;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

/**
 * 逐 AIS 点检测区域进入/离开，逻辑参考 event 项目 SeaAreaPassDetector。
 * 进入、离开各生成一条事件记录。
 */
public class ZoneCrossingDetector {

    public List<ZoneCrossingEvent> detectEvents(String mmsi, Vector<NewShipForm> points, ZoneSpatialMatcher matcher) {
        if (points == null || points.isEmpty() || matcher == null) {
            return Collections.emptyList();
        }

        List<ZoneCrossingEvent> events = new ArrayList<ZoneCrossingEvent>();
        ZoneDefinition previousZone = null;

        for (NewShipForm point : points) {
            if (!isValidPoint(point)) {
                continue;
            }

            double lon = Double.parseDouble(point.getLo().trim());
            double lat = Double.parseDouble(point.getLa().trim());
            ZoneDefinition currentZone = matcher.match(lon, lat);

            Integer previousZoneId = previousZone == null ? null : previousZone.getId();
            Integer currentZoneId = currentZone == null ? null : currentZone.getId();

            if (previousZoneId == null && currentZoneId != null) {
                events.add(buildEvent(mmsi, currentZone, ZoneCrossingEvent.ENTER, point, lon, lat));
            } else if (previousZoneId != null && currentZoneId == null) {
                events.add(buildEvent(mmsi, previousZone, ZoneCrossingEvent.EXIT, point, lon, lat));
            } else if (previousZoneId != null && currentZoneId != null && !previousZoneId.equals(currentZoneId)) {
                events.add(buildEvent(mmsi, previousZone, ZoneCrossingEvent.EXIT, point, lon, lat));
                events.add(buildEvent(mmsi, currentZone, ZoneCrossingEvent.ENTER, point, lon, lat));
            }

            previousZone = currentZone;
        }
        return events;
    }

    private ZoneCrossingEvent buildEvent(String mmsi,
                                         ZoneDefinition zone,
                                         String eventType,
                                         NewShipForm point,
                                         double lon,
                                         double lat) {
        ZoneCrossingEvent event = new ZoneCrossingEvent();
        event.setMmsi(mmsi);
        event.setZoneId(zone.getId());
        event.setZoneName(zone.getName());
        event.setEventType(eventType);
        event.setEventTime(normalizeTime(point.getTi()));
        event.setLon(lon);
        event.setLat(lat);
        event.setPortId(zone.getPortId());
        event.setZoneType(zone.getZoneType());
        event.setZoneSource(zone.getSource());
        return event;
    }

    private boolean isValidPoint(NewShipForm point) {
        return point != null
                && StringUtils.isNotBlank(point.getLo())
                && StringUtils.isNotBlank(point.getLa())
                && StringUtils.isNotBlank(point.getTi());
    }

    private String normalizeTime(String time) {
        if (time == null) {
            return null;
        }
        String trimmed = time.trim();
        return trimmed.length() == 16 ? trimmed + ":00" : trimmed;
    }
}
