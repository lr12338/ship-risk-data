package com.hifleet.service;

import com.hifleet.hbase.GetTrajectoryFromHbase;
import com.hifleet.hbase.NewOffersForm;
import com.hifleet.route.beans.NewShipForm;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * 基于 HBase 轨迹与数据库区域定义，统计船舶穿过区域的数据。
 */
@Service
public class ZoneCrossingStatsService {

    private static final Logger log = LoggerFactory.getLogger(ZoneCrossingStatsService.class);
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final WKTReader WKT_READER = new WKTReader(GEOMETRY_FACTORY);

    private final GetTrajectoryFromHbase getTrajectoryFromHbase = new GetTrajectoryFromHbase();

    @Autowired
    @Qualifier("pgJdbcTemplate")
    private JdbcTemplate pgJdbcTemplate;

    public boolean isCrossingZone(String mmsi, String startTime, String endTime, String zoneWkt) {
        NewOffersForm offers = getTrajectoryFromHbase.getOriginalVoffersFromHbase(mmsi, startTime, endTime);
        Vector<NewShipForm> points = offers.getOffers();
        if (points == null || points.size() < 2) {
            return false;
        }
        try {
            Polygon zone = (Polygon) WKT_READER.read(zoneWkt);
            LineString route = buildRouteLine(points);
            return route.intersects(zone);
        } catch (Exception ex) {
            log.error("isCrossingZone failed, mmsi={}", mmsi, ex);
            return false;
        }
    }

    public void calculateZoneCrossingStats(String params) {
        log.info("zone crossing stats job started, params={}", params);
        // TODO: 从数据库加载区域与船舶列表，调用 isCrossingZone 并写入统计表
    }

    private LineString buildRouteLine(Vector<NewShipForm> points) {
        List<Coordinate> coordinates = new ArrayList<>(points.size());
        for (NewShipForm point : points) {
            coordinates.add(new Coordinate(Double.parseDouble(point.getLo()), Double.parseDouble(point.getLa())));
        }
        return GEOMETRY_FACTORY.createLineString(coordinates.toArray(new Coordinate[0]));
    }
}
