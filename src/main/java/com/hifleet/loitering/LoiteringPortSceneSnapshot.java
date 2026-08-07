package com.hifleet.loitering;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.index.strtree.STRtree;

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
        for (Port port : ports) {
            double candidate = distanceNm(lon, lat, port.lon, port.lat);
            if (candidate < distance) { distance = candidate; nearest = port; }
        }
        return new Match(operational != null, anchor != null, nearest, distance,
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
    static final class Port { final int id; final String name; final double lon; final double lat;
        Port(int id, String name, double lon, double lat) { this.id = id; this.name = name; this.lon = lon; this.lat = lat; } }
    public static final class Match {
        private final boolean operational; private final boolean anchor; private final Port port;
        private final double distanceNm; private final boolean withinPortApproach;
        Match(boolean operational, boolean anchor, Port port, double distanceNm, boolean withinPortApproach) {
            this.operational = operational; this.anchor = anchor; this.port = port;
            this.distanceNm = distanceNm; this.withinPortApproach = withinPortApproach;
        }
        public boolean isOperational() { return operational; }
        public boolean isAnchor() { return anchor; }
        public boolean isWithinPortApproach() { return withinPortApproach; }
        public boolean isEligible() { return !operational && !anchor && withinPortApproach; }
        public Integer getNearestPortId() { return port == null ? null : port.id; }
        public String getNearestPortName() { return port == null ? null : port.name; }
        public double getDistanceNm() { return distanceNm; }
    }
    private static double distanceNm(double lon1, double lat1, double lon2, double lat2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1), dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2)
                * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 3440.065 * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
    }
}
