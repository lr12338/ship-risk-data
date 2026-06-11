package com.hifleet.service;

import com.hifleet.domain.ZoneCrossingEvent;
import com.hifleet.route.beans.NewShipForm;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
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
 * 步骤1：大风浪 / 大风区域进出计算。
 * <p>
 * 算法参考 hifleet.maintainance.tools 项目 {@code StormAreaIntersectService}：
 * 1. HBase 读取轨迹
 * 2. 轨迹按 8 小时切段
 * 3. PostgreSQL regions_wave / regions_wind 做 ST_Intersection
 * 4. 交集首尾时间映射为进入/离开时间（区域有效时段 6 小时）
 */
@Service
public class StormWavePassageService {

    private static final Logger log = LoggerFactory.getLogger(StormWavePassageService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final ZoneId UTC_ZONE = ZoneId.of("UTC");
    private static final int SEGMENT_THRESHOLD_HOURS = 8;

    @Autowired
    @Qualifier("pgJdbcTemplate")
    private JdbcTemplate pgJdbcTemplate;

    /**
     * 计算单船在大风浪/大风区域内的进出事件。
     *
     * @param mmsi   船舶 MMSI
     * @param points 轨迹点（由调用方在 for 循环中统一从 HBase 查询后传入，避免重复查库）
     */
    public List<ZoneCrossingEvent> calculateStormPassage(String mmsi, Vector<NewShipForm> points) {
        if (points == null || points.size() < 2) {
            return Collections.emptyList();
        }

        List<TrajectoryPoint> trajectoryPoints = toTrajectoryPoints(points);
        List<List<TrajectoryPoint>> segments = segmentTrajectory(trajectoryPoints, SEGMENT_THRESHOLD_HOURS);

        List<ZoneCrossingEvent> allEvents = new ArrayList<ZoneCrossingEvent>();
        for (List<TrajectoryPoint> segment : segments) {
            if (segment.size() < 2) {
                continue;
            }
            String lineWkt = buildLineStringWkt(segment);
            String segStart = segment.get(0).getTime();
            String segEnd = segment.get(segment.size() - 1).getTime();

            allEvents.addAll(calculateByTable(mmsi, segment, lineWkt, segStart, segEnd, "regions_wave",
                    ZoneCrossingEvent.CATEGORY_STORM_WAVE));
            allEvents.addAll(calculateByTable(mmsi, segment, lineWkt, segStart, segEnd, "regions_wind",
                    ZoneCrossingEvent.CATEGORY_STORM_WIND));
        }
        return allEvents;
    }

    private List<ZoneCrossingEvent> calculateByTable(String mmsi,
                                                     List<TrajectoryPoint> segment,
                                                     String lineWkt,
                                                     String segStart,
                                                     String segEnd,
                                                     String tableName,
                                                     String category) {
        List<StormAreaRef> candidates = loadCandidateAreas(segStart, segEnd, lineWkt, tableName);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, String> coordTimeMap = buildCoordTimeMap(segment);
        List<ZoneCrossingEvent> events = new ArrayList<ZoneCrossingEvent>();

        String placeholders = candidates.stream().map(a -> "?").collect(Collectors.joining(","));
        List<Object> params = new ArrayList<Object>();
        params.add(lineWkt);
        for (StormAreaRef area : candidates) {
            params.add(area.id);
        }
        params.add(segEnd);
        params.add(segStart);

        String sql = "SELECT sa.id AS area_id, sa.name AS area_name, sa.time AS area_time, " +
                "ST_AsText(ST_Intersection(sa.geom, traj.geom)) AS intersection " +
                "FROM " + tableName + " sa, " +
                "(SELECT ST_SetSRID(ST_GeometryFromText(?, 4326), 4326) AS geom) AS traj " +
                "WHERE sa.id IN (" + placeholders + ") " +
                "AND ST_Intersects(sa.geom, traj.geom) " +
                "AND (to_timestamp(sa.time, 'yyyy-MM-dd HH24:mi:ss') AT TIME ZONE 'UTC') < " +
                "    (to_timestamp(?, 'yyyy-MM-dd HH24:mi:ss') AT TIME ZONE 'Asia/Shanghai' AT TIME ZONE 'UTC') " +
                "AND (to_timestamp(sa.time, 'yyyy-MM-dd HH24:mi:ss') AT TIME ZONE 'UTC' + INTERVAL '6 hours') > " +
                "    (to_timestamp(?, 'yyyy-MM-dd HH24:mi:ss') AT TIME ZONE 'Asia/Shanghai' AT TIME ZONE 'UTC')";

        try {
            pgJdbcTemplate.query(sql, params.toArray(), (ResultSet rs) -> {
                while (rs.next()) {
                    int areaId = rs.getInt("area_id");
                    String areaName = rs.getString("area_name");
                    String areaTimeUtc = rs.getString("area_time");
                    String intersectionWkt = rs.getString("intersection");

                    List<String> intersectionTimes = extractIntersectionTimes(intersectionWkt, coordTimeMap);
                    if (intersectionTimes.isEmpty()) {
                        continue;
                    }
                    String enterTime = intersectionTimes.get(0);
                    String exitTime = intersectionTimes.get(intersectionTimes.size() - 1);
                    TimeOverlap overlap = calculateTimeOverlap(enterTime, exitTime, areaTimeUtc);
                    if (overlap == null) {
                        continue;
                    }
                    events.addAll(toEnterExitPair(mmsi, areaId, areaName, category, tableName,
                            overlap.enterTime, overlap.exitTime));
                }
                return null;
            });
        } catch (Exception ex) {
            log.error("storm passage failed, mmsi={}, table={}", mmsi, tableName, ex);
        }
        return events;
    }

    private List<StormAreaRef> loadCandidateAreas(String startBj, String endBj, String lineWkt, String tableName) {
        String sql = "SELECT id, name, time FROM " + tableName + " WHERE " +
                "(to_timestamp(time, 'yyyy-MM-dd HH24:mi:ss') AT TIME ZONE 'UTC') < " +
                "(to_timestamp(?, 'yyyy-MM-dd HH24:mi:ss') AT TIME ZONE 'Asia/Shanghai' AT TIME ZONE 'UTC') AND " +
                "(to_timestamp(time, 'yyyy-MM-dd HH24:mi:ss') AT TIME ZONE 'UTC' + INTERVAL '6 hours') > " +
                "(to_timestamp(?, 'yyyy-MM-dd HH24:mi:ss') AT TIME ZONE 'Asia/Shanghai' AT TIME ZONE 'UTC') AND " +
                "ST_Intersects(ST_SetSRID(ST_GeometryFromText(?, 4326), 4326), geom)";
        try {
            return pgJdbcTemplate.query(sql, new Object[]{endBj, startBj, lineWkt}, (rs, rowNum) -> {
                StormAreaRef ref = new StormAreaRef();
                ref.id = rs.getInt("id");
                ref.name = rs.getString("name");
                ref.time = rs.getString("time");
                return ref;
            });
        } catch (Exception ex) {
            log.error("load candidate storm areas failed, table={}", tableName, ex);
            return Collections.emptyList();
        }
    }

    private List<String> extractIntersectionTimes(String intersectionWkt, Map<String, String> coordTimeMap) {
        List<String> times = new ArrayList<String>();
        if (StringUtils.isBlank(intersectionWkt)) {
            return times;
        }
        if (intersectionWkt.startsWith("POINT")) {
            String coord = normalizeCoordKey(intersectionWkt.replace("POINT(", "").replace(")", "").trim());
            String time = coordTimeMap.get(coord);
            if (time != null) {
                times.add(time);
            }
        } else if (intersectionWkt.startsWith("LINESTRING")) {
            String coordsPart = intersectionWkt.replace("LINESTRING(", "").replace(")", "").trim();
            for (String coordPair : coordsPart.split(",")) {
                String key = normalizeCoordKey(coordPair.trim().replace(" ", ","));
                String time = coordTimeMap.get(key);
                if (time != null) {
                    times.add(time);
                }
            }
        }
        return times.stream().sorted().distinct().collect(Collectors.toList());
    }

    private TimeOverlap calculateTimeOverlap(String enterTime, String exitTime, String areaTimeUtc) {
        try {
            ZonedDateTime enterBeijing = LocalDateTime.parse(enterTime, TIME_FORMATTER).atZone(BEIJING_ZONE);
            ZonedDateTime exitBeijing = LocalDateTime.parse(exitTime, TIME_FORMATTER).atZone(BEIJING_ZONE);
            ZonedDateTime areaStartUtc = LocalDateTime.parse(areaTimeUtc, TIME_FORMATTER).atZone(UTC_ZONE);
            ZonedDateTime areaEndUtc = areaStartUtc.plus(6, ChronoUnit.HOURS);
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

    private String buildLineStringWkt(List<TrajectoryPoint> points) {
        StringBuilder builder = new StringBuilder("LINESTRING(");
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(points.get(i).getLon()).append(' ').append(points.get(i).getLat());
        }
        builder.append(')');
        return builder.toString();
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

    private String normalizeCoordKey(String raw) {
        String normalized = raw.trim().replace(" ", ",");
        String[] parts = normalized.split(",");
        if (parts.length >= 2) {
            return String.format("%.6f,%.6f", Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
        }
        return normalized;
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

    private static class StormAreaRef {
        private int id;
        private String name;
        private String time;
    }

    private static class TimeOverlap {
        private String enterTime;
        private String exitTime;
    }
}
