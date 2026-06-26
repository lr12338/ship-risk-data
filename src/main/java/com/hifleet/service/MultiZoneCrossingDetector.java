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
 * 单次遍历轨迹，同时检测海峡/海区/桥区风电/alertarea 进出事件。
 */
public class MultiZoneCrossingDetector {

    private static final String WIND_FARM_NAME_PREFIX = "风电";
    private static final String ALERT_AREA_ZONE_SOURCE = "ais41.alertzones";

    public List<ZoneCrossingEvent> detectEvents(String mmsi,
                                                Vector<NewShipForm> points,
                                                ZoneSpatialMatcher straitMatcher,
                                                ZoneSpatialMatcher seaAreaMatcher,
                                                ZoneSpatialMatcher bridgeWindFarmMatcher,
                                                ZoneSpatialMatcher alertAreaMatcher) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }

        TrackState straitTrack = TrackState.of(straitMatcher);
        TrackState seaAreaTrack = TrackState.of(seaAreaMatcher);
        TrackState bridgeWindFarmTrack = TrackState.of(bridgeWindFarmMatcher);
        TrackState alertAreaTrack = TrackState.of(alertAreaMatcher);

        List<ZoneCrossingEvent> events = new ArrayList<ZoneCrossingEvent>();
        for (NewShipForm point : points) {
            if (!isValidPoint(point)) {
                continue;
            }
            double lon = Double.parseDouble(point.getLo().trim());
            double lat = Double.parseDouble(point.getLa().trim());
            straitTrack.collect(mmsi, point, lon, lat, events, TrackType.STRAIT);
            seaAreaTrack.collect(mmsi, point, lon, lat, events, TrackType.SEA_AREA);
            bridgeWindFarmTrack.collect(mmsi, point, lon, lat, events, TrackType.BRIDGE_WIND_FARM);
            alertAreaTrack.collect(mmsi, point, lon, lat, events, TrackType.ALERT_AREA);
        }
        return events;
    }

    private static final class TrackState {
        private final ZoneSpatialMatcher matcher;
        private ZoneDefinition previousZone;

        private TrackState(ZoneSpatialMatcher matcher) {
            this.matcher = matcher;
        }

        private static TrackState of(ZoneSpatialMatcher matcher) {
            return new TrackState(matcher);
        }

        private void collect(String mmsi,
                             NewShipForm point,
                             double lon,
                             double lat,
                             List<ZoneCrossingEvent> events,
                             TrackType trackType) {
            if (matcher == null) {
                return;
            }
            ZoneDefinition currentZone = matcher.match(lon, lat);
            Integer previousZoneId = previousZone == null ? null : previousZone.getId();
            Integer currentZoneId = currentZone == null ? null : currentZone.getId();

            if (previousZoneId == null && currentZoneId != null) {
                addTaggedEvent(events, mmsi, currentZone, ZoneCrossingEvent.ENTER, point, lon, lat, trackType);
            } else if (previousZoneId != null && currentZoneId == null) {
                addTaggedEvent(events, mmsi, previousZone, ZoneCrossingEvent.EXIT, point, lon, lat, trackType);
            } else if (previousZoneId != null && currentZoneId != null && !previousZoneId.equals(currentZoneId)) {
                addTaggedEvent(events, mmsi, previousZone, ZoneCrossingEvent.EXIT, point, lon, lat, trackType);
                addTaggedEvent(events, mmsi, currentZone, ZoneCrossingEvent.ENTER, point, lon, lat, trackType);
            }
            previousZone = currentZone;
        }
    }

    private enum TrackType {
        STRAIT, SEA_AREA, BRIDGE_WIND_FARM, ALERT_AREA
    }

    private static void addTaggedEvent(List<ZoneCrossingEvent> events,
                                       String mmsi,
                                       ZoneDefinition zone,
                                       String eventType,
                                       NewShipForm point,
                                       double lon,
                                       double lat,
                                       TrackType trackType) {
        ZoneCrossingEvent event = buildBaseEvent(mmsi, zone, eventType, point, lon, lat);
        switch (trackType) {
            case STRAIT:
                event.setEventCategory(ZoneCrossingEvent.CATEGORY_STRAIT);
                event.setZoneSource("ais41.alertzones");
                events.add(event);
                break;
            case SEA_AREA:
                event.setEventCategory(ZoneCrossingEvent.CATEGORY_SEA_AREA);
                event.setZoneSource("ais41.world_sea");
                events.add(event);
                break;
            case BRIDGE_WIND_FARM:
                tagBridgeAndWindFarmEvent(event);
                events.add(event);
                break;
            case ALERT_AREA:
                tagAlertAreaEvent(event, events);
                break;
            default:
                break;
        }
    }

    private static void tagBridgeAndWindFarmEvent(ZoneCrossingEvent event) {
        String zoneName = event.getZoneName();
        if (zoneName != null && zoneName.startsWith(WIND_FARM_NAME_PREFIX)) {
            event.setEventCategory(ZoneCrossingEvent.CATEGORY_WIND_FARM);
        } else {
            event.setEventCategory(ZoneCrossingEvent.CATEGORY_BRIDGE);
        }
        event.setZoneSource("ais41.alertzones");
    }

    private static void tagAlertAreaEvent(ZoneCrossingEvent event, List<ZoneCrossingEvent> events) {
        String category = resolveAlertAreaCategory(event.getZoneName());
        if (category == null) {
            return;
        }
        event.setEventCategory(category);
        event.setZoneSource(ALERT_AREA_ZONE_SOURCE);
        events.add(event);
    }

    private static String resolveAlertAreaCategory(String zoneName) {
        if (StringUtils.isBlank(zoneName)) {
            return null;
        }
        String name = zoneName.trim();
        if (name.contains("桥") || name.contains("海盗")) {
            return null;
        }
        if (name.contains("(JWC)")) {
            return ZoneCrossingEvent.CATEGORY_JWC;
        }
        if (StringUtils.containsIgnoreCase(name, "marpol")) {
            return ZoneCrossingEvent.CATEGORY_MARPOL;
        }
        if (name.contains("排放控制区")) {
            return ZoneCrossingEvent.CATEGORY_EMISSION_CONTROL;
        }
        if (name.contains("养殖区")) {
            return ZoneCrossingEvent.CATEGORY_AQUACULTURE;
        }
        if (name.contains("海洋保护区")) {
            return ZoneCrossingEvent.CATEGORY_MARINE_PROTECTED;
        }
        if (name.contains("冰区")) {
            return ZoneCrossingEvent.CATEGORY_ICE;
        }
        if (name.contains("高风险警示区") || name.contains("警示区")) {
            return ZoneCrossingEvent.CATEGORY_HIGH_RISK;
        }
        return null;
    }

    private static ZoneCrossingEvent buildBaseEvent(String mmsi,
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
        return event;
    }

    private static boolean isValidPoint(NewShipForm point) {
        return point != null
                && StringUtils.isNotBlank(point.getLo())
                && StringUtils.isNotBlank(point.getLa())
                && StringUtils.isNotBlank(point.getTi());
    }

    private static String normalizeTime(String time) {
        if (time == null) {
            return null;
        }
        String trimmed = time.trim();
        return trimmed.length() == 16 ? trimmed + ":00" : trimmed;
    }
}
