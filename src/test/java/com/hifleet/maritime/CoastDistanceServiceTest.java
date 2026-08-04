package com.hifleet.maritime;

import com.alibaba.fastjson.JSONObject;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CoastDistanceServiceTest {
    @Test
    public void calculatesLocalGeodesicDistanceAndFeatureIdentity() {
        GeometryFactory factory = new GeometryFactory();
        MaritimeSceneTileLoader.TileRecord coast = new MaritimeSceneTileLoader.TileRecord(
                "ocean_coastline", "coast-1", "N31_E121",
                factory.createLineString(new Coordinate[] {
                        new Coordinate(121.0, 31.0), new Coordinate(122.0, 31.0)
                }), new JSONObject());

        CoastDistanceService.Result result = new CoastDistanceService().nearest(
                121.0, 31.5, Collections.singletonList(coast), 60.0);

        assertTrue(result.isAvailable());
        assertEquals("coast-1", result.getNearestFeatureId());
        assertEquals(30.0, result.getDistanceNm(), 0.3);
    }
}
