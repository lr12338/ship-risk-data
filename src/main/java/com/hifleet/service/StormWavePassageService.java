package com.hifleet.service;

import com.hifleet.domain.ZoneCrossingEvent;
import com.hifleet.route.beans.NewShipForm;
import com.hifleet.service.StormRegionIndex.StormRegion;
import org.apache.commons.lang3.StringUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.WKTReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.stream.Collectors;

/**
 * 大风浪 / 大风区域进出计算。
 * <p>
 * 批次开始时从 PostgreSQL 预加载 regions_wave / regions_wind，单船计算在本地用 JTS 做线面相交。
 */
@Service
public class StormWavePassageService {

    private static final Logger log = LoggerFactory.getLogger(StormWavePassageService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final ZoneId UTC_ZONE = ZoneId.of("UTC");
    private static final int SEGMENT_THRESHOLD_HOURS = 8;
    private static final int AREA_VALID_HOURS = 6;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final WKTReader WKT_READER = new WKTReader(GEOMETRY_FACTORY);

    private static final String LOAD_REGIONS_SQL =
            "SELECT id, name, time, ST_AsText(geom) AS geom_wkt FROM %s WHERE " +
            "(to_timestamp(time, 'yyyy-MM-dd HH24:mi:ss') AT TIME ZONE 'UTC') < " +
            "(to_timestamp(?, 'yyyy-MM-dd HH24:mi:ss') AT TIME ZONE 'Asia/Shanghai' AT TIME ZONE 'UTC') AND " +
            "(to_timestamp(time, 'yyyy-MM-dd HH24:mi:ss') AT TIME ZONE 'UTC' + INTERVAL '6 hours') > " +
            "(to_timestamp(?, 'yyyy-MM-dd HH24:mi:ss') AT TIME ZONE 'Asia/Shanghai' AT TIME ZONE 'UTC')";

    @Autowired
    @Qualifier("wgq111PGJdbcTemplate")
    private JdbcTemplate wgq111PGJdbcTemplate;

    private volatile boolean pgAvailable = true;

    public boolean initPgAvailability() {
        try {
            wgq111PGJdbcTemplate.queryForObject("SELECT 1", Integer.class);
            pgAvailable = true;
        } catch (Exception ex) {
            pgAvailable = false;
            log.warn("PostgreSQL unavailable, skip storm wave/wind for this job: {}", ex.getMessage());
        }
        return pgAvailable;
    }

    /**
     * 按批次时间范围预加载大风/大浪区域，整个批次内复用。
     */
    public StormRegionIndex loadStormRegions(String startTime, String endTime) {
        if (!pgAvailable) {
            return StormRegionIndex.empty();
        }
        try {
            List<StormRegion> waveRegions = loadRegions("regions_wave",
                    ZoneCrossingEvent.CATEGORY_STORM_WAVE, startTime, endTime);
            List<StormRegion> windRegions = loadRegions("regions_wind",
                    ZoneCrossingEvent.CATEGORY_STORM_WIND, startTime, endTime);
            StormRegionIndex index = StormRegionIndex.of(waveRegions, windRegions);
            log.info("preloaded storm regions, wave={}, wind={}, range={}~{}",
                    index.getWaveCount(), index.getWindCount(), startTime, endTime);
            return index;
        } catch (Exception ex) {
            log.error("preload storm regions failed, range={}~{}", startTime, endTime, ex);
            return StormRegionIndex.empty();
        }
    }

    public List<ZoneCrossingEvent> calculateStormPassage(String mmsi,
                                                         Vector<NewShipForm> points,
                                                         StormRegionIndex stormRegionIndex) {
        if (points == null || points.size() < 2 || stormRegionIndex == null || stormRegionIndex.isEmpty()) {
            return Collections.emptyList();
        }

        List<TrajectoryPoint> trajectoryPoints = toTrajectoryPoints(points);
        if (trajectoryPoints.size() < 2) {
            return Collections.emptyList();
        }

        List<List<TrajectoryPoint>> segments = segmentTrajectory(trajectoryPoints, SEGMENT_THRESHOLD_HOURS);
        List<ZoneCrossingEvent> allEvents = new ArrayList<ZoneCrossingEvent>();
        for (List<TrajectoryPoint> segment : segments) {
            if (segment.size() < 2) {
                continue;
            }
            LineString line = buildLineString(segment);
            if (line == null || line.isEmpty()) {
                continue;
            }
            Envelope lineEnvelope = line.getEnvelopeInternal();
            String segStart = segment.get(0).getTime();
            String segEnd = segment.get(segment.size() - 1).getTime();
            Map<String, String> coordTimeMap = buildCoordTimeMap(segment);

            if (stormRegionIndex.mayIntersectWave(lineEnvelope)) {
                allEvents.addAll(calculateByRegions(mmsi, line, lineEnvelope, segStart, segEnd, coordTimeMap,
                        stormRegionIndex.findWaveCandidates(lineEnvelope, segStart, segEnd)));
            }
            if (stormRegionIndex.mayIntersectWind(lineEnvelope)) {
                allEvents.addAll(calculateByRegions(mmsi, line, lineEnvelope, segStart, segEnd, coordTimeMap,
                        stormRegionIndex.findWindCandidates(lineEnvelope, segStart, segEnd)));
            }
        }
        return allEvents;
    }

    private List<ZoneCrossingEvent> calculateByRegions(String mmsi,
                                                       LineString line,
                                                       Envelope lineEnvelope,
                                                       String segStart,
                                                       String segEnd,
                                                       Map<String, String> coordTimeMap,
                                                       List<StormRegion> candidates) {
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<ZoneCrossingEvent> events = new ArrayList<ZoneCrossingEvent>();
        for (StormRegion region : candidates) {
            if (!region.getPreparedGeometry().intersects(line)) {
                continue;
            }
            Geometry intersection = region.getGeometry().intersection(line);
            List<String> intersectionTimes = extractIntersectionTimes(intersection, coordTimeMap);
            if (intersectionTimes.isEmpty()) {
                continue;
            }
            String enterTime = intersectionTimes.get(0);
            String exitTime = intersectionTimes.get(intersectionTimes.size() - 1);
            TimeOverlap overlap = calculateTimeOverlap(enterTime, exitTime, region.getTimeUtc());
            if (overlap == null) {
                continue;
            }
            events.addAll(toEnterExitPair(mmsi, region.getId(), region.getName(), region.getCategory(), region.getTableName(),
                    overlap.enterTime, overlap.exitTime));
        }
        return events;
    }

    private List<StormRegion> loadRegions(String tableName, String category, String startTime, String endTime) {
        String sql = String.format(LOAD_REGIONS_SQL, tableName);
        return wgq111PGJdbcTemplate.query(sql, new Object[]{endTime, startTime}, (rs, rowNum) -> {
            String wkt = rs.getString("geom_wkt");
            if (StringUtils.isBlank(wkt)) {
                return null;
            }
            try {
                Geometry geometry = WKT_READER.read(wkt);
                if (geometry == null || geometry.isEmpty()) {
                    return null;
                }
                geometry.setSRID(4326);
                return new StormRegion(rs.getInt("id"), rs.getString("name"), rs.getString("time"),
                        geometry, tableName, category);
            } catch (Exception ex) {
                log.warn("invalid storm region geometry, table={}, id={}", tableName, rs.getInt("id"));
                return null;
            }
        }).stream().filter(r -> r != null).collect(Collectors.toList());
    }

    private List<String> extractIntersectionTimes(Geometry intersection, Map<String, String> coordTimeMap) {
        List<String> times = new ArrayList<String>();
        if (intersection == null || intersection.isEmpty()) {
            return times;
        }
        for (Coordinate coordinate : intersection.getCoordinates()) {
            String key = String.format("%.6f,%.6f", coordinate.x, coordinate.y);
            String time = coordTimeMap.get(key);
            if (time != null) {
                times.add(time);
            }
        }
        return times.stream().sorted().distinct().collect(Collectors.toList());
    }

    private TimeOverlap calculateTimeOverlap(String enterTime, String exitTime, String areaTimeUtc) {
        try {
            ZonedDateTime enterBeijing = LocalDateTime.parse(enterTime, TIME_FORMATTER).atZone(BEIJING_ZONE);
            ZonedDateTime exitBeijing = LocalDateTime.parse(exitTime, TIME_FORMATTER).atZone(BEIJING_ZONE);
            ZonedDateTime areaStartUtc = LocalDateTime.parse(areaTimeUtc, TIME_FORMATTER).atZone(UTC_ZONE);
            ZonedDateTime areaEndUtc = areaStartUtc.plus(AREA_VALID_HOURS, ChronoUnit.HOURS);
            ZonedDateTime areaStartBeijing = areaStartUtc.withZoneSameInstant(BEIJING_ZONE);
            ZonedDateTime areaEndBeijing = areaEndUtc.withZoneSameInstant(BEIJING_ZONE);

            ZonedDateTime overlapStart = enterBeijing.isAfter(areaStartBeijing) ? enterBeijing : areaStartBeijing;
            ZonedDateTime overlapEnd = exitBeijing.isBefore(areaEndBeijing) ? exitBeijing : areaEndBeijing;
            if (overlapStart.isAfter(overlapEnd)) {
                return null;
            }
            TimeOverlap overlap = new TimeOverlap();
            overlap.enterTime = overlapStart.format(TIME_FORMATTER);
            overlap.exitTime = overlapEnd.format(TIME_FORMATTER);
            return overlap;
        } catch (Exception ex) {
            log.warn("storm time overlap failed, enter={}, exit={}, areaTime={}", enterTime, exitTime, areaTimeUtc);
            return null;
        }
    }

    private List<ZoneCrossingEvent> toEnterExitPair(String mmsi, int zoneId, String zoneName,
                                                   String category, String source,
                                                   String enterTime, String exitTime) {
        List<ZoneCrossingEvent> events = new ArrayList<ZoneCrossingEvent>();
        events.add(buildEvent(mmsi, zoneId, zoneName, category, source, ZoneCrossingEvent.ENTER, enterTime));
        events.add(buildEvent(mmsi, zoneId, zoneName, category, source, ZoneCrossingEvent.EXIT, exitTime));
        return events;
    }

    private ZoneCrossingEvent buildEvent(String mmsi, int zoneId, String zoneName, String category,
                                         String source, String eventType, String eventTime) {
        ZoneCrossingEvent event = new ZoneCrossingEvent();
        event.setMmsi(mmsi);
        event.setZoneId(zoneId);
        event.setZoneName(zoneName);
        event.setEventCategory(category);
        event.setEventType(eventType);
        event.setEventTime(eventTime);
        event.setZoneSource(source);
        return event;
    }

    private Map<String, String> buildCoordTimeMap(List<TrajectoryPoint> points) {
        Map<String, String> map = new HashMap<String, String>();
        for (TrajectoryPoint point : points) {
            map.putIfAbsent(String.format("%.6f,%.6f", point.getLon(), point.getLat()), point.getTime());
        }
        return map;
    }

    private LineString buildLineString(List<TrajectoryPoint> points) {
        Coordinate[] coordinates = new Coordinate[points.size()];
        for (int i = 0; i < points.size(); i++) {
            TrajectoryPoint point = points.get(i);
            coordinates[i] = new Coordinate(point.getLon(), point.getLat());
        }
        return GEOMETRY_FACTORY.createLineString(coordinates);
    }

    private List<TrajectoryPoint> toTrajectoryPoints(Vector<NewShipForm> points) {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        for (NewShipForm point : points) {
            if (point == null || StringUtils.isBlank(point.getLo()) || StringUtils.isBlank(point.getLa())
                    || StringUtils.isBlank(point.getTi())) {
                continue;
            }
            TrajectoryPoint tp = new TrajectoryPoint();
            tp.setLon(Double.parseDouble(point.getLo().trim()));
            tp.setLat(Double.parseDouble(point.getLa().trim()));
            tp.setTime(normalizeTime(point.getTi()));
            result.add(tp);
        }
        return result;
    }

    private List<List<TrajectoryPoint>> segmentTrajectory(List<TrajectoryPoint> points, int thresholdHours) {
        List<List<TrajectoryPoint>> segments = new ArrayList<List<TrajectoryPoint>>();
        List<TrajectoryPoint> current = new ArrayList<TrajectoryPoint>();
        current.add(points.get(0));
        for (int i = 1; i < points.size(); i++) {
            TrajectoryPoint prev = points.get(i - 1);
            TrajectoryPoint curr = points.get(i);
            long hours = ChronoUnit.HOURS.between(
                    LocalDateTime.parse(prev.getTime(), TIME_FORMATTER),
                    LocalDateTime.parse(curr.getTime(), TIME_FORMATTER));
            if (hours > thresholdHours) {
                segments.add(current);
                current = new ArrayList<TrajectoryPoint>();
            }
            current.add(curr);
        }
        if (!current.isEmpty()) {
            segments.add(current);
        }
        return segments;
    }

    private String normalizeTime(String time) {
        String trimmed = time.trim();
        return trimmed.length() == 16 ? trimmed + ":00" : trimmed;
    }

    private static class TrajectoryPoint {
        private double lon;
        private double lat;
        private String time;

        public double getLon() {
            return lon;
        }

        public void setLon(double lon) {
            this.lon = lon;
        }

        public double getLat() {
            return lat;
        }

        public void setLat(double lat) {
            this.lat = lat;
        }

        public String getTime() {
            return time;
        }

        public void setTime(String time) {
            this.time = time;
        }
    }

    private static class TimeOverlap {
        private String enterTime;
        private String exitTime;
    }
}
