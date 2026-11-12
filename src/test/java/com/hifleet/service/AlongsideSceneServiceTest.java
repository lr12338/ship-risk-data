package com.hifleet.service;

import com.hifleet.domain.ZoneDefinition;
import org.junit.Test;
import org.locationtech.jts.io.WKTReader;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AlongsideSceneServiceTest {
    @Test
    public void missingPortOrAnchorDataReturnsIncompleteContext() {
        AlongsideSceneService service = new AlongsideSceneService();
        ReflectionTestUtils.setField(service, "zoneLoaderService", new EmptyZoneLoader());

        AlongsideSceneService.Scene scene = service.classify(120.0, 30.0);

        assertFalse(scene.isDataAvailable());
        assertFalse(scene.isOpenSea());
        assertFalse(scene.isOutsideKnownPortAndAnchor());
        assertEquals("UNKNOWN", scene.getSceneType());
    }

    @Test
    public void outsideLoadedZonesIsNotCalledOpenSea() throws Exception {
        AlongsideSceneService service = new AlongsideSceneService();
        ReflectionTestUtils.setField(service, "zoneLoaderService", new LoadedZoneLoader());

        AlongsideSceneService.Scene scene = service.classify(120.0, 30.0);

        assertTrue(scene.isDataAvailable());
        assertTrue(scene.isOutsideKnownPortAndAnchor());
        assertFalse(scene.isOpenSea());
    }

    private static class EmptyZoneLoader extends ZoneLoaderService {
        @Override public List<ZoneDefinition> loadPortZones(List<Integer> zoneIds) { return Collections.emptyList(); }
        @Override public List<ZoneDefinition> loadAnchorZones() { return Collections.emptyList(); }
    }

    private static class LoadedZoneLoader extends ZoneLoaderService {
        @Override public List<ZoneDefinition> loadPortZones(List<Integer> zoneIds) { return oneZone(); }
        @Override public List<ZoneDefinition> loadAnchorZones() { return oneZone(); }

        private List<ZoneDefinition> oneZone() {
            try {
                ZoneDefinition zone = new ZoneDefinition();
                zone.setGeometry(new WKTReader().read("POLYGON ((0 0, 1 0, 1 1, 0 1, 0 0))"));
                return Collections.singletonList(zone);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
