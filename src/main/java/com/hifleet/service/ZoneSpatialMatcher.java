package com.hifleet.service;

import com.hifleet.domain.ZoneDefinition;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.List;

/**
 * 区域空间索引与点匹配，逻辑参考 event 项目 DimensionCache#matchZone。
 */
public class ZoneSpatialMatcher {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private final STRtree spatialIndex;

    public ZoneSpatialMatcher(List<ZoneDefinition> zones) {
        STRtree index = new STRtree();
        for (ZoneDefinition zone : zones) {
            Geometry geometry = normalizeGeometry(zone == null ? null : zone.getGeometry());
            if (geometry == null) {
                continue;
            }
            index.insert(geometry.getEnvelopeInternal(),
                    new ZoneSpatialEntry(zone, geometry, PreparedGeometryFactory.prepare(geometry), Math.abs(geometry.getArea())));
        }
        index.build();
        this.spatialIndex = index;
    }

    public ZoneDefinition match(double lon, double lat) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
        List<?> candidates = spatialIndex.query(point.getEnvelopeInternal());
        ZoneSpatialEntry best = null;
        for (Object candidate : candidates) {
            ZoneSpatialEntry entry = (ZoneSpatialEntry) candidate;
            if (!entry.preparedGeometry.intersects(point) || !entry.geometry.covers(point)) {
                continue;
            }
            if (best == null || entry.area < best.area) {
                best = entry;
            }
        }
        return best == null ? null : best.zone;
    }

    private Geometry normalizeGeometry(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        if ("MultiPolygon".equals(geometry.getGeometryType()) && geometry.getNumGeometries() == 1) {
            return geometry.getGeometryN(0);
        }
        return geometry;
    }

    private static class ZoneSpatialEntry {
        private final ZoneDefinition zone;
        private final Geometry geometry;
        private final PreparedGeometry preparedGeometry;
        private final double area;

        private ZoneSpatialEntry(ZoneDefinition zone, Geometry geometry, PreparedGeometry preparedGeometry, double area) {
            this.zone = zone;
            this.geometry = geometry;
            this.preparedGeometry = preparedGeometry;
            this.area = area;
        }
    }
}
