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
import java.util.List;
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

    private TrajectoryPoint convert(NewShipForm rawPoint) {
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
                    parseDouble(rawPoint.getSp()),
                    parseDouble(rawPoint.getCo()),
                    rawPoint.getNavstatus());
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeTime(String time) {
        String trimmed = time.trim();
        return trimmed.length() == 16 ? trimmed + ":00" : trimmed;
    }

    private double parseDouble(String value) {
        if (StringUtils.isBlank(value) || "null".equalsIgnoreCase(value.trim())) {
            return 0.0;
        }
        return Double.parseDouble(value.trim());
    }
}
