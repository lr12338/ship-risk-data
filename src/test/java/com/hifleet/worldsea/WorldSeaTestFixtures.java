package com.hifleet.worldsea;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;

final class WorldSeaTestFixtures {
    static WorldSeaMaskSnapshot snapshot() {
        GeometryFactory factory = new GeometryFactory();
        Polygon polygon = factory.createPolygon(new Coordinate[]{new Coordinate(0, 0),
                new Coordinate(10, 0), new Coordinate(10, 10), new Coordinate(0, 10), new Coordinate(0, 0)});
        STRtree index = new STRtree();
        index.insert(polygon.getEnvelopeInternal(), WorldSeaMaskSnapshot.entry(
                1, "Test Sea", "测试海", polygon, PreparedGeometryFactory.prepare(polygon)));
        index.build();
        return new WorldSeaMaskSnapshot(index, 1, 1, 0, 5,
                "WORLD_SEA_102_V1R1", "source", "fingerprint", 1,
                WorldSeaMaskLoader.COORDINATE_ASSUMPTION);
    }
    static WorldSeaMaskService service() {
        final WorldSeaMaskSnapshot snapshot = snapshot();
        return new WorldSeaMaskService(new WorldSeaMaskLoader(null, new WorldSeaMaskProperties()) {
            @Override public WorldSeaMaskSnapshot load() { return snapshot; }
        });
    }
    static WorldSeaMaskService disjointService() {
        GeometryFactory factory = new GeometryFactory();
        Polygon left = factory.createPolygon(new Coordinate[]{new Coordinate(0, 0), new Coordinate(2, 0),
                new Coordinate(2, 2), new Coordinate(0, 2), new Coordinate(0, 0)});
        Polygon right = factory.createPolygon(new Coordinate[]{new Coordinate(8, 0), new Coordinate(10, 0),
                new Coordinate(10, 2), new Coordinate(8, 2), new Coordinate(8, 0)});
        STRtree index = new STRtree();
        index.insert(left.getEnvelopeInternal(), WorldSeaMaskSnapshot.entry(1, "Left", null, left,
                PreparedGeometryFactory.prepare(left)));
        index.insert(right.getEnvelopeInternal(), WorldSeaMaskSnapshot.entry(2, "Right", null, right,
                PreparedGeometryFactory.prepare(right)));
        index.build();
        final WorldSeaMaskSnapshot snapshot = new WorldSeaMaskSnapshot(index, 2, 2, 0, 10,
                "WORLD_SEA_102_V1R1", "source", "fingerprint", 1,
                WorldSeaMaskLoader.COORDINATE_ASSUMPTION);
        return new WorldSeaMaskService(new WorldSeaMaskLoader(null, new WorldSeaMaskProperties()) {
            @Override public WorldSeaMaskSnapshot load() { return snapshot; }
        });
    }
    static WorldSeaMaskService antimeridianService() {
        GeometryFactory factory = new GeometryFactory();
        Polygon east = factory.createPolygon(new Coordinate[]{new Coordinate(179, -1), new Coordinate(180, -1),
                new Coordinate(180, 1), new Coordinate(179, 1), new Coordinate(179, -1)});
        Polygon west = factory.createPolygon(new Coordinate[]{new Coordinate(-180, -1), new Coordinate(-179, -1),
                new Coordinate(-179, 1), new Coordinate(-180, 1), new Coordinate(-180, -1)});
        STRtree index = new STRtree();
        index.insert(east.getEnvelopeInternal(), WorldSeaMaskSnapshot.entry(1, "East Dateline", null, east,
                PreparedGeometryFactory.prepare(east)));
        index.insert(west.getEnvelopeInternal(), WorldSeaMaskSnapshot.entry(2, "West Dateline", null, west,
                PreparedGeometryFactory.prepare(west)));
        index.build();
        final WorldSeaMaskSnapshot snapshot = new WorldSeaMaskSnapshot(index, 2, 2, 0, 10,
                "WORLD_SEA_102_V1R1", "source", "fingerprint", 1,
                WorldSeaMaskLoader.COORDINATE_ASSUMPTION);
        return new WorldSeaMaskService(new WorldSeaMaskLoader(null, new WorldSeaMaskProperties()) {
            @Override public WorldSeaMaskSnapshot load() { return snapshot; }
        });
    }
    private WorldSeaTestFixtures() {}
}
