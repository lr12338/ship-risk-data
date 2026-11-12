package com.hifleet.trajectory;

import com.hifleet.hbase.GetTrajectoryFromHbase;
import com.hifleet.hbase.NewOffersForm;
import com.hifleet.route.beans.NewShipForm;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * 轨迹类业务统一从这里读取和转换 HBase 轨迹。
 */
@Service
public class TrajectoryLoaderService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GetTrajectoryFromHbase getTrajectoryFromHbase = new GetTrajectoryFromHbase();

    public List<TrajectoryPoint> load(String mmsi, String startTime, String endTime, int simplifyZoom) {
        NewOffersForm offers;
        if (simplifyZoom > 0) {
            offers = getTrajectoryFromHbase.getVoffersFromHbase(mmsi, startTime, endTime, simplifyZoom);
        } else {
            offers = getTrajectoryFromHbase.getOriginalVoffersFromHbase(mmsi, startTime, endTime);
        }
        if (offers == null || offers.getOffers() == null) {
            return Collections.emptyList();
        }
        return convert(offers.getOffers());
    }

    public List<TrajectoryPoint> loadSafely(String mmsi, String startTime, String endTime,
                                            int simplifyZoom, int sliceHours, int maxPointsPerWindow) {
        LocalDateTime start = LocalDateTime.parse(normalizeTime(startTime), TIME_FORMATTER);
        LocalDateTime end = LocalDateTime.parse(normalizeTime(endTime), TIME_FORMATTER);
        int hours = Math.max(1, sliceHours);
        Map<String, TrajectoryPoint> merged = new LinkedHashMap<String, TrajectoryPoint>();
        LocalDateTime cursor = start;
        while (cursor.isBefore(end)) {
            LocalDateTime next = cursor.plusHours(hours);
            if (next.isAfter(end)) {
                next = end;
            }
            List<TrajectoryPoint> part = load(mmsi, cursor.format(TIME_FORMATTER), next.format(TIME_FORMATTER),
                    simplifyZoom);
            for (TrajectoryPoint point : part) {
                if (point == null || point.getTime() == null) {
                    continue;
                }
                merged.put(pointKey(point), point);
            }
            if (maxPointsPerWindow > 0 && merged.size() > maxPointsPerWindow) {
                throw new IllegalStateException("trajectory points exceed limit, mmsi=" + mmsi
                        + ", startTime=" + startTime
                        + ", endTime=" + endTime
                        + ", maxPoints=" + maxPointsPerWindow
                        + ", actualPoints=" + merged.size());
            }
            cursor = next;
        }
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>(merged.values());
        Collections.sort(result, (a, b) -> a.getTime().compareTo(b.getTime()));
        return result;
    }

    private List<TrajectoryPoint> convert(Vector<NewShipForm> rawPoints) {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        for (NewShipForm rawPoint : rawPoints) {
            TrajectoryPoint point = convert(rawPoint);
            if (point != null) {
                result.add(point);
            }
        }
        Collections.sort(result, (a, b) -> a.getTime().compareTo(b.getTime()));
        return result;
    }

    TrajectoryPoint convert(NewShipForm rawPoint) {
        if (rawPoint == null
                || StringUtils.isBlank(rawPoint.getM())
                || StringUtils.isBlank(rawPoint.getTi())
                || StringUtils.isBlank(rawPoint.getLo())
                || StringUtils.isBlank(rawPoint.getLa())) {
            return null;
        }
        try {
            return new TrajectoryPoint(
                    rawPoint.getM(),
                    LocalDateTime.parse(normalizeTime(rawPoint.getTi()), TIME_FORMATTER),
                    Double.parseDouble(rawPoint.getLo().trim()),
                    Double.parseDouble(rawPoint.getLa().trim()),
                    parseNullableDouble(rawPoint.getSp()),
                    parseNullableDouble(rawPoint.getCo()),
                    parseNullableDouble(rawPoint.getHdg()),
                    rawPoint.getNavstatus());
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeTime(String time) {
        String trimmed = time.trim();
        return trimmed.length() == 16 ? trimmed + ":00" : trimmed;
    }

    private String pointKey(TrajectoryPoint point) {
        return point.getMmsi() + "|" + point.getTime() + "|" + point.getLon() + "|" + point.getLat();
    }

    private Double parseNullableDouble(String value) {
        if (StringUtils.isBlank(value) || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return Double.parseDouble(value.trim());
    }
}
