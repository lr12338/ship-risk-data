package com.hifleet.loitering;

import com.alibaba.fastjson.JSON;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LoiteringPortSceneTest {
    @Test
    public void lowSpeedIsHardSplitWithoutRejoining() {
        LoiteringPortTrackSegmenter segmenter = new LoiteringPortTrackSegmenter(service(snapshot()), new LoiteringPortSceneProperties());
        List<List<TrajectoryPoint>> segments = segmenter.splitEligible(Arrays.asList(
                point(.05, .05, 1.6, 0), point(.05, .05, 2.0, 1), point(.05, .05, .2, 2),
                point(.05, .05, .1, 3), point(.05, .05, 1.7, 4), point(.05, .05, 2.1, 5)));
        assertEquals(2, segments.size()); assertEquals(2, segments.get(0).size()); assertEquals(2, segments.get(1).size());
        assertEquals(2, segmenter.getLastStats().getBelowMinSpeedSplitCount());
    }

    @Test
    public void operationalAndAnchorAreHardBoundaries() {
        LoiteringPortTrackSegmenter segmenter = new LoiteringPortTrackSegmenter(service(snapshot()), new LoiteringPortSceneProperties());
        List<List<TrajectoryPoint>> segments = segmenter.splitEligible(Arrays.asList(
                point(.05, .05, 2, 0), point(.2, .2, 2, 1), point(.05, .05, 2, 2),
                point(.3, .3, 2, 3), point(.05, .05, 2, 4)));
        assertEquals(3, segments.size()); assertEquals(1, segments.get(0).size()); assertEquals(1, segments.get(1).size());
        assertEquals(1, segmenter.getLastStats().getPortOperationalRejectedCount());
        assertEquals(1, segmenter.getLastStats().getAnchorRejectedCount());
    }

    @Test
    public void finalGatePassesOnlyCompleteEligibleEvidence() {
        LoiteringPortEventGate gate = new LoiteringPortEventGate(service(snapshot()), new LoiteringPortSceneProperties());
        assertEquals(1, gate.filterAndAnnotate(Collections.singletonList(trace(.05, .05, point(.05, .05, 1.5, 0), point(.06, .06, 2.5, 1)))).size());
        assertEquals(0, gate.filterAndAnnotate(Collections.singletonList(trace(.05, .05, point(.05, .05, .9, 0)))).size());
        assertEquals(0, gate.filterAndAnnotate(Collections.singletonList(trace(.2, .2, point(.2, .2, 2, 0)))).size());
        assertEquals(0, gate.filterAndAnnotate(Collections.singletonList(trace(.3, .3, point(.3, .3, 2, 0)))).size());
        assertEquals(0, gate.filterAndAnnotate(Collections.singletonList(trace(1, 1, point(1, 1, 2, 0)))).size());
        ShipBehaviorTrace accepted = trace(.05, .05, point(.05, .05, 1.5, 0), point(.06, .06, 2.5, 1));
        gate.filterAndAnnotate(Collections.singletonList(accepted));
        assertEquals("PASS", JSON.parseObject(accepted.getEvent().getScoreDetailJson()).getString("loiteringSceneDecision"));
        assertTrue(JSON.parseObject(accepted.getEvent().getScoreDetailJson()).getDoubleValue("portApproachEvidenceCoverage") == 1.0);
    }

    @Test(expected = IllegalStateException.class)
    public void emptyAnchorOrPortDataFailsClosed() {
        new LoiteringPortSceneLoader(null, null, new LoiteringPortSceneProperties())
                .build(Collections.<LoiteringPortSceneLoader.Row>emptyList(), Collections.<LoiteringPortSceneLoader.Row>emptyList(), Collections.<LoiteringPortSceneSnapshot.Port>emptyList());
    }

    private LoiteringPortSceneService service(LoiteringPortSceneSnapshot snapshot) {
        LoiteringPortSceneService service = mock(LoiteringPortSceneService.class); when(service.requireSnapshot()).thenReturn(snapshot); return service;
    }
    private LoiteringPortSceneSnapshot snapshot() {
        GeometryFactory factory = new GeometryFactory(); STRtree operational = new STRtree(), anchor = new STRtree();
        add(operational, factory, .19, .19, .21, .21); add(anchor, factory, .29, .29, .31, .31); operational.build(); anchor.build();
        return new LoiteringPortSceneSnapshot(operational, anchor,
                Collections.singletonList(new LoiteringPortSceneSnapshot.Port(1, "Test Port", 0, 0)), "scene-fingerprint");
    }
    private void add(STRtree index, GeometryFactory factory, double min, double minLat, double max, double maxLat) {
        Geometry geometry = factory.createPolygon(new Coordinate[]{new Coordinate(min,minLat),new Coordinate(max,minLat),new Coordinate(max,maxLat),new Coordinate(min,maxLat),new Coordinate(min,minLat)});
        index.insert(geometry.getEnvelopeInternal(), LoiteringPortSceneSnapshot.entry(geometry, PreparedGeometryFactory.prepare(geometry)));
    }
    private ShipBehaviorTrace trace(double lon, double lat, TrajectoryPoint... points) {
        ShipBehaviorEvent event = new ShipBehaviorEvent(); event.setBehaviorType(ShipBehaviorEvent.TYPE_LOITERING); event.setRuleVersion("LOITERING_V2_PORT_APPROACH_V1"); event.setCenterLon(lon); event.setCenterLat(lat);
        return new ShipBehaviorTrace(event, Arrays.asList(points), Collections.<TrajectoryPoint>emptyList());
    }
    private TrajectoryPoint point(double lon, double lat, double speed, int minute) {
        return new TrajectoryPoint("1", LocalDateTime.of(2026,1,1,0,minute), lon, lat, speed, 0, null);
    }
}
