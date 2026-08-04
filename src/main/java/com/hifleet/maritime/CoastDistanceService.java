package com.hifleet.maritime;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;

import java.util.List;

public class CoastDistanceService {
    private static final double METERS_PER_DEGREE_LATITUDE = 110574.0;
    private static final double METERS_PER_NAUTICAL_MILE = 1852.0;

    public Result nearest(
            double longitude,
            double latitude,
            List<MaritimeSceneTileLoader.TileRecord> coastRecords,
            double searchRadiusNm) {
        double bestMeters = Double.POSITIVE_INFINITY;
        String featureId = null;
        String tileId = null;
        if (coastRecords != null) {
            for (MaritimeSceneTileLoader.TileRecord record : coastRecords) {
                double distance = distanceToGeometryMeters(longitude, latitude, record.getGeometry());
                if (distance < bestMeters) {
                    bestMeters = distance;
                    featureId = record.getSourceFeatureId();
                    tileId = record.getTileId();
                }
            }
        }
        boolean available = Double.isFinite(bestMeters)
                && bestMeters <= searchRadiusNm * METERS_PER_NAUTICAL_MILE;
        return new Result(
                available ? bestMeters / METERS_PER_NAUTICAL_MILE : null,
                featureId, tileId, searchRadiusNm, available);
    }

    public static double distanceToGeometryMeters(double longitude, double latitude, Geometry geometry) {
        Coordinate[] coordinates = geometry == null ? new Coordinate[0] : geometry.getCoordinates();
        if (coordinates.length == 0) return Double.POSITIVE_INFINITY;
        if (coordinates.length == 1) return pointDistance(longitude, latitude, coordinates[0].x, coordinates[0].y);
        double best = Double.POSITIVE_INFINITY;
        for (int i = 1; i < coordinates.length; i++) {
            best = Math.min(best, segmentDistance(
                    longitude, latitude, coordinates[i - 1], coordinates[i]));
        }
        return best;
    }

    private static double segmentDistance(
            double longitude, double latitude, Coordinate start, Coordinate end) {
        double cosLatitude = Math.max(0.01, Math.cos(Math.toRadians(latitude)));
        double x1 = (start.x - longitude) * 111320.0 * cosLatitude;
        double y1 = (start.y - latitude) * METERS_PER_DEGREE_LATITUDE;
        double x2 = (end.x - longitude) * 111320.0 * cosLatitude;
        double y2 = (end.y - latitude) * METERS_PER_DEGREE_LATITUDE;
        double dx = x2 - x1;
        double dy = y2 - y1;
        double denominator = dx * dx + dy * dy;
        double ratio = denominator == 0.0 ? 0.0 : -(x1 * dx + y1 * dy) / denominator;
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        double x = x1 + ratio * dx;
        double y = y1 + ratio * dy;
        return Math.sqrt(x * x + y * y);
    }

    private static double pointDistance(
            double longitude, double latitude, double otherLongitude, double otherLatitude) {
        double cosLatitude = Math.max(0.01, Math.cos(Math.toRadians(latitude)));
        double x = (otherLongitude - longitude) * 111320.0 * cosLatitude;
        double y = (otherLatitude - latitude) * METERS_PER_DEGREE_LATITUDE;
        return Math.sqrt(x * x + y * y);
    }

    public static final class Result {
        private final Double distanceNm;
        private final String nearestFeatureId;
        private final String nearestTileId;
        private final double searchRadiusNm;
        private final boolean available;

        private Result(Double distanceNm, String nearestFeatureId, String nearestTileId,
                       double searchRadiusNm, boolean available) {
            this.distanceNm = distanceNm;
            this.nearestFeatureId = nearestFeatureId;
            this.nearestTileId = nearestTileId;
            this.searchRadiusNm = searchRadiusNm;
            this.available = available;
        }

        public Double getDistanceNm() { return distanceNm; }
        public String getNearestFeatureId() { return nearestFeatureId; }
        public String getNearestTileId() { return nearestTileId; }
        public double getSearchRadiusNm() { return searchRadiusNm; }
        public boolean isAvailable() { return available; }
    }
}
