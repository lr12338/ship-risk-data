package com.hifleet.loitering;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.operation.distance.DistanceOp;

import java.util.List;

public final class LoiteringPortSceneSnapshot {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private final STRtree operationalIndex;
    private final STRtree anchorIndex;
    private final List<Port> ports;
    private final String fingerprint;

    public LoiteringPortSceneSnapshot(STRtree operationalIndex, STRtree anchorIndex, List<Port> ports,
                                      String fingerprint) {
        this.operationalIndex = operationalIndex;
        this.anchorIndex = anchorIndex;
        this.ports = ports;
        this.fingerprint = fingerprint;
    }

    public Match match(double lon, double lat, double maxDistanceNm) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
        Entry operational = match(operationalIndex, point);
        Entry anchor = match(anchorIndex, point);
        Port nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        double centroidDistance = Double.POSITIVE_INFINITY;
        for (Port port : ports) {
            double candidate = port.boundaryDistanceNm(lon, lat);
            if (candidate < distance) { distance = candidate; nearest = port; }
            centroidDistance = Math.min(centroidDistance, distanceNm(lon, lat, port.lon, port.lat));
        }
        return new Match(operational != null, anchor != null, nearest, distance, centroidDistance,
                nearest != null && distance <= maxDistanceNm);
    }

    public String getFingerprint() { return fingerprint; }
    public int getPortCount() { return ports.size(); }

    private Entry match(STRtree index, Point point) {
        for (Object value : index.query(point.getEnvelopeInternal())) {
            Entry entry = (Entry) value;
            if (entry.prepared.covers(point)) return entry;
        }
        return null;
    }

    static Entry entry(Geometry geometry, PreparedGeometry prepared) { return new Entry(geometry, prepared); }
    static final class Entry { final Geometry geometry; final PreparedGeometry prepared;
        Entry(Geometry geometry, PreparedGeometry prepared) { this.geometry = geometry; this.prepared = prepared; } }
    static final class Port { final int id; final String name; final double lon; final double lat; final Geometry boundary;
        Port(int id, String name, double lon, double lat) { this(id, name, lon, lat, null); }
        Port(int id, String name, double lon, double lat, Geometry boundary) { this.id = id; this.name = name; this.lon = lon; this.lat = lat; this.boundary = boundary; }
        double boundaryDistanceNm(double lon, double lat) {
            if (boundary == null) return distanceNm(lon, lat, this.lon, this.lat);
            Coordinate nearest = DistanceOp.nearestPoints(boundary, GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat)))[0];
            return distanceNm(lon, lat, nearest.x, nearest.y);
        }
    }
    public static final class Match {
        private final boolean operational; private final boolean anchor; private final Port port;
        private final double distanceNm; private final double centroidDistanceNm; private final boolean withinPortApproach;
        Match(boolean operational, boolean anchor, Port port, double distanceNm, double centroidDistanceNm, boolean withinPortApproach) {
            this.operational = operational; this.anchor = anchor; this.port = port;
            this.distanceNm = distanceNm; this.centroidDistanceNm = centroidDistanceNm; this.withinPortApproach = withinPortApproach;
        }
        public boolean isOperational() { return operational; }
        public boolean isAnchor() { return anchor; }
        public boolean isWithinPortApproach() { return withinPortApproach; }
        public boolean isBehaviorEligible() { return !operational && !anchor; }
        public boolean isEligible() { return isBehaviorEligible() && withinPortApproach; }
        public String getSceneType() { return operational ? "PORT_OPERATIONAL" : anchor ? "ANCHORAGE" : withinPortApproach ? "PORT_APPROACH" : "OFFSHORE"; }
        public Integer getNearestPortId() { return port == null ? null : port.id; }
        public String getNearestPortName() { return port == null ? null : port.name; }
        public double getDistanceNm() { return distanceNm; }
        public double getDistanceToPortBoundaryNm() { return distanceNm; }
        public double getDistanceToPortCentroidNm() { return centroidDistanceNm; }
    }
    private static double distanceNm(double lon1, double lat1, double lon2, double lat2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1), dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2)
                * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 3440.065 * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
    }
}
